package dev.jarcadia.vimes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import com.jarcadia.rcommando.DaoValues;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.jarcadia.rcommando.Dao;
import com.jarcadia.rcommando.Index;
import com.jarcadia.rcommando.RedisCommando;
import com.jarcadia.rcommando.Modification;
import com.jarcadia.retask.Retask;
import com.jarcadia.retask.Task;
import com.jarcadia.retask.annontations.RetaskHandler;
import com.jarcadia.retask.annontations.RetaskWorker;
import dev.jarcadia.vimes.exception.DiscoveryException;

@RetaskWorker
public class DiscoveryWorker {

	private final Logger logger = LoggerFactory.getLogger(DiscoveryWorker.class);

	private final NotificationService notificationService;
	private final TypeReference<Set<DiscoveredArtifact>> discoveredArtifactSetTypeRef;
	private final TypeReference<List<DiscoveredInstance>> discoveredInstanceListTypeRef;
	private final TypeReference<List<DiscoveredGroup>> discoveredGroupListTypeRef;

	public DiscoveryWorker(NotificationService notificationService) {
		this.notificationService = notificationService;
		this.discoveredArtifactSetTypeRef = new TypeReference<>() {};
		this.discoveredInstanceListTypeRef = new TypeReference<>() {};
		this.discoveredGroupListTypeRef = new TypeReference<>() {};
	}

	private boolean acquireLock(RedisCommando rcommando, String type) {
		return rcommando.core().getset("discover." + type + ".lock", "locked") == null;
	}

	private void unlock(RedisCommando rcommando, String type) {
		rcommando.core().del("discover." + type + ".lock", "locked");
	}

	@RetaskHandler("discover.artifacts")
	public void discoverArtifacts(RedisCommando rcommando, Retask retask) {
		try {
			if (acquireLock(rcommando, "artifacts")) {
				logger.info("Starting artifact discovery");
				String discoveryId = UUID.randomUUID().toString();

				// Ensure agent implementation is ready
				Set<String> missingRoutes = retask.verifyRecruits(Arrays.asList("discover.artifacts.impl"));
				if (!missingRoutes.isEmpty()) {
					logger.warn("Missing discover.agent.artifacts handler, cannot discover artifacts");
					throw new DiscoveryException("Undefined implementation for artifact discovery route " + missingRoutes.toString());
				}
				Task agentTask = Task.create("discover.artifacts.impl");
				Future<Set<DiscoveredArtifact>> future = retask.call(agentTask, discoveredArtifactSetTypeRef);

				Set<DiscoveredArtifact> discoveredArtifacts = null;
				try {
					discoveredArtifacts = future.get();
				} catch (InterruptedException | ExecutionException e) {
					throw new DiscoveryException("Unexpected exception while discovering artifacts", e);
				}

				Index artifactsIndex = rcommando.getPrimaryIndex("artifact");

				StatsByApp artifactResults = new StatsByApp();
				for (DiscoveredArtifact discovered : discoveredArtifacts) {

					// Add app and version to additional properties
					discovered.getProperties().put("app", discovered.getApp());
					discovered.getProperties().put("version", discovered.getVersion());
					Dao artifact = artifactsIndex.get(discovered.getApp() + "." + discovered.getVersion());

					// Persist all properties and detect inserts/update
					Optional<Modification> result = artifact.setAll(discovered.getProperties());
					artifactResults.record(discovered.getApp(), result);

					artifact.set("_discoveryId", discoveryId);
				}

				// Remove stale artifacts
				for (Dao artifact : artifactsIndex) {
				    DaoValues.Pair values = artifact.get("_discoveryId", "app").asPair();
					if (!discoveryId.equals(values.getValue0().asString())) {
						artifact.delete();
						artifactResults.recordRemoved(values.getValue1().asString());
					}
				}

				artifactResults.getApps().stream().sorted().forEach(app -> {
					if (artifactResults.isRelevant(app)) {
						logger.info("Discovered {} {} artifacts ({})", artifactResults.getDiscovered(app), app, artifactResults.getChangesString(app));
					}
				});

			} else {
				logger.info("Artifact discovery already in progress");
			}
		} finally {
			unlock(rcommando, "artifacts");
		}
	}

