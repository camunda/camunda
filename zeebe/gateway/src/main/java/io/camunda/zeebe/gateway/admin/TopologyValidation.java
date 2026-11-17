/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.admin;

import io.camunda.zeebe.broker.client.api.BrokerClusterState;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class TopologyValidation {

  private TopologyValidation() {}

  /**
   * Throws {@link IncompleteTopologyException} unless every partition of the topology, and every
   * one of its members, is known — shared by every caller that fans out to every partition replica
   * (e.g. {@link ClusterRocksDbMigrationStatusProvider}), since a partially-known topology would
   * otherwise silently skip a partition or replica instead of counting it against the result.
   */
  public static void validateTopology(final BrokerClusterState topology) {
    final var replicationFactor = topology.getReplicationFactor();
    final var expectedPartitions = topology.getPartitionsCount();
    final var partitions = topology.getPartitions();

    if (partitions.size() != expectedPartitions) {
      throw new IncompleteTopologyException(
          "Found %s partitions but expected %s, current topology: %s"
              .formatted(partitions.size(), expectedPartitions, topology));
    }

    for (final var partition : partitions) {
      final var leaderId = topology.getLeaderForPartition(partition);

      if (leaderId == null) {
        throw new IncompleteTopologyException(
            "Leader of partition %s is not known, current topology: %s"
                .formatted(partition, topology));
      }

      final var followers = topology.getFollowersForPartition(partition);
      final var memberCount = followers.size() + 1;
      if (memberCount != replicationFactor) {
        throw new IncompleteTopologyException(
            "Expected %s members of partition %s but found %s, current topology: %s"
                .formatted(replicationFactor, partition, memberCount, topology));
      }
    }
  }
}
