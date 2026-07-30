/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning.topology;

import io.atomix.cluster.BrokerMemberId;
import io.atomix.cluster.MemberId;
import io.camunda.zeebe.broker.client.api.BrokerTopologyManager;
import io.camunda.zeebe.dynamic.config.rebalance.PartitionLeaders;
import java.util.Optional;
import org.jspecify.annotations.NullMarked;

/**
 * Answers the rebalancing coordinator's questions about leadership from this broker's live view of
 * the cluster topology, which every broker maintains from the roles its peers gossip.
 */
@NullMarked
final class TopologyPartitionLeaders implements PartitionLeaders {

  private final BrokerTopologyManager topologyManager;

  TopologyPartitionLeaders(final BrokerTopologyManager topologyManager) {
    this.topologyManager = topologyManager;
  }

  @Override
  public Optional<MemberId> currentLeader(final String physicalTenantId, final int partitionId) {
    return Optional.ofNullable(
            topologyManager.getTopology(physicalTenantId).getLeaderForPartition(partitionId))
        .map(BrokerMemberId::memberId);
  }
}
