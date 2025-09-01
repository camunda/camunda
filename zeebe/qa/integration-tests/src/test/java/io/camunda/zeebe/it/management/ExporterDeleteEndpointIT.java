/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

package io.camunda.zeebe.it.management;

import static io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker.RECORDING_EXPORTER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.management.cluster.ExporterStatus;
import io.camunda.zeebe.management.cluster.Operation;
import io.camunda.zeebe.management.cluster.Operation.OperationEnum;
import io.camunda.zeebe.management.cluster.PlannedOperationsResponse;
import io.camunda.zeebe.qa.util.actuator.ExportersActuator;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
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
final class ExporterDeleteEndpointIT {

  @TempDir private Path baseWorkingDir;

  private TestCluster cluster;

  private ExportersActuator actuator;

  @BeforeEach
  void setup() {
    cluster =
        TestCluster.builder()
            .useRecordingExporter(true)
            .withBrokersCount(1)
            .withPartitionsCount(1)
            .withReplicationFactor(1)
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
  void shouldDeleteExporterAfterRestartWithMissingConfig(@TempDir final Path tempDir) {
    // given
    // sanity: exporter is enabled initially
    assertThat(ExportersActuator.of(cluster.anyGateway()).getExporters())
        .hasSize(1)
        .first()
        .extracting(ExporterStatus::getStatus)
        .isEqualTo(ExporterStatus.StatusEnum.ENABLED);

    cluster.shutdown();

    // Restart cluster with exporter removed
    cluster.brokers().values().forEach(b -> b.withRecordingExporter(false));
    final var restartedCluster = cluster.start().awaitCompleteTopology();

    final ExportersActuator exportersActuator = ExportersActuator.of(restartedCluster.anyGateway());

    // exporter shows up as CONFIG_NOT_FOUND
    assertThat(exportersActuator.getExporters())
        .hasSize(1)
        .first()
        .satisfies(
            status -> {
              assertThat(status.getExporterId()).isEqualTo(RECORDING_EXPORTER_ID);
              assertThat(status.getStatus()).isEqualTo(ExporterStatus.StatusEnum.CONFIG_NOT_FOUND);
            });

    // when: delete exporter
    final var deleteResponse =
        ExportersActuator.of(restartedCluster.anyGateway()).deleteExporter(RECORDING_EXPORTER_ID);
    assertThat(deleteResponse.getPlannedChanges())
        .hasSize(1)
        .first()
        .extracting(Operation::getOperation)
        .isEqualTo(OperationEnum.PARTITION_DELETE_EXPORTER);

    waitUntilOperationIsApplied(restartedCluster, deleteResponse);

    // then: exporter no longer appears
    Awaitility.await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () ->
                assertThat(exportersActuator.getExporters())
                    .describedAs("Exporter is deleted")
                    .isEmpty());
  }

  @Test
  void shouldFailDeletingExporterIfConfigStillPresent(@TempDir final Path tempDir) {

    try (final var cluster = startCluster(true, tempDir)) {
      // given - cluster with recording exporter enabled
      assertThat(ExportersActuator.of(cluster.anyGateway()).getExporters())
          .hasSize(1)
          .first()
          .extracting(ExporterStatus::getStatus)
          .isEqualTo(ExporterStatus.StatusEnum.ENABLED)
          .describedAs("Exporter should be enabled on startup");

      // when - then
      assertThatThrownBy(
              () ->
                  ExportersActuator.of(cluster.anyGateway()).deleteExporter(RECORDING_EXPORTER_ID))
          .isInstanceOf(feign.FeignException.class)
          .extracting(e -> ((feign.FeignException) e).status())
          .isEqualTo(400)
          .describedAs("Enabled exporter cannot be deleted when config is present");
    }
  }

  private TestCluster startCluster(final boolean useRecordingExporter, final Path tempDir) {
    return TestCluster.builder()
        .useRecordingExporter(useRecordingExporter)
        .withBrokersCount(1)
        .withPartitionsCount(1)
        .withReplicationFactor(1)
        .withEmbeddedGateway(true)
        .withBrokerConfig(
            (memberId, broker) -> broker.withWorkingDirectory(resolveBrokerDir(tempDir, memberId)))
        .build()
        .start()
        .awaitCompleteTopology();
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
