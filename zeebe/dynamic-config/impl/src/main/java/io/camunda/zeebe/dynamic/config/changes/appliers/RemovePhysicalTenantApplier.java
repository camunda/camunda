/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.changes.appliers;

import io.camunda.zeebe.dynamic.config.changes.PartitionGroupConfigurationChangeApplier;
import io.camunda.zeebe.dynamic.config.state.GlobalConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.util.Either;
import java.util.function.UnaryOperator;

/**
 * Applier for {@code PartitionGroupOperation.RemovePhysicalTenantOperation}. Tombstones a disabled
 * physical tenant as explicitly discarded ({@link PartitionGroupConfiguration#remove()}), which is
 * what allows a broker still holding that tenant's partitions to leave the cluster (see {@code
 * MemberLeaveApplier}).
 *
 * <p>Purely a configuration edit, with no broker-side effect: the tenant runs nowhere, so there is
 * nothing to ask a broker to do and nothing to wait for. See {@code
 * ClusterConfigurationManagerImpl} for how this applier gets dispatched even though no broker ever
 * registers one for a disabled tenant's group.
 */
public final class RemovePhysicalTenantApplier implements PartitionGroupConfigurationChangeApplier {

  @Override
  public Either<Exception, UnaryOperator<PartitionGroupConfiguration>> init(
      final GlobalConfiguration currentGlobalConfiguration,
      final PartitionGroupConfiguration currentPartitionGroupConfiguration) {
    if (currentPartitionGroupConfiguration.isRemoved()) {
      // Idempotent: REMOVED is terminal, so a second removal is a no-op, not an error.
      return Either.right(UnaryOperator.identity());
    }
    if (!currentPartitionGroupConfiguration.isDisabled()) {
      // A tenant still enabled is still in static configuration, so
      // PhysicalTenantProvisioningInitializer would just re-create it; require disabling first.
      return Either.left(
          new IllegalStateException(
              "Expected to remove a physical tenant, but it is still enabled. Remove it from the "
                  + "brokers' static configuration first, so that it is disabled, and then retry"));
    }
    return Either.right(UnaryOperator.identity());
  }

  @Override
  public ActorFuture<UnaryOperator<PartitionGroupConfiguration>> apply() {
    return CompletableActorFuture.completed(PartitionGroupConfiguration::remove);
  }
}
