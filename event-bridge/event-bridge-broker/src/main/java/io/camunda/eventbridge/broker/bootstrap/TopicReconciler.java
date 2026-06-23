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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reconciles this broker's local topic Raft groups against the desired topic registry broadcast by
 * the coordinator (the reconcile half of Option 1). Placement is decided centrally by the
 * coordinator and carried in the registry as an explicit assignment (partition id &rarr; replica
 * node ids); this broker simply provisions the partitions whose replica set includes the local
 * node. It does not derive placement, so the coordinator can rebalance later by rewriting the
 * assignment.
 *
 * <p>Reconciliation is idempotent — a topic already provisioned is skipped — so re-broadcasts and
 * duplicate deliveries are harmless. Removal of de-provisioned topics is intentionally not handled
 * yet (a topic only ever appears); tearing down a topic's Raft group is a later increment.
 */
final class TopicReconciler {

  private static final Logger LOG = LoggerFactory.getLogger(TopicReconciler.class);

  private final PartitionBootstrapper partitionBootstrapper;
  private final TopologySetup topologySetup;
  private final MemberId localMemberId;
  private final int localNodeId;

  // Topics whose local partitions have already been provisioned (idempotency guard).
  private final Set<String> provisioned = ConcurrentHashMap.newKeySet();

  TopicReconciler(
      final PartitionBootstrapper partitionBootstrapper,
      final TopologySetup topologySetup,
      final MemberId localMemberId) {
    this.partitionBootstrapper = partitionBootstrapper;
    this.topologySetup = topologySetup;
    this.localMemberId = localMemberId;
    localNodeId = parseNodeId(localMemberId.id());
  }

  /**
   * Provisions any newly-desired topic's local partitions. Synchronized so concurrent deliveries
   * (remote broadcast + local self-delivery) cannot double-provision the same topic.
   */
  synchronized void reconcile(final Map<String, TopicMetadata> desired) {
    desired.forEach(
        (name, meta) -> {
          if (provisioned.contains(name) || meta.status() == TopicMetadata.TopicStatus.DELETING) {
            return;
          }
          provisionTopic(name, meta);
        });
  }

  private void provisionTopic(final String name, final TopicMetadata meta) {
    final var groupName = PartitionFactory.topicGroupName(name);

    // The local partitions are exactly those whose centrally-decided replica set includes this
    // node.
    final var localPartitions =
        meta.assignment().entrySet().stream()
            .filter(e -> e.getValue().contains(localNodeId))
            .toList();

    // Mark provisioned even with no local partitions: nothing to start here, but we must not
    // recompute every broadcast cycle.
    provisioned.add(name);
    if (localPartitions.isEmpty()) {
      LOG.debug("Topic {} has no partitions assigned to {}", name, localMemberId);
      return;
    }

    final var topologyManager =
        topologySetup.createTopicTopologyManager(groupName, meta.partitionCount());
    final var startedIds = new HashSet<Integer>();
    for (final var partition : localPartitions) {
      final var members =
          partition.getValue().stream()
              .map(id -> MemberId.from("broker-" + id))
              .collect(Collectors.toSet());
      partitionBootstrapper.provisionDataPartition(
          groupName, partition.getKey(), members, topologyManager);
      startedIds.add(partition.getKey());
    }
    LOG.info("Provisioned topic {} (group {}) local partitions {}", name, groupName, startedIds);
  }

  private static int parseNodeId(final String memberId) {
    try {
      return Integer.parseInt(memberId.replaceAll("[^0-9]", ""));
    } catch (final NumberFormatException e) {
      return 0;
    }
  }
}
