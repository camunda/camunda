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
 * New-model applier for {@code PartitionGroupOperation.UpdateIncarnationNumberOperation}, operating
 * on a single named {@link PartitionGroupConfiguration} as a whole. Mirrors the legacy {@code
 * UpdateIncarnationNumberApplier} in {@code changes/}, which this does not replace or modify.
 * Increments the group's incarnation number by 1.
 */
public final class UpdateIncarnationNumberApplier
    implements PartitionGroupConfigurationChangeApplier {

  @Override
  public Either<Exception, UnaryOperator<PartitionGroupConfiguration>> init(
      final GlobalConfiguration currentGlobalConfiguration,
      final PartitionGroupConfiguration currentPartitionGroupConfiguration) {
    return Either.right(UnaryOperator.identity());
  }

  @Override
  public ActorFuture<UnaryOperator<PartitionGroupConfiguration>> apply() {
    return CompletableActorFuture.completed(
        group ->
            new PartitionGroupConfiguration(
                group.version(),
                group.incarnationNumber() + 1,
                group.members(),
                group.routingState(),
                group.pendingChanges(),
                group.lastChange()));
  }
}
