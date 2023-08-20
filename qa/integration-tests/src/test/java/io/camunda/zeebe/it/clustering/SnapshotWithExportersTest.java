/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.clustering;

import static io.camunda.zeebe.test.StableValuePredicate.hasStableValue;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.qa.util.actuator.PartitionsActuator;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.cluster.ZeebeHealthProbe;
import io.camunda.zeebe.qa.util.cluster.junit.ManageTestNodes;
import io.camunda.zeebe.qa.util.cluster.junit.ManageTestNodes.TestNode;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotId;
import io.camunda.zeebe.test.util.junit.AutoCloseResources;
import io.camunda.zeebe.test.util.junit.AutoCloseResources.AutoCloseResource;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.stream.IntStream;
import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@ManageTestNodes
@AutoCloseResources
final class SnapshotWithExportersTest {

  private void publishMessages(final ZeebeClient client) {
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
  final class WithExporterTest {

    @AutoCloseResource private ZeebeClient client;

    @TestNode
    private final TestStandaloneBroker zeebe =
        new TestStandaloneBroker().withRecordingExporter(true);

    @BeforeEach
    void beforeEach() {
      client = zeebe.newClientBuilder().build();
    }

    @Test
    void shouldIncludeExportedPositionInSnapshot() {
      // given
      final var partitions = PartitionsActuator.ofAddress(zeebe.monitoringAddress());
      publishMessages(client);
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

    @Test
    void shouldTakeSnapshotWhenExporterPositionIsMinusOne() {
      // given -- broker with exporter that does not acknowledge anything
      RecordingExporter.setAcknowledge(false);
      publishMessages(client);

      final var partitions = PartitionsActuator.ofAddress(zeebe.monitoringAddress());
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

  @Nested
  final class WithoutExporterTest {
    @Test
    void shouldNotTakeNewSnapshotWhenAddingNewExporter(final @TempDir Path directory) {
      // given -- snapshot taken by broker without exporters
      final FileBasedSnapshotId snapshotWithoutExporters;
      final FileBasedSnapshotId snapshotWithExporters;
      try (final var zeebeWithoutExporter =
          new TestStandaloneBroker()
              .withWorkingDirectory(directory)
              .start()
              .await(ZeebeHealthProbe.READY)
              .awaitCompleteTopology()) {
        try (final var client = zeebeWithoutExporter.newClientBuilder().build()) {
          publishMessages(client);
        }

        final var partitions =
            PartitionsActuator.ofAddress(zeebeWithoutExporter.monitoringAddress());

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
      }

      // when -- taking snapshot on broker with exporters configured
      try (final var zeebeWithExporter =
          new TestStandaloneBroker()
              .withRecordingExporter(true)
              .withWorkingDirectory(directory)
              .start()
              .await(ZeebeHealthProbe.READY)) {
        final var partitions = PartitionsActuator.ofAddress(zeebeWithExporter.monitoringAddress());

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
      }

      // then -- broker with exporter configured should not have taken a new backup
      assertThat(snapshotWithExporters).isEqualTo(snapshotWithoutExporters);
    }
  }
}
