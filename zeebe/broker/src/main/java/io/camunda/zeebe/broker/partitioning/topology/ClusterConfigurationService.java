/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning.topology;

import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.PartitionMetadata;
import io.camunda.zeebe.broker.bootstrap.BrokerStartupContext;
import io.camunda.zeebe.dynamic.config.ClusterConfigurationManager.InconsistentConfigurationListener;
import io.camunda.zeebe.dynamic.config.changes.ClusterChangeExecutor;
import io.camunda.zeebe.dynamic.config.changes.PartitionChangeExecutor;
import io.camunda.zeebe.dynamic.config.changes.PartitionScalingChangeExecutor;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionState.State;
import io.camunda.zeebe.scheduler.AsyncClosable;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import java.util.List;

public interface ClusterConfigurationService extends AsyncClosable {
  PartitionDistribution getPartitionDistribution();

  void registerPartitionChangeExecutors(
      PartitionChangeExecutor partitionChangeExecutor,
      PartitionScalingChangeExecutor partitionScalingChangeExecutor);

  void removePartitionChangeExecutor();

  ActorFuture<Void> start(BrokerStartupContext brokerStartupContext);

  void registerInconsistentConfigurationListener(InconsistentConfigurationListener listener);

  void removeInconsistentConfigurationListener();

  default List<PartitionMetadata> getMemberPartitions(final MemberId memberId) {
    final var partitionDistribution = getPartitionDistribution();

    if (partitionDistribution != null) {
      return partitionDistribution.partitions().stream()
          .filter(partition -> partition.members().contains(memberId))
          .toList();
    }

    throw new IllegalStateException(
        "Cannot get member partitions before the topology manager is started");
  }

  ClusterConfiguration getInitialClusterConfiguration();

  /**
   * Returns the current cluster configuration, including in-progress topology changes. Unlike
   * {@link #getInitialClusterConfiguration()}, this reflects updates that occurred after startup
   * (e.g., partitions in {@code JOINING} state during scale-up).
   */
  ClusterConfiguration getCurrentClusterConfiguration();

  /**
   * Returns the number of partitions assigned to the given member that are currently in the {@code
   * JOINING} state.
   */
  default int getJoiningMemberPartitionCount(final MemberId memberId) {
    final var config = getCurrentClusterConfiguration();
    if (config == null || !config.hasMember(memberId)) {
      return 0;
    }
    return (int)
        config.getMember(memberId).partitions().values().stream()
            .filter(p -> p.state() == State.JOINING)
            .count();
  }

  ClusterChangeExecutor getClusterChangeExecutor();

  ActorFuture<ClusterConfiguration> getLatestClusterConfiguration();
}
