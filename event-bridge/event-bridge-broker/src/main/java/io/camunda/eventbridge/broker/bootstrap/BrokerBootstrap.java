/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.bootstrap;

import io.atomix.cluster.AtomixCluster;
import io.atomix.cluster.MemberId;
import io.camunda.eventbridge.broker.partitioning.PartitionDistributor;
import io.camunda.eventbridge.broker.partitioning.PartitionFactory;
import io.camunda.eventbridge.broker.partitioning.PartitionLeaderReporter;
import io.camunda.eventbridge.clustermetadata.reconfig.ReconfigurationCommand;
import io.camunda.eventbridge.clustermetadata.reconfig.ReconfigurationExecutor;
import io.camunda.eventbridge.clustermetadata.reconfig.ReconfigurationOp.Kind;
import io.camunda.eventbridge.clustermetadata.state.topic.TopicMetadata;
import io.camunda.eventbridge.clustermetadata.state.topic.TopicMetadata.TopicStatus;
import io.camunda.eventbridge.consumergroups.membership.TopicRegistry;
import io.camunda.eventbridge.core.config.EventBridgeProperties;
import io.camunda.eventbridge.messaging.threading.ExecutorServiceFactory;
import io.camunda.zeebe.broker.client.api.BrokerTopologyManager;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.util.ConfigurationUtil;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.InstantSource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.IntStream;
import org.agrona.concurrent.IdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Coordinates broker startup and shutdown.
 *
 * <p>Startup order:
 *
 * <ol>
 *   <li>{@link MessagingServiceSetup} — broker command API (port 26501)
 *   <li>{@link TopologySetup} — BrokerInfo + SWIM gossip
 *   <li>{@link PartitionBootstrapper} — raft partitions + lifecycle actors
 * </ol>
 *
 * <p>Shutdown is reverse order.
 */
public final class BrokerBootstrap {

  private static final Logger LOG = LoggerFactory.getLogger(BrokerBootstrap.class);

  private final AtomixCluster cluster;
  private final ActorSchedulingService actorScheduler;
  private final EventBridgeProperties properties;
  private final PartitionDistributor distributor;
  private final ExecutorServiceFactory executorServiceFactory;
  private final IdGenerator idGenerator;
  private final MeterRegistry meterRegistry;
  private final BrokerTopologyManager gatewayTopologyManager;
  private final PartitionLeaderReporter leaderReporter;

  private MessagingServiceSetup messagingServiceSetup;
  private TopologySetup topologySetup;
  private PartitionBootstrapper partitionBootstrapper;
  private ExecutorServiceSetup executorServiceSetup;

  private volatile boolean started = false;

  public BrokerBootstrap(
      final AtomixCluster cluster,
      final ActorSchedulingService actorScheduler,
      final EventBridgeProperties properties,
      final PartitionDistributor distributor,
      final ExecutorServiceFactory executorServiceFactory,
      final IdGenerator idGenerator,
      final MeterRegistry meterRegistry,
      final BrokerTopologyManager gatewayTopologyManager,
      final PartitionLeaderReporter leaderReporter) {
    this.cluster = cluster;
    this.actorScheduler = actorScheduler;
    this.properties = properties;
    this.distributor = distributor;
    this.executorServiceFactory = executorServiceFactory;
    this.idGenerator = idGenerator;
    this.meterRegistry = meterRegistry;
    this.gatewayTopologyManager = gatewayTopologyManager;
    this.leaderReporter = leaderReporter;
  }

