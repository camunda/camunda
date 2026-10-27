/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.exporter;

import static io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker.RECORDING_EXPORTER_ID;
import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.camunda.client.CamundaClient;
import io.camunda.zeebe.management.cluster.ExporterStatus;
import io.camunda.zeebe.management.cluster.Operation;
import io.camunda.zeebe.management.cluster.PlannedOperationsResponse;
import io.camunda.zeebe.qa.util.actuator.ExportersActuator;
import io.camunda.zeebe.qa.util.actuator.PartitionsActuator;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.topology.ClusterActuatorAssert;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import org.apache.commons.lang3.RandomStringUtils;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

@ZeebeIntegration
final class ExporterDeleteTest {

  private static Path resolveBrokerDir(final Path base, final MemberId memberId) {
    return base.resolve("broker-" + memberId.id());
  }

  private static void waitUntilOperationIsApplied(
      final TestCluster cluster, final PlannedOperationsResponse response) {
    Awaitility.await()
        .timeout(Duration.ofSeconds(30))
        .untilAsserted(() -> ClusterActuatorAssert.assertThat(cluster).hasAppliedChanges(response));
  }

  @Nested
  @Timeout(2 * 60) // 2 minutes
  final class DeleteFromAllPartitionsTest {
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
                assertThat(status.getStatus())
                    .isEqualTo(ExporterStatus.StatusEnum.CONFIG_NOT_FOUND);
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
  }

  @Nested
  final class CompactionAfterExporterDeletedTest {
    private static final String PARTITION_DATA_DIRECTORY = "data/default/partitions/1";

    @TempDir private Path baseWorkingDir;

    private TestCluster cluster;

    @BeforeEach
    void setup() {
      // an exporter which never acknowledges any record pins the exported position, which blocks
      // log compaction until the exporter itself is deleted
      RecordingExporter.autoAcknowledge(false);

      cluster =
          TestCluster.builder()
              .useRecordingExporter(true)
              .withBrokersCount(1)
              .withPartitionsCount(1)
              .withReplicationFactor(1)
              .withEmbeddedGateway(true)
              .withBrokerConfig(
                  (memberId, broker) ->
                      broker
                          .withWorkingDirectory(resolveBrokerDir(baseWorkingDir, memberId))
                          .withDataConfig(
                              data -> {
                                data.setSnapshotPeriod(Duration.ofMinutes(5));
                              }))
              .build();

      cluster.start().awaitCompleteTopology();
    }

    @AfterEach
    void tearDown() {
      RecordingExporter.autoAcknowledge(true);
      if (cluster != null) {
        cluster.shutdown();
      }
    }

    @Test
    @Timeout(3 * 60)
    void shouldCompactLogAfterExporterIsDeletedEvenIfItNeverAcknowledgedRecords() {
      // given - fill up the log and take a snapshot; since the exporter never acknowledges
      // anything, its exported position stays pinned at 0 and nothing can be compacted yet
      final var broker = cluster.brokers().values().iterator().next();
      final var partitions = PartitionsActuator.of(broker);

      try (final var client = cluster.newClientBuilder().build()) {
        publish(broker, client, 10);
      }
      partitions.takeSnapshot();
      final long exportedPositionInSnapshot = awaitSnapshotTaken(partitions);
      assertThat(exportedPositionInSnapshot)
          .describedAs("Exporter is configured and not exporting")
          .isZero();

      // when - restarting without the exporter config (CONFIG_NOT_FOUND), then deleting the
      // exporter through the actuator endpoint
      cluster.shutdown();
      cluster.brokers().values().forEach(b -> b.withRecordingExporter(false));
      cluster.start().awaitCompleteTopology();

      final var restartedActuator = ExportersActuator.of(cluster.availableGateway());
      final var deleteResponse = restartedActuator.deleteExporter(RECORDING_EXPORTER_ID);
      waitUntilOperationIsApplied(cluster, deleteResponse);

      try (final var client = cluster.newClientBuilder().build()) {
        publish(broker, client, 10);
      }

      partitions.takeSnapshot();
      final var exportedPositionAfterDeletion = awaitSnapshotTaken(partitions);

      // then - the log is compacted now that the exporter's pinned position is gone
      assertThat(exportedPositionAfterDeletion).isGreaterThan(0);
    }

    private long awaitSnapshotTaken(final PartitionsActuator partitions) {
      Awaitility.await("until a snapshot is taken")
          .atMost(Duration.ofSeconds(30))
          .until(() -> partitions.query().get(1).snapshotId(), Objects::nonNull);
      final var partitionStatus = partitions.query().get(1);
      return partitionStatus.exportedPositionInSnapshot();
    }

    private void publish(
        final TestStandaloneBroker broker, final CamundaClient client, final long numMessages) {
      for (int i = 0; i < numMessages; i++) {
        client
            .newPublishMessageCommand()
            .messageName("msg")
            .correlationKey("compaction-test")
            .variable("foo", RandomStringUtils.insecure().nextAscii(1024))
            .send()
            .join();
      }
    }
  }
}
