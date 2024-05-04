/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
import io.camunda.zeebe.topology.api.TopologyRequestFailedException.ConcurrentModificationException;
import io.camunda.zeebe.topology.api.TopologyRequestFailedException.InvalidRequest;
import io.camunda.zeebe.topology.changes.TopologyChangeCoordinator.TopologyChangeRequest;
import io.camunda.zeebe.topology.state.ClusterChangePlan;
import io.camunda.zeebe.topology.state.ClusterTopology;
import io.camunda.zeebe.topology.state.CompletedChange;
import io.camunda.zeebe.topology.state.MemberState;
import io.camunda.zeebe.topology.state.PartitionState;
import io.camunda.zeebe.topology.state.TopologyChangeOperation;
import io.camunda.zeebe.topology.state.TopologyChangeOperation.MemberJoinOperation;
import io.camunda.zeebe.topology.state.TopologyChangeOperation.PartitionChangeOperation.PartitionJoinOperation;
import io.camunda.zeebe.topology.state.TopologyChangeOperation.PartitionChangeOperation.PartitionLeaveOperation;
import io.camunda.zeebe.util.Either;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;

final class TopologyChangeCoordinatorImplTest {
  private final TestClusterTopologyManager clusterTopologyManager =
      new TestClusterTopologyManager();
  final TopologyChangeCoordinatorImpl coordinator =
      new TopologyChangeCoordinatorImpl(
          clusterTopologyManager, MemberId.from("0"), new TestConcurrencyControl());

  private final ClusterTopology initialTopology =
      ClusterTopology.init()
          .addMember(MemberId.from("0"), MemberState.initializeAsActive(Map.of()));