  public void start() {
    if (started) {
      LOG.debug("BrokerBootstrap already started; ignoring");
      return;
    }
    started = true;

    LOG.info("Starting EventBridge broker");

    // 1. Start broker messaging service — handlers are registered here
    messagingServiceSetup = new MessagingServiceSetup(properties, meterRegistry);
    final var brokerMessagingService = messagingServiceSetup.start();

    // 2. Start topology — BrokerInfo + SWIM gossip
    topologySetup =
        new TopologySetup(
            cluster.getMembershipService(), actorScheduler, properties, gatewayTopologyManager);
    final var topologyManager = topologySetup.start();

    // 2b. Build the cluster configuration once — the single source of truth (as in Zeebe) for both
    // raft partition placement (which partitions this broker starts) and gateway routing (which
    // partitions exist, via BrokerClusterState.getPartitions()).
    final var configuration = buildClusterConfiguration();

    // Feed it to the gateway topology so getPartitions() is populated; combined with gossiped
    // leadership this lets the BrokerClient route partition-addressed requests natively.
    gatewayTopologyManager.onClusterConfigurationUpdated(configuration);
    LOG.info(
        "Seeded gateway cluster configuration with partitions {}",
        configuration.partitionIds().boxed().toList());

    // Derive the raft partition distribution from the same configuration (mirrors Zeebe's
    // PartitionManagerImpl, which starts the partitions whose members include the local node).
    final var distribution =
        ConfigurationUtil.getPartitionDistributionFrom(configuration, PartitionFactory.GROUP_NAME);

    // 3. Start Fetch Stream Executor Service
    executorServiceSetup = new ExecutorServiceSetup(executorServiceFactory);
    final var executorService = executorServiceSetup.start();

    // 4. Build partition bootstrapper + the topic reconciler that the registry broadcast drives.
    partitionBootstrapper =
        new PartitionBootstrapper(
            cluster,
            actorScheduler,
            properties,
            InstantSource.system(),
            idGenerator,
            executorService,
            leaderReporter);
    final var localMemberId = cluster.getMembershipService().getLocalMember().id();
    final var comm = cluster.getCommunicationService();

    final var topicReconciler =
        new TopicReconciler(partitionBootstrapper, topologySetup, localMemberId);

    // 4b. Topic-registry propagation is pull-from-observed-state, not push: every broker is a
    // member or passive observer of the metadata Raft group and periodically hands its local
    // replicated registry to this sink (see MetadataPartition), which reconciles the broker's local
    // topic Raft groups. The reconcile runs off the metadata partition's actor thread (the snapshot
    // read already happened on it). This replaces the old 2s whole-registry broadcast.
    //
    // The same observed registry feeds a thread-safe topic→partitionCount cache: the coordinator
    // leader (which observes the metadata group on this broker too) reads it at join time to derive
    // a group's partition count from its subscribed topic, rather than from a static config. Only
    // topics that are servable (not DELETING) are cached; a missing entry resolves to 0, which the
    // join processor rejects as an unknown topic.
    final ConcurrentHashMap<String, Integer> topicPartitionCounts = new ConcurrentHashMap<>();
    final TopicRegistry topicRegistry = topic -> topicPartitionCounts.getOrDefault(topic, 0);
    final Consumer<Map<String, TopicMetadata>> registryReconciler =
        desired -> {
          updateTopicPartitionCounts(topicPartitionCounts, desired);
          executorService.execute(() -> topicReconciler.reconcile(desired));
        };

    // 4c. Change-coordinator command channel (CC-3). The broker that must act on a reassignment
    // step
    // executes the runtime Raft join/leave and replies only once it has completed (the confirm).
    comm.replyToAsync(
        ReconfigurationCommand.SUBJECT,
        ReconfigurationCommand::decode,
        cmd -> reconfigure(topicReconciler, localMemberId, cmd).thenApply(done -> new byte[0]),
        Function.identity(),
        executorService);

    // The executor sends each step to the broker that must act (the recipient resolved by the
    // change-coordinator: the joiner for a JOIN, the leaving member for a live LEAVE, or a
    // surviving
    // replica for a dead LEAVE) and completes when that broker confirms; the change-coordinator
    // advances committed only then, and retries on failure.
    final ReconfigurationExecutor reconfigurationExecutor =
        (op, recipientNodeId, members, partitionCount) ->
            comm.send(
                ReconfigurationCommand.SUBJECT,
                new ReconfigurationCommand(
                    op.kind(), op.topic(), op.partitionId(), op.member(), partitionCount, members),
                ReconfigurationCommand::encode,
                reply -> (Void) null,
                MemberId.from("broker-" + recipientNodeId),
                java.time.Duration.ofSeconds(30));

    // 5. Start partitions — raft + lifecycle actors (uses broker messaging service)
    partitionBootstrapper.start(
        distribution,
        topologyManager,
        topologySetup.getCoordinatorTopologyManager(),
        topologySetup.getMetadataTopologyManager(),
        brokerMessagingService,
        topicRegistry,
        registryReconciler,
        reconfigurationExecutor);

    LOG.info("EventBridge broker started — waiting for raft elections");
  }

  /**
   * Applies one change-coordinator step on this broker:
   *
   * <ul>
   *   <li><b>JOIN</b> — add this broker as a replica of the partition (the joiner acts).
   *   <li><b>LEAVE of self</b> — this broker is the departing replica, so it leaves and tears down
   *       its local partition.
   *   <li><b>LEAVE of another</b> — this broker is a surviving replica asked to remove a (typically
   *       dead) member that cannot leave on its own; it drives the leader-side removal without
   *       tearing down its own replica.
   * </ul>
   */
  private static java.util.concurrent.CompletableFuture<Void> reconfigure(
      final TopicReconciler topicReconciler,
      final MemberId localMemberId,
      final ReconfigurationCommand cmd) {
    if (cmd.kind() == Kind.JOIN) {
      return topicReconciler.join(
          cmd.topic(), cmd.partitionId(), cmd.members(), cmd.partitionCount());
    }
    if (localMemberId.equals(MemberId.from("broker-" + cmd.member()))) {
      return topicReconciler.leave(cmd.topic(), cmd.partitionId());
    }
    return topicReconciler.removeMember(cmd.topic(), cmd.partitionId(), cmd.member());
  }

  /**
   * Refreshes the topic→partitionCount cache from an observed registry snapshot: servable topics
   * (anything but {@code DELETING}) keep their count, removed/deleting topics drop out. The
   * coordinator reads this to resolve a group's partition count from its subscribed topic.
   */
  private static void updateTopicPartitionCounts(
      final Map<String, Integer> cache, final Map<String, TopicMetadata> desired) {
    cache.keySet().removeIf(topic -> !desired.containsKey(topic));
    desired.forEach(
        (name, metadata) -> {
          if (metadata.status() == TopicStatus.DELETING) {
            cache.remove(name);
          } else {
            cache.put(name, metadata.partitionCount());
          }
        });
  }

  /**
   * Builds the cluster configuration from the (deterministic) partition distribution over the
   * configured members. Every node computes the same configuration, so raft placement and gateway
   * routing agree across the cluster.
   */
  private ClusterConfiguration buildClusterConfiguration() {
    final var clusterSize = properties.cluster().clusterSize();
    final var members =
        IntStream.range(0, clusterSize)
            .mapToObj(i -> MemberId.from("broker-" + i))
            .sorted()
            .toList();
    final var distribution =
        distributor.distributePartitions(
            members, properties.broker().partitionCount(), properties.raft().replicationFactor());

    return ConfigurationUtil.getClusterConfigFrom(
        distribution, DynamicPartitionConfig.init(), properties.cluster().name());
  }

  public void stop() {
    if (!started) {
      return;
    }

    LOG.info("Stopping EventBridge broker");

    // Reverse order
    partitionBootstrapper.stop();
    executorServiceSetup.stop();
    topologySetup.stop();
    messagingServiceSetup.stop();

    started = false;
    LOG.info("EventBridge broker stopped");
  }
}
