/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.changes.appliers;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.changes.PartitionGroupConfigurationChangeApplier;
import io.camunda.zeebe.dynamic.config.changes.RestoreChangeExecutor;
import io.camunda.zeebe.dynamic.config.state.GlobalConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.util.Either;
import java.util.function.UnaryOperator;

/**
 * Runs the broker-wide secondary-storage schema initialization barrier for an in-process restore.
 */
public final class SchemaInitializationApplier implements PartitionGroupConfigurationChangeApplier {

  private final RestoreChangeExecutor restoreChangeExecutor;
  private final MemberId memberId;

  public SchemaInitializationApplier(
      final MemberId memberId, final RestoreChangeExecutor restoreChangeExecutor) {
    this.memberId = memberId;
    this.restoreChangeExecutor = restoreChangeExecutor;
  }

  @Override
  public Either<Exception, UnaryOperator<PartitionGroupConfiguration>> init(
      final GlobalConfiguration globalConfiguration,
      final PartitionGroupConfiguration currentPartitionGroupConfiguration) {
    return Either.right(UnaryOperator.identity());
  }

  @Override
  public ActorFuture<UnaryOperator<PartitionGroupConfiguration>> apply() {
    return restoreChangeExecutor.initializeSchema().thenApply(ignored -> UnaryOperator.identity());
  }
}
