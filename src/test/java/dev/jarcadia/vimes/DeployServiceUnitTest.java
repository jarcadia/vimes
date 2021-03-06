package dev.jarcadia.vimes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import dev.jarcadia.vimes.model.Group;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.jarcadia.redao.Dao;
import dev.jarcadia.redao.Index;
import dev.jarcadia.redao.RedaoCommando;
import dev.jarcadia.retask.Retask;
import dev.jarcadia.retask.RetaskManager;
import dev.jarcadia.retask.RetaskRecruiter;
import dev.jarcadia.retask.Task;
import dev.jarcadia.retask.annontations.RetaskChangeHandler;
import dev.jarcadia.retask.annontations.RetaskDeleteHandler;
import dev.jarcadia.retask.annontations.RetaskHandler;
import dev.jarcadia.retask.annontations.RetaskInsertHandler;
import dev.jarcadia.retask.annontations.RetaskParam;
import dev.jarcadia.retask.annontations.RetaskWorker;
import dev.jarcadia.vimes.States.DistributionState;
import dev.jarcadia.vimes.States.InstanceState;
import dev.jarcadia.vimes.model.Artifact;

import io.lettuce.core.RedisClient;
import io.netty.util.internal.ThreadLocalRandom;

@RetaskWorker
public class DeployServiceUnitTest {

    private final Logger logger = LoggerFactory.getLogger(DeployServiceUnitTest.class);
    
    
    private RetaskRecruiter recruiter() {
    	RetaskRecruiter recruiter = new RetaskRecruiter();
        recruiter.recruitFromClass(DeploymentWorker.class);
        recruiter.recruitFromClass(DeployServiceUnitTest.class);
//        recruiter.recruitFromPackage("com.jarcadia.watchdog.model");
        recruiter.recruitFromClass(Group.class); // Group is referenced only indirectly, must be included explicitly
        recruiter.recruitFromClass(Artifact.class); // Artifact is referenced only indirectly, must be included explicitly
        return recruiter;
    }
    
    @Test
    public void testSingleInstanceDeployment() throws Exception {
    	RedisClient redisClient = RedisClient.create("redis://localhost:6379/1");
    	
        RedaoCommando rcommando = RedaoCommando.create(redisClient);
        rcommando.core().flushdb();
        RetaskManager manager = Retask.init(redisClient, rcommando, recruiter());
        
        // Setup group
        Index groups = rcommando.getPrimaryIndex("group");
        Dao group = groups.get("group");
        group.set("app", "webserver");

        // Setup instances
        Index instances = rcommando.getPrimaryIndex("instance");
        Dao instance = instances.get("inst");
        instance.set("app", "webserver", "group", group.getPath(), "host", "web01", "port", 8080, "state", InstanceState.Enabled);
        
        // Setup artifact
        Dao artifact = rcommando.getPrimaryIndex("artifact").get("webserver_1.0");
        artifact.set("app", "webserver", "version", "1.0");
        
        // Setup test Deployment implementation
        TestDeploymentImpl testDeployImpl = Mockito.spy(new TestDeploymentImpl());
        manager.addWorker(TestDeploymentImpl.class, testDeployImpl);

        // Setup DeploymentStateRecorder for assertions
        StateRecorder deployStateRecorder = new StateRecorder(rcommando);
        manager.addWorker(StateRecorder.class, deployStateRecorder);

        // Create DeployWorker under test
        DeploymentWorker deploymentWorker = new DeploymentWorker(rcommando, new NotificationService(rcommando));
        manager.addWorker(DeploymentWorker.class, deploymentWorker);

        // Start retask and submit deploy task 
        manager.start(Task.create("deploy.artifact")
                    .param("instances", List.of(instance.getPath()))
                    .param("artifact", artifact.getPath()));

        // Wait for the deployment to complete
        deployStateRecorder.awaitCompletion(1, TimeUnit.SECONDS);

        // Verify the instances deploy states progressed as expected
        Assertions.assertIterableEquals(expectedDeployUpgradeStates(), deployStateRecorder.getStates("inst"));

        // Verify the deployment agent spy callbacks were invoked in order
        verifyDeployAgent(testDeployImpl, instance, artifact);

        // Shutdown retask
        manager.shutdown(1, TimeUnit.SECONDS);
    }

