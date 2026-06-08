/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning;

import io.atomix.cluster.ClusterMembershipService;
import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.PartitionMetadata;
import io.camunda.zeebe.broker.partitioning.topology.ClusterConfigurationService;
import io.camunda.zeebe.broker.partitioning.topology.TopologyManagerImpl;
import io.camunda.zeebe.protocol.impl.encoding.BrokerInfo;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import java.util.List;

/**
 * Holds the state and lifecycle shared by all {@link PartitionManager} implementations: the {@link
 * TopologyManagerImpl} bound to this partition group, the cluster configuration used to resolve the
 * local partitions, and the concurrency control on which the manager runs.
 */
abstract class AbstractPartitionManager implements PartitionManager {

  protected final String partitionGroup;
  protected final ConcurrencyControl concurrencyControl;
  protected final ActorSchedulingService actorSchedulingService;
  protected final ClusterConfigurationService clusterConfigurationService;
  protected final ClusterMembershipService membershipService;
  protected final TopologyManagerImpl topologyManager;

  protected AbstractPartitionManager(
      final String partitionGroup,
      final ConcurrencyControl concurrencyControl,
      final ActorSchedulingService actorSchedulingService,
      final ClusterConfigurationService clusterConfigurationService,
      final ClusterMembershipService membershipService,
      final BrokerInfo brokerInfo) {
    this.partitionGroup = partitionGroup;
    this.concurrencyControl = concurrencyControl;
    this.actorSchedulingService = actorSchedulingService;
    this.clusterConfigurationService = clusterConfigurationService;
    this.membershipService = membershipService;
    topologyManager =
        new TopologyManagerImpl(membershipService, brokerInfo.withPartitionGroup(partitionGroup));
  }

  protected MemberId localMemberId() {
    return membershipService.getLocalMember().id();
  }

  /**
   * Resolves the partitions of this partition group that the local broker is a member of, according
   * to the current partition distribution.
   */
  protected List<PartitionMetadata> localPartitions() {
    final var localMemberId = localMemberId();
    // The default physical tenant's partition distribution is the only one stored in dynamic
    // config; other physical tenants derive their distribution by rewriting the group on every
    // PartitionId.
    return clusterConfigurationService
        .getPartitionDistribution()
        .withGroupName(partitionGroup)
        .partitions()
        .stream()
        .filter(p -> p.members().contains(localMemberId))
        .toList();
  }

  protected void submitTopologyManager() {
    actorSchedulingService.submitActor(topologyManager);
  }

  protected ActorFuture<Void> closeTopologyManager() {
    return topologyManager.closeAsync();
  }
}
