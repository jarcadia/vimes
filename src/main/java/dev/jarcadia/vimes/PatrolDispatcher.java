package dev.jarcadia.vimes;

import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jarcadia.redao.Dao;
import dev.jarcadia.redao.DaoValue;
import dev.jarcadia.redao.ProxyIndex;
import dev.jarcadia.redao.RedaoCommando;
import dev.jarcadia.redao.proxy.Proxy;
import dev.jarcadia.retask.HandlerMethod;
import dev.jarcadia.retask.Retask;
import dev.jarcadia.retask.Task;
import dev.jarcadia.retask.annontations.RetaskChangeHandler;
import dev.jarcadia.retask.annontations.RetaskDeleteHandler;
import dev.jarcadia.retask.annontations.RetaskInsertHandler;
import dev.jarcadia.retask.annontations.RetaskWorker;
import dev.jarcadia.vimes.annontation.GroupPatrol;
import dev.jarcadia.vimes.annontation.InstancePatrol;
import dev.jarcadia.vimes.exception.WatchdogException;
import dev.jarcadia.vimes.model.Group;
import dev.jarcadia.vimes.model.Instance;
import dev.jarcadia.vimes.model.AppAssignable;

import io.netty.util.internal.ThreadLocalRandom;

@RetaskWorker
public class PatrolDispatcher {
    
    private final Logger logger = LoggerFactory.getLogger(PatrolDispatcher.class);

//    private final List<ActivePatrol> instancePatrols;
//    private final List<ActivePatrol> groupPatrols;
    
//    private final Map<String, HandlerMethod> instancePatrolsHandlers;
    
    private final RedaoCommando rcommando;
    private final ProxyIndex<Instance> instanceSet;
    private final ProxyIndex<Group> groupSet;
    private final ProxyIndex<Patrol> instancePatrolSet;
    private final ProxyIndex<Patrol> groupPatrolSet;
    private final ProxyIndex<ActivePatrol> activePatrolSet;
    

    protected PatrolDispatcher(RedaoCommando rcommando, List<HandlerMethod> instancePatrolHandlers, List<HandlerMethod> groupPatrolHandlers) {
    	this.rcommando = rcommando;
    	this.instanceSet = rcommando.getPrimaryIndex("instance", Instance.class);
    	this.groupSet = rcommando.getPrimaryIndex("group", Group.class);
    	this.instancePatrolSet = rcommando.getPrimaryIndex("patrol.instance", Patrol.class);
    	this.groupPatrolSet = rcommando.getPrimaryIndex("patrol.group", Patrol.class);
    	this.activePatrolSet = rcommando.getPrimaryIndex("patrol.active", ActivePatrol.class);
    	
    	// Insert/update all registered Instance Patrols
        for (HandlerMethod patrol : instancePatrolHandlers) {
        	Patrol record = instancePatrolSet.get(patrol.getRoutingKey());
            InstancePatrol annontation = (InstancePatrol) patrol.getAnnontation();
        	record.setup(annontation.app(), annontation.interval(), annontation.unit(), annontation.properties());
        }
        
    	// Insert/update all defined Group Patrols
        for (HandlerMethod patrol : groupPatrolHandlers) {
        	Patrol record = groupPatrolSet.get(patrol.getRoutingKey());
            GroupPatrol annontation = (GroupPatrol) patrol.getAnnontation();
        	record.setup(annontation.app(), annontation.interval(), annontation.unit(), annontation.properties());
        }
    }
    
    @RetaskChangeHandler(type = "patrol.instance", field = "*")
    public void instancePatrolChanged(Patrol object, String field) {
        handlePatrolInsertedOrUpdated(object, field, instanceSet);
    }
    
    @RetaskChangeHandler(type = "patrol.group", field = "*")
    public void groupPatrolChanged(Patrol object, String field) {
    	handlePatrolInsertedOrUpdated(object, field, groupSet);
    }
    
