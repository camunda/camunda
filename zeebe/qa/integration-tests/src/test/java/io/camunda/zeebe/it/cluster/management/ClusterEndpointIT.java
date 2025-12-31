/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.management;

import static org.assertj.core.api.Assertions.*;

import feign.FeignException;
import io.atomix.cluster.MemberId;
import io.camunda.zeebe.management.cluster.ClusterConfigPatchRequest;
import io.camunda.zeebe.management.cluster.ClusterConfigPatchRequestBrokers;
import io.camunda.zeebe.management.cluster.ClusterConfigPatchRequestPartitions;
import io.camunda.zeebe.management.cluster.Operation;
import io.camunda.zeebe.management.cluster.Operation.OperationEnum;
import io.camunda.zeebe.qa.util.actuator.ClusterActuator;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.topology.ClusterActuatorAssert;
import java.util.List;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(2 * 60) // 2 minutes
final class ClusterEndpointIT {
  private static final int BROKER_COUNT = 2;
  private static final int PARTITION_COUNT = 2;

  @Test
  void shouldQueryCurrentClusterTopology() {
    final ClusterActuator actuator;
    try (final var cluster = createCluster(2)) {
      // given
      cluster.awaitCompleteTopology();
      actuator = ClusterActuator.of(cluster.availableGateway());

      // when
      final var response = actuator.getTopology();

      // then - topology is as expected
      assertThat(response.getBrokers())
          .hasSize(BROKER_COUNT)
          .allSatisfy(
              b ->
                  assertThat(b.getPartitions())
                      .describedAs("All brokers have two partitions each")
                      .hasSize(PARTITION_COUNT));
      assertThat(response.getLastChange()).isNull();
      assertThat(response.getPendingChange()).isNull();
    }
  }

  @Test
  void shouldRequestPartitionLeave() {
    final ClusterActuator actuator;
    try (final var cluster = createCluster(2)) {
      // given
      cluster.awaitCompleteTopology();

      actuator = ClusterActuator.of(cluster.availableGateway());
      // when -- request a leave
      final var response = actuator.leavePartition(1, 2);
      // then
      assertThat(response.getPlannedChanges())
          .singleElement()
          .asInstanceOf(InstanceOfAssertFactories.type(Operation.class))
          .returns(OperationEnum.PARTITION_LEAVE, Operation::getOperation)
          .returns(1, Operation::getBrokerId)
          .returns(2, Operation::getPartitionId);
    }
  }

  @Test
  void shouldRequestClusterPurge() {
    final ClusterActuator actuator;
    try (final var cluster = createCluster(2)) {
      // given
      cluster.awaitCompleteTopology();
      actuator = ClusterActuator.of(cluster.availableGateway());

      // when -- request a purge
      final var response = actuator.purge(false);

      // then
      assertThat(response.getPlannedChanges().stream().map(Operation::getOperation))
          .containsExactlyElementsOf(
              List.of(
                  OperationEnum.PARTITION_LEAVE,
                  OperationEnum.PARTITION_LEAVE,
                  OperationEnum.PARTITION_LEAVE,
                  OperationEnum.PARTITION_LEAVE,
                  OperationEnum.DELETE_HISTORY,
                  OperationEnum.UPDATE_INCARNATION_NUMBER,
                  OperationEnum.PARTITION_BOOTSTRAP,
                  OperationEnum.PARTITION_BOOTSTRAP,
                  OperationEnum.PARTITION_JOIN,
                  OperationEnum.PARTITION_JOIN));
    }
  }

  @Test
  void shouldRequestPartitionJoin() {
    try (final var cluster = createCluster(1)) {
      // given
      cluster.awaitCompleteTopology();
      final var actuator = ClusterActuator.of(cluster.availableGateway());
      // when -- request a join
      final var response = actuator.joinPartition(0, 2, 3);
      // then
      assertThat(response.getPlannedChanges())
          .singleElement()
          .asInstanceOf(InstanceOfAssertFactories.type(Operation.class))
          .returns(OperationEnum.PARTITION_JOIN, Operation::getOperation)
          .returns(0, Operation::getBrokerId)
          .returns(2, Operation::getPartitionId)
          .returns(3, Operation::getPriority);
    }
  }

  @Test
  void shouldRejectJoinOnNonExistingPartition() {
    try (final var cluster = createCluster(1)) {
      // given
      cluster.awaitCompleteTopology();
      final var actuator = ClusterActuator.of(cluster.availableGateway());
      // when -- request a join
      assertThatCode(() -> actuator.joinPartition(0, 3, 3))
          .describedAs("Joining a non-existing partition should fail with 400 Bad Request")
          .isInstanceOf(FeignException.BadRequest.class)
          .hasMessageContaining("partition has no active members");
    }
  }

  @Test
  void shouldRequestScaleBrokers() {
    try (final var cluster = createCluster(1)) {
      // given
      cluster.awaitCompleteTopology();
      final var actuator = ClusterActuator.of(cluster.availableGateway());

      // when
      final var response = actuator.scaleBrokers(List.of(0, 1, 2));

      // then
      assertThat(response.getExpectedTopology()).hasSize(3);
    }
  }