	@RetaskHandler("discover.instances")
	public void discover(RedisCommando rcommando, Retask retask) {
		try {
			if (acquireLock(rcommando, "instances")) {

				logger.info("Starting instance discovery");
				String discoveryId = UUID.randomUUID().toString();

				notificationService.info("Discovery Started");

				Set<String> missingRoutes = retask.verifyRecruits(List.of("discover.instances.impl", "discover.groups.impl"));
				if (!missingRoutes.isEmpty()) {
					logger.warn("Missing discovery implementation tasks {}", missingRoutes);
					throw new DiscoveryException("Undefined implementation for instance discovery routes: " + missingRoutes.toString());
				}

				Future<List<DiscoveredInstance>> futureInstances = retask.call(Task.create("discover.instances.impl"),
						discoveredInstanceListTypeRef);

				List<DiscoveredInstance> discoveredInstances;
				try {
					discoveredInstances = futureInstances.get();
				} catch (InterruptedException | ExecutionException ex) {
					throw new DiscoveryException("Unexpected exception while discovering instances", ex);
				}

				// Group the discovered instances by app
				Map<String, List<DiscoveredInstance>> discoveredInstancesByApp = discoveredInstances.stream()
						.collect(Collectors.groupingBy(DiscoveredInstance::getApp));

				// Prepare map to store created proxies
				Map<String, List<Dao>> instancesByApp = new HashMap<>();

				// Prepare result stats object
				StatsByApp instanceStats = new StatsByApp();

				// Process discovered instances
				Index instanceIndex = rcommando.getPrimaryIndex("instance");
				for (Entry<String, List<DiscoveredInstance>> entry : discoveredInstancesByApp.entrySet()) {
					String app = entry.getKey();

					// Process each discovered instance of this app
					for (DiscoveredInstance discovered : entry.getValue()) {

						// Create a Dao for this instance
						Dao instance = instanceIndex.get(discovered.getApp() + "." + discovered.getId());

						// Apply all discovered properties to the new or existing instance proxy
                        discovered.getProperties().put("app", discovered.getApp());
						Optional<Modification> result = instance.setAll(discovered.getProperties());

						// Record statistics for this app/result
						instanceStats.record(app, result);

						instancesByApp.computeIfAbsent(app, t -> new ArrayList<>()).add(instance);

						instance.set("_discoveryId", discoveryId);

						// TODO extract published / monitored states and set... somewhere

						/*

						Let's have monitored definitions at the app level stored as a separate hash

						'monitoring/instance.quotewin'
							'memUsage': {'ascending: true', thresholds: [1, 2, 3, 4]}

							Each worker has all monitoring loaded into memory. The worker has a change listener
							for all apps. If the app/field matches a loaded monitoring definition then the new value is
							checked. A level is calculated, then a field in the dao is set. The field will be prefixed:

							alarm.fieldName: level

							If this value CHANGES, then alarms may needed to be raised or dismissed
							It should dismiss previous activated alarms (if needed) and raise new ones (if needed)

							Monitoring triggers alarms. Alarms are raised or dismissed. Alarms will be standalone
							objects that are set with a timestamp as a score

							alarm/instance.app.id.fieldName: {
								subject: "app.id"
								trigger: "value between x and y" or "value = FAILED"
								timestamp: ts
							}
						 */
					}
				}

				// Stream all instances of discovered apps (including those that already existed but were not in this discovery)
				instanceIndex.stream()
				.forEach(instance -> {
					DaoValues.Pair values = instance.get("_discoveryId", "app").asPair();
					if (!discoveryId.equals(values.getValue0().asString())) {
						instance.delete();
						instanceStats.recordRemoved(values.getValue1().asString());
					}
				});

				// Create group discovery futures
				List<DiscoveredGroupsFuture> groupDiscoveryFutures = instancesByApp.entrySet().stream()
						.map(e -> {
							Task discoveryTask = Task.create("discover.groups.impl")
									.param("app", e.getKey())
									.param("instances", e.getValue());
							Future<List<DiscoveredGroup>> future = retask.call(discoveryTask, discoveredGroupListTypeRef);
							return new DiscoveredGroupsFuture(e.getKey(), future);
						}).collect(Collectors.toList());

				// Combine DiscoveredGroups returned by implementation tasks
				Map<String, List<DiscoveredGroup>> discoveredGroupsByApp = new HashMap<>();
				for (DiscoveredGroupsFuture discoveredGroupsFuture : groupDiscoveryFutures) {
					try {
						List<DiscoveredGroup> groups = discoveredGroupsFuture.getFuture().get();
						if (groups.size() > 0) {
							discoveredGroupsByApp.put(discoveredGroupsFuture.getApp(), groups);
						}
					} catch (InterruptedException | ExecutionException ex) {
						throw new DiscoveryException("Unexpected exception while grouping instances", ex);
					}
				}

				StatsByApp groupStats = new StatsByApp();
//				ProxyIndex<DiscoveryGroupProxy> groupSet = rcommando.getPrimaryIndex("group", DiscoveryGroupProxy.class);
				Index groupIndex = rcommando.getPrimaryIndex("group");
				for (String app : discoveredGroupsByApp.keySet()) {
					for (DiscoveredGroup discovered : discoveredGroupsByApp.get(app)) {

						Map<String, Object> props = discovered.getProperties();
						props.put("app", app);

						// Sort instances to prevent changes in order from causing modification
						props.put("instances", discovered.getInstances().stream()
								.sorted(Comparator.comparing(Dao::getId))
								.collect(Collectors.toList()));

						Dao group = groupIndex.get(app + "." + discovered.getId());
						Optional<Modification> result = group.setAll(props);
						groupStats.record(app, result);
						group.set("_discoveryId", discoveryId);
					}
				}

				// Stream all existing groups (including those that existed previously but weren't discovered just now)
				groupIndex.stream()
				.forEach(group -> {
					// Remove stale groups
					DaoValues.Triplet vals = group.get("_discoveryId", "app", "instances").asTriplet();
					if (!discoveryId.equals(vals.getValue0().asString())) {
						groupStats.recordRemoved(vals.getValue1().asString());
						group.delete();
					} else { // Otherwise update the group field within each instance
						for (Dao instance: vals.getValue2().asListOf(Dao.class)) {
							instance.set("group", group);
						}
					}
				});

				// Log results
				instanceStats.getApps().stream().sorted().forEach(app -> {
					if (instanceStats.isRelevant(app)) {
						logger.info("Discovered {} {} instances ({})", instanceStats.getDiscovered(app), app,
								instanceStats.getChangesString(app));
					}
					if (groupStats.isRelevant(app)) {
						logger.info("Discovered {} {} groups ({})", groupStats.getDiscovered(app), app,
								groupStats.getChangesString(app));
					}
				});

				// Raise notification with results
                notificationService.info("Discovery Complete");
			} else {
				logger.info("Instance discovery request ignored (already in progress)");
			}
		} finally {
			unlock(rcommando, "instances");
		}
	}

//	private interface DiscoveryInstanceProxy extends Instance {
//
//		@Internal
//		public String getDiscoveryId();
//
//		public void setDiscoveryId(@Internal String discoveryId);
//		public void setGroup(DiscoveryGroupProxy group);
//	}
//
//	private interface DiscoveryGroupProxy extends Group {
//
//		public @Internal String getDiscoveryId();
//
//		public void setDiscoveryId(@Internal String discoveryId);
//	}

