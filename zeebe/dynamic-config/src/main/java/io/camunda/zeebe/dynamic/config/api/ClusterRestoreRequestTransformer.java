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
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestFailedException.NotFound;
import io.camunda.zeebe.dynamic.config.changes.ConfigurationChangeCoordinator.ConfigurationChangeRequest;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.PartitionGroupPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.Phase;
import io.camunda.zeebe.dynamic.config.util.RequestValidatorRegistry;
import io.camunda.zeebe.util.Either;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Restores one or every physical tenant of the cluster, as requested through the cluster-admin API.
 *
 * <p>{@link #phases(CurrentClusterConfiguration)} is the entry point the new-model coordinator
 * actually drives: each named physical tenant is restored from its own {@link
 * PartitionGroupConfiguration}, exactly like the same restore submitted through that tenant's own
 * API, and the resulting plans are combined into one {@link PartitionGroupPhase} so every named
 * tenant restores in parallel. A cluster-wide request — one naming every physical tenant of the
 * cluster — restores them all this way in a single change.
 *
 * <p>A request naming no physical tenant at all plans nothing, rather than being rejected: there is
 * no tenant whose partitions it could restore.
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
  public Either<Exception, List<Phase>> phases(
      final CurrentClusterConfiguration clusterConfiguration) {
    final Map<String, List<PartitionGroupOperation>> operationsPerTenant = new LinkedHashMap<>();
    for (final var physicalTenantId : request.tenantArguments().keySet()) {
      final var partitionGroup = clusterConfiguration.partitionGroup(physicalTenantId);
      if (partitionGroup == null) {
        return Either.left(
            new NotFound(
                "Expected to restore physical tenant '%s', but there's no such tenant"
                    .formatted(physicalTenantId)));
      }
      final var transformer =
          new RestoreRequestTransformer(request.toRestoreRequest(physicalTenantId), registry);
      final var result = transformer.groupOperations(partitionGroup);
      if (result.isLeft()) {
        return Either.left(result.getLeft());
      }
      if (!result.get().isEmpty()) {
        operationsPerTenant.put(physicalTenantId, result.get());
      }
    }
    if (operationsPerTenant.isEmpty()) {
      return Either.right(List.of());
    }
    return Either.right(List.of(PartitionGroupPhase.sequential(operationsPerTenant)));
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
