package com.jarcadia.watchdog;

import com.fasterxml.jackson.databind.JsonNode;
import com.jarcadia.rcommando.Dao;
import com.jarcadia.rcommando.DaoValue;
import com.jarcadia.rcommando.Modification;
import com.jarcadia.rcommando.RedisCommando;
import com.jarcadia.retask.annontations.RetaskChangeHandler;
import com.jarcadia.retask.annontations.RetaskParam;
import com.jarcadia.vimes.model.AlarmLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.monitor.Monitor;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class MonitoringWorker {

    private final Logger logger = LoggerFactory.getLogger(MonitoringWorker.class);

    private final RedisCommando rcommando;
    private final Map<String, MonitorDef> monitoringMap; // instance.quotewin > MonitorDef

    public MonitoringWorker(RedisCommando rcommando) {
        this.rcommando = rcommando;
        this.monitoringMap = new ConcurrentHashMap<>();

        for (Dao monitor : rcommando.getPrimaryIndex("monitor")) {
            for (DaoValue value : monitor.getAll()) {
                if (!"v".equals(value.getFieldName())) {
                    monitoringMap.put(monitor.getId() + "." + value.getFieldName(), value.as(MonitorDef.class));
                }
            }
        }
    }

    @RetaskChangeHandler(type = "monitor", field = "*")
    public void monitorChanged(@RetaskParam("object") Dao monitor, String field, @RetaskParam("after") MonitorDef def) {
        monitoringMap.put(monitor.getId() + "." + field, def);
        // TODO process all records for changes
    }

    @RetaskChangeHandler(type = "instance", field = "*")
    public void checkInstanceMonitoring(@RetaskParam("object") Dao instance, String field, JsonNode after) {
        check(instance, field, after);
    }

    @RetaskChangeHandler(type = "group", field = "*")
    public void checkGroupMonitoring(@RetaskParam("object") Dao group, String field, JsonNode after) {
        check(group, field, after);
    }

    private void check(Dao dao, String fieldName, JsonNode after) {
        // Extract the app from the instance ID so loading the proxy isn't required
        String app = dao.getId().substring(0, dao.getId().indexOf("."));
        String key = dao.getType() + "." + app + "." + fieldName;
        MonitorDef def = monitoringMap.get(key);
        if (def != null) {
            final String alarmFieldName = "alarm." + fieldName;
            final AlarmLevel level = gauge(dao, def, after);
            Optional<Modification> levelModification = level == null ? dao.clear(alarmFieldName) : dao.set(alarmFieldName, level);
            if (levelModification.isPresent()) {
                final String alarmId = dao.getType() + "." + dao.getId() + "." + fieldName;
                Dao alarm = rcommando.getDao("alarm", alarmId);
                if (level == null) {
                    alarm.delete();
                } else {
                    alarm.setTs("subject", dao, "field", fieldName, "level", level);
                }
            }
        } else {
            logger.trace("{} is not a monitored field", key);
        }
    }

    private AlarmLevel gauge(Dao dao, MonitorDef def, JsonNode value) {
        switch(def.getType()) {
            case VALUE:
                return gaugeByValue(dao, def, value.asText());
            default:
                throw new UnsupportedOperationException("Gauging monitor of type " + def.getType() + " is not implemented");
        }
    }

    private AlarmLevel gaugeByValue(Dao dao, MonitorDef def, String value) {
        if (def.getPanic().contains(value)) {
            return AlarmLevel.PANIC;
        } else if (def.getCritical().contains(value)) {
            return AlarmLevel.CRITICAL;
        } else if (def.getWarn().contains(value)) {
            return AlarmLevel.WARN;
        } else if (def.getAttention().contains(value)) {
            return AlarmLevel.ATTENTION;
        } else {
            return null;
        }
    }
}
