/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.changes;

import io.camunda.zeebe.dynamic.config.state.GlobalConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.util.Either;
import java.util.function.UnaryOperator;

/**
 * New-model counterpart of {@code ConfigurationChangeAppliers.ClusterOperationApplier}, scoped to a
 * single named {@link PartitionGroupConfiguration}. Applies a {@code PartitionGroupOperation}.
 *
 * <p>The applier is group-agnostic: it operates on whichever {@link PartitionGroupConfiguration} it
 * is given. Selecting the target group (by group id) and writing the result back via {@code
 * CurrentClusterConfiguration#updatePartitionGroupConfig} is the caller's responsibility.
 *
 * <p>{@link #init} also receives the current {@link GlobalConfiguration}, since broker lifecycle
 * (e.g. whether the local broker is an {@code ACTIVE} member of the cluster at all) is a
 * cluster-wide concern tracked there, not in any single partition group.
 */
public interface PartitionGroupConfigurationChangeApplier {

  /**
   * Called before {@link #apply()}. Validates the operation and returns a transform that marks the
   * start of the operation (e.g. adding the partition in {@code JOINING} state).
   *
   * @param currentGlobalConfiguration the current cluster-wide broker lifecycle state
   * @param currentPartitionGroupConfiguration the current state of the target partition group
   * @return an either containing an exception if the operation is invalid, or a transform to update
   *     the partition group configuration
   */
  Either<Exception, UnaryOperator<PartitionGroupConfiguration>> init(
      GlobalConfiguration currentGlobalConfiguration,
      PartitionGroupConfiguration currentPartitionGroupConfiguration);

  /**
   * Performs the operation, possibly asynchronously, and completes with a transform that marks the
   * operation as completed (e.g. moving the partition to {@code ACTIVE}).
   *
   * @return a future completed with the transform once the operation succeeds, or completed
   *     exceptionally on failure
   */
  ActorFuture<UnaryOperator<PartitionGroupConfiguration>> apply();
}