    @Test
    public void testTwoInstanceDeployment() throws Exception {
    	RedisClient redisClient = RedisClient.create("redis://localhost/1");
        RedaoCommando rcommando = RedaoCommando.create(redisClient);
        rcommando.core().flushdb();
        RetaskManager manager = Retask.init(redisClient, rcommando, recruiter());

        // Setup group
        Index groups = rcommando.getPrimaryIndex("group");
        Dao group = groups.get("group");
        group.set("app", "webserver");

        // Setup instances
        Index instances = rcommando.getPrimaryIndex("instance");
        Dao inst1 = instances.get("inst1");
        inst1.set("app", "webserver", "group", group.getPath(), "host", "web01", "port", 8080, "state", InstanceState.Enabled);
        Dao inst2 = instances.get("inst2");
        inst2.set("app", "webserver", "group", group.getPath(), "host", "web02", "port", 8080, "state", InstanceState.Enabled);
        group.set("app", "webserver", "instances", Arrays.asList(inst1, inst2));
        
        // Setup artifact
        Dao artifact = rcommando.getPrimaryIndex("artifact").get("webserver_1.0");
        artifact.set("app", "webserver", "version", "1.0");

        // Setup spied test Deployment implementation
        TestDeploymentImpl deploymentImpl = Mockito.spy(new TestDeploymentImpl());
        manager.addWorker(TestDeploymentImpl.class, deploymentImpl);

        // Setup test StateRecorder for assertions
        StateRecorder stateRecorder = new StateRecorder(rcommando);
        manager.addWorker(StateRecorder.class, stateRecorder);

        // Create DeployWorker under test
        DeploymentWorker deployWorker = new DeploymentWorker(rcommando,  new NotificationService(rcommando));
        manager.addWorker(DeploymentWorker.class, deployWorker);

        // Submit task to start deployment
        manager.start(Task.create("deploy.artifact")
                .param("instances",  Arrays.asList("instance/inst1", "instance/inst2"))
                .param("artifact", "artifact/webserver_1.0"));

        // Wait for the deployment to complete
        stateRecorder.awaitCompletion(1, TimeUnit.SECONDS);

        // Verify the instances deploy states progressed as expected
        Assertions.assertIterableEquals(expectedDeployUpgradeStates(), stateRecorder.getStates("inst1"));
        Assertions.assertIterableEquals(expectedDeployUpgradeStates(), stateRecorder.getStates("inst2"));

        // Verify the deployment agent spy callbacks were invoked in order
        verifyDeployAgent(deploymentImpl, rcommando.getPrimaryIndex("instance").get("inst1"), artifact);
        verifyDeployAgent(deploymentImpl, rcommando.getPrimaryIndex("instance").get("inst2"), artifact);

        // Shutdown retask
        manager.shutdown(1, TimeUnit.SECONDS);
    }