	private class DiscoveredGroupsFuture {

		private final String app;
		private final Future<List<DiscoveredGroup>> future;

		private DiscoveredGroupsFuture(String app, Future<List<DiscoveredGroup>> future) {
			this.app = app;
			this.future = future;
		}

		private String getApp() {
			return app;
		}

		private Future<List<DiscoveredGroup>> getFuture() {
			return future;
		}
	}

	private class StatsByApp {

		private final Map<String, Stats> map;

		private StatsByApp() {
			this.map = new HashMap<>();
		}

		private Set<String> getApps() {
			return map.keySet();
		}

		private void record(String app, Optional<Modification> result) {
			getStats(app).record(result);
		}

		private void recordRemoved(String app) { getStats(app).markRemoved(); }

		private Stats getStats(String app) {
			return map.computeIfAbsent(app, t -> new Stats());
		}

		private boolean isRelevant(String app) {
			if (map.containsKey(app)) {
				return map.get(app).isRelevant();
			} else {
				return false;
			}
		}

		private int getDiscovered(String app) {
			if (map.containsKey(app)) {
				return map.get(app).getDiscovered();
			} else {
				return 0;
			}
		}

		private String getChangesString(String app) {
			if (map.containsKey(app)) {
				return map.get(app).getChangesString();
			} else {
				return "";
			}
		}
	}

	private class Stats {
		private int discovered;
		private int existing;
		private int added;
		private int modified;
		private int removed;

		private Stats() {
			this.discovered = 0;
			this.existing = 0;
			this.added = 0;
			this.modified = 0;
			this.removed = 0;
		}

		private void record(Optional<Modification> result) {
			discovered += 1;
			if (result.isPresent()) {
				if (result.get().isInsert()) {
					added += 1;
				} else {
					modified += 1;
				}
			} else {
				existing += 1;
			}
		}

		private void markRemoved() {
			removed += 1;
		}

		private boolean isRelevant() {
			return discovered + removed > 0;
		}

		private int getDiscovered() {
			return discovered;
		}

		private String getChangesString() {
			StringJoiner joiner = new StringJoiner("/");
			if (existing > 0) {
				joiner.add(existing + " existing");
			}
			if (added > 0) {
				joiner.add(added + " added");
			}
			if (modified > 0) {
				joiner.add(modified + " modified");
			}
			if (removed > 0) {
				joiner.add(removed + " removed");
			}
			return joiner.toString();
		}
	}
}
