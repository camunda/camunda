/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationChangeResponse;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ClusterRestoreRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ModeChangeRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.RestoreParameters;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.TenantRestoreArguments;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequestSender;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestFailedException.NotFound;
import io.camunda.zeebe.dynamic.config.api.ErrorResponse;
import io.camunda.zeebe.dynamic.config.api.ErrorResponse.ErrorCode;
import io.camunda.zeebe.dynamic.config.state.Mode;
import io.camunda.zeebe.util.Either;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Recovery operations that span the whole cluster, backing the cluster-admin API under {@code
 * /cluster/v2}.
 */
@NullMarked
public final class ClusterRecoveryServices {

  private final ClusterConfigurationManagementRequestSender clusterConfigurationRequestSender;
  private final Map<String, TenantRestoreEnvironment> restoreEnvironmentByPhysicalTenant;

  public ClusterRecoveryServices(
      final ClusterConfigurationManagementRequestSender clusterConfigurationRequestSender,
      final Map<String, TenantRestoreEnvironment> restoreEnvironmentByPhysicalTenant) {
    this.clusterConfigurationRequestSender = clusterConfigurationRequestSender;
    this.restoreEnvironmentByPhysicalTenant = restoreEnvironmentByPhysicalTenant;
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

  /**
   * Restores physical tenants from backups. The request targets a single physical tenant, or every
   * physical tenant of the cluster when it names none.
   *
   * @param physicalTenantId the physical tenant to restore, or empty to restore every physical
   *     tenant of the cluster
   * @param parameters the backup selection applied to every targeted physical tenant that has no
   *     entry in {@code overrides}
   * @param overrides the backup selection of individual physical tenants, keyed by physical tenant
   *     id; only meaningful for a cluster-wide restore
   */
  public CompletableFuture<Either<ErrorResponse, ClusterConfigurationChangeResponse>> restore(
      final Optional<String> physicalTenantId,
      final RestoreParameters parameters,
      final Map<String, RestoreParameters> overrides,
      final boolean dryRun) {
    final ClusterRestoreRequest restoreRequest;
    try {
      restoreRequest =
          physicalTenantId
              .map(tenantId -> tenantRestoreRequest(tenantId, parameters, dryRun))
              .orElseGet(() -> clusterWideRestoreRequest(parameters, overrides, dryRun));
    } catch (final NotFound e) {
      return CompletableFuture.completedFuture(
          Either.left(new ErrorResponse(ErrorCode.NOT_FOUND, e.getMessage())));
    }
    return clusterConfigurationRequestSender.clusterRestore(restoreRequest);
  }

  private ClusterRestoreRequest tenantRestoreRequest(
      final String physicalTenantId, final RestoreParameters parameters, final boolean dryRun) {
    final var args = toArguments(physicalTenantId, parameters);
    return new ClusterRestoreRequest(Map.of(physicalTenantId, args), dryRun);
  }

  private ClusterRestoreRequest clusterWideRestoreRequest(
      final RestoreParameters parameters,
      final Map<String, RestoreParameters> overrides,
      final boolean dryRun) {
    final var restoreArgumentsMap =
        restoreEnvironmentByPhysicalTenant.keySet().stream()
            .collect(
                Collectors.toMap(
                    tenantId -> tenantId,
                    tenantId ->
                        toArguments(tenantId, overrides.getOrDefault(tenantId, parameters))));
    return new ClusterRestoreRequest(restoreArgumentsMap, dryRun);
  }

  private TenantRestoreArguments toArguments(
      final String physicalTenantId, final RestoreParameters parameters) {
    final var environment = restoreEnvironmentFor(physicalTenantId);
    return new TenantRestoreArguments(
        parameters, environment.databaseType(), environment.continuousBackups());
  }

  private TenantRestoreEnvironment restoreEnvironmentFor(final String physicalTenantId) {
    final var environment = restoreEnvironmentByPhysicalTenant.get(physicalTenantId);
    if (environment == null) {
      throw new NotFound(
          "No physical tenant '%s' is configured in this cluster.".formatted(physicalTenantId));
    }
    return environment;
  }
}
