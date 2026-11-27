/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.util;

import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.GlobalPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.PartitionGroupPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.Phase;
import java.util.ArrayList;
import java.util.List;

/** Builds the phases of a test change plan for a cluster with only the default physical tenant. */
public final class DefaultTenantPhases {

  private DefaultTenantPhases() {}

  /**
   * Splits a flat operation list into phases, preserving order: each maximal run of consecutive
   * {@link GlobalChangeOperation}s becomes a {@link GlobalPhase}, and each run of {@link
   * PartitionGroupOperation}s a sequential {@link PartitionGroupPhase} of the default tenant.
   */
  public static List<Phase> of(
      final List<? extends ClusterConfigurationChangeOperation> operations) {
    final List<Phase> phases = new ArrayList<>();
    final List<GlobalChangeOperation> globalRun = new ArrayList<>();
    final List<PartitionGroupOperation> partitionRun = new ArrayList<>();
    for (final ClusterConfigurationChangeOperation operation : operations) {
      switch (operation) {
        case final GlobalChangeOperation global -> {
          flushPartitionRun(phases, partitionRun);
          globalRun.add(global);
        }
        case final PartitionGroupOperation partition -> {
          flushGlobalRun(phases, globalRun);
          partitionRun.add(partition);
        }
      }
    }
    flushGlobalRun(phases, globalRun);
    flushPartitionRun(phases, partitionRun);
    return phases;
  }

  private static void flushGlobalRun(
      final List<Phase> phases, final List<GlobalChangeOperation> run) {
    if (!run.isEmpty()) {
      phases.add(new GlobalPhase(List.copyOf(run)));
      run.clear();
    }
  }

  private static void flushPartitionRun(
      final List<Phase> phases, final List<PartitionGroupOperation> run) {
    if (!run.isEmpty()) {
      phases.add(
          PartitionGroupPhase.sequential(
              CurrentClusterConfiguration.DEFAULT_GROUP, List.copyOf(run)));
      run.clear();
    }
  }
}
