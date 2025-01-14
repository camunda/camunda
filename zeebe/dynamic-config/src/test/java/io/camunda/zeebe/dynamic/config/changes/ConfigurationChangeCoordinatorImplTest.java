/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.changes;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.ClusterConfigurationAssert;
import io.camunda.zeebe.dynamic.config.ClusterConfigurationManager;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestFailedException;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestFailedException.ConcurrentModificationException;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestFailedException.InvalidRequest;
import io.camunda.zeebe.dynamic.config.changes.ConfigurationChangeCoordinator.ConfigurationChangeRequest;
import io.camunda.zeebe.dynamic.config.state.ClusterChangePlan;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.MemberJoinOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionJoinOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionLeaveOperation;
import io.camunda.zeebe.dynamic.config.state.CompletedChange;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.testing.TestActorFuture;
import io.camunda.zeebe.scheduler.testing.TestConcurrencyControl;
import io.camunda.zeebe.util.Either;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;

final class ConfigurationChangeCoordinatorImplTest {
  private final TestClusterConfigurationManager clusterTopologyManager =
      new TestClusterConfigurationManager();
  final ConfigurationChangeCoordinatorImpl coordinator =
      new ConfigurationChangeCoordinatorImpl(
          clusterTopologyManager, MemberId.from("0"), new TestConcurrencyControl());

  private final ClusterConfiguration initialTopology =
      ClusterConfiguration.init()
          .addMember(MemberId.from("0"), MemberState.initializeAsActive(Map.of()));

  private final DynamicPartitionConfig partitionConfig = DynamicPartitionConfig.init();

  @Test
  void shouldFailOnInvalidChanges() {
    // given
    clusterTopologyManager.setClusterTopology(initialTopology);

    // when
    final var applyFuture =
        coordinator.applyOperations(
            getTransformer(List.of(new PartitionLeaveOperation(MemberId.from("1"), 1, 1))));

    // then
    assertThat(applyFuture)
        .failsWithin(Duration.ofMillis(100))
        .withThrowableOfType(ExecutionException.class)
        .withCauseInstanceOf(ClusterConfigurationRequestFailedException.InvalidRequest.class)
        .withMessageContaining(
            "Expected to leave partition, but the local member does not exist in the cluster");
  }

  private ConfigurationChangeRequest getTransformer(
      final List<ClusterConfigurationChangeOperation> operations) {
    return ignore -> Either.right(operations);
  }

  @Test
  void shouldFailOnSecondInvalidOperation() {
    // given

    clusterTopologyManager.setClusterTopology(
        initialTopology
            .addMember(MemberId.from("1"), MemberState.initializeAsActive(Map.of()))
            .updateMember(
                MemberId.from("1"),
                m -> m.addPartition(1, PartitionState.active(1, partitionConfig)))
            .addMember(MemberId.from("2"), MemberState.initializeAsActive(Map.of()))
            .updateMember(
                MemberId.from("2"),
                m -> m.addPartition(1, PartitionState.active(1, partitionConfig))));

    // when

    final var applyFuture =
        coordinator.applyOperations(
            getTransformer(
                List.of(
                    new PartitionLeaveOperation(MemberId.from("1"), 1, 1), // Valid operation
                    new PartitionLeaveOperation(
                        MemberId.from("1"), 1, 1) // Duplicate leave not valid
                    )));

    // then
    assertThat(applyFuture)
        .failsWithin(Duration.ofMillis(100))
        .withThrowableOfType(ExecutionException.class)
        .withCauseInstanceOf(ClusterConfigurationRequestFailedException.InvalidRequest.class)
        .withMessageContaining(
            "Expected to leave partition, but the local member does not have the partition 1");
  }

  @Test
  void shouldFailIfAnotherTopologyChangeIsInProgress() {
    // given
    clusterTopologyManager.setClusterTopology(
        initialTopology.startConfigurationChange(
            List.of(new PartitionLeaveOperation(MemberId.from("1"), 1, 1))));

    // when
    final var applyFuture =
        coordinator.applyOperations(
            getTransformer(List.of(new PartitionLeaveOperation(MemberId.from("1"), 1, 1))));

    // then
    assertThat(applyFuture)
        .failsWithin(Duration.ofMillis(100))
        .withThrowableOfType(ExecutionException.class)
        .withCauseInstanceOf(ConcurrentModificationException.class);
  }

  @Test
  void shouldStartTopologyChanges() {
    // given
    final ClusterConfiguration topology =
        initialTopology
            .addMember(MemberId.from("1"), MemberState.initializeAsActive(Map.of()))
            .addMember(
                MemberId.from("2"),
                MemberState.initializeAsActive(
                    Map.of(1, PartitionState.active(1, partitionConfig))));
    clusterTopologyManager.setClusterTopology(topology);

    final List<ClusterConfigurationChangeOperation> operations =
        List.of(new PartitionJoinOperation(MemberId.from("1"), 1, 1));

    // when
    final var applyFuture = coordinator.applyOperations(getTransformer(operations));

    // then
    assertThat(applyFuture).succeedsWithin(Duration.ofMillis(100));
    ClusterConfigurationAssert.assertThatClusterTopology(
            clusterTopologyManager.getClusterConfiguration().join())
        .hasPendingOperationsWithSize(1);
  }

