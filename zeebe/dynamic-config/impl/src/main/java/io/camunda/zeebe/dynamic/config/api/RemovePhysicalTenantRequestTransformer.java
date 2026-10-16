/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.api;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestFailedException.InvalidRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestFailedException.NotFound;
import io.camunda.zeebe.dynamic.config.changes.ConfigurationChangeCoordinator.ConfigurationChangeRequest;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.RemovePhysicalTenantOperation;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.PartitionGroupPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.Phase;
import io.camunda.zeebe.util.Either;
import java.util.List;
import java.util.Map;

/**
 * Discards a disabled physical tenant, so that a broker still holding its partitions may leave the
 * cluster. Does not delete the tenant's data — it stays on the brokers' disks, unreachable through
 * this configuration, until reclaimed out of band.
 *
 * <p>Normally the request runs on the elected coordinator like any other configuration change,
 * exactly as {@link #isForced()} returning {@code false} implies. With {@code force}, it instead
 * executes on whichever broker received it, naming that broker as {@code coordinator} regardless of
 * whether it holds the election — the operator's bail-out for a disaster where the elected
 * coordinator is unreachable.
 *
 * <p>{@link #applyToDisabledTenants()} returns {@code true}, since a disabled tenant is exactly
 * what this request targets and the configuration it is evaluated against would otherwise have
 * already filtered it out.
 */
public class RemovePhysicalTenantRequestTransformer implements ConfigurationChangeRequest {

  private final String physicalTenantId;
  private final MemberId coordinator;
  private final boolean force;

  public RemovePhysicalTenantRequestTransformer(
      final String physicalTenantId, final MemberId coordinator, final boolean force) {
    this.physicalTenantId = physicalTenantId;
    this.coordinator = coordinator;
    this.force = force;
  }

  @Override
  public Either<Exception, List<Phase>> phases(
      final CurrentClusterConfiguration clusterConfiguration) {
    if (!clusterConfiguration.hasPartitionGroup(physicalTenantId)) {
      return Either.left(
          new NotFound(
              "Expected to remove physical tenant '%s', but there's no such tenant"
                  .formatted(physicalTenantId)));
    }
    if (clusterConfiguration.partitionGroup(physicalTenantId).isRemoved()) {
      return Either.left(
          new InvalidRequest(
              "Expected to remove physical tenant '%s', but it has already been removed"
                  .formatted(physicalTenantId)));
    }
    if (!clusterConfiguration.partitionGroup(physicalTenantId).isDisabled()) {
      return Either.left(
          new InvalidRequest(
              "Expected to remove physical tenant '%s', but it is still enabled. Remove it from the brokers' static configuration first, so that it is disabled, and then retry"
                  .formatted(physicalTenantId)));
    }
    return Either.right(
        List.of(
            PartitionGroupPhase.sequential(
                Map.of(
                    physicalTenantId, List.of(new RemovePhysicalTenantOperation(coordinator))))));
  }

  @Override
  public boolean applyToDisabledTenants() {
    return true;
  }

  @Override
  public boolean isForced() {
    return force;
  }
}
