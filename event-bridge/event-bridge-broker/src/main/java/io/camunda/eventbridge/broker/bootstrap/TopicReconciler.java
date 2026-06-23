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
import io.camunda.eventbridge.broker.partitioning.RoundRobinPartitionDistributor;
import io.camunda.eventbridge.coordinator.stream.TopicMetadata;
import io.camunda.eventbridge.core.config.EventBridgeProperties;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reconciles this broker's local topic Raft groups against the desired topic registry broadcast by
 * the coordinator (the reconcile half of Option 1). The assignment is deterministic: every broker
 * derives the same placement from {@code (topic, partitionCount, replicationFactor, cluster
 * members)} via the same round-robin distributor the data and coordinator groups use, then starts
 * the topic partitions whose members include the local node.
 *
 * <p>Reconciliation is idempotent — a topic already provisioned is skipped — so re-broadcasts and
 * duplicate deliveries are harmless. Removal of de-provisioned topics is intentionally not handled
 * yet (a topic only ever appears); tearing down a topic's Raft group is a later increment.
 */
final class TopicReconciler {

  private static final Logger LOG = LoggerFactory.getLogger(TopicReconciler.class);

  private final EventBridgeProperties properties;
  private final PartitionBootstrapper partitionBootstrapper;
  private final TopologySetup topologySetup;
  private final MemberId localMemberId;
  private final List<MemberId> clusterMembers;

  // Topics whose local partitions have already been provisioned (idempotency guard).
  private final Set<String> provisioned = ConcurrentHashMap.newKeySet();

  TopicReconciler(
      final EventBridgeProperties properties,
      final PartitionBootstrapper partitionBootstrapper,
      final TopologySetup topologySetup,
      final MemberId localMemberId) {
    this.properties = properties;
    this.partitionBootstrapper = partitionBootstrapper;
    this.topologySetup = topologySetup;
    this.localMemberId = localMemberId;
    clusterMembers =
        IntStream.range(0, properties.cluster().clusterSize())
            .mapToObj(i -> MemberId.from("broker-" + i))
            .sorted()
            .toList();
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
    final var replicationFactor =
        Math.min(meta.replicationFactor(), properties.cluster().clusterSize());
    final var distribution =
        new RoundRobinPartitionDistributor(groupName)
            .distributePartitions(clusterMembers, meta.partitionCount(), replicationFactor);

    final var localPartitions =
        distribution.stream().filter(p -> p.members().contains(localMemberId)).toList();

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
      partitionBootstrapper.provisionDataPartition(
          groupName, partition.id().id(), Set.copyOf(partition.members()), topologyManager);
      startedIds.add(partition.id().id());
    }
    LOG.info("Provisioned topic {} (group {}) local partitions {}", name, groupName, startedIds);
  }
}
