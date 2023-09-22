/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.management;

import static io.camunda.zeebe.test.StableValuePredicate.hasStableValue;
import static org.assertj.core.api.Assertions.assertThat;

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
    startProcess();

    final var recordsBeforePause =
        Awaitility.await()
            .atMost(Duration.ofSeconds(30))
            .during(Duration.ofSeconds(5))
            .until(RecordingExporter.getRecords()::size, hasStableValue());

    // when
    getActuator().pause();
    startProcess();

    // then
    RecordingExporter.records().limit(recordsBeforePause + 1).await();
    Awaitility.await().untilAsserted(this::allPartitionsPausedExporting);
  }

  @Test
  void shouldResumeExporting() {
    // given
    final var actuator = getActuator();
    actuator.pause();

    startProcess();

    final var recordsBeforePause =
        Awaitility.await()
            .atMost(Duration.ofSeconds(30))
            .during(Duration.ofSeconds(5))
            .until(RecordingExporter.getRecords()::size, hasStableValue());

    // when
    getActuator().resume();
    startProcess();

    // then
    RecordingExporter.records().limit(recordsBeforePause + 1).await();
    Awaitility.await().untilAsserted(this::allPartitionsExporting);
  }

  private ExportingActuator getActuator() {
    return ExportingActuator.of(CLUSTER.availableGateway());
  }

  private void startProcess() {
    client
        .newCreateInstanceCommand()
        .bpmnProcessId("processId")
        .latestVersion()
        .withResult()
        .send()
        .join();
  }

  private void allPartitionsPausedExporting() {
    for (final var broker : CLUSTER.brokers().values()) {
      assertThat(PartitionsActuator.of(broker).query().values())
          .allMatch(
              status -> status.exporterPhase() == null || status.exporterPhase().equals("PAUSED"),
              "All exporters should be paused");
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
}