  @Test
  void shouldNotStartOnDryRun() {
    // given
    final ClusterConfiguration topology =
        initialTopology
            .addMember(MemberId.from("1"), MemberState.initializeAsActive(Map.of()))
            .addMember(
                MemberId.from("2"),
                MemberState.initializeAsActive(
                    Map.of(1, PartitionState.active(1, partitionConfig))));
    clusterTopologyManager.setClusterTopology(topology);

    final List<ClusterConfigurationChangeOperation> operations =
        List.of(new PartitionJoinOperation(MemberId.from("1"), 1, 1));

    // when
    final var simulationResult = coordinator.simulateOperations(getTransformer(operations));

    // then
    assertThat(simulationResult).succeedsWithin(Duration.ofMillis(100));
    ClusterConfigurationAssert.assertThatClusterTopology(
            clusterTopologyManager.getClusterConfiguration().join())
        .hasSameTopologyAs(topology);
  }

  @Test
  void shouldFailDryRunWithValidationError() {
    // given
    clusterTopologyManager.setClusterTopology(initialTopology);

    // when

    final var simulationResult =
        coordinator.simulateOperations(
            getTransformer(List.of(new PartitionLeaveOperation(MemberId.from("1"), 1, 1))));

    // then
    assertThat(simulationResult)
        .failsWithin(Duration.ofMillis(100))
        .withThrowableOfType(ExecutionException.class)
        .withCauseInstanceOf(ClusterConfigurationRequestFailedException.InvalidRequest.class)
        .withMessageContaining(
            "Expected to leave partition, but the local member does not exist in the cluster");
  }

  @Test
  void shouldCancelOngoingChange() {
    // given
    final ClusterConfiguration topology =
        initialTopology
            .addMember(MemberId.from("1"), MemberState.initializeAsActive(Map.of()))
            .addMember(
                MemberId.from("2"),
                MemberState.initializeAsActive(
                    Map.of(1, PartitionState.active(1, partitionConfig))));
    clusterTopologyManager.setClusterTopology(topology);

    final List<ClusterConfigurationChangeOperation> operations =
        List.of(new PartitionJoinOperation(MemberId.from("1"), 1, 1));

    final var applyResult = coordinator.applyOperations(getTransformer(operations)).join();

    // when
    final var cancelFuture = coordinator.cancelChange(applyResult.changeId());

    // then
    assertThat(cancelFuture).succeedsWithin(Duration.ofMillis(100));
    final ClusterConfiguration cancelledTopology =
        clusterTopologyManager.getClusterConfiguration().join();
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
    final ClusterConfiguration topology =
        initialTopology
            .addMember(MemberId.from("1"), MemberState.initializeAsActive(Map.of()))
            .addMember(
                MemberId.from("2"),
                MemberState.initializeAsActive(
                    Map.of(1, PartitionState.active(1, partitionConfig))));
    clusterTopologyManager.setClusterTopology(topology);

    final List<ClusterConfigurationChangeOperation> operations =
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
    final ClusterConfiguration topologyWithoutMember0 =
        ClusterConfiguration.init()
            .addMember(MemberId.from("1"), MemberState.initializeAsActive(Map.of()))
            .addMember(MemberId.from("2"), MemberState.initializeAsActive(Map.of()));
    clusterTopologyManager.setClusterTopology(topologyWithoutMember0);

    final List<ClusterConfigurationChangeOperation> operations =
        List.of(new MemberJoinOperation(MemberId.from("3")));

    // when
    final var applyFuture = coordinator.applyOperations(getTransformer(operations));

    // then
    assertThat(applyFuture)
        .failsWithin(Duration.ofMillis(100))
        .withThrowableOfType(ExecutionException.class)
        .withCauseInstanceOf(ClusterConfigurationRequestFailedException.InternalError.class)
        .withMessageContaining("The broker '0' is not the coordinator.");
  }

  private static final class TestClusterConfigurationManager
      implements ClusterConfigurationManager {

    private ClusterConfiguration clusterConfiguration;

    @Override
    public ActorFuture<ClusterConfiguration> getClusterConfiguration() {
      return TestActorFuture.completedFuture(clusterConfiguration);
    }

    @Override
    public ActorFuture<ClusterConfiguration> updateClusterConfiguration(
        final UnaryOperator<ClusterConfiguration> updatedConfiguration) {
      clusterConfiguration = updatedConfiguration.apply(clusterConfiguration);
      return getClusterConfiguration();
    }

    void setClusterTopology(final ClusterConfiguration clusterConfiguration) {
      this.clusterConfiguration = clusterConfiguration;
    }
  }
}
