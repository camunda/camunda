/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.bootstrap;

import io.atomix.cluster.AtomixCluster;
import io.atomix.cluster.Member;
import io.atomix.cluster.MemberId;
import io.atomix.cluster.messaging.MessagingService;
import io.atomix.primitive.partition.impl.DefaultPartitionManagementService;
import io.camunda.eventbridge.broker.partitioning.PartitionDistributor;
import io.camunda.eventbridge.broker.partitioning.PartitionFactory;
import io.camunda.eventbridge.broker.partitioning.PartitionFactory.CreatedPartition;
import io.camunda.eventbridge.broker.partitioning.PartitionLifecycle;
import io.camunda.eventbridge.core.config.EventBridgeProperties;
import io.camunda.zeebe.broker.partitioning.topology.TopologyManagerImpl;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.agrona.concurrent.IdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Computes partition distribution, uses {@link PartitionFactory} to create raft-level components,
 * creates lifecycle actors, wires role change listeners, and bootstraps raft.
 */
final class PartitionBootstrapper {

  private static final Logger LOG = LoggerFactory.getLogger(PartitionBootstrapper.class);
  private static final int CLOSE_TIMEOUT_SECONDS = 30;

  private final AtomixCluster cluster;
  private final ActorSchedulingService actorScheduler;
  private final EventBridgeProperties properties;
  private final PartitionDistributor distributor;
  private final InstantSource clock;
  private final IdGenerator idGenerator;

  private final List<CreatedPartition> createdPartitions = new ArrayList<>();
  private final List<PartitionLifecycle> lifecycles = new ArrayList<>();

  PartitionBootstrapper(
      final AtomixCluster cluster,
      final ActorSchedulingService actorScheduler,
      final EventBridgeProperties properties,
      final PartitionDistributor distributor,
      final InstantSource clock,
      final IdGenerator idGenerator) {
    this.cluster = cluster;
    this.actorScheduler = actorScheduler;
    this.properties = properties;
    this.distributor = distributor;
    this.clock = clock;
    this.idGenerator = idGenerator;
  }

  void start(
      final TopologyManagerImpl topologyManager, final MessagingService brokerMessagingService) {

    final var membershipService = cluster.getMembershipService();
    final var localMemberId = membershipService.getLocalMember().id();
    final var sortedMembers =
        membershipService.getMembers().stream().map(Member::id).sorted().toList();

    final var partitionCount = properties.broker().partitionCount();
    final var replicationFactor = properties.raft().replicationFactor();

    final var distribution =
        distributor.distributeForMember(
            localMemberId, sortedMembers, partitionCount, replicationFactor);

    LOG.info(
        "Bootstrapping EventBridge partitions {} (total: {}, replication: {}, local: {})",
        distribution.getPartitionIds(),
        partitionCount,
        replicationFactor,
        localMemberId);

    final var factory = new PartitionFactory(properties, actorScheduler);

    final var managementService =
        new DefaultPartitionManagementService(membershipService, cluster.getCommunicationService());

    for (final var partitionId : distribution.getPartitionIds()) {
      final var members = distribution.getMembersForPartition(partitionId);
      bootstrapPartition(
          partitionId,
          members,
          localMemberId,
          factory,
          managementService,
          topologyManager,
          brokerMessagingService);
    }
  }

  void stop() {
    LOG.info("Stopping {} EventBridge partition(s)", lifecycles.size());

    for (final var lifecycle : lifecycles) {
      try {
        lifecycle.closeAsync();
      } catch (final Exception e) {
        LOG.warn("Error closing lifecycle for partition {}", lifecycle.getPartitionId(), e);
      }
    }
    lifecycles.clear();

    for (final var partition : createdPartitions) {
      try {
        partition.raftPartition().close().get(CLOSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
      } catch (final Exception e) {
        LOG.warn("Error closing raft partition {}", partition.partitionId(), e);
      }

      try {
        partition.snapshotStore().closeAsync();
      } catch (final Exception e) {
        LOG.warn("Error closing snapshot store for partition {}", partition.partitionId(), e);
      }
    }
    createdPartitions.clear();
  }

  private void bootstrapPartition(
      final int partitionId,
      final java.util.Set<MemberId> members,
      final MemberId localMemberId,
      final PartitionFactory factory,
      final DefaultPartitionManagementService managementService,
      final TopologyManagerImpl topologyManager,
      final MessagingService brokerMessagingService) {

    // 1. Create raft-level components
    final var created = factory.create(partitionId, members, localMemberId);
    createdPartitions.add(created);

    // 2. Create lifecycle actor
    final var lifecycle =
        new PartitionLifecycle(
            partitionId,
            properties.broker().partitionCount(),
            created.raftPartition(),
            actorScheduler,
            brokerMessagingService,
            clock,
            idGenerator,
            topologyManager);
    lifecycles.add(lifecycle);
    actorScheduler.submitActor(lifecycle);

    // 3. Wire raft role changes to lifecycle — before bootstrap so no events are lost
    created.raftPartition().addRoleChangeListener((role, term) -> lifecycle.onRoleChange(role));

    // 4. Bootstrap raft — elections fire via the listener above
    created
        .raftPartition()
        .bootstrap(managementService, created.snapshotStore())
        .whenComplete(
            (rp, error) -> {
              if (error != null) {
                LOG.error("Failed to bootstrap raft partition {}", partitionId, error);
              } else {
                LOG.info("Raft partition {} bootstrapped", partitionId);
              }
            });
  }
}
