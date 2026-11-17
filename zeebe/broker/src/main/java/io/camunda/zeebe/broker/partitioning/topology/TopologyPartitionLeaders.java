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
import io.camunda.zeebe.broker.client.api.BrokerClusterState;
import io.camunda.zeebe.broker.client.api.BrokerTopologyManager;
import io.camunda.zeebe.rebalance.PartitionLeaders;
import java.util.Optional;
import org.jspecify.annotations.NullMarked;

/** Identifies which member currently leads each partition, as the cluster topology sees it. */
@NullMarked
public final class TopologyPartitionLeaders implements PartitionLeaders {

  private final BrokerTopologyManager topologyManager;

  public TopologyPartitionLeaders(final BrokerTopologyManager topologyManager) {
    this.topologyManager = topologyManager;
  }

  @Override
  public PartitionGroupLeaders forGroup(final String physicalTenantId) {
    return new TopologyGroupLeaders(topologyManager.getTopology(physicalTenantId));
  }

  private record TopologyGroupLeaders(BrokerClusterState topology)
      implements PartitionGroupLeaders {
    @Override
    public Optional<MemberId> currentLeader(final int partitionId) {
      return Optional.ofNullable(topology.getLeaderForPartition(partitionId))
          .map(BrokerMemberId::memberId);
    }
  }
}
