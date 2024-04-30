/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.management;

import static io.camunda.zeebe.test.StableValuePredicate.hasStableValue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.actuator.ExportingActuator;
import io.camunda.zeebe.qa.util.actuator.PartitionsActuator;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.junit.AutoCloseResources;
import io.camunda.zeebe.test.util.junit.AutoCloseResources.AutoCloseResource;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
@AutoCloseResources
final class ExportingEndpointIT {
  @TestZeebe
  private static final TestCluster CLUSTER =
      TestCluster.builder()
          .useRecordingExporter(true)
          .withBrokersCount(2)
          .withPartitionsCount(2)
          .withReplicationFactor(1)
          .withEmbeddedGateway(true)
          .build();

  @AutoCloseResource private final ZeebeClient client = CLUSTER.newClientBuilder().build();

  @BeforeAll
  static void beforeAll() {
    try (final var client = CLUSTER.newClientBuilder().build()) {
      client
          .newDeployResourceCommand()
          .addProcessModel(
              Bpmn.createExecutableProcess("processId").startEvent().endEvent().done(),
              "process.bpmn")
          .send()
          .join();
    }
  }

  @Test
  void shouldPauseExporting() {
    // given
    client
        .newPublishMessageCommand()
        .messageName("Test")
        .correlationKey("1")
        .messageId("1")
        .send()
        .join();

    final var recordsBeforePause =
        Awaitility.await()
            .atMost(Duration.ofSeconds(30))
            .during(Duration.ofSeconds(5))
            .until(RecordingExporter.getRecords()::size, hasStableValue());

    // when
    getActuator().pause();

    client
        .newPublishMessageCommand()
        .messageName("Test")
        .correlationKey("2")
        .messageId("2")
        .send()
        .join();

    final var recordsAfterPause =
        Awaitility.await()
            .atMost(Duration.ofSeconds(30))
            .during(Duration.ofSeconds(5))
            .until(RecordingExporter.getRecords()::size, hasStableValue());

    // then
    assertEquals(recordsAfterPause, recordsBeforePause);
    Awaitility.await().untilAsserted(this::allPartitionsPausedExporting);
  }

  @Test
  void shouldResumeExporting() {
    // given
    final var actuator = getActuator();

    actuator.pause();

    client
        .newPublishMessageCommand()
        .messageName("Test")
        .correlationKey("3")
        .messageId("3")
        .send()
        .join();

    final var recordsBeforeResume =
        Awaitility.await()
            .atMost(Duration.ofSeconds(30))
            .during(Duration.ofSeconds(5))
            .until(RecordingExporter.getRecords()::size, hasStableValue());

    // when
    getActuator().resume();

    final var recordsAfterResume =
        Awaitility.await()
            .atMost(Duration.ofSeconds(30))
            .during(Duration.ofSeconds(5))
            .until(RecordingExporter.getRecords()::size, hasStableValue());

    // then
    assertThat(recordsAfterResume).isGreaterThan(recordsBeforeResume);
    Awaitility.await().untilAsserted(this::allPartitionsExporting);
  }

  @Test
  void shouldStayPausedAfterRestart() {
    // given
    // in case this test gets run right after shouldPauseExporting
    getActuator().resume();
    client
        .newPublishMessageCommand()
        .messageName("Test")
        .correlationKey("5")
        .messageId("5")
        .send()
        .join();

    final var recordsBeforePause =
        Awaitility.await()
            .atMost(Duration.ofSeconds(30))
            .during(Duration.ofSeconds(5))
            .until(RecordingExporter.getRecords()::size, hasStableValue());

    // when
    getActuator().pause();
    CLUSTER.shutdown();
    CLUSTER.start();

    client
        .newPublishMessageCommand()
        .messageName("Test")
        .correlationKey("6")
        .messageId("6")
        .send()
        .join();

    final var recordsAfterRestart =
        Awaitility.await()
            .atMost(Duration.ofSeconds(30))
            .during(Duration.ofSeconds(5))
            .until(RecordingExporter.getRecords()::size, hasStableValue());

    // then
    assertEquals(recordsAfterRestart, recordsBeforePause);
    Awaitility.await().untilAsserted(this::allPartitionsPausedExporting);
  }

  private ExportingActuator getActuator() {
    return ExportingActuator.of(CLUSTER.availableGateway());
  }

  private void allPartitionsPausedExporting() {
    for (final var broker : CLUSTER.brokers().values()) {
      assertThat(PartitionsActuator.of(broker).query().values())
          .allMatch(
              status -> status.exporterPhase() == null || status.exporterPhase().equals("PAUSED"),
              "All exporters should be paused");
    }
  }

  private void allPartitionsSoftPausedExporting() {
    for (final var broker : CLUSTER.brokers().values()) {
      assertThat(PartitionsActuator.of(broker).query().values())
          .allMatch(
              status ->
                  status.exporterPhase() == null || status.exporterPhase().equals("SOFT_PAUSED"),
              "All exporters should be soft paused");
    }
  }

  private void allPartitionsExporting() {
    for (final var broker : CLUSTER.brokers().values()) {
      assertThat(PartitionsActuator.of(broker).query().values())
          .allMatch(
              status ->
                  status.exporterPhase() == null || status.exporterPhase().equals("EXPORTING"),
              "All exporters should be running");
    }
  }

