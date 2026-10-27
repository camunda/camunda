/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.clustering.dynamic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ClientStatusException;
import io.camunda.zeebe.it.cluster.backup.InProcessRestoreTestUtil;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.qa.util.actuator.ClusterActuator;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.qa.util.topology.ClusterActuatorAssert;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@ZeebeIntegration
@Timeout(value = 60, unit = TimeUnit.SECONDS)
final class ModeChangeAcceptanceIT {

  private static final int BROKERS_COUNT = 3;
  private static final int PARTITIONS_COUNT = 3;

  @TestZeebe(purgeAfterEach = false)
  static TestCluster cluster =
      TestCluster.builder()
          .withEmbeddedGateway(true)
          .withBrokersCount(BROKERS_COUNT)
          .withPartitionsCount(PARTITIONS_COUNT)
          .withReplicationFactor(BROKERS_COUNT)
          .build();

  private static final String JOB_TYPE = "job";
  @AutoClose CamundaClient camundaClient;

  @BeforeEach
  void setUp() {
    camundaClient = cluster.availableGateway().newClientBuilder().preferRestOverGrpc(false).build();
  }

  @Test
  void shouldCycleBetweenRecoveryAndProcessing() {
    // given
    final var processId = "mode-change-test-process";
    Utils.deployProcessModel(camundaClient, JOB_TYPE, processId);
    final var actuator = ClusterActuator.of(cluster.availableGateway());

    for (int i = 0; i < 3; i++) {
      // when - transition to RECOVERING
      final var toRecovering =
          InProcessRestoreTestUtil.changeMode(camundaClient, "RECOVERING", false);
      Awaitility.await("Cluster transitions to RECOVERING (iteration " + i + ")")
          .timeout(Duration.ofMinutes(2))
          .untilAsserted(
              () ->
                  ClusterActuatorAssert.assertThat(actuator)
                      .hasCompletedChanges(toRecovering)
                      .doesNotHavePendingChanges());

      // then - a series of PI creation attempts must all fail across all partitions
      Awaitility.await("All PI creations blocked in RECOVERING mode (iteration " + i + ")")
          .timeout(Duration.ofSeconds(30))
          .untilAsserted(() -> assertAllCreateInstanceAttemptsFail(processId));

      // and - every local partition reports RECOVERING via the cluster topology actuator
      Awaitility.await("All local partitions report RECOVERING (iteration " + i + ")")
          .timeout(Duration.ofSeconds(30))
          .untilAsserted(
              () -> {
                final var topology = actuator.getTopology();
                assertThat(topology.getBrokers())
                    .flatExtracting(io.camunda.zeebe.management.cluster.BrokerState::getPartitions)
                    .extracting(io.camunda.zeebe.management.cluster.PartitionState::getState)
                    .allMatch(
                        state ->
                            state
                                == io.camunda.zeebe.management.cluster.PartitionStateCode
                                    .RECOVERING);
              });

      // when - transition back to PROCESSING
      final var toProcessing =
          InProcessRestoreTestUtil.changeMode(camundaClient, "PROCESSING", false);
      Awaitility.await("Cluster transitions to PROCESSING (iteration " + i + ")")
          .timeout(Duration.ofMinutes(2))
          .untilAsserted(
              () ->
                  ClusterActuatorAssert.assertThat(actuator)
                      .hasCompletedChanges(toProcessing)
                      .doesNotHavePendingChanges());

      // then - PI creation must succeed on every partition after returning to processing mode
      final var createdKeys =
          Utils.createInstanceWithAJobOnAllPartitions(
              camundaClient, JOB_TYPE, PARTITIONS_COUNT, false, processId);
      assertThat(createdKeys.stream().map(Protocol::decodePartitionId).collect(Collectors.toSet()))
          .describedAs(
              "All %d partitions have at least one created process instance (iteration %d)",
              PARTITIONS_COUNT, i)
          .containsExactlyInAnyOrderElementsOf(
              IntStream.rangeClosed(1, PARTITIONS_COUNT).boxed().collect(Collectors.toList()));

      // and - every local partition reports ACTIVE again via the cluster topology actuator
      Awaitility.await("All local partitions report ACTIVE (iteration " + i + ")")
          .timeout(Duration.ofSeconds(30))
          .untilAsserted(
              () -> {
                final var topology = actuator.getTopology();
                assertThat(topology.getBrokers())
                    .flatExtracting(io.camunda.zeebe.management.cluster.BrokerState::getPartitions)
                    .extracting(io.camunda.zeebe.management.cluster.PartitionState::getState)
                    .allMatch(
                        state ->
                            state == io.camunda.zeebe.management.cluster.PartitionStateCode.ACTIVE);
              });
    }
  }

  private void assertAllCreateInstanceAttemptsFail(final String processId) {
    IntStream.range(0, PARTITIONS_COUNT * 2)
        .forEach(
            j ->
                assertThatThrownBy(
                        () ->
                            camundaClient
                                .newCreateInstanceCommand()
                                .bpmnProcessId(processId)
                                .latestVersion()
                                .send()
                                .join())
                    .describedAs("PI creation attempt %d should be rejected in RECOVERING mode", j)
                    .isInstanceOf(ClientStatusException.class));
  }
}