    public void handlePatrolInsertedOrUpdated(Patrol patrol, String field, ProxyIndex<? extends AppAssignable> set) {
    	 logger.info("Detected change on patrol {}", patrol.getId());
         
         // If the app changed, all the active instances of this patrol must be cancelled
         if ("app".equals(field)) {
             activePatrolSet.stream()
                     .filter(activePatrol -> patrol.equals(activePatrol.getPatrol()))
                     .forEach(activePatrol -> activePatrol.delete());
         }
         
         /* 
          * Dispatch this updated/inserted patrol for all instances 
          * 
          * If this is a new patrol, or the app changed, then this will be the initial dispatch.
          * Otherwise, one of the other patrol related fields (interval, unit, properties) changed
          * and this will update those active patrol records, which will redispatch the currently active patrols
          */
          set.stream()
              .filter(group -> patrol.getApp().equals(group.getApp()))
              .forEach(instance -> activatePatrol(instance, patrol));
    }
    

    @RetaskDeleteHandler("patrol.instance")
    public void instancePatrolDeleted(String patrolId) {
    	handleDeletedPatrol(patrolId);
    }
    
    @RetaskDeleteHandler("patrol.group")
    public void groupPatrolDeleted(String patrolId) {
    	handleDeletedPatrol(patrolId);
    }
    
    private void handleDeletedPatrol(String patrolId) {
    	activePatrolSet.stream()
            .filter(activePatrol -> patrolId.equals(activePatrol.getPatrol().getId()))
            .forEach(activePatrol -> activePatrol.delete());
    }
    
    @RetaskInsertHandler("instance")
    public void instanceInserted(Instance object) {
    	handleItemInserted(object, instancePatrolSet);
    }
    
    @RetaskInsertHandler("group")
    public void groupInserted(Instance object) {
    	handleItemInserted(object, groupPatrolSet);
    }
    
    private void handleItemInserted(AppAssignable item, ProxyIndex<Patrol> patrolSet) {
    	patrolSet.stream()
            .filter(patrol -> item.getApp().equals(patrol.getApp()))
            .forEach(patrol -> {
                logger.info("Insertion of {} {} requires activation of {}", item.getApp(), item.getId(), patrol.getId());
                activatePatrol(item, patrol);
            });
    }
    
    @RetaskChangeHandler(type = "instance", field = "*")
    public void instanceFieldChanged(Instance object, String field) {
    	handleItemFieldChanged(object, field);
    }
    
    @RetaskChangeHandler(type = "group", field = "*")
    public void groupFieldChanged(Group object, String field) {
    	handleItemFieldChanged(object, field);
    }
    
    private void handleItemFieldChanged(AppAssignable item, String field) {
        activePatrolSet.stream()
        		.filter(activePatrol -> item.getId().equals(activePatrol.getTarget().getId()))
        		.filter(activePatrol -> activePatrol.getFields().contains(field))
        		.forEach(activePatrol -> {
        			logger.info("Change to {}.{} requires update to {}", item.getId(), field, activePatrol.getPatrol().getId());
        			activePatrol.setValues(getRawValues(item, activePatrol.getPatrol().getFields()));
        		});
    }
    
    @RetaskDeleteHandler("instance")
    public void instanceDeleted(String instanceId) {
    	handleItemDeleted(instanceId);
    }
    
    @RetaskDeleteHandler("group")
    public void groupDeleted(String groupId) {
    	handleItemDeleted(groupId);
    }
    
    private void handleItemDeleted(String itemId) {
    	activePatrolSet.stream()
            .filter(activePatrol -> itemId.equals(activePatrol.getTarget().getId()))
            .forEach(activePatrol -> {
                logger.info("Deletion of {} {} requires deletion of patrol {}", activePatrol.getApp(), itemId, activePatrol.getPatrol().getId());
                activePatrol.delete();
            });
    }
    
    private void activatePatrol(AppAssignable item, Patrol patrol) {
    	ActivePatrol record = activePatrolSet.get(patrol.getId() + "->" + item.getId());
    	String[] values = getRawValues(item, patrol.getFields());
    	record.setup(patrol.getApp(), item, patrol, patrol.getInterval(), patrol.getUnit(), patrol.getFields(), values);
    }
    
    private String[] getRawValues(AppAssignable item, String[] fields) {
		String[] values = new String[fields.length];
		Iterator<DaoValue> iter = item.getDao().get(fields).iterator();
		for (int i=0; i<values.length; i++) {
			values[i] = iter.next().getRawValue();
		}
		return values;
    }
    
    @RetaskInsertHandler("patrol.active")
    public Task activePatrolCreated(Retask retask, ActivePatrol object) {
    	logger.info("Active patrol {} inserted", object.getId());
    	return createOrUpdateActivePatrol(retask, object);
    }
    
