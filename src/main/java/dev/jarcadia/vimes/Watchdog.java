package dev.jarcadia.vimes;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.jarcadia.rcommando.Dao;
import com.jarcadia.rcommando.RedisCommando;
import com.jarcadia.retask.Retask;
import com.jarcadia.retask.RetaskManager;
import com.jarcadia.retask.RetaskRecruiter;
import dev.jarcadia.vimes.annontation.ArtifactDiscoveryHandler;
import dev.jarcadia.vimes.annontation.GroupDiscoveryHandler;
import dev.jarcadia.vimes.annontation.GroupPatrol;
import dev.jarcadia.vimes.annontation.InstanceDiscoveryHandler;
import dev.jarcadia.vimes.annontation.InstancePatrol;

import io.lettuce.core.RedisClient;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class Watchdog {
    
    public static WatchdogManager init(RedisClient redisClient, RedisCommando rcommando, String packageName) {

		// Setup NotificationService
		NotificationService notificationService = new NotificationService(rcommando);

		// Setup rcommando internal object mapper to correctly serialize/deserialize Discovery models
    	registerDiscoveredInstanceModule(rcommando.getObjectMapper());
    	registerDiscoveredGroupModule(rcommando.getObjectMapper());
    	registerDiscoveredArtifactModule(rcommando.getObjectMapper());

    	// Setup recruitment from source package and internal package
        RetaskRecruiter recruiter = new RetaskRecruiter();
        recruiter.recruitFromPackage(packageName);
        recruiter.recruitFromPackage("com.jarcadia.watchdog");

    	// Setup recruitment of Watchdog annontations
        recruiter.registerTaskHandlerAnnotation(InstanceDiscoveryHandler.class,
        		(clazz, method, annotation) -> "discover.instances.impl");
        recruiter.registerTaskHandlerAnnotation(GroupDiscoveryHandler.class,
        		(clazz, method, annotation) -> "discover.groups.impl");
        recruiter.registerTaskHandlerAnnotation(ArtifactDiscoveryHandler.class,
        		(clazz, method, annotation) -> "discover.artifacts.impl");
		recruiter.registerTaskHandlerAnnotation(InstancePatrol.class,
       		(clazz, method, annotation) -> "patrol.instance." + clazz.getSimpleName() + "." + method.getName());
        recruiter.registerTaskHandlerAnnotation(GroupPatrol.class,
        		(clazz, method, annotation) -> "patrol.group." + clazz.getSimpleName() + "." + method.getName());
        
        RetaskManager retaskManager = Retask.init(redisClient, rcommando, recruiter);

        // Setup PatrolDispatcher
        PatrolDispatcher dispatcher = new PatrolDispatcher(rcommando,
        		retaskManager.getHandlersByAnnotation(InstancePatrol.class),
        		retaskManager.getHandlersByAnnotation(GroupPatrol.class));

        // Setup DiscoveryWorker
        DiscoveryWorker discoveryWorker = new DiscoveryWorker(notificationService);
        
//        WatchdogMonitoringFactory monitoringFactory = new WatchdogMonitoringFactory(rcommando);
//        for (Class<? extends Proxy> proxyClass : retaskManager.getDaoProxies()) {
//        	monitoringFactory.setupMonitoring(proxyClass);
//        }

        // Setup Workers
        DeploymentWorker deploymentWorker = new DeploymentWorker(rcommando, notificationService);
        MonitoringWorker monitoringWorker = new MonitoringWorker(rcommando);
		StateWatcher stateWatcher = new StateWatcher(notificationService);
        retaskManager.addWorker(dispatcher, discoveryWorker, deploymentWorker, monitoringWorker, stateWatcher);

        return new WatchdogManager(retaskManager, notificationService);
    }

    private static void registerDiscoveredInstanceModule(ObjectMapper mapper) {
        SimpleModule module = new SimpleModule();
        module.addSerializer(new DiscoveredInstanceSerializer());
        JavaType valueMapType = mapper.getTypeFactory().constructMapLikeType(Map.class, String.class, Object.class);
        module.addDeserializer(DiscoveredInstance.class, new DiscoveredInstanceDeserializer(valueMapType));
        mapper.registerModule(module);
    }

    private static void registerDiscoveredGroupModule(ObjectMapper mapper) {
        SimpleModule module = new SimpleModule();
        module.addSerializer(new DiscoveredGroupSerializer());
        JavaType valueMapType = mapper.getTypeFactory().constructMapLikeType(Map.class, String.class, Object.class);
        JavaType listType = mapper.getTypeFactory().constructCollectionLikeType(List.class, Dao.class);
		module.addDeserializer(DiscoveredGroup.class, new DiscoveredGroupDeserializer(valueMapType, listType));
        mapper.registerModule(module);
    }

    private static void registerDiscoveredArtifactModule(ObjectMapper mapper) {
        SimpleModule module = new SimpleModule();
        module.addSerializer(new DiscoveredArtifactSerializer());
        JavaType valueMapType = mapper.getTypeFactory().constructMapLikeType(Map.class, String.class, Object.class);
		module.addDeserializer(DiscoveredArtifact.class, new DiscoveredArtifactDeserializer(valueMapType));
        mapper.registerModule(module);
    }

	private static void registerMonitoredDefModule(ObjectMapper mapper) {
		SimpleModule module = new SimpleModule();
		module.addSerializer(new DiscoveredInstanceSerializer());
		JavaType valueMapType = mapper.getTypeFactory().constructMapLikeType(Map.class, String.class, Object.class);
		module.addDeserializer(DiscoveredInstance.class, new DiscoveredInstanceDeserializer(valueMapType));
		mapper.registerModule(module);
	}

    private static class DiscoveredInstanceSerializer extends StdSerializer<DiscoveredInstance> {

	    public DiscoveredInstanceSerializer() {
	        super(DiscoveredInstance.class);
	    }

		@Override
		public void serialize(DiscoveredInstance value, JsonGenerator gen, SerializerProvider provider) throws
                IOException {
			 gen.writeStartObject();
			 gen.writeStringField("app", value.getApp());
			 gen.writeStringField("id", value.getId());
			 gen.writeObjectField("props", value.getProperties());
		     gen.writeEndObject();
		}
	}

	private static class DiscoveredInstanceDeserializer extends StdDeserializer<DiscoveredInstance> {

		private final JavaType valueMapType;

		public DiscoveredInstanceDeserializer(JavaType valueMapType) {
			super(DiscoveredInstance.class);
			this.valueMapType = valueMapType;
	    }

	    @Override
	    public DiscoveredInstance deserialize(JsonParser parser, DeserializationContext deserializer) throws IOException {
	    	JsonNode node = parser.readValueAsTree();
	    	final String app = node.get("app").asText();
	    	final String id = node.get("id").asText();
	    	JsonParser propsParser = node.get("props").traverse();
	    	propsParser.nextToken();
	    	final Map<String, Object> props = deserializer.readValue(propsParser, valueMapType);
			return new DiscoveredInstance(app, id, props);
	    }
	}

    private static class DiscoveredGroupSerializer extends StdSerializer<DiscoveredGroup> {

	    public DiscoveredGroupSerializer() {
	        super(DiscoveredGroup.class);
	    }

		@Override
		public void serialize(DiscoveredGroup value, JsonGenerator gen, SerializerProvider provider) throws IOException {
			 gen.writeStartObject();
			 gen.writeStringField("id", value.getId());
			 gen.writeObjectField("instances", value.getInstances());
			 gen.writeObjectField("props", value.getProperties());
		     gen.writeEndObject();
		}
	}

	private static class DiscoveredGroupDeserializer extends StdDeserializer<DiscoveredGroup> {

		private final JavaType listType;
		private final JavaType valueMapType;

		public DiscoveredGroupDeserializer(JavaType valueMapType, JavaType listType) {
			super(DiscoveredGroup.class);
			this.valueMapType = valueMapType;
			this.listType = listType;
		}

	    @Override
	    public DiscoveredGroup deserialize(JsonParser parser, DeserializationContext deserializer) throws IOException {
	    	JsonNode node = parser.readValueAsTree();
	    	final String id = node.get("id").asText();
	    	JsonParser instancesParser = node.get("instances").traverse(deserializer.getParser().getCodec());
	    	instancesParser.nextToken();
	    	final List<Dao> instances = deserializer.readValue(instancesParser, listType);
	    	JsonParser propsParser = node.get("props").traverse();
	    	propsParser.nextToken();
	    	final Map<String, Object> props = deserializer.readValue(propsParser, valueMapType);
	    	return new DiscoveredGroup(id, instances, props);
	    }
	}

    private static class DiscoveredArtifactSerializer extends StdSerializer<DiscoveredArtifact> {

	    public DiscoveredArtifactSerializer() {
	        super(DiscoveredArtifact.class);
	    }

		@Override
		public void serialize(DiscoveredArtifact value, JsonGenerator gen, SerializerProvider provider) throws IOException {
			 gen.writeStartObject();
			 gen.writeStringField("app", value.getApp());
			 gen.writeObjectField("version", value.getVersion());
			 gen.writeObjectField("props", value.getProperties());
		     gen.writeEndObject();
		}
	}

	private static class DiscoveredArtifactDeserializer extends StdDeserializer<DiscoveredArtifact> {

		private final JavaType valueMapType;

		public DiscoveredArtifactDeserializer(JavaType valueMapType) {
			super(DiscoveredArtifact.class);
			this.valueMapType = valueMapType;
		}

	    @Override
	    public DiscoveredArtifact deserialize(JsonParser parser, DeserializationContext deserializer) throws IOException {
	    	JsonNode node = parser.readValueAsTree();
	    	final String type = node.get("app").asText();
	    	final String version = node.get("version").asText();
	    	JsonParser propsParser = node.get("props").traverse();
	    	propsParser.nextToken();
	    	final Map<String, Object> props = deserializer.readValue(propsParser, valueMapType);
			return new DiscoveredArtifact(type, version, props);
	    }
	}
}
