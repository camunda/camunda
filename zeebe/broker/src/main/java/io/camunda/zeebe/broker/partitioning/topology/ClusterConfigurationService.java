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
import io.camunda.zeebe.dynamic.config.ClusterConfigurationUpdateNotifier.ClusterConfigurationUpdateListener;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestValidator;
import io.camunda.zeebe.dynamic.config.changes.ClusterChangeExecutor;
import io.camunda.zeebe.dynamic.config.changes.ModeChangeExecutor;
import io.camunda.zeebe.dynamic.config.changes.PartitionChangeExecutor;
import io.camunda.zeebe.dynamic.config.changes.PartitionScalingChangeExecutor;
import io.camunda.zeebe.dynamic.config.changes.RestoreChangeExecutor;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionState.State;
import io.camunda.zeebe.scheduler.AsyncClosable;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public interface ClusterConfigurationService extends AsyncClosable {

  PartitionDistribution getPartitionDistribution(String physicalTenantId);

  Map<String, PartitionDistribution> getPartitionDistribution();

  default void registerPartitionChangeExecutors(
      final String physicalTenantId,
      final PartitionChangeExecutor partitionChangeExecutor,
      final PartitionScalingChangeExecutor partitionScalingChangeExecutor) {
    registerPartitionChangeExecutors(
        physicalTenantId,
        partitionChangeExecutor,
        partitionScalingChangeExecutor,
        new RestoreChangeExecutor.DeniedRestoreChangeExecutor());
  }

  void registerPartitionChangeExecutors(
      final String physicalTenantId,
      PartitionChangeExecutor partitionChangeExecutor,
      PartitionScalingChangeExecutor partitionScalingChangeExecutor,
      RestoreChangeExecutor restoreChangeExecutor);

  void removePartitionChangeExecutor(final String physicalTenantId);

  void registerModeChangeExecutor(
      final String physicalTenantId, ModeChangeExecutor modeChangeExecutor);

  void removeModeChangeExecutor(final String physicalTenantId);

  ActorFuture<Void> start(BrokerStartupContext brokerStartupContext);

  void registerInconsistentConfigurationListener(InconsistentConfigurationListener listener);

  void removeInconsistentConfigurationListener();

  void addUpdateListener(ClusterConfigurationUpdateListener listener);

  void removeUpdateListener(ClusterConfigurationUpdateListener listener);

  void registerRequestValidator(
      @Nullable String physicalTenantId, ClusterConfigurationRequestValidator<?, ?> validator);

  void removeRequestValidator(
      @Nullable String physicalTenantId,
      Class<? extends ClusterConfigurationManagementRequest> requestType);

  default List<PartitionMetadata> getMemberPartitions(final MemberId memberId) {
    final var partitionDistribution = getPartitionDistribution();

    if (partitionDistribution != null) {
      return partitionDistribution.values().stream()
          .flatMap(p -> p.partitions().stream())
          .filter(partition -> partition.members().contains(memberId))
          .toList();
    }

    throw new IllegalStateException(
        "Cannot get member partitions before the topology manager is started");
  }

  CurrentClusterConfiguration getInitialClusterConfiguration();

  /**
   * Returns the current cluster configuration, including in-progress topology changes. Unlike
   * {@link #getInitialClusterConfiguration()}, this reflects updates that occurred after startup
   * (e.g., partitions in {@code JOINING} state during scale-up).
   */
  CurrentClusterConfiguration getCurrentClusterConfiguration();

  /**
   * Returns the number of partitions assigned to the given member that are currently in the {@code
   * JOINING} state.
   */
  default int getJoiningMemberPartitionCount(final MemberId memberId) {
    final var config = getCurrentClusterConfiguration();
    if (config == null || !config.globalConfiguration().hasMember(memberId)) {
      return 0;
    }
    return (int)
        config.partitionGroups().entrySet().stream()
            .filter(group -> group.getValue().members().containsKey(memberId))
            .flatMap(
                group -> group.getValue().members().get(memberId).partitions().values().stream())
            .filter(p -> p.state() == State.JOINING)
            .count();
  }

  ClusterChangeExecutor getClusterChangeExecutor();

  ActorFuture<CurrentClusterConfiguration> getLatestClusterConfiguration();
}