    @RetaskChangeHandler(type = "patrol.active", field = "*")
    public Task activePatrolValuesChange(Retask retask, ActivePatrol object, String field, Object before) {
    	if (before != null) {
            logger.info("Active patrol {}.{} changed (was {})", object.getId(), field, before);
            return createOrUpdateActivePatrol(retask, object);
    	} else {
    		return null;
    	}
    }
    
    private Task createOrUpdateActivePatrol(Retask retask, ActivePatrol activePatrol) {
    	Dao dao = activePatrol.getTarget();
    	Patrol patrol = activePatrol.getPatrol();

    	// Use the active patrol ID as the recur key
    	String recurKey = activePatrol.getId();
    	long delayMillis = ThreadLocalRandom.current().nextLong(patrol.getUnit().toMillis(patrol.getInterval())) + 5000;
    	Task task = Task.create(patrol.getId())
    			.in(delayMillis, TimeUnit.MILLISECONDS)
    			.recurEvery(recurKey, patrol.getInterval(), patrol.getUnit());

    	// Lookup the handler by routingKey
    	List<HandlerMethod> handlers = retask.getHandlersByRoutingKey(patrol.getId());
    	if (handlers.size() > 1) {
    		throw new WatchdogException("Found " + handlers.size() + " handlers for patrol " + patrol.getId() + ". Generated patrol routing keys should always be unique");
    	}
    	HandlerMethod handler = handlers.get(0);
    	String[] properties = handler.getAnnontation() instanceof InstancePatrol ?
    			((InstancePatrol) handler.getAnnontation()).properties() :
    			((GroupPatrol) handler.getAnnontation()).properties();
    				
    	PatrolParamProducer paramProducer = createParamProducer(rcommando.getObjectMapper(), handler, properties);
    	paramProducer.prepareTaskParameters(task, dao);
    	logger.info("Dispatched patrol {} for {} in {}", patrol.getId(), dao.getId());	
    	return task;
    }
    
    @RetaskDeleteHandler("patrol.active")
    public void activePatrolDeleted(Retask retask, String activePatrolId) {
    	retask.revokeAuthority(activePatrolId);
    	logger.info("Cancelled patrol {}", activePatrolId);
    }
    
    private PatrolParamProducer createParamProducer(ObjectMapper objectMapper, HandlerMethod handler, String[] properties) {
    	List<PatrolParam> params = Stream.of(handler.getMethod().getParameters())
                .filter(p -> Stream.of(properties).anyMatch(prop -> prop.equals(p.getName())))
                .map(p -> new PatrolParam(p.getName(), objectMapper.constructType(p.getParameterizedType())))
                .collect(Collectors.toList());
    	return new PatrolParamProducer(params);
    }

    private interface Patrol extends Proxy {

    	public String getApp();
    	public long getInterval();
    	public TimeUnit getUnit();
    	public String[] getFields();
    	
    	public void setup(String app, long interval, TimeUnit unit, String[] fields);
    }
    
    private interface ActivePatrol extends Proxy {
    	
    	public String getApp();
    	public Dao getTarget();
    	public Patrol getPatrol();
    	public long getInterval();
    	public TimeUnit getUnit();
    	public Set<String> getFields();
    	public String[] getValues();
    	
    	public void setup(String app, Proxy target, Patrol patrol, long interval, TimeUnit unit, String[] fields, String[] values);
    	public void setValues(String[] values);
    }
    
    
    
    
    
    
    
    
    
    
    
    
    private class PatrolParamProducer {

        private final List<PatrolParam>  params;
        
        private PatrolParamProducer(List<PatrolParam> params) {
            this.params = params;
        }
        
        private void prepareTaskParameters(Task task, Dao dao) {
            task.param(dao.getType(), dao);
            addParams(task, dao);
        }
        
        private void addParams(Task task, Dao dao) {
        	// TODO get all values in one call
            for (PatrolParam param : params) {
            	DaoValue value = dao.get(param.getName());
            	Object typed = value.isPresent() ? value.as(param.getJavaType()) : null;
                task.param(param.getName(), typed);
            }
        }
    }

   private class PatrolParam {

        private final JavaType javaType;
        private final String name;

        private PatrolParam(String name, JavaType javaType) {
            super();
            this.name = name;
            this.javaType = javaType;
        }
        
        private JavaType getJavaType() {
            return javaType;
        }
        
        private String getName() {
            return name;
        }
    }
}
