/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.consumergroups.stream;

import io.atomix.cluster.messaging.MessagingService;
import io.atomix.raft.partition.RaftPartition;
import io.camunda.eventbridge.consumergroups.membership.ConsumerGroupCoordinator;
import io.camunda.eventbridge.consumergroups.membership.TopicRegistry;
import io.camunda.eventbridge.consumergroups.state.EventBridgeColumnFamilies;
import io.camunda.eventbridge.consumergroups.transport.CoordinationRequestHandler;
import io.camunda.eventbridge.stream.RaftPartitionLifecycle;
import io.camunda.zeebe.broker.logstreams.AtomixLogStorage;
import io.camunda.zeebe.broker.partitioning.topology.TopologyManagerImpl;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.snapshots.ConstructableSnapshotStore;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.file.Path;
import java.time.InstantSource;

/**
 * Lifecycle actor for a coordinator Raft partition (one shard of the coordinator group). It runs a
 * {@link CoordinatorStream} and, when leader, a {@link ConsumerGroupCoordinator} that serves
 * join/heartbeat/leave/commit and replicates offsets + group metadata. Mirrors {@code
 * MetadataPartition}; see {@link RaftPartitionLifecycle} for the role-change/recovery/snapshot
 * flow.
 */
public final class CoordinatorPartition
    extends RaftPartitionLifecycle<EventBridgeColumnFamilies, CoordinatorStream> {

  private final TopicRegistry topicRegistry;

  private ConsumerGroupCoordinator consumerGroupCoordinator;

  public CoordinatorPartition(
      final int partitionId,
      final TopicRegistry topicRegistry,
      final RaftPartition raftPartition,
      final ActorSchedulingService actorScheduler,
      final MessagingService messagingService,
      final InstantSource clock,
      final Path runtimeDirectory,
      final ConstructableSnapshotStore snapshotStore,
      final TopologyManagerImpl coordinatorTopologyManager) {
    super(
        partitionId,
        raftPartition,
        actorScheduler,
        messagingService,
        clock,
        runtimeDirectory,
        snapshotStore,
        coordinatorTopologyManager);
    this.topicRegistry = topicRegistry;
  }

  @Override
  protected String label() {
    return "Coordinator";
  }

  @Override
  protected CoordinatorStream createStream(
      final int partitionId,
      final AtomixLogStorage logStorage,
      final ActorSchedulingService actorScheduler,
      final ZeebeDb<EventBridgeColumnFamilies> zeebeDb,
      final InstantSource clock,
      final MeterRegistry meterRegistry) {
    return new CoordinatorStream(
        partitionId, logStorage, actorScheduler, zeebeDb, clock, meterRegistry, topicRegistry);
  }

  @Override
  protected void onLeaderReady() {
    consumerGroupCoordinator = new ConsumerGroupCoordinator(partitionId, clock, stream);
    actorScheduler.submitActor(consumerGroupCoordinator);
    requestHandlerRegistry.register(
        CoordinationRequestHandler.topicName(partitionId),
        new CoordinationRequestHandler(partitionId, consumerGroupCoordinator));
  }

  @Override
  protected void onManagerTeardown() {
    if (consumerGroupCoordinator != null) {
      requestHandlerRegistry.unregister(CoordinationRequestHandler.topicName(partitionId));
      consumerGroupCoordinator.closeAsync();
      consumerGroupCoordinator = null;
    }
  }
}
