/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.api;

import static org.assertj.core.api.Assertions.fail;

import io.camunda.zeebe.dynamic.config.changes.ClusterChangeExecutor.NoopClusterChangeExecutor;
import io.camunda.zeebe.dynamic.config.changes.GlobalConfigurationChangeAppliers;
import io.camunda.zeebe.dynamic.config.changes.GlobalConfigurationChangeAppliersImpl;
import io.camunda.zeebe.dynamic.config.changes.ModeChangeExecutor.NoopModeChangeExecutor;
import io.camunda.zeebe.dynamic.config.changes.NoopClusterMembershipChangeExecutor;
import io.camunda.zeebe.dynamic.config.changes.NoopPartitionChangeExecutor;
import io.camunda.zeebe.dynamic.config.changes.PartitionGroupConfigurationChangeAppliers;
import io.camunda.zeebe.dynamic.config.changes.PartitionGroupConfigurationChangeAppliersImpl;
import io.camunda.zeebe.dynamic.config.changes.PartitionScalingChangeExecutor.NoopPartitionScalingChangeExecutor;
import io.camunda.zeebe.dynamic.config.changes.RestoreChangeExecutor;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation;
import io.camunda.zeebe.dynamic.config.state.OperationId;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.GlobalPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.PartitionGroupPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.Phase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlanStatus;
import java.util.List;
import java.util.TreeMap;

/**
 * Drains a change plan against the same appliers the broker uses, so a transformer test can assert
 * on the configuration its request actually produces rather than on the operation list alone.
 */
final class TestTopologyChangeSimulator {

  static CurrentClusterConfiguration apply(
      final CurrentClusterConfiguration currentTopology, final List<Phase> phases) {
    if (phases.isEmpty()) {
      return currentTopology;
    }

    final var globalAppliers =
        new GlobalConfigurationChangeAppliersImpl(
            new NoopClusterMembershipChangeExecutor(), new NoopClusterChangeExecutor());
    final var groupAppliers =
        new PartitionGroupConfigurationChangeAppliersImpl(
            new NoopPartitionChangeExecutor(),
            new NoopPartitionScalingChangeExecutor(),
            new NoopModeChangeExecutor(),
            new RestoreChangeExecutor.NoopRestoreChangeExecutor());

    final var started = currentTopology.initPlan(phases);
    final var planId = started.phasedChangeState().pending().keySet().iterator().next();

    return drainPlan(started, planId, globalAppliers, groupAppliers);
  }

  private static CurrentClusterConfiguration drainPlan(
      final CurrentClusterConfiguration configuration,
      final long planId,
      final GlobalConfigurationChangeAppliers globalAppliers,
      final PartitionGroupConfigurationChangeAppliers groupAppliers) {
    var current = configuration;
    while (true) {
      final var plan = current.phasedChangeState().pending().get(planId);
      if (plan == null) {
        return current;
      }
      current =
          switch (plan.currentPhase()) {
            case final GlobalPhase ignored -> drainGlobalPhase(current, globalAppliers);
            case final PartitionGroupPhase groupPhase ->
                drainPartitionGroupPhase(current, groupPhase, groupAppliers);
          };
      current =
          current.phasedChangeState().pending().get(planId).hasNextPhase()
              ? current.activateNextPhase(planId)
              : current.completePlan(planId, PhasedChangePlanStatus.COMPLETED);
    }
  }

  /**
   * Drains the global phase by running whatever the cluster-wide graph declares runnable, in
   * operation-id order. That graph is sequential, so this takes one operation per round — but it is
   * read from the graph rather than assumed, so the helper keeps working if a cluster-wide change
   * ever declares two independent operations.
   */
  private static CurrentClusterConfiguration drainGlobalPhase(
      final CurrentClusterConfiguration configuration,
      final GlobalConfigurationChangeAppliers appliers) {
    var current = configuration;
    while (current.globalConfiguration().hasPendingChanges()) {
      final var plan = current.globalConfiguration().pendingChanges().orElseThrow();
      final var next =
          plan.operations().keySet().stream()
              .filter(plan::isRunnable)
              .findFirst()
              .orElseGet(
                  () ->
                      fail(
                          "Cluster-wide change cannot make progress; outstanding operations are"
                              + " blocked on '%s'",
                          plan.blockedBy()));
      final var operation = (GlobalChangeOperation) plan.operation(next);
      final var applier = appliers.getApplier(operation);
      final var init = applier.init(current);
      if (init.isLeft()) {
        fail("Failed to init operation '%s' : '%s'", operation, init.getLeft());
      }
      final var transformer = applier.apply().join();
      current =
          current
              .updateGlobalConfiguration(init.get())
              .updateGlobalConfiguration(
                  global ->
                      global.completeOperation(next, transformer).completeGraphChangeIfDrained());
    }
    return current;
  }

  /**
   * Drains a partition-group phase by repeatedly running whatever each group's graph currently
   * declares runnable, in operation-id order. A graph can offer several operations at once and
   * offers none while an unfinished dependency blocks them — so a round that finds nothing runnable
   * while operations remain is a stalled graph, not a finished one, and is failed loudly rather
   * than looping forever.
   */
  private static CurrentClusterConfiguration drainPartitionGroupPhase(
      final CurrentClusterConfiguration configuration,
      final PartitionGroupPhase phase,
      final PartitionGroupConfigurationChangeAppliers appliers) {
    var current = configuration;
    for (final var groupId : phase.groupGraphs().keySet()) {
      while (current.partitionGroup(groupId).hasPendingChanges()) {
        final var group = current.partitionGroup(groupId);
        final var plan = group.pendingChanges().orElseThrow();
        final var runnable = new TreeMap<OperationId, PartitionGroupOperation>();
        plan.graph()
            .operations()
            .forEach(
                (operationId, planned) -> {
                  if (plan.isRunnable(operationId)) {
                    runnable.put(operationId, (PartitionGroupOperation) planned.operation());
                  }
                });
        if (runnable.isEmpty()) {
          fail(
              "Graph change of group '%s' has %d operation(s) left but none is runnable; blocked by %s",
              groupId, plan.pendingOperations().size(), plan.blockedBy());
        }
        for (final var entry : runnable.entrySet()) {
          final var operation = entry.getValue();
          final var applier = appliers.getApplier(operation);
          final var staged = current.partitionGroup(groupId);
          final var init = applier.init(current.globalConfiguration(), staged);
          if (init.isLeft()) {
            fail("Failed to init operation '%s' : '%s'", operation, init.getLeft());
          }
          final var transformer = applier.apply().join();
          current =
              current
                  .updatePartitionGroupConfig(groupId, init.get())
                  .updatePartitionGroupConfig(
                      groupId,
                      updated ->
                          updated
                              .completeOperation(entry.getKey(), transformer)
                              .completeGraphChangeIfDrained());
        }
      }
    }
    return current;
  }
}
