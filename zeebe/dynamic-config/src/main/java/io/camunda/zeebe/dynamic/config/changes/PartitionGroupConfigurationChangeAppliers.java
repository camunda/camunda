/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.changes;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.util.Either;
import java.util.function.UnaryOperator;

/**
 * Provides appliers for configuration change operations targeting a non-default partition group.
 *
 * <p>Mirrors {@link ConfigurationChangeAppliers} but works with {@link PartitionGroupConfiguration}
 * instead of {@link io.camunda.zeebe.dynamic.config.state.ClusterConfiguration}.
 */
@FunctionalInterface
public interface PartitionGroupConfigurationChangeAppliers {

  PartitionGroupOperationApplier getApplier(ClusterConfigurationChangeOperation operation);

  interface PartitionGroupOperationApplier {
    MemberId memberId();

    /**
     * Initialises local state before the operation is applied and returns a transformer that
     * updates the group configuration to reflect the initialised state.
     */
    Either<Exception, UnaryOperator<PartitionGroupConfiguration>> init(
        PartitionGroupConfiguration config);

    /**
     * Applies the operation asynchronously and returns a transformer that updates the group
     * configuration to reflect the completed operation.
     */
    ActorFuture<UnaryOperator<PartitionGroupConfiguration>> apply();
  }
}