    @Test
    public void testLargeMultiDeployment() throws Exception {
    	RedisClient redisClient = RedisClient.create("redis://localhost/1");
        RedaoCommando rcommando = RedaoCommando.create(redisClient);
        rcommando.core().flushdb();
        RetaskManager manager = Retask.init(redisClient, rcommando, recruiter());
        
        // Setup instances and groups
        int numGroups = 50;
        String[][] hosts = {{"web01", "web02"}, {"web03", "web04"}, {"web05", "web06"}, {"web07", "web08"}};
        Index instanceSet = rcommando.getPrimaryIndex("instance");
        Index groups = rcommando.getPrimaryIndex("group");
        List<Dao> instances = new ArrayList<>();
        for (int i=0; i<numGroups; i++) {
            String groupId = "group" + i;
            String inst1Id = groupId + "-" + "inst1";
            String inst2Id = groupId + "-" + "inst2";

            String[] groupHosts = hosts[ThreadLocalRandom.current().nextInt(hosts.length)];
            Dao inst1 = instanceSet.get(inst1Id);
            inst1.set("app", "webserver", "group", "group/" + groupId, "host", groupHosts[0], "port", 8080 + i, "state", InstanceState.Enabled);
            Dao inst2 = instanceSet.get(inst2Id);
            inst2.set("app", "webserver", "group", "group/" + groupId, "host", groupHosts[1], "port", 8080 + i, "state", InstanceState.Enabled);
            groups.get(groupId).set("app", "webserver", "instances", Arrays.asList(inst1, inst2));

            instances.add(inst1);
            instances.add(inst2);
        }

        // Setup mocked artifact
        Dao artifact = rcommando.getPrimaryIndex("artifact").get("webserver_1.0");
        artifact.set("app", "webserver", "version", "1.0");

        // Setup spied TestDeploymentWorker 
        TestDeploymentImpl testDeploymentWorker = Mockito.spy(new TestDeploymentImpl());

        // Setup DeploymentStateRecorder for assertions
        StateRecorder deployStateRecorder = new StateRecorder(rcommando);

        // Create Deploy Service for test
        DeploymentWorker deployService = new DeploymentWorker(rcommando,  new NotificationService(rcommando));

        manager.addWorker(TestDeploymentImpl.class, testDeploymentWorker);
        manager.addWorker(StateRecorder.class, deployStateRecorder);
        manager.addWorker(DeploymentWorker.class, deployService);

        // Submit task to start deployment
        manager.start(Task.create("deploy.artifact")
                .param("instances",  instances)
                .param("artifact", "artifact/webserver_1.0"));

        // Wait for the deployment to complete
        deployStateRecorder.awaitCompletion(10, TimeUnit.SECONDS);

        for (Dao instance : instances) {
            Assertions.assertIterableEquals(expectedDeployUpgradeStates(), deployStateRecorder.getStates(instance.getId()));
            verifyDeployAgent(testDeploymentWorker, instance, artifact);
        }
        manager.shutdown(1, TimeUnit.SECONDS);
    }
    
    
    
    @Test
    public void testSingleInstanceRestart() throws Exception {
    	RedisClient redisClient = RedisClient.create("redis://localhost:6379/1");

        RedaoCommando rcommando = RedaoCommando.create(redisClient);
        rcommando.core().flushdb();
        RetaskManager manager = Retask.init(redisClient, rcommando, recruiter());
        
        // Setup group
        Index groups = rcommando.getPrimaryIndex("group");
        groups.get("group").set("app", "webserver");
    	

        // Setup instances
        Index instances = rcommando.getPrimaryIndex("instance");
        instances.get("inst").set("app", "webserver", "group", "group/group", "host", "web01", "port", 8080, "state", InstanceState.Enabled);
        
        // Setup test Deployment implementation
        TestDeploymentImpl testDeployImpl = Mockito.spy(new TestDeploymentImpl());
        manager.addWorker(TestDeploymentImpl.class, testDeployImpl);

        // Setup DeploymentStateRecorder for assertions
        StateRecorder deployStateRecorder = new StateRecorder(rcommando);
        manager.addWorker(StateRecorder.class, deployStateRecorder);

        // Create DeployWorker under test
        DeploymentWorker deploymentWorker = new DeploymentWorker(rcommando, new NotificationService(rcommando));
        manager.addWorker(DeploymentWorker.class, deploymentWorker);

        // Start retask and submit deploy task 
        manager.start(Task.create("deploy.restart")
                    .param("instances", List.of("instance/inst")));

        // Wait for the deployment to complete
        deployStateRecorder.awaitCompletion(1, TimeUnit.SECONDS);

        // Verify the instances deploy states progressed as expected
        Assertions.assertIterableEquals(expectedDeployRestartStates(), deployStateRecorder.getStates("inst"));

        // Verify the deployment agent spy callbacks were invoked in order
//        verifyDeployAgent(testDeployImpl, rcommando.getSetOf("instance").get("inst"), artifact);

        // Shutdown retask
        manager.shutdown(1, TimeUnit.SECONDS);
    }
    
    
    
    
    
    
    
    
    
    
    
    
    
    

    private List<DeployState> expectedDeployUpgradeStates() {
        return Arrays.asList(DeployState.Waiting, DeployState.Ready,
                DeployState.PendingDrain, DeployState.Draining, 
                DeployState.PendingStop, DeployState.Stopping,
                DeployState.PendingUpgrade, DeployState.Upgrading, DeployState.Upgraded,
                DeployState.PendingStart, DeployState.Starting,
                DeployState.PendingEnable, DeployState.Enabling, DeployState.Complete, null);
    }
    