  @Test
  void shouldRequestForceScaleDownBrokers() {
    // create cluster with two brokers
    try (final var cluster = createCluster(2)) {
      // given
      cluster.awaitCompleteTopology();
      cluster.brokers().get(MemberId.from("1")).close();
      final var actuator = ClusterActuator.of(cluster.availableGateway());

      // when - force remove broker 1
      final var response = actuator.scaleBrokers(List.of(0), false, true);

      // then
      assertThat(response.getExpectedTopology()).hasSize(1);
    }
  }

  @Test
  void shouldRequestAddBroker() {
    try (final var cluster = createCluster(1)) {
      // given
      cluster.awaitCompleteTopology();
      final var actuator = ClusterActuator.of(cluster.availableGateway());

      // when
      final var response = actuator.addBroker(2);

      // then
      assertThat(response.getExpectedTopology()).hasSize(3);
    }
  }

  @Test
  void shouldRequestRemoveBroker() {
    try (final var cluster = createCluster(1)) {
      // given
      cluster.awaitCompleteTopology();
      final var actuator = ClusterActuator.of(cluster.availableGateway());
      // Must move partitions to broker 0 before removing broker 1 from the cluster
      movePartition(actuator);

      // when
      final var response = actuator.removeBroker(1);

      // then
      assertThat(response.getExpectedTopology()).hasSize(1);
    }
  }

  @Test
  void canDryRunScale() {
    try (final var cluster = createCluster(1)) {
      // given
      cluster.awaitCompleteTopology();
      final var actuator = ClusterActuator.of(cluster.availableGateway());
      final var initialTopology = actuator.getTopology();

      // when
      final var dryRun = actuator.scaleBrokers(List.of(1, 2), true);

      // then -- dry run response looks as expected
      assertThat(dryRun.getExpectedTopology()).hasSize(2);
      assertThat(dryRun.getPlannedChanges()).isNotEmpty();
      assertThat(dryRun.getCurrentTopology()).isEqualTo(initialTopology.getBrokers());
      // then -- topology did not change
      assertThat(actuator.getTopology()).isEqualTo(initialTopology);
    }
  }

  private static void movePartition(final ClusterActuator actuator) {
    final var plannedJoin = actuator.joinPartition(0, 2, 1);
    Awaitility.await()
        .untilAsserted(
            () -> ClusterActuatorAssert.assertThat(actuator).hasAppliedChanges(plannedJoin));
    final var leave = actuator.leavePartition(1, 2);
    Awaitility.await()
        .untilAsserted(() -> ClusterActuatorAssert.assertThat(actuator).hasAppliedChanges(leave));
  }

  private static TestCluster createCluster(final int replicationFactor) {
    return TestCluster.builder()
        .withEmbeddedGateway(true)
        .withBrokersCount(BROKER_COUNT)
        .withPartitionsCount(PARTITION_COUNT)
        .withReplicationFactor(replicationFactor)
        .build()
        .start();
  }

  @Nested
  final class ClusterPatchRequest {
    @Test
    void shouldRequestClusterScale() {
      try (final var cluster = createCluster(1)) {
        // given
        cluster.awaitCompleteTopology();
        final var actuator = ClusterActuator.of(cluster.availableGateway());

        // when
        final var request =
            new ClusterConfigPatchRequest()
                .brokers(new ClusterConfigPatchRequestBrokers().count(2))
                .partitions(
                    new ClusterConfigPatchRequestPartitions().count(2).replicationFactor(2));
        final var response = actuator.patchCluster(request, false, false);
        // then
        assertThat(response.getExpectedTopology())
            .describedAs("ClusterSize is increased to 2")
            .hasSize(2);
        assertThat(response.getExpectedTopology().getFirst().getPartitions().size())
            .describedAs("Each broker has 2 partitions")
            .isEqualTo(response.getExpectedTopology().getLast().getPartitions().size())
            .isEqualTo(2);

        assertThat(response.getPlannedChanges()).isNotEmpty();
      }
    }

    @Test
    void shouldRequestClusterPatch() {
      try (final var cluster = createCluster(1)) {
        // given
        cluster.awaitCompleteTopology();
        final var actuator = ClusterActuator.of(cluster.availableGateway());

        // when
        final var request =
            new ClusterConfigPatchRequest()
                .brokers(new ClusterConfigPatchRequestBrokers().add(List.of(1)))
                .partitions(
                    new ClusterConfigPatchRequestPartitions().count(2).replicationFactor(2));
        final var response = actuator.patchCluster(request, false, false);
        // then
        assertThat(response.getExpectedTopology())
            .describedAs("ClusterSize is increased to 2")
            .hasSize(2);
        assertThat(response.getExpectedTopology().getFirst().getPartitions().size())
            .describedAs("Each broker has 2 partitions")
            .isEqualTo(response.getExpectedTopology().getLast().getPartitions().size())
            .isEqualTo(2);

        assertThat(response.getPlannedChanges()).isNotEmpty();
      }
    }

    @Test
    void shouldRequestForceRemoveBroker() {
      // create cluster with two brokers
      try (final var cluster = createCluster(2)) {
        // given
        cluster.awaitCompleteTopology();
        cluster.brokers().get(MemberId.from("1")).close();
        final var actuator = ClusterActuator.of(cluster.availableGateway());

        // when - force remove broker 1
        final var request =
            new ClusterConfigPatchRequest()
                .brokers(new ClusterConfigPatchRequestBrokers().remove(List.of(1)));
        final var response = actuator.patchCluster(request, false, true);

        // then
        assertThat(response.getExpectedTopology()).hasSize(1);
      }
    }
  }
}
