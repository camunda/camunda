/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.api;

import io.camunda.zeebe.dynamic.config.changes.ConfigurationChangeCoordinator;
import io.camunda.zeebe.dynamic.config.state.ClusterChangePlan;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.GlobalPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.PartitionGroupGraphPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.PartitionGroupParallelPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.Phase;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.testing.TestActorFuture;
import io.camunda.zeebe.util.Either;
import java.util.ArrayList;
import java.util.List;

final class RecordingChangeCoordinator implements ConfigurationChangeCoordinator {

  private ClusterConfiguration currentTopology = ClusterConfiguration.init();
  private final List<ClusterConfigurationChangeOperation> lastAppliedOperation = new ArrayList<>();

  public void setCurrentTopology(final ClusterConfiguration topology) {
    currentTopology = topology;
  }

  @Override
  public ActorFuture<CurrentClusterConfiguration> getClusterConfiguration() {
    return TestActorFuture.completedFuture(CurrentClusterConfiguration.fromLegacy(currentTopology));
  }

  @Override
  public ActorFuture<ConfigurationChangeResult> applyOperations(
      final ConfigurationChangeRequest request) {
    final var operationsEither = resolveOperations(request);
    if (operationsEither.isLeft()) {
      return TestActorFuture.failedFuture(operationsEither.getLeft());
    }

    final var operations = operationsEither.get();
    lastAppliedOperation.clear();
    lastAppliedOperation.addAll(operations);
    final var newTopology =
        operations.isEmpty()
            ? currentTopology
            : currentTopology.startConfigurationChange(operations);

    return TestActorFuture.completedFuture(
        new ConfigurationChangeResult(
            currentTopology,
            newTopology, // This is not correct, but enough for tests
            CurrentClusterConfiguration.fromLegacy(currentTopology),
            CurrentClusterConfiguration.fromLegacy(newTopology),
            newTopology.pendingChanges().map(ClusterChangePlan::id).orElse(0L),
            operations,
            List.of()));
  }

  @Override
  public ActorFuture<ConfigurationChangeResult> simulateOperations(
      final ConfigurationChangeRequest requestTransformer) {
    throw new UnsupportedOperationException("Simulating changes is not supported in tests");
  }

  @Override
  public ActorFuture<CurrentClusterConfiguration> cancelChange(final long changeId) {
    return TestActorFuture.failedFuture(new UnsupportedOperationException());
  }

  public List<ClusterConfigurationChangeOperation> getLastAppliedOperation() {
    return lastAppliedOperation;
  }

  /**
   * Requests that only implement {@code phases()} (the new multi-partition-group model) have no
   * meaningful {@code operations(ClusterConfiguration)}; fall back to {@code phases()} on a
   * fromLegacy projection of the test's legacy topology in that case.
   */
  private Either<Exception, List<ClusterConfigurationChangeOperation>> resolveOperations(
      final ConfigurationChangeRequest request) {
    try {
      return request.operations(currentTopology);
    } catch (final UnsupportedOperationException requestIsPhasesOnly) {
      return request
          .phases(CurrentClusterConfiguration.fromLegacy(currentTopology))
          .map(RecordingChangeCoordinator::flattenPhases);
    }
  }

  private static List<ClusterConfigurationChangeOperation> flattenPhases(final List<Phase> phases) {
    final List<ClusterConfigurationChangeOperation> operations = new ArrayList<>();
    for (final var phase : phases) {
      switch (phase) {
        case final GlobalPhase globalPhase -> operations.addAll(globalPhase.operations());
        case final PartitionGroupParallelPhase parallelPhase ->
            parallelPhase.groupOperations().values().forEach(operations::addAll);
        case final PartitionGroupGraphPhase graphPhase ->
            graphPhase.groupOperations().values().forEach(operations::addAll);
      }
    }
    return operations;
  }
}
