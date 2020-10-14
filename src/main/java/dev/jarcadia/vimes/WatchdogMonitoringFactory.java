package dev.jarcadia.vimes;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;

import dev.jarcadia.vimes.model.AlarmLevel;
import dev.jarcadia.vimes.annontation.MonitoredValue;
import dev.jarcadia.vimes.annontation.MonitoredValues;
import dev.jarcadia.vimes.exception.WatchdogException;
import dev.jarcadia.vimes.model.Group;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.jarcadia.redao.Dao;
import dev.jarcadia.redao.DaoValue;
import dev.jarcadia.redao.RedaoCommando;
import dev.jarcadia.redao.Modification;
import dev.jarcadia.redao.proxy.Proxy;

public class WatchdogMonitoringFactory {
	
    private final Logger logger = LoggerFactory.getLogger(WatchdogMonitoringFactory.class);

	private final RedaoCommando rcommando;

	public WatchdogMonitoringFactory(RedaoCommando rcommando) {
		this.rcommando = rcommando;
	}

	@SuppressWarnings("unchecked")
	public void setupMonitoring(Class<? extends Proxy> proxyClass) {
		if (Group.class.isAssignableFrom(proxyClass)) {
			setupMonitoredFieldForGroup((Class<? extends Group>) proxyClass);
    	}
	}
	
	public void setupMonitoredFieldForGroup(Class<? extends Group> group) {
		String groupType = group.getSimpleName().toLowerCase().replace("group", "");
		String app = group.getSimpleName().toLowerCase().replace("group", "");


		for (Method method : group.getMethods()) {
            for (MonitoredValue annontation : getMonitoredValueAnnotations(method)) {
            	setupMonitoring("group", app, method, annontation);
            }
		}
	}

	private void dedupe(String fieldName, Set<String> set, String[] array) {
		for (String s : array) {
			boolean added = set.add(s);
			if (!added) {
                throw new WatchdogException("@MonitoredValue " + fieldName + " specifies duplicate value");
			}
		}
	}
	
	private BigDecimal extractNumeric(String fieldName, String[] array) {
		if (array.length == 0) {
			return null;
		} else if (array.length == 1) {
			return new BigDecimal(array[0]);
		} else {
            throw new WatchdogException("@MonitoredValue for " + fieldName + " specifies multiple values. Only one value is allowed for numeric monitored values");
		}
	}
	
	private final LevelLookup buildLookup(String fieldName, Class<?> fieldType, MonitoredValue annotation) {
		// Check that the specified annotation values are not duplicates
		Set<String> values = new HashSet<>();
		dedupe(fieldName, values, annotation.panic());
		dedupe(fieldName, values, annotation.critical());
		dedupe(fieldName, values, annotation.warn());
		dedupe(fieldName, values, annotation.attention());
		
		if (String.class.equals(fieldType)) {
			return new ValueLookup(annotation.panic(), annotation.critical(), annotation.warn(), annotation.attention());
		} else if (Enum.class.isAssignableFrom(fieldType)) {
			// Check if enum values match the fieldType enum
			return new ValueLookup(annotation.panic(), annotation.critical(), annotation.warn(), annotation.attention());
		} else if (Double.class.equals(fieldType) || Integer.class.equals(fieldType) ||
				Long.class.equals(fieldType) || BigDecimal.class.equals(fieldType) ||
				BigInteger.class.equals(fieldType)) {
			
			BigDecimal panic = extractNumeric(fieldName, annotation.panic());
			BigDecimal critical = extractNumeric(fieldName, annotation.critical());
			BigDecimal warn = extractNumeric(fieldName, annotation.warn());
			BigDecimal attention = extractNumeric(fieldName, annotation.attention());
			
			return new RangeLookup(annotation.ascending(), panic, critical, warn, attention);
			
		} else {
			throw new WatchdogException("Invalid @MonitoredValue " + fieldName + " reference parameter of type "
                    + fieldType.getSimpleName() + ". @MonitoredValues can only reference parameters that are Strings, Enums or Numeric ");
		}
	}