    private List<DeployState> expectedDeployRestartStates() {
        return Arrays.asList(DeployState.Waiting, DeployState.Ready,
                DeployState.PendingDrain, DeployState.Draining, 
                DeployState.PendingStop, DeployState.Stopping,
                DeployState.PendingStart, DeployState.Starting,
                DeployState.PendingEnable, DeployState.Enabling, DeployState.Complete, null);
    }

    private void verifyDeployAgent(TestDeploymentImpl agent, Dao expected, Dao artifact) throws Exception {
        InOrder depVerifer = Mockito.inOrder(agent);
        depVerifer.verify(agent, Mockito.times(1)).disable(expected);
        depVerifer.verify(agent, Mockito.times(1)).stop(expected);
        depVerifer.verify(agent, Mockito.times(1)).upgrade(Mockito.eq(expected), Mockito.eq(artifact), Mockito.any());
        depVerifer.verify(agent, Mockito.times(1)).start(expected);
        depVerifer.verify(agent, Mockito.times(1)).join(expected);
    }
    
    private static void delay() {
    	try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }
    
    @RetaskWorker
    public class TestDeploymentImpl {
        
        @RetaskHandler("deploy.drain.webserver")
        public void disable(Dao instance) {
            logger.info("Draining {}", instance.getId());
            delay();
            instance.set("state", InstanceState.Disabled);
        }

        @RetaskHandler("deploy.stop.webserver")
        public void stop(Dao instance) {
            logger.info("Stopping {}", instance.getId());
            delay();
            instance.set("state", InstanceState.Down);
        }

        @RetaskHandler("deploy.upgrade.webserver")
        public void upgrade(Dao instance, Dao artifact, Dao distribution) {
            delay();
            logger.info("Upgrading {}", instance.getId());
        }

        @RetaskHandler("deploy.start.webserver")
        public void start(Dao instance) {
            logger.info("Starting {}", instance.getId());
            delay();
            instance.set("state", InstanceState.Disabled);
        }

        @RetaskHandler("deploy.enable.webserver")
        public void join(Dao instance) {
            logger.info("Enabling {}", instance.getId());
            delay();
            instance.set("state", InstanceState.Enabled);
        }

        @RetaskHandler("deploy.distribute.webserver")
        public void distribute(String host, Dao distribution, Dao artifact) throws InterruptedException {
            delay();
            distribution.set("state", DistributionState.Transferred);
        }

        @RetaskHandler("deploy.cleanup.webserver")
        public void cleanup(String host, Dao distribution, Dao artifact) {
            delay();
            distribution.set("state", DistributionState.CleanedUp);
        }
        
        @RetaskHandler("deploy.next.webserver")
        public Dao chooseNext(Dao deployment, List<Dao> remaining) {
            delay();
            return remaining.get(0);
        }
    }

    @RetaskWorker
    public class StateRecorder {
    	
    	private final RedaoCommando rcommando;
    	private final String guid;
    	private final String activeKey;

        public StateRecorder(RedaoCommando rcommando) {
        	this.rcommando = rcommando;
        	this.guid = UUID.randomUUID().toString();
        	this.activeKey = key("active");
        }
        
        @RetaskInsertHandler("deployment")
        public void deploymentInserted(@RetaskParam("object") Dao deployment) {
        	logger.info("Deployment {} was created", deployment.getId());
        	rcommando.core().incr(activeKey);
        }

        @RetaskDeleteHandler("deployment")
        public void deploymentDeleted(String id) {
        	logger.info("Deployment {} was deleted", id);
        	rcommando.core().decr(activeKey);
        }

        @RetaskChangeHandler(type = "instance", field = "deploymentState")
        public void changeState(@RetaskParam("object") Dao instance, DeployState before, DeployState after) {
            logger.info("State change for {}: {} -> {}", instance.getId(), before, after);
        	rcommando.core().rpush(key(instance.getId()), after == null ? "null" : after.toString());
        }

        public void awaitCompletion(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            while (!isDone()) {
                Thread.sleep(100);
            }
        }
        
        private boolean isDone() {
            return "0".equals(rcommando.core().get(activeKey));
        }

        public List<DeployState> getStates(String instanceId) {
            return rcommando.core().lrange(key(instanceId), 0, -1).stream()
            		.map(str -> str.equals("null") ? null : DeployState.valueOf(str))
            		.collect(Collectors.toList());
        }

        private String key(String id) {
        	return "recorder." + guid + "." + id;
        }
    }
}
