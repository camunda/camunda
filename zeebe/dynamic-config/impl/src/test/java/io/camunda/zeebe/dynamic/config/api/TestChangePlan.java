/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.api;

import io.camunda.zeebe.dynamic.config.changes.ConfigurationChangeCoordinator.ConfigurationChangeRequest;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.GlobalPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.PartitionGroupPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.Phase;
import io.camunda.zeebe.util.Either;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads a request's change plan as the flat operation list the transformer tests are written
 * against, by planning it the way the coordinator does and flattening the result.
 *
 * <p>A plan is a sequence of phases, but a test that hands the request a cluster with a single
 * physical tenant gets at most one partition group per phase, so flattening loses nothing: the
 * operations come back in the order they will be applied in. This is the same projection the
 * coordinator makes for {@code ConfigurationChangeResult#legacyOperations}, which is what the
 * management API answers a request with.
 *
 * <p>Tests that assert on more than one physical tenant should read the phases instead — which
 * group an operation belongs to is exactly what this drops.
 */
final class TestChangePlan {

  private TestChangePlan() {}

  static Either<Exception, List<ClusterConfigurationChangeOperation>> plannedOperations(
      final ConfigurationChangeRequest request, final CurrentClusterConfiguration configuration) {
    return request.phases(configuration).map(TestChangePlan::flatten);
  }

  static List<ClusterConfigurationChangeOperation> flatten(final List<Phase> phases) {
    final List<ClusterConfigurationChangeOperation> operations = new ArrayList<>();
    for (final var phase : phases) {
      switch (phase) {
        case final GlobalPhase globalPhase -> operations.addAll(globalPhase.operations());
        // A graph's operations flatten in plan order; the edges between them are execution detail
        // this view does not carry.
        case final PartitionGroupPhase groupPhase ->
            groupPhase.groupGraphs().values().forEach(graph -> operations.addAll(graph.inOrder()));
      }
    }
    return operations;
  }
}
