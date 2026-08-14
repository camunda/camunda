/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.api;

import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ClusterRestoreRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestFailedException.InvalidRequest;
import io.camunda.zeebe.dynamic.config.changes.ConfigurationChangeCoordinator.ConfigurationChangeRequest;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.Phase;
import io.camunda.zeebe.dynamic.config.util.RequestValidatorRegistry;
import io.camunda.zeebe.util.Either;
import java.util.List;
import java.util.Optional;

/**
 * Restores one or every physical tenant of the cluster, as requested through the cluster-admin API.
 *
 * <p>A request naming exactly one physical tenant in {@code tenantArguments} is transformed exactly
 * like the same restore submitted through that tenant's own API. Fanning a cluster-wide request out
 * over several physical tenants at once is not implemented yet, so a request naming zero or more
 * than one tenant is rejected.
 */
public final class ClusterRestoreRequestTransformer implements ConfigurationChangeRequest {

  private final ClusterRestoreRequest request;
  private final RequestValidatorRegistry registry;

  public ClusterRestoreRequestTransformer(
      final ClusterRestoreRequest request, final RequestValidatorRegistry registry) {
    this.request = request;
    this.registry = registry;
  }

  @Override
  public Either<Exception, List<ClusterConfigurationChangeOperation>> operations(
      final ClusterConfiguration clusterConfiguration) {
    return singleTenantRestore()
        .map(restore -> restore.operations(clusterConfiguration))
        .orElseGet(() -> Either.left(clusterWideNotSupported()));
  }

  @Override
  public Either<Exception, List<Phase>> phases(
      final CurrentClusterConfiguration clusterConfiguration) {
    return singleTenantRestore()
        .map(restore -> restore.phases(clusterConfiguration))
        .orElseGet(() -> Either.left(clusterWideNotSupported()));
  }

  private Optional<RestoreRequestTransformer> singleTenantRestore() {
    final var tenantArguments = request.tenantArguments();
    if (tenantArguments.size() != 1) {
      return Optional.empty();
    }
    final var physicalTenantId = tenantArguments.keySet().iterator().next();
    return Optional.of(
        new RestoreRequestTransformer(request.toRestoreRequest(physicalTenantId), registry));
  }

  private static InvalidRequest clusterWideNotSupported() {
    return new InvalidRequest(
        "Restoring every physical tenant of the cluster in one request is not supported yet; "
            + "provide a physicalTenantId to restore a single physical tenant.");
  }
}
