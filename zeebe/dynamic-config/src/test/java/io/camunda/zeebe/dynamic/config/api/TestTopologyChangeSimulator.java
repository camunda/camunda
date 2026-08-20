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
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.GlobalPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.PartitionGroupParallelPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlanStatus;
import java.util.List;

/**
 * Drains a change plan against the same appliers the broker uses, so a transformer test can assert
 * on the configuration its request actually produces rather than on the operation list alone.
 *
 * <p>Takes and returns the legacy single-group {@link ClusterConfiguration} because that is what
 * the transformer tests build their fixtures and expectations in. On a single-tenant cluster the
 * conversion is lossless in both directions, so the plan is simulated on the real model in between.
 */
final class TestTopologyChangeSimulator {

  static ClusterConfiguration apply(
      final ClusterConfiguration currentTopology,
      final List<ClusterConfigurationChangeOperation> operations) {
    if (operations.isEmpty()) {
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

    final var started =
        CurrentClusterConfiguration.fromLegacy(currentTopology)
            .initPlan(CurrentClusterConfiguration.toPhases(operations));
    final var planId = started.phasedChangeState().pending().keySet().iterator().next();

    return drainPlan(started, planId, globalAppliers, groupAppliers).toLegacyDefault();
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
            case final PartitionGroupParallelPhase parallelPhase ->
                drainPartitionGroupPhase(current, parallelPhase, groupAppliers);
          };
      current =
          current.phasedChangeState().pending().get(planId).hasNextPhase()
              ? current.activateNextPhase(planId)
              : current.completePlan(planId, PhasedChangePlanStatus.COMPLETED);
    }
  }

  private static CurrentClusterConfiguration drainGlobalPhase(
      final CurrentClusterConfiguration configuration,
      final GlobalConfigurationChangeAppliers appliers) {
    var current = configuration;
    while (current.globalConfiguration().hasPendingChanges()) {
      final var operation =
          (GlobalChangeOperation) current.globalConfiguration().nextPendingOperation();
      final var applier = appliers.getApplier(operation);
      final var init = applier.init(current);
      if (init.isLeft()) {
        fail("Failed to init operation '%s' : '%s'", operation, init.getLeft());
      }
      final var transformer = applier.apply().join();
      current =
          current
              .updateGlobalConfiguration(init.get())
              .updateGlobalConfiguration(global -> global.advanceConfigurationChange(transformer));
    }
    return current;
  }

  private static CurrentClusterConfiguration drainPartitionGroupPhase(
      final CurrentClusterConfiguration configuration,
      final PartitionGroupParallelPhase phase,
      final PartitionGroupConfigurationChangeAppliers appliers) {
    var current = configuration;
    for (final var groupId : phase.groupOperations().keySet()) {
      while (current.partitionGroup(groupId).hasPendingChanges()) {
        final var group = current.partitionGroup(groupId);
        final var operation = group.nextPendingOperation();
        final var applier = appliers.getApplier(operation);
        final var init = applier.init(current.globalConfiguration(), group);
        if (init.isLeft()) {
          fail("Failed to init operation '%s' : '%s'", operation, init.getLeft());
        }
        final var transformer = applier.apply().join();
        current =
            current
                .updatePartitionGroupConfig(groupId, init.get())
                .updatePartitionGroupConfig(
                    groupId, updated -> updated.advanceConfigurationChange(transformer));
      }
    }
    return current;
  }
}
