/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationChangeResponse;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ModeChangeRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequestSender;
import io.camunda.zeebe.dynamic.config.api.ErrorResponse;
import io.camunda.zeebe.dynamic.config.state.Mode;
import io.camunda.zeebe.util.Either;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Recovery operations that span the whole cluster, backing the cluster-admin API under {@code
 * /cluster/v2}.
 */
@NullMarked
public final class ClusterRecoveryServices {

  private final ClusterConfigurationManagementRequestSender clusterConfigurationRequestSender;

  public ClusterRecoveryServices(
      final ClusterConfigurationManagementRequestSender clusterConfigurationRequestSender) {
    this.clusterConfigurationRequestSender = clusterConfigurationRequestSender;
  }

  /**
   * Transitions partitions between processing and recovery mode.
   *
   * @param physicalTenantId the physical tenant to transition, or {@code null} for every physical
   *     tenant
   */
  public CompletableFuture<Either<ErrorResponse, ClusterConfigurationChangeResponse>> changeMode(
      final @Nullable String physicalTenantId, final Mode mode, final boolean dryRun) {
    return clusterConfigurationRequestSender.modeChange(
        new ModeChangeRequest(Optional.ofNullable(physicalTenantId), mode, dryRun));
  }
}
