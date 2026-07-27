/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.changes;

import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.GlobalConfiguration;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.util.Either;
import java.util.function.UnaryOperator;

/**
 * New-model counterpart of {@code ConfigurationChangeAppliers.ClusterOperationApplier}, scoped to
 * {@link GlobalConfiguration}. Applies a {@code GlobalChangeOperation}.
 */
public interface GlobalConfigurationChangeApplier {

  /**
   * Called before {@link #apply()}. Validates the operation and returns a transform that marks the
   * start of the operation (e.g. adding the member in {@code JOINING} state).
   *
   * @param currentClusterConfiguration the current state of the whole cluster: cluster-wide broker
   *     lifecycle plus every partition group. Some global operations (e.g. leaving the cluster)
   *     must be validated against partition assignment across every group, which {@link
   *     GlobalConfiguration} alone does not carry.
   * @return an either containing an exception if the operation is invalid, or a transform to update
   *     the global configuration
   */
  Either<Exception, UnaryOperator<GlobalConfiguration>> init(
      CurrentClusterConfiguration currentClusterConfiguration);

  /**
   * Performs the operation, possibly asynchronously, and completes with a transform that marks the
   * operation as completed (e.g. moving the member to {@code ACTIVE}).
   *
   * @return a future completed with the transform once the operation succeeds, or completed
   *     exceptionally on failure
   */
  ActorFuture<UnaryOperator<GlobalConfiguration>> apply();
}
