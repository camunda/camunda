/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.clustering;

import static io.camunda.zeebe.test.StableValuePredicate.hasStableValue;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.zeebe.qa.util.actuator.PartitionsActuator;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotId;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import java.util.Optional;
import java.util.stream.IntStream;
import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
final class SnapshotWithExportersTest {

  private void publishMessages(final CamundaClient client) {
    IntStream.range(0, 10)
        .forEach(
            (i) ->
                client
                    .newPublishMessageCommand()
                    .messageName("msg")
                    .correlationKey("msg-" + i)
                    .send()
                    .join());
  }

  @Nested
  final class WithoutInitialExporterTest {
    @TestZeebe
    private final TestStandaloneBroker zeebe =
        new TestStandaloneBroker().withUnauthenticatedAccess();

    private final PartitionsActuator partitions = PartitionsActuator.of(zeebe);

    @Test
    void shouldNotTakeNewSnapshotWhenAddingNewExporter() {
      // given -- snapshot taken by broker without exporters
      final FileBasedSnapshotId snapshotWithoutExporters;
      final FileBasedSnapshotId snapshotWithExporters;

      try (final var client = zeebe.newClientBuilder().build()) {
        publishMessages(client);
      }

      partitions.takeSnapshot();
      snapshotWithoutExporters =
          Awaitility.await("Snapshot is taken")
              .atMost(Duration.ofSeconds(60))
              .until(
                  () ->
                      Optional.ofNullable(partitions.query().get(1).snapshotId())
                          .flatMap(FileBasedSnapshotId::ofFileName),
                  Optional::isPresent)
              .orElseThrow();
      // pause processing to avoid new processing to cause a new snapshot after restart
      PartitionsActuator.of(zeebe).pauseProcessing();
      zeebe.stop();

      // when -- taking snapshot on broker with exporters configured
      zeebe.withRecordingExporter(true).start().awaitCompleteTopology();

      partitions.takeSnapshot();
      snapshotWithExporters =
          Awaitility.await("Snapshot is taken")
              .atMost(Duration.ofSeconds(60))
              .during(Duration.ofSeconds(5))
              .until(
                  () ->
                      Optional.ofNullable(partitions.query().get(1).snapshotId())
                          .flatMap(FileBasedSnapshotId::ofFileName),
                  hasStableValue())
              .orElseThrow();

      // then -- broker with exporter configured should not have taken a new backup
      assertThat(snapshotWithExporters).isEqualTo(snapshotWithoutExporters);
    }
  }

  @Nested
  final class WithExporter {
    @TestZeebe
    private final TestStandaloneBroker zeebe =
        new TestStandaloneBroker().withRecordingExporter(true).withUnauthenticatedAccess();

    private final PartitionsActuator partitions = PartitionsActuator.of(zeebe);

    @Test
    void shouldIncludeExportedPositionInSnapshot() {
      // given - a receiver who has "acknowledged" everything ever in history
      try (final var client = zeebe.newClientBuilder().build()) {
        publishMessages(client);
      }

      final var exporterPosition =
          Awaitility.await("Exported position is stable")
              .atMost(Duration.ofSeconds(30))
              .during(Duration.ofSeconds(5))
              .until(() -> partitions.query().get(1).exportedPosition(), hasStableValue());
      assertThat(exporterPosition).isPositive();

      // when
      partitions.takeSnapshot();
      final var snapshotId =
          Awaitility.await("Snapshot is taken")
              .atMost(Duration.ofSeconds(60))
              .until(
                  () ->
                      Optional.ofNullable(partitions.query().get(1).snapshotId())
                          .flatMap(FileBasedSnapshotId::ofFileName),
                  Optional::isPresent)
              .orElseThrow();

      // then
      assertThat(snapshotId).returns(exporterPosition, FileBasedSnapshotId::getExportedPosition);
    }
  }

  @Nested
  final class WithPassiveExporter {

    @Test
    void shouldTakeSnapshotWhenExporterPositionIsMinusOne() {
      // given -- broker with exporter that does not acknowledge anything
      RecordingExporter.autoAcknowledge(false);
      try (final var zeebe =
          new TestStandaloneBroker()
              .withRecordingExporter(true)
              .start()
              .awaitCompleteTopology()
              .withUnauthenticatedAccess()) {

        final var partitions = PartitionsActuator.of(zeebe);
        try (final var client = zeebe.newClientBuilder().build()) {
          publishMessages(client);
        }

        Awaitility.await("Processed position is stable")
            .atMost(Duration.ofSeconds(60))
            .during(Duration.ofSeconds(5))
            .until(() -> partitions.query().get(1).processedPosition(), hasStableValue());

        partitions.takeSnapshot();

        // then -- snapshot has exported position 0
        final var snapshotWithExporters =
            Awaitility.await("Snapshot is taken")
                .atMost(Duration.ofSeconds(60))
                .during(Duration.ofSeconds(5))
                .until(
                    () ->
                        Optional.ofNullable(partitions.query().get(1).snapshotId())
                            .flatMap(FileBasedSnapshotId::ofFileName),
                    hasStableValue())
                .orElseThrow();

        Assertions.assertThat(snapshotWithExporters)
            .returns(0L, FileBasedSnapshotId::getExportedPosition);
      }
    }
  }
}
