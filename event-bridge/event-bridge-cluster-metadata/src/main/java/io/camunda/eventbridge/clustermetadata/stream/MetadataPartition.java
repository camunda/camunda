/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.clustermetadata.stream;

import io.atomix.cluster.messaging.MessagingService;
import io.atomix.raft.partition.RaftPartition;
import io.camunda.eventbridge.clustermetadata.MetadataManager;
import io.camunda.eventbridge.clustermetadata.MetadataQueryHandler;
import io.camunda.eventbridge.clustermetadata.membership.BrokerHeartbeatHandler;
import io.camunda.eventbridge.clustermetadata.reconfig.ReconfigurationExecutor;
import io.camunda.eventbridge.clustermetadata.state.MetadataColumnFamilies;
import io.camunda.eventbridge.clustermetadata.state.topic.TopicMetadata;
import io.camunda.eventbridge.clustermetadata.state.topic.TopicQueryService;
import io.camunda.eventbridge.clustermetadata.transport.MetadataRequestHandler;
import io.camunda.eventbridge.stream.RaftPartitionLifecycle;
import io.camunda.zeebe.broker.logstreams.AtomixLogStorage;
import io.camunda.zeebe.broker.partitioning.topology.TopologyManagerImpl;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.snapshots.ConstructableSnapshotStore;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.file.Path;
import java.time.Duration;
import java.time.InstantSource;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lifecycle actor for the metadata Raft partition (the single shard of the {@code
 * event-bridge-metadata} group that holds the topic registry). It runs a {@link MetadataStream}
 * and, when leader, a {@link MetadataManager} that serves topic admin
 * (create/delete/reassign/list), runs the {@code CREATING -> ACTIVE} transition and the
 * change-coordinator.
 *
 * <p>On <em>every</em> role this partition periodically feeds its local replicated registry to a
 * reconcile sink, so each broker provisions its assigned topic Raft groups from its own observed
 * copy of the registry rather than from a leader-side network broadcast.
 *
 * <p>Mirrors {@code CoordinatorPartition}; see {@link RaftPartitionLifecycle} for the
 * role-change/recovery/snapshot flow.
 */
public final class MetadataPartition
    extends RaftPartitionLifecycle<MetadataColumnFamilies, MetadataStream> {

  private static final Logger LOG = LoggerFactory.getLogger(MetadataPartition.class);
  // Per-broker reconcile cadence: re-asserting the local replicated registry is the anti-entropy
  // that lets a broker that missed an update still converge (replaces the old 2s leader broadcast).
  private static final Duration RECONCILE_INTERVAL = Duration.ofSeconds(1);

  private final Consumer<Map<String, TopicMetadata>> registryReconciler;
  private final ReconfigurationExecutor reconfigurationExecutor;

  private MetadataManager metadataManager;
  private MetadataQueryHandler metadataQueryHandler;
  private BrokerHeartbeatHandler brokerHeartbeatHandler;
  // This partition actor's own off-actor read view, used by the reconcile loop on every role; built
  // lazily once the stream exists so its context/flyweights belong to this actor.
  private TopicQueryService reconcileTopics;

  public MetadataPartition(
      final int partitionId,
      final RaftPartition raftPartition,
      final ActorSchedulingService actorScheduler,
      final MessagingService messagingService,
      final InstantSource clock,
      final Path runtimeDirectory,
      final ConstructableSnapshotStore snapshotStore,
      final TopologyManagerImpl metadataTopologyManager,
      final Consumer<Map<String, TopicMetadata>> registryReconciler,
      final ReconfigurationExecutor reconfigurationExecutor) {
    super(
        partitionId,
        raftPartition,
        actorScheduler,
        messagingService,
        clock,
        runtimeDirectory,
        snapshotStore,
        metadataTopologyManager);
    this.registryReconciler = registryReconciler;
    this.reconfigurationExecutor = reconfigurationExecutor;
  }

  @Override
  protected String label() {
    return "Metadata";
  }

  @Override
  protected MetadataStream createStream(
      final int partitionId,
      final AtomixLogStorage logStorage,
      final ActorSchedulingService actorScheduler,
      final ZeebeDb<MetadataColumnFamilies> zeebeDb,
      final InstantSource clock,
      final MeterRegistry meterRegistry) {
    return new MetadataStream(
        partitionId,
        logStorage,
        actorScheduler,
        zeebeDb,
        clock,
        meterRegistry,
        this::registeredBrokerIds);
  }

  @Override
  protected void onActorStarted() {
    scheduleReconcile();
  }

  @Override
  protected void onLeaderReady() {
    metadataManager =
        new MetadataManager(
            partitionId,
            stream,
            stream.newTopicQueryService(),
            stream.newBrokerQueryService(),
            reconfigurationExecutor);
    actorScheduler.submitActor(metadataManager);
    metadataQueryHandler = new MetadataQueryHandler(partitionId, stream.newTopicQueryService());
    actorScheduler.submitActor(metadataQueryHandler);
    brokerHeartbeatHandler = new BrokerHeartbeatHandler(partitionId, clock, stream);
    actorScheduler.submitActor(brokerHeartbeatHandler);
    requestHandlerRegistry.register(
        MetadataRequestHandler.topicName(partitionId),
        new MetadataRequestHandler(
            partitionId, metadataManager, metadataQueryHandler, brokerHeartbeatHandler));
  }

  @Override
  protected void onManagerTeardown() {
    if (metadataManager != null) {
      requestHandlerRegistry.unregister(MetadataRequestHandler.topicName(partitionId));
      metadataManager.closeAsync();
      metadataManager = null;
    }
    if (metadataQueryHandler != null) {
      metadataQueryHandler.closeAsync();
      metadataQueryHandler = null;
    }
    if (brokerHeartbeatHandler != null) {
      brokerHeartbeatHandler.closeAsync();
      brokerHeartbeatHandler = null;
    }
  }

  /**
   * Periodically hands this broker's local replicated registry to the reconcile sink, on every role
   * (leader, follower, passive observer). Reads through this actor's own {@link TopicQueryService}
   * (a private context, no shared flyweights), so it is safe to call from the partition actor while
   * the stream processor writes; the sink dispatches the actual provisioning off-actor. No-op until
   * the stream has started.
   */
  private void scheduleReconcile() {
    if (registryReconciler != null && stream != null) {
      try {
        if (reconcileTopics == null) {
          reconcileTopics = stream.newTopicQueryService();
        }
        registryReconciler.accept(reconcileTopics.topicsSnapshot());
      } catch (final Exception e) {
        LOG.warn("Metadata partition {} — registry reconcile failed", partitionId, e);
      }
    }
    actor.schedule(RECONCILE_INTERVAL, this::scheduleReconcile);
  }

  /**
   * The broker node ids registered in the metadata Raft group — its live membership (voting members
   * + passive observers). This is the placeable broker set the leader's {@link MetadataManager}
   * uses instead of a static configured cluster size, so a broker is only a placement target once
   * it has joined (registered with) the group.
   */
  private List<Integer> registeredBrokerIds() {
    return raftPartition.members().stream()
        .map(member -> parseNodeId(member.id()))
        .filter(id -> id >= 0)
        .distinct()
        .sorted()
        .toList();
  }

  private static int parseNodeId(final String memberId) {
    try {
      return Integer.parseInt(memberId.replaceAll("[^0-9]", ""));
    } catch (final NumberFormatException e) {
      return -1;
    }
  }
}