  @Test
  void shouldFailOnInvalidChanges() {
    // given
    clusterTopologyManager.setClusterTopology(initialTopology);

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
        initialTopology
            .addMember(MemberId.from("1"), MemberState.initializeAsActive(Map.of()))
            .updateMember(MemberId.from("1"), m -> m.addPartition(1, PartitionState.active(1)))
            .addMember(MemberId.from("2"), MemberState.initializeAsActive(Map.of()))
            .updateMember(MemberId.from("2"), m -> m.addPartition(1, PartitionState.active(1))));

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
        initialTopology.startTopologyChange(
            List.of(new PartitionLeaveOperation(MemberId.from("1"), 1))));

    // when
    final var applyFuture =
        coordinator.applyOperations(
            getTransformer(List.of(new PartitionLeaveOperation(MemberId.from("1"), 1))));

    // then
    assertThat(applyFuture)
        .failsWithin(Duration.ofMillis(100))
        .withThrowableOfType(ExecutionException.class)
        .withCauseInstanceOf(ConcurrentModificationException.class);
  }

  @Test
  void shouldStartTopologyChanges() {
    // given
    final ClusterTopology topology =
        initialTopology
            .addMember(MemberId.from("1"), MemberState.initializeAsActive(Map.of()))
            .addMember(
                MemberId.from("2"),
                MemberState.initializeAsActive(Map.of(1, PartitionState.active(1))));
    clusterTopologyManager.setClusterTopology(topology);

    final List<TopologyChangeOperation> operations =
        List.of(new PartitionJoinOperation(MemberId.from("1"), 1, 1));

    // when
    final var applyFuture = coordinator.applyOperations(getTransformer(operations));

    // then
    assertThat(applyFuture).succeedsWithin(Duration.ofMillis(100));
    ClusterTopologyAssert.assertThatClusterTopology(
            clusterTopologyManager.getClusterTopology().join())
        .hasPendingOperationsWithSize(1);
  }

  @Test
  void shouldNotStartOnDryRun() {
    // given
    final ClusterTopology topology =
        initialTopology
            .addMember(MemberId.from("1"), MemberState.initializeAsActive(Map.of()))
            .addMember(
                MemberId.from("2"),
                MemberState.initializeAsActive(Map.of(1, PartitionState.active(1))));
    clusterTopologyManager.setClusterTopology(topology);

    final List<TopologyChangeOperation> operations =
        List.of(new PartitionJoinOperation(MemberId.from("1"), 1, 1));

    // when
    final var simulationResult = coordinator.simulateOperations(getTransformer(operations));

    // then
    assertThat(simulationResult).succeedsWithin(Duration.ofMillis(100));
    ClusterTopologyAssert.assertThatClusterTopology(
            clusterTopologyManager.getClusterTopology().join())
        .hasSameTopologyAs(topology);
  }

  @Test
  void shouldFailDryRunWithValidationError() {
    // given
    clusterTopologyManager.setClusterTopology(initialTopology);

    // when

    final var simulationResult =
        coordinator.simulateOperations(
            getTransformer(List.of(new PartitionLeaveOperation(MemberId.from("1"), 1))));

    // then
    assertThat(simulationResult)
        .failsWithin(Duration.ofMillis(100))
        .withThrowableOfType(ExecutionException.class)
        .withCauseInstanceOf(TopologyRequestFailedException.InvalidRequest.class)
        .withMessageContaining(
            "Expected to leave partition, but the local member does not exist in the topology");
  }

  @Test
  void shouldCancelOngoingChange() {
    // given
    final ClusterTopology topology =
        initialTopology
            .addMember(MemberId.from("1"), MemberState.initializeAsActive(Map.of()))
            .addMember(
                MemberId.from("2"),
                MemberState.initializeAsActive(Map.of(1, PartitionState.active(1))));
    clusterTopologyManager.setClusterTopology(topology);

    final List<TopologyChangeOperation> operations =
        List.of(new PartitionJoinOperation(MemberId.from("1"), 1, 1));

    final var applyResult = coordinator.applyOperations(getTransformer(operations)).join();

    // when
    final var cancelFuture = coordinator.cancelChange(applyResult.changeId());

    // then
    assertThat(cancelFuture).succeedsWithin(Duration.ofMillis(100));
    final ClusterTopology cancelledTopology = clusterTopologyManager.getClusterTopology().join();
    assertThat(cancelledTopology.lastChange())
        .isNotEmpty()
        .get()
        .extracting(CompletedChange::status)
        .isEqualTo(ClusterChangePlan.Status.CANCELLED);
    assertThat(cancelledTopology.pendingChanges()).isEmpty();
    assertThat(cancelledTopology.members())
        .usingRecursiveComparison()
        .ignoringFieldsOfTypes(Instant.class)
        .isEqualTo(topology.members());
  }

  @Test
  void shouldNotCancelOngoingChangeIdIsDifferent() {
    // given
    final ClusterTopology topology =
        initialTopology
            .addMember(MemberId.from("1"), MemberState.initializeAsActive(Map.of()))
            .addMember(
                MemberId.from("2"),
                MemberState.initializeAsActive(Map.of(1, PartitionState.active(1))));
    clusterTopologyManager.setClusterTopology(topology);

    final List<TopologyChangeOperation> operations =
        List.of(new PartitionJoinOperation(MemberId.from("1"), 1, 1));

    final var applyResult = coordinator.applyOperations(getTransformer(operations)).join();

    // when
    final var cancelFuture = coordinator.cancelChange(applyResult.changeId() - 1);

    // then
    assertThat(cancelFuture)
        .failsWithin(Duration.ofMillis(100))
        .withThrowableOfType(ExecutionException.class)
        .withCauseInstanceOf(InvalidRequest.class)
        .withMessageContaining("it is not the current change");
  }

  @Test
  void shouldRejectIfTheMemberIsNotTheCoordinator() {
    // given
    final ClusterTopology topologyWithoutMember0 =
        ClusterTopology.init()
            .addMember(MemberId.from("1"), MemberState.initializeAsActive(Map.of()))
            .addMember(MemberId.from("2"), MemberState.initializeAsActive(Map.of()));
    clusterTopologyManager.setClusterTopology(topologyWithoutMember0);

    final List<TopologyChangeOperation> operations =
        List.of(new MemberJoinOperation(MemberId.from("3")));

    // when
    final var applyFuture = coordinator.applyOperations(getTransformer(operations));

    // then
    assertThat(applyFuture)
        .failsWithin(Duration.ofMillis(100))
        .withThrowableOfType(ExecutionException.class)
        .withCauseInstanceOf(TopologyRequestFailedException.InternalError.class)
        .withMessageContaining("The broker '0' is not the coordinator.");
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
