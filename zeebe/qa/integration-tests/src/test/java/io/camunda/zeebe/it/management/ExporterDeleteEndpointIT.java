/*
Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
one or more contributor license agreements. See the NOTICE file distributed
with this work for additional information regarding copyright ownership.
Licensed under the Camunda License 1.0. You may not use this file
except in compliance with the Camunda License 1.0. */

package io.camunda.zeebe.it.management;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

@Timeout(2 * 60) // 2 minutes
final class ExporterDeleteEndpointIT {
  @Test
  void shouldDeleteExporterAfterRestartWithMissingConfig(@TempDir final Path tempDir) {
    // given - cluster with recording exporter restarted after removing the exporter config
    final var restartedCluster = restartClusterWithExporterConfigRemoved(tempDir);

    // exporter shows up as CONFIG_NOT_FOUND
    final ExportersActuator exportersActuator = ExportersActuator.of(restartedCluster.anyGateway());

    // it should still list the recording exporter once, but with CONFIG_NOT_FOUND
    assertThat(exportersActuator.getExporters())
        .hasSize(1)
        .first()
        .satisfies(
            status -> {
              assertThat(status.getExporterId()).isEqualTo("recordingExporter");
              assertThat(status.getStatus()).isEqualTo(ExporterStatus.StatusEnum.CONFIG_NOT_FOUND);
            });

    // when: delete exporter
    final var deleteResponse =
        ExportersActuator.of(restartedCluster.anyGateway()).deleteExporter("recordingExporter");
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

    restartedCluster.shutdown();
  }

  private TestCluster restartClusterWithExporterConfigRemoved(final Path tempDir) {
    // first cluster with recording exporter enabled, persisted working directory
    final var cluster =
        TestCluster.builder()
            .useRecordingExporter(true)
            .withBrokersCount(1)
            .withPartitionsCount(1)
            .withReplicationFactor(1)
            .withEmbeddedGateway(true)
            .withBrokerConfig(
                (memberId, broker) ->
                    broker.withWorkingDirectory(resolveBrokerDir(tempDir, memberId)))
            .build()
            .start()
            .awaitCompleteTopology();
    // sanity: exporter is enabled initially
    Awaitility.await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () ->
                assertThat(ExportersActuator.of(cluster.anyGateway()).getExporters())
                    .hasSize(1)
                    .first()
                    .extracting(ExporterStatus::getStatus)
                    .isEqualTo(ExporterStatus.StatusEnum.ENABLED));

    cluster.shutdown();

    // when: cluster restarted without recording exporter configured
    final var restartedCluster =
        TestCluster.builder()
            .useRecordingExporter(false)
            .withBrokersCount(1)
            .withPartitionsCount(1)
            .withReplicationFactor(1)
            .withEmbeddedGateway(true)
            .withBrokerConfig(
                (memberId, broker) ->
                    broker.withWorkingDirectory(resolveBrokerDir(tempDir, memberId)))
            .build()
            .start()
            .awaitCompleteTopology();
    return restartedCluster;
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
