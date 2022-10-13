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
import io.camunda.zeebe.qa.util.testcontainers.ZeebeTestContainerDefaults;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotId;
import io.camunda.zeebe.test.util.socket.SocketUtil;
import io.zeebe.containers.ZeebeContainer;
import io.zeebe.containers.ZeebeVolume;
import io.zeebe.containers.exporter.DebugReceiver;
import java.time.Duration;
import java.util.stream.IntStream;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

final class SnapshotWithExportersTest {

  @Test
  void shouldIncludeExportedPositionInSnapshot() {
    // given
    try (final var debugReceiver =
        new DebugReceiver((record) -> {}, SocketUtil.getNextAddress(), true).start()) {
      try (final var zeebe =
          new ZeebeContainer(ZeebeTestContainerDefaults.defaultTestImage())
              .withDebugExporter(debugReceiver.serverAddress().getPort())) {
        zeebe.start();

        final var partitions = PartitionsActuator.of(zeebe);

        try (final var client =
            ZeebeClient.newClientBuilder()
                .gatewayAddress(zeebe.getExternalGatewayAddress())
                .usePlaintext()
                .build()) {

          publishMessages(client);
          final var exporterPosition =
              Awaitility.await("Exported position is stable")
                  .atMost(Duration.ofSeconds(30))
                  .during(Duration.ofSeconds(5))
                  .until(() -> partitions.query().get(1).exportedPosition(), hasStableValue());
          assertThat(exporterPosition).isPositive();

          // when
          partitions.takeSnapshot();

          // then
          final var exportedPositionInSnapshot =
              Awaitility.await("Snapshot is taken")
                  .atMost(Duration.ofSeconds(60))
                  .during(Duration.ofSeconds(5))
                  .until(
                      () ->
                          FileBasedSnapshotId.ofFileName(partitions.query().get(1).snapshotId())
                              .orElseThrow()
                              .getExportedPosition(),
                      hasStableValue());

          assertThat(exportedPositionInSnapshot).isEqualTo(exporterPosition);
        }
      }
    }
  }

  @Test
  void shouldTakeSnapshotWhenExporterPositionIsMinusOne() {
    // given -- broker with exporter that does not acknowledge anything
    try (final var unresponsiveExporterTarget = new DebugReceiver((record) -> {}, false).start()) {
      try (final var zeebe =
          new ZeebeContainer(ZeebeTestContainerDefaults.defaultTestImage())
              .withDebugExporter(unresponsiveExporterTarget.serverAddress().getPort())) {
        zeebe.start();

        try (final var client =
            ZeebeClient.newClientBuilder()
                .gatewayAddress(zeebe.getExternalGatewayAddress())
                .usePlaintext()
                .build()) {
          publishMessages(client);
        }

        final var partitions = PartitionsActuator.of(zeebe);
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
                        FileBasedSnapshotId.ofFileName(partitions.query().get(1).snapshotId())
                            .orElseThrow(),
                    hasStableValue());

        assertThat(snapshotWithExporters).returns(0L, FileBasedSnapshotId::getExportedPosition);
      }
    }
  }

  @Test
  void shouldNotTakeNewSnapshotWhenAddingNewExporter() {
    // given -- snapshot taken by broker without exporters
    final var dataVolume = ZeebeVolume.newVolume();
    final FileBasedSnapshotId snapshotWithoutExporters;
    final FileBasedSnapshotId snapshotWithExporters;
    try (final var zeebeWithoutExporter =
        new ZeebeContainer(ZeebeTestContainerDefaults.defaultTestImage())
            .withZeebeData(dataVolume)) {
      zeebeWithoutExporter.start();

      try (final var client =
          ZeebeClient.newClientBuilder()
              .gatewayAddress(zeebeWithoutExporter.getExternalGatewayAddress())
              .usePlaintext()
              .build()) {
        publishMessages(client);
      }

      final var partitions = PartitionsActuator.of(zeebeWithoutExporter);

      partitions.takeSnapshot();
      snapshotWithoutExporters =
          Awaitility.await("Snapshot is taken")
              .atMost(Duration.ofSeconds(60))
              .during(Duration.ofSeconds(5))
              .until(
                  () ->
                      FileBasedSnapshotId.ofFileName(partitions.query().get(1).snapshotId())
                          .orElseThrow(),
                  hasStableValue());
    }

    // when -- taking snapshot on broker with exporters configured
    try (final var debugReceiver =
        new DebugReceiver((record) -> {}, SocketUtil.getNextAddress()).start()) {
      try (final var zeebeWithExporter =
          new ZeebeContainer(ZeebeTestContainerDefaults.defaultTestImage())
              .withZeebeData(dataVolume)
              .withDebugExporter(debugReceiver.serverAddress().getPort())) {
        zeebeWithExporter.start();

        final var partitions = PartitionsActuator.of(zeebeWithExporter);

        partitions.takeSnapshot();
        snapshotWithExporters =
            Awaitility.await("Snapshot is taken")
                .atMost(Duration.ofSeconds(60))
                .during(Duration.ofSeconds(5))
                .until(
                    () ->
                        FileBasedSnapshotId.ofFileName(partitions.query().get(1).snapshotId())
                            .orElseThrow(),
                    hasStableValue());
      }
    }

    // then -- broker with exporter configured should not have taken a new backup
    assertThat(snapshotWithExporters).isEqualTo(snapshotWithoutExporters);
  }

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
}
