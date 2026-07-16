/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.bootstrap;

import io.atomix.cluster.AtomixCluster;
import io.camunda.eventbridge.broker.BrokerMembers;
import io.camunda.eventbridge.clustermetadata.reconfig.ReconfigurationCommand;
import io.camunda.eventbridge.clustermetadata.reconfig.ReconfigurationExecutor;
import io.camunda.eventbridge.clustermetadata.state.topic.TopicMetadata;
import io.camunda.eventbridge.core.config.EventBridgeProperties;
import io.camunda.eventbridge.core.partition.PartitionLeaderReporter;
import io.camunda.eventbridge.messaging.threading.ExecutorServiceFactory;
import io.camunda.zeebe.broker.client.api.BrokerTopologyManager;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.InstantSource;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
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
      final ExecutorServiceFactory executorServiceFactory,
      final IdGenerator idGenerator,
      final MeterRegistry meterRegistry,
      final BrokerTopologyManager gatewayTopologyManager,
      final PartitionLeaderReporter leaderReporter) {
    this.cluster = cluster;
    this.actorScheduler = actorScheduler;
    this.properties = properties;
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

    // 2. Start topology — coordinator + metadata BrokerInfo + SWIM gossip. There is no default data
    // group: every data partition belongs to a per-topic Raft group, provisioned by the topic
    // reconciler from the replicated registry (config-declared topics are auto-created by the
    // metadata leader). Each topic group publishes its own BrokerInfo, so the gateway resolves a
    // topic partition's leader from gossip without any seeded cluster configuration.
    topologySetup =
        new TopologySetup(
            cluster.getMembershipService(), actorScheduler, properties, gatewayTopologyManager);
    topologySetup.start();

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

    // The observed registry feeds two thread-safe caches read at points that don't carry the full
    // TopicMetadata: the coordinator leader reads topicPartitionCounts at join time to derive a
    // group's partition count, and the reconciler reads topicCleanupPolicies for the
    // change-coordinator-driven join path (whose ReconfigurationCommand carries only the topic
    // name, not its metadata — see TopicCleanupPolicies).
    final var topicPartitionCounts = new TopicPartitionCounts();
    final var topicCleanupPolicies = new TopicCleanupPolicies();

    final var topicReconciler =
        new TopicReconciler(
            partitionBootstrapper, topologySetup, localMemberId, topicCleanupPolicies);

    // 4b. Topic-registry propagation is pull-from-observed-state, not push: every broker is a
    // member or passive observer of the metadata Raft group and periodically hands its local
    // replicated registry to this sink (see MetadataPartition), which reconciles the broker's local
    // topic Raft groups. The reconcile runs off the metadata partition's actor thread (the snapshot
    // read already happened on it). This replaces the old 2s whole-registry broadcast.
    //
    // Only topics that are servable (not DELETING) are cached in topicPartitionCounts; a missing
    // entry resolves to 0, which the join processor rejects as an unknown topic.
    final Consumer<Map<String, TopicMetadata>> registryReconciler =
        desired -> {
          topicPartitionCounts.update(desired);
          topicCleanupPolicies.update(desired);
          executorService.execute(() -> topicReconciler.reconcile(desired));
        };

    // 4c. Change-coordinator command channel (CC-3). The broker that must act on a reassignment
    // step
    // executes the runtime Raft join/leave and replies only once it has completed (the confirm).
    comm.replyToAsync(
        ReconfigurationCommand.SUBJECT,
        ReconfigurationCommand::decode,
        cmd -> topicReconciler.apply(cmd).thenApply(done -> new byte[0]),
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
                BrokerMembers.memberId(recipientNodeId),
                Duration.ofSeconds(30));

    // 5. Start the auxiliary groups — raft + lifecycle actors (uses broker messaging service).
    // Topic
    // data partitions are not started here; the topic reconciler provisions them from the registry.
    partitionBootstrapper.start(
        topologySetup.getCoordinatorTopologyManager(),
        topologySetup.getMetadataTopologyManager(),
        brokerMessagingService,
        topicPartitionCounts,
        registryReconciler,
        reconfigurationExecutor);

    LOG.info("EventBridge broker started — waiting for raft elections");
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
