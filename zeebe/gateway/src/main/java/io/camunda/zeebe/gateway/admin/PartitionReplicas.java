/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.admin;

import io.atomix.cluster.BrokerMemberId;
import io.camunda.zeebe.broker.client.api.BrokerClusterState;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.NullMarked;

/**
 * Resolves the replicas of a partition from a {@link BrokerClusterState}, for callers that need to
 * query every partition replica directly over the admin API (e.g. {@link
 * ClusterRocksDbMigrationStatusProvider}, {@link ClusterExporterMigrationStatusProvider}).
 */
@NullMarked
final class PartitionReplicas {

  private PartitionReplicas() {}

  /**
   * Every replica of a partition: leader, followers, and inactive members. Inactive members keep
   * their data and can be promoted back once they recover, so they are as relevant as any other
   * replica for a condition that genuinely differs per replica (e.g. RocksDB migration state).
   */
  static Set<BrokerMemberId> allOf(final BrokerClusterState topology, final int partitionId) {
    final var leader = topology.getLeaderForPartition(partitionId);
    final var followers = topology.getFollowersForPartition(partitionId);
    final var inactive = topology.getInactiveNodesForPartition(partitionId);

    final var members = new HashSet<BrokerMemberId>(topology.getReplicationFactor());
    if (leader != null) {
      members.add(leader);
    }
    members.addAll(followers);
    members.addAll(inactive);
    return members;
  }

  /**
   * The replicas of a partition as a single ordered candidate list — leader first, then followers,
   * then inactive members — for callers that only need one reachable replica to answer, e.g.
   * because the condition being checked genuinely only runs on the leader and a follower's answer
   * can only lag it, never diverge from or run ahead of it.
   */
  static List<BrokerMemberId> preferenceOrderOf(
      final BrokerClusterState topology, final int partitionId) {
    final var candidates = new ArrayList<BrokerMemberId>(topology.getReplicationFactor());
    final var leader = topology.getLeaderForPartition(partitionId);
    if (leader != null) {
      candidates.add(leader);
    }
    candidates.addAll(topology.getFollowersForPartition(partitionId));
    candidates.addAll(topology.getInactiveNodesForPartition(partitionId));
    return candidates;
  }
}