  @Test
  void shouldSoftPauseExporting() {

    final Map<Integer, Long> exportedPositionPerPartition;
    final Map<Integer, Long> secondExportedPositionPerPartition;
    // given
    client
        .newPublishMessageCommand()
        .messageName("Test")
        .correlationKey("7")
        .messageId("7")
        .send()
        .join();

    final var recordsBeforePause =
        Awaitility.await()
            .atMost(Duration.ofSeconds(30))
            .during(Duration.ofSeconds(5))
            .until(RecordingExporter.getRecords()::size, hasStableValue());

    exportedPositionPerPartition = getExportedPositions();
    // when
    getActuator().softPause();

    // given
    client
        .newPublishMessageCommand()
        .messageName("Test")
        .correlationKey("8")
        .messageId("8")
        .send()
        .join();

    final var recordsAfterSoftPause =
        Awaitility.await()
            .atMost(Duration.ofSeconds(30))
            .during(Duration.ofSeconds(5))
            .until(RecordingExporter.getRecords()::size, hasStableValue());

    secondExportedPositionPerPartition = getExportedPositions();
    assertThat(recordsAfterSoftPause).isGreaterThan(recordsBeforePause);
    assertThat(exportedPositionPerPartition).isEqualTo(secondExportedPositionPerPartition);
  }

  @Test
  void shouldResumeAfterSoftPauseExporting() {

    final Map<Integer, Long> exportedPositionPerPartition;
    final Map<Integer, Long> secondExportedPositionPerPartition;

    // given
    client
        .newPublishMessageCommand()
        .messageName("Test")
        .correlationKey("9")
        .messageId("9")
        .send()
        .join();

    final var recordsBeforePause =
        Awaitility.await()
            .atMost(Duration.ofSeconds(30))
            .during(Duration.ofSeconds(5))
            .until(RecordingExporter.getRecords()::size, hasStableValue());

    exportedPositionPerPartition = getExportedPositions();
    // when
    getActuator().softPause();

    // given
    client
        .newPublishMessageCommand()
        .messageName("Test")
        .correlationKey("10")
        .messageId("10")
        .send()
        .join();

    final var recordsAfterSoftPause =
        Awaitility.await()
            .atMost(Duration.ofSeconds(30))
            .during(Duration.ofSeconds(5))
            .until(RecordingExporter.getRecords()::size, hasStableValue());

    getActuator().resume();

    secondExportedPositionPerPartition = getExportedPositions();

    assertThat(recordsAfterSoftPause).isGreaterThan(recordsBeforePause);
    // at least one partition should have a higher exported position
    assertTrue(
        exportedPositionPerPartition.entrySet().stream()
            .anyMatch(
                exportedPosition ->
                    secondExportedPositionPerPartition.get(exportedPosition.getKey())
                        > exportedPosition.getValue()));
  }

  @Test
  void shouldStaySoftPausedAfterRestart() {
    // given
    getActuator().resume();
    client
        .newPublishMessageCommand()
        .messageName("Test")
        .correlationKey("11")
        .messageId("11")
        .send()
        .join();

    final var recordsBeforePause =
        Awaitility.await()
            .atMost(Duration.ofSeconds(30))
            .during(Duration.ofSeconds(5))
            .until(RecordingExporter.getRecords()::size, hasStableValue());

    // when
    getActuator().softPause();
    Awaitility.await().untilAsserted(this::allPartitionsSoftPausedExporting);
    CLUSTER.shutdown();
    CLUSTER.start();

    // all partitions should be soft paused after restart
    Awaitility.await().untilAsserted(this::allPartitionsSoftPausedExporting);

    client
        .newPublishMessageCommand()
        .messageName("Test")
        .correlationKey("12")
        .messageId("12")
        .send()
        .join();

    final var recordsAfterRestart =
        Awaitility.await()
            .atMost(Duration.ofSeconds(30))
            .during(Duration.ofSeconds(5))
            .until(RecordingExporter.getRecords()::size, hasStableValue());

    final Map<Integer, Long> exportedPositionsAfterSoftPause = getExportedPositions();

    // then
    assertThat(recordsAfterRestart).isGreaterThan(recordsBeforePause);
    // all partitions should have -1 as exported position due to restart and the exporter positions
    // not being able to update due to soft pause.
    try {
      // TODO: after reason for flakiness is found, remove the debug try-catch block.
      //  https://github.com/camunda/zeebe/issues/17836
      assertTrue(
          exportedPositionsAfterSoftPause.entrySet().stream()
              .allMatch(exportedPosition -> exportedPosition.getValue().equals(-1L)));
    } catch (final AssertionError e) {
      // if the assertion fails, we want to  throw the exported positions for debugging
      final String errorMessage =
          "Exported positions are not -1 after soft pause. Exported positions: "
              .concat(
                  exportedPositionsAfterSoftPause.entrySet().stream()
                      .map(
                          element ->
                              String.format(
                                  "Partition %d -> position %d",
                                  element.getKey(), element.getValue()))
                      .toList()
                      .toString());
      throw new AssertionError(errorMessage, e);
    }
  }

  Map<Integer, Long> getExportedPositions() {
    final Map<Integer, Long> exportedPositionPerPartition = new HashMap<>();
    for (final var broker : CLUSTER.brokers().values()) {
      PartitionsActuator.of(broker)
          .query()
          .forEach(
              (partitionId, partitionStatus) -> {
                exportedPositionPerPartition.put(partitionId, partitionStatus.exportedPosition());
              });
    }
    return exportedPositionPerPartition;
  }
}
