/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.impl.changes.appliers;

import io.camunda.zeebe.dynamic.config.changes.PhysicalTenantConfigChangeExecutor;
import io.camunda.zeebe.dynamic.config.impl.changes.PartitionGroupConfigurationChangeApplier;
import io.camunda.zeebe.dynamic.config.state.GlobalConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.util.Either;
import java.util.function.UnaryOperator;
import org.jspecify.annotations.NullMarked;

/**
 * Applier for {@code PartitionGroupOperation.StopPhysicalTenantOperation}: stops the module of a
 * tenant that is being removed on this member. The removal itself is recorded by the {@code
 * RemovePhysicalTenantOperation} that follows once every member stopped it.
 */
@NullMarked
public final class StopPhysicalTenantApplier implements PartitionGroupConfigurationChangeApplier {

  private final String physicalTenantId;
  private final PhysicalTenantConfigChangeExecutor executor;

  public StopPhysicalTenantApplier(
      final String physicalTenantId, final PhysicalTenantConfigChangeExecutor executor) {
    this.physicalTenantId = physicalTenantId;
    this.executor = executor;
  }

  @Override
  public Either<Exception, UnaryOperator<PartitionGroupConfiguration>> init(
      final GlobalConfiguration currentGlobalConfiguration,
      final PartitionGroupConfiguration currentPartitionGroupConfiguration) {
    return Either.right(UnaryOperator.identity());
  }

  @Override
  public ActorFuture<UnaryOperator<PartitionGroupConfiguration>> apply() {
    final var result = new CompletableActorFuture<UnaryOperator<PartitionGroupConfiguration>>();
    executor
        .stop(physicalTenantId)
        .whenComplete(
            (ignored, error) -> {
              if (error != null) {
                result.completeExceptionally(error);
              } else {
                result.complete(UnaryOperator.identity());
              }
            });
    return result;
  }
}
