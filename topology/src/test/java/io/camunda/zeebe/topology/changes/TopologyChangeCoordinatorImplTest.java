/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.topology.changes;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.testing.TestActorFuture;
import io.camunda.zeebe.scheduler.testing.TestConcurrencyControl;
import io.camunda.zeebe.topology.ClusterTopologyAssert;
import io.camunda.zeebe.topology.ClusterTopologyManager;
import io.camunda.zeebe.topology.api.TopologyRequestFailedException;
import io.camunda.zeebe.topology.api.TopologyRequestFailedException.OperationNotAllowed;
import io.camunda.zeebe.topology.changes.TopologyChangeCoordinator.TopologyChangeRequest;
import io.camunda.zeebe.topology.state.ClusterTopology;
import io.camunda.zeebe.topology.state.MemberState;
import io.camunda.zeebe.topology.state.PartitionState;
import io.camunda.zeebe.topology.state.TopologyChangeOperation;
import io.camunda.zeebe.topology.state.TopologyChangeOperation.PartitionChangeOperation.PartitionJoinOperation;
import io.camunda.zeebe.topology.state.TopologyChangeOperation.PartitionChangeOperation.PartitionLeaveOperation;
import io.camunda.zeebe.util.Either;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;

final class TopologyChangeCoordinatorImplTest {

  final TestClusterTopologyManager clusterTopologyManager = new TestClusterTopologyManager();

  @Test
  void shouldFailOnInvalidChanges() {
    // given
    clusterTopologyManager.setClusterTopology(ClusterTopology.init());
    final var coordinator =
        new TopologyChangeCoordinatorImpl(clusterTopologyManager, new TestConcurrencyControl());

    // when

    final var applyFuture =
        coordinator.applyOperations(
            getTransformer(List.of(new PartitionLeaveOperation(MemberId.from("1"), 1))));

    // then
    assertThat(applyFuture)
        .failsWithin(Duration.ofMillis(100))
        .withThrowableOfType(ExecutionException.class)
        .withCauseInstanceOf(TopologyRequestFailedException.InvalidRequest.class)
        .withMessageContaining(
            "Expected to leave partition, but the local member does not exist in the topology");
  }

  private TopologyChangeRequest getTransformer(final List<TopologyChangeOperation> operations) {
    return ignore -> Either.right(operations);
  }

  @Test
  void shouldFailOnSecondInvalidOperation() {
    // given
    clusterTopologyManager.setClusterTopology(
        ClusterTopology.init()
            .addMember(MemberId.from("1"), MemberState.initializeAsActive(Map.of()))
            .updateMember(MemberId.from("1"), m -> m.addPartition(1, PartitionState.active(1))));
    final var coordinator =
        new TopologyChangeCoordinatorImpl(clusterTopologyManager, new TestConcurrencyControl());

    // when

    final var applyFuture =
        coordinator.applyOperations(
            getTransformer(
                List.of(
                    new PartitionLeaveOperation(MemberId.from("1"), 1), // Valid operation
                    new PartitionLeaveOperation(MemberId.from("1"), 1) // Duplicate leave not valid
                    )));

    // then
    assertThat(applyFuture)
        .failsWithin(Duration.ofMillis(100))
        .withThrowableOfType(ExecutionException.class)
        .withCauseInstanceOf(TopologyRequestFailedException.InvalidRequest.class)
        .withMessageContaining(
            "Expected to leave partition, but the local member does not have the partition 1");
  }

  @Test
  void shouldFailIfAnotherTopologyChangeIsInProgress() {
    // given
    clusterTopologyManager.setClusterTopology(
        ClusterTopology.init()
            .startTopologyChange(List.of(new PartitionLeaveOperation(MemberId.from("1"), 1))));
    final var coordinator =
        new TopologyChangeCoordinatorImpl(clusterTopologyManager, new TestConcurrencyControl());

    // when
    final var applyFuture =
        coordinator.applyOperations(
            getTransformer(List.of(new PartitionLeaveOperation(MemberId.from("1"), 1))));

    // then
    assertThat(applyFuture)
        .failsWithin(Duration.ofMillis(100))
        .withThrowableOfType(ExecutionException.class)
        .withCauseInstanceOf(OperationNotAllowed.class);
  }

  @Test
  void shouldStartTopologyChanges() {
    // given
    final ClusterTopology initialTopology =
        ClusterTopology.init()
            .addMember(MemberId.from("1"), MemberState.initializeAsActive(Map.of()))
            .addMember(
                MemberId.from("2"),
                MemberState.initializeAsActive(Map.of(1, PartitionState.active(1))));
    clusterTopologyManager.setClusterTopology(initialTopology);

    final List<TopologyChangeOperation> operations =
        List.of(new PartitionJoinOperation(MemberId.from("1"), 1, 1));

    final var coordinator =
        new TopologyChangeCoordinatorImpl(clusterTopologyManager, new TestConcurrencyControl());

    // when
    final var applyFuture = coordinator.applyOperations(getTransformer(operations));

    // then
    assertThat(applyFuture).succeedsWithin(Duration.ofMillis(100));
    ClusterTopologyAssert.assertThatClusterTopology(
            clusterTopologyManager.getClusterTopology().join())
        .hasPendingOperationsWithSize(1);
  }

  private static final class TestClusterTopologyManager implements ClusterTopologyManager {

    private ClusterTopology clusterTopology;

    @Override
    public ActorFuture<ClusterTopology> getClusterTopology() {
      return TestActorFuture.completedFuture(clusterTopology);
    }

    void setClusterTopology(final ClusterTopology clusterTopology) {
      this.clusterTopology = clusterTopology;
    }

    @Override
    public ActorFuture<ClusterTopology> updateClusterTopology(
        final UnaryOperator<ClusterTopology> topologyUpdated) {
      clusterTopology = topologyUpdated.apply(clusterTopology);
      return getClusterTopology();
    }
  }
}
