/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.topology.api;

import static org.assertj.core.api.Assertions.fail;

import io.camunda.zeebe.topology.changes.NoopPartitionChangeExecutor;
import io.camunda.zeebe.topology.changes.NoopClusterMembershipChangeExecutor;
import io.camunda.zeebe.topology.changes.ConfigurationChangeAppliersImpl;
import io.camunda.zeebe.topology.state.ClusterConfiguration;
import io.camunda.zeebe.topology.state.ClusterConfigurationChangeOperation;
import java.util.List;

final class TestTopologyChangeSimulator {

  static ClusterConfiguration apply(
      final ClusterConfiguration currentTopology, final List<ClusterConfigurationChangeOperation> operations) {
    final var topologyChangeSimulator =
        new ConfigurationChangeAppliersImpl(
            new NoopPartitionChangeExecutor(), new NoopClusterMembershipChangeExecutor());
    ClusterConfiguration newTopology = currentTopology;
    if (!operations.isEmpty()) {
      newTopology = currentTopology.startTopologyChange(operations);
    }
    while (newTopology.hasPendingChanges()) {
      final var operation = newTopology.nextPendingOperation();
      final var applier = topologyChangeSimulator.getApplier(operation);
      final var init = applier.init(newTopology);
      if (init.isLeft()) {
        fail("Failed to init operation ", init.getLeft());
      }
      newTopology = init.get().apply(newTopology);
      newTopology = newTopology.advanceTopologyChange(applier.apply().join());
    }
    return newTopology;
  }
}
