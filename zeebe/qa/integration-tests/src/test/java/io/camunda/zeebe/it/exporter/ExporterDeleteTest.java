/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.exporter;

import static io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker.RECORDING_EXPORTER_ID;
import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.management.cluster.ExporterStatus;
import io.camunda.zeebe.management.cluster.Operation;
import io.camunda.zeebe.management.cluster.PlannedOperationsResponse;
import io.camunda.zeebe.qa.util.actuator.ExportersActuator;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.topology.ClusterActuatorAssert;
import java.nio.file.Path;
import java.time.Duration;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

@Timeout(2 * 60) // 2 minutes
@ZeebeIntegration
final class ExporterDeleteTest {
  private static final int PARTITIONS_COUNT = 3;
  private static final int BROKERS_COUNT = 3;
  private static final int REPLICATION_FACTOR = 3;

  @TempDir private Path baseWorkingDir;

  private TestCluster cluster;

  private ExportersActuator actuator;

  @BeforeEach
  void setup() {
    cluster =
        TestCluster.builder()
            .useRecordingExporter(true)
            .withBrokersCount(BROKERS_COUNT)
            .withPartitionsCount(PARTITIONS_COUNT)
            .withReplicationFactor(REPLICATION_FACTOR)
            .withEmbeddedGateway(true)
            .withBrokerConfig(
                (memberId, broker) ->
                    broker.withWorkingDirectory(resolveBrokerDir(baseWorkingDir, memberId)))
            .build();

    cluster.start().awaitCompleteTopology();

    actuator = ExportersActuator.of(cluster.availableGateway());
  }

  @AfterEach
  void tearDown() {
    if (cluster != null) {
      cluster.shutdown();
    }
  }

  @Test
  void shouldDeleteExporterFromAllPartitions() {
    // given - verify exporter is initially enabled on all partitions
    assertThat(actuator.getExporters())
        .hasSize(1)
        .first()
        .extracting(ExporterStatus::getStatus)
        .isEqualTo(ExporterStatus.StatusEnum.ENABLED);

    // restart cluster without exporter config to simulate CONFIG_NOT_FOUND state
    cluster.shutdown();

    cluster.brokers().values().forEach(b -> b.withRecordingExporter(false));
    final var restartedCluster = cluster.start().awaitCompleteTopology();

    final var restartedActuator = ExportersActuator.of(restartedCluster.availableGateway());

    // verify exporter shows up as CONFIG_NOT_FOUND across all partitions
    assertThat(restartedActuator.getExporters())
        .hasSize(1)
        .first()
        .satisfies(
            status -> {
              assertThat(status.getExporterId()).isEqualTo(RECORDING_EXPORTER_ID);
              assertThat(status.getStatus()).isEqualTo(ExporterStatus.StatusEnum.CONFIG_NOT_FOUND);
            });

    // when - delete exporter
    final var deleteResponse = restartedActuator.deleteExporter(RECORDING_EXPORTER_ID);

    assertThat(deleteResponse.getPlannedChanges())
        .hasSize(PARTITIONS_COUNT * REPLICATION_FACTOR)
        .allMatch(
            operation ->
                operation.getOperation() == Operation.OperationEnum.PARTITION_DELETE_EXPORTER);

    waitUntilOperationIsApplied(restartedCluster, deleteResponse);

    // then - verify exporter is deleted from all partitions
    Awaitility.await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () ->
                assertThat(restartedActuator.getExporters())
                    .describedAs("Exporter is deleted from all partitions")
                    .isEmpty());
  }

  private Path resolveBrokerDir(final Path base, final MemberId memberId) {
    return base.resolve("broker-" + memberId.id());
  }

  private void waitUntilOperationIsApplied(
      final TestCluster cluster, final PlannedOperationsResponse response) {
    Awaitility.await()
        .timeout(Duration.ofSeconds(30))
        .untilAsserted(() -> ClusterActuatorAssert.assertThat(cluster).hasAppliedChanges(response));
  }
}
