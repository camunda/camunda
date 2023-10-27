/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.topology.api;

import static org.assertj.core.api.Assertions.fail;

import io.camunda.zeebe.topology.changes.NoopPartitionChangeExecutor;
import io.camunda.zeebe.topology.changes.NoopTopologyMembershipChangeExecutor;
import io.camunda.zeebe.topology.changes.TopologyChangeAppliersImpl;
import io.camunda.zeebe.topology.state.ClusterTopology;
import io.camunda.zeebe.topology.state.MemberState;
import io.camunda.zeebe.topology.state.TopologyChangeOperation;
import io.camunda.zeebe.util.Either;
import java.util.List;
import java.util.function.UnaryOperator;

final class TestTopologyChangeSimulator {

  static ClusterTopology apply(
      final ClusterTopology currentTopology, final List<TopologyChangeOperation> operations) {
    final var topologyChangeSimulator =
        new TopologyChangeAppliersImpl(
            new NoopPartitionChangeExecutor(), new NoopTopologyMembershipChangeExecutor());
    ClusterTopology newTopology = currentTopology;
    if (!operations.isEmpty()) {
      newTopology = currentTopology.startTopologyChange(operations);
    }
    while (newTopology.hasPendingChanges()) {
      final var operation = newTopology.nextPendingOperation();
      final var applier = topologyChangeSimulator.getApplier(operation);
      final Either<Exception, UnaryOperator<MemberState>> init = applier.init(newTopology);
      if (init.isLeft()) {
        fail("Failed to init operation ", init.getLeft());
      }
      newTopology = newTopology.updateMember(operation.memberId(), init.get());
      newTopology = newTopology.advanceTopologyChange(operation.memberId(), applier.apply().join());
    }
    return newTopology;
  }
}
