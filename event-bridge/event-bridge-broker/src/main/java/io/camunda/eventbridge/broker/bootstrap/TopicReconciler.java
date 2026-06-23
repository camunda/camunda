/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.bootstrap;

import io.atomix.cluster.MemberId;
import io.camunda.eventbridge.broker.partitioning.PartitionFactory;
import io.camunda.eventbridge.coordinator.stream.TopicMetadata;
import io.camunda.eventbridge.coordinator.stream.TopicProvisionedGossip;
import io.camunda.zeebe.broker.partitioning.topology.TopologyManagerImpl;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The broker-side owner of this node's topic Raft-group replicas. It serves two drivers, sharing
 * per-(group, partition) running state and per-topic topology managers so they never conflict:
 *
 * <ul>
 *   <li><b>reconcile</b> (broadcast-driven): bootstraps the partitions this broker is assigned that
 *       it isn't already running — but only those it can <em>bootstrap</em>: a fresh group at
 *       creation, or recovery of on-disk data after a restart. A partition assigned to this broker
 *       on an already-ACTIVE topic with no local data is a reassignment add — it is skipped here
 *       and left to {@link #join}.
 *   <li><b>join/leave</b> (change-coordinator-driven): adds or removes a single replica of a
 *       running group at runtime (a reassignment step).
 * </ul>
 *
 * Bootstrap-vs-join rule: has-on-disk-data &rarr; bootstrap (recover); no data + CREATING &rarr;
 * bootstrap (initial); no data + ACTIVE &rarr; join (handled by the change-coordinator).
 */
final class TopicReconciler {

  private static final Logger LOG = LoggerFactory.getLogger(TopicReconciler.class);

  private final PartitionBootstrapper partitionBootstrapper;
  private final TopologySetup topologySetup;
  private final MemberId localMemberId;
  private final int localNodeId;
  private final TopicProvisionedGossip.Publisher provisionedPublisher;

  // Per-topic routing topology manager, shared by reconcile and join (created on first need).
  private final Map<String, TopologyManagerImpl> topologyManagers = new ConcurrentHashMap<>();

  TopicReconciler(
      final PartitionBootstrapper partitionBootstrapper,
      final TopologySetup topologySetup,
      final MemberId localMemberId,
      final TopicProvisionedGossip.Publisher provisionedPublisher) {
    this.partitionBootstrapper = partitionBootstrapper;
    this.topologySetup = topologySetup;
    this.localMemberId = localMemberId;
    this.provisionedPublisher = provisionedPublisher;
    localNodeId = parseNodeId(localMemberId.id());
  }

  /**
   * Bootstraps newly-assigned local partitions this broker can bootstrap (see the class doc rule).
   * Synchronized so concurrent deliveries (remote broadcast + local self-delivery) and reassignment
   * commands cannot race on the same topic's topology manager / partitions.
   */
  synchronized void reconcile(final Map<String, TopicMetadata> desired) {
    desired.forEach(
        (name, meta) -> {
          if (meta.status() != TopicMetadata.TopicStatus.DELETING) {
            reconcileTopic(name, meta);
          }
        });
  }

  private void reconcileTopic(final String name, final TopicMetadata meta) {
    final var groupName = PartitionFactory.topicGroupName(name);
    final var creating = meta.status() == TopicMetadata.TopicStatus.CREATING;
    final var started = new ArrayList<Integer>();

    for (final var entry : meta.assignment().entrySet()) {
      final var partitionId = entry.getKey();
      final var replicas = entry.getValue();
      if (!replicas.contains(localNodeId)
          || partitionBootstrapper.isRunning(groupName, partitionId)) {
        continue;
      }
      // Only bootstrap what we may: fresh group (CREATING) or recover on-disk data. A no-data
      // partition on an ACTIVE topic is a reassignment add — leave it to join().
      if (!creating && !partitionBootstrapper.hasData(groupName, partitionId)) {
        continue;
      }
      partitionBootstrapper.provisionDataPartition(
          groupName, partitionId, memberSet(replicas), topologyFor(name, meta.partitionCount()));
      started.add(partitionId);
    }

    if (!started.isEmpty()) {
      LOG.info("Provisioned topic {} (group {}) local partitions {}", name, groupName, started);
      if (provisionedPublisher != null) {
        provisionedPublisher.publish(name, started);
      }
    }
  }

  /** Adds a single replica of a running topic partition to this broker (a reassignment step). */
  synchronized java.util.concurrent.CompletableFuture<Void> join(
      final String topic,
      final int partitionId,
      final List<Integer> members,
      final int partitionCount) {
    final var groupName = PartitionFactory.topicGroupName(topic);
    if (partitionBootstrapper.isRunning(groupName, partitionId)) {
      return java.util.concurrent.CompletableFuture.completedFuture(null);
    }
    LOG.info("Joining topic {} partition {} as member {}", topic, partitionId, localNodeId);
    return partitionBootstrapper.joinDataPartition(
        groupName, partitionId, memberSet(members), topologyFor(topic, partitionCount));
  }

  /** Removes this broker's replica of a topic partition (a reassignment step). */
  synchronized java.util.concurrent.CompletableFuture<Void> leave(
      final String topic, final int partitionId) {
    final var groupName = PartitionFactory.topicGroupName(topic);
    LOG.info("Leaving topic {} partition {} as member {}", topic, partitionId, localNodeId);
    return partitionBootstrapper.leaveDataPartition(groupName, partitionId);
  }

  private TopologyManagerImpl topologyFor(final String topic, final int partitionCount) {
    return topologyManagers.computeIfAbsent(
        topic,
        t ->
            topologySetup.createTopicTopologyManager(
                PartitionFactory.topicGroupName(t), partitionCount));
  }

  private static Set<MemberId> memberSet(final List<Integer> nodeIds) {
    return nodeIds.stream().map(id -> MemberId.from("broker-" + id)).collect(Collectors.toSet());
  }

  private static int parseNodeId(final String memberId) {
    try {
      return Integer.parseInt(memberId.replaceAll("[^0-9]", ""));
    } catch (final NumberFormatException e) {
      return 0;
    }
  }
}
