/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config;

import io.camunda.cluster.PartitionId;
import io.camunda.zeebe.dynamic.config.ClusterConfigurationManager.InconsistentConfigurationListener;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestValidator;
import io.camunda.zeebe.dynamic.config.changes.ModeChangeExecutor;
import io.camunda.zeebe.dynamic.config.changes.PartitionChangeExecutor;
import io.camunda.zeebe.dynamic.config.changes.PartitionScalingChangeExecutor;
import io.camunda.zeebe.dynamic.config.changes.RestoreChangeExecutor;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.ExportingState;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.scheduler.AsyncClosable;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Broker-side service that owns, gossips and applies changes to the cluster configuration.
 * Composition roots construct the implementation directly; consumers depend on this interface.
 */
public interface ClusterConfigurationManagerService
    extends ClusterConfigurationUpdateNotifier, AsyncClosable {

  /** Starts ClusterConfigurationManager which initializes ClusterConfiguration */
  ActorFuture<Void> start(
      ActorSchedulingService actorSchedulingService,
      StaticConfiguration staticConfiguration,
      Map<PartitionId, ExportingState> legacyExportingStates);

  ActorFuture<CurrentClusterConfiguration> getClusterConfiguration();

  void registerPartitionChangeExecutors(
      String groupId,
      PartitionChangeExecutor partitionChangeExecutor,
      PartitionScalingChangeExecutor partitionScalingChangeExecutor);

  void registerPartitionChangeExecutors(
      String groupId,
      PartitionChangeExecutor partitionChangeExecutor,
      PartitionScalingChangeExecutor partitionScalingChangeExecutor,
      RestoreChangeExecutor restoreChangeExecutor);

  void removePartitionChangeExecutor(String groupId);

  void registerModeChangeExecutor(String groupId, ModeChangeExecutor modeChangeExecutor);

  void removeModeChangeExecutor(String groupId);

  void registerTopologyChangedListener(InconsistentConfigurationListener listener);

  void removeTopologyChangedListener();

  void registerRequestValidator(
      @Nullable String physicalTenantId, ClusterConfigurationRequestValidator<?, ?> validator);

  void removeRequestValidator(
      @Nullable String physicalTenantId,
      Class<? extends ClusterConfigurationManagementRequest> requestType);
}
