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
import java.util.List;
import org.jspecify.annotations.NullMarked;

/**
 * Resolves the replicas of a partition from a {@link BrokerClusterState}, for callers that need to
 * query partition replicas directly over the admin API (e.g. {@link
 * ClusterRocksDbMigrationStatusProvider}, {@link ClusterExporterMigrationStatusProvider}).
 */
@NullMarked
final class PartitionReplicas {

  private PartitionReplicas() {}

  /**
   * Every replica of a partition, as a single ordered list — leader first, then followers, then
   * inactive members. Inactive members are included because they keep their partition data and can
   * be promoted back once they recover, so they are as relevant as any other replica for a
   * condition that genuinely differs per replica (e.g. RocksDB migration state). The order lets a
   * caller that only needs one reachable replica to answer prefer the leader and fall back through
   * the rest; a caller that needs every replica can just iterate the whole list, ignoring order.
   */
  static List<BrokerMemberId> allOf(final BrokerClusterState topology, final int partitionId) {
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