	private void setupMonitoring(String setKey, String app, Method method, MonitoredValue annontation) {
		Entry<String, Class<?>> fieldEntry = getFieldName(method, annontation);
		final String fieldName = fieldEntry.getKey();
		final Class<?> fieldType = fieldEntry.getValue();

		final LevelLookup lookup = buildLookup(fieldName, fieldType, annontation);
		final String alarmName = "_" + fieldName + "Alarm";
        logger.info("Setting up monitoring on {}.{}.{} ({})", setKey, app, fieldName, fieldType.getSimpleName());
		rcommando.registerFieldChangeCallback(setKey, fieldName, (dao, field, before, after) -> {
			if (app.equals(dao.get("app").asString())) {
				AlarmLevel level = lookup.get(after.asString());
				Optional<Modification> alarmLevelChange = level == null ? dao.clear(alarmName) : dao.set(alarmName, level);
                if (alarmLevelChange.isPresent()) {
                	DaoValue value = alarmLevelChange.get().getChanges().get(0).getAfter();
                    Dao alarm = rcommando.getDao("alarm." + setKey, dao.getId() + "." + fieldName);
                	if (value.isPresent()) {
                        alarm.set(setKey, dao, "app", app, "level", level, "field", fieldName);
                	} else {
                		alarm.delete();
                	}
                }
			}
		});
	}

	private Entry<String, Class<?>> getFieldName(Method method, MonitoredValue annontation) {
		if (!"".equals(annontation.field())) {
			String fieldName = annontation.field();
			for (Parameter parameter : method.getParameters()) {
				if (fieldName.equals(parameter.getName())) {
					return Map.entry(fieldName, parameter.getType());
				}
			}
			throw new WatchdogException("Unable to find parameter " + fieldName + " in " +
					method.getDeclaringClass().getSimpleName() + "." + method.getName());
		} else if (method.getParameterCount() == 1) {
			Parameter parameter = method.getParameters()[0];
            return Map.entry(parameter.getName(), parameter.getType());
		} else {
			throw new WatchdogException("Unable to determine field for @Monitored annontation in " +
					method.getDeclaringClass().getSimpleName() + "." + method.getName() +
					". Field name is required for set methods with multiple parameters");
		}
	}

	private List<MonitoredValue> getMonitoredValueAnnotations(Method method) {
        dev.jarcadia.vimes.annontation.MonitoredValues monitoredValuesAnnotation = method.getAnnotation(MonitoredValues.class);
        if (monitoredValuesAnnotation != null) {
            return List.of(monitoredValuesAnnotation.value());
        }
        
        MonitoredValue annotation = method.getAnnotation(MonitoredValue.class);
        if (annotation != null) {
        	return List.of(annotation);
        }
        return List.of();
	}
	
	protected interface LevelLookup {
		public AlarmLevel get(String value);
	}
	
	protected static class ValueLookup implements LevelLookup {
		
		private final Map<String, AlarmLevel> map;
		
		public ValueLookup(String[] panic, String[] critical, String[] warn, String[] attention) {
			map = new HashMap<>();
			for (String p : panic) {
				map.put(p, AlarmLevel.PANIC);
			}
			for (String c : critical) {
				map.put(c, AlarmLevel.CRITICAL);
			}
			for (String w : warn) {
				map.put(w, AlarmLevel.WARN);
			}
			for (String a : attention) {
				map.put(a, AlarmLevel.ATTENTION);
			}
		}

		@Override
		public AlarmLevel get(String value) {
			return map.get(value);
		}
		
	}
	
	protected static class RangeLookup implements LevelLookup {
		
		private final boolean ascending;
		private final List<Entry<BigDecimal, AlarmLevel>> list;
		
		public RangeLookup(boolean ascending, BigDecimal panic, BigDecimal critical, BigDecimal warn, BigDecimal attention) {
			this.ascending = ascending;
            this.list = new LinkedList<>();
			if (panic != null) {
				list.add(Map.entry(panic, AlarmLevel.PANIC));
			}
			if (critical != null) {
				list.add(Map.entry(critical, AlarmLevel.CRITICAL));
			}
			if (warn != null) {
				list.add(Map.entry(warn, AlarmLevel.WARN));
			}
			if (attention != null) {
				list.add(Map.entry(attention, AlarmLevel.ATTENTION));
			}
			
			if (ascending) {
                list.sort((e1, e2) -> e1.getKey().compareTo(e2.getKey()));
			} else {
                list.sort((e1, e2) -> -e1.getKey().compareTo(e2.getKey()));
			}
		}

		@Override
		public AlarmLevel get(String strValue) {
			if (list.isEmpty()) {
				return null;
			}
			BigDecimal value = new BigDecimal(strValue);
			AlarmLevel level = null;
			for (Entry<BigDecimal, AlarmLevel> entry : list) {
				if (compare(entry.getKey(), value)) {
					break;
				} else {
                    level = entry.getValue();
				}
			}
			return level;
		}
		
		private boolean compare(BigDecimal threshold, BigDecimal value) {
			return ascending ? threshold.compareTo(value) > 0 : threshold.compareTo(value) < 0;
		}
	}
}
