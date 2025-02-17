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
import io.camunda.zeebe.dynamic.config.changes.ConfigurationChangeAppliersImpl;
import io.camunda.zeebe.dynamic.config.changes.NoopClusterMembershipChangeExecutor;
import io.camunda.zeebe.dynamic.config.changes.NoopPartitionChangeExecutor;
import io.camunda.zeebe.dynamic.config.changes.PartitionScalingChangeExecutor.NoopPartitionScalingChangeExecutor;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation;
import java.util.List;

final class TestTopologyChangeSimulator {

  static ClusterConfiguration apply(
      final ClusterConfiguration currentTopology,
      final List<ClusterConfigurationChangeOperation> operations) {
    final var topologyChangeSimulator =
        new ConfigurationChangeAppliersImpl(
            new NoopPartitionChangeExecutor(),
            new NoopClusterMembershipChangeExecutor(),
            new NoopPartitionScalingChangeExecutor(),
            new NoopClusterChangeExecutor());
    ClusterConfiguration newTopology = currentTopology;
    if (!operations.isEmpty()) {
      newTopology = currentTopology.startConfigurationChange(operations);
    }
    while (newTopology.hasPendingChanges()) {
      final var operation = newTopology.nextPendingOperation();
      final var applier = topologyChangeSimulator.getApplier(operation);
      final var init = applier.init(newTopology);
      if (init.isLeft()) {
        fail("Failed to init operation '%s' : '%s'", operation, init.getLeft());
      }
      newTopology = init.get().apply(newTopology);
      newTopology = newTopology.advanceConfigurationChange(applier.apply().join());
    }
    return newTopology;
  }
}
