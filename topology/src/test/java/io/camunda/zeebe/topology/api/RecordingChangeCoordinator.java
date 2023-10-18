/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.topology.api;

import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.testing.TestActorFuture;
import io.camunda.zeebe.topology.changes.TopologyChangeCoordinator;
import io.camunda.zeebe.topology.state.ClusterTopology;
import io.camunda.zeebe.topology.state.TopologyChangeOperation;
import java.util.ArrayList;
import java.util.List;

final class RecordingChangeCoordinator implements TopologyChangeCoordinator {

  private ClusterTopology currentTopology = ClusterTopology.init();
  private final List<TopologyChangeOperation> lastAppliedOperation = new ArrayList<>();

  @Override
  public ActorFuture<ClusterTopology> applyOperations(
      final List<TopologyChangeOperation> operations) {
    lastAppliedOperation.clear();
    lastAppliedOperation.addAll(operations);

    return TestActorFuture.completedFuture(currentTopology.startTopologyChange(operations));
  }

  @Override
  public ActorFuture<Boolean> hasCompletedChanges(final long version) {
    return TestActorFuture.completedFuture(true);
  }

  @Override
  public ActorFuture<ClusterTopology> getCurrentTopology() {
    return TestActorFuture.completedFuture(currentTopology);
  }

  public void setCurrentTopology(final ClusterTopology topology) {
    currentTopology = topology;
  }

  public List<TopologyChangeOperation> getLastAppliedOperation() {
    return lastAppliedOperation;
  }
}
