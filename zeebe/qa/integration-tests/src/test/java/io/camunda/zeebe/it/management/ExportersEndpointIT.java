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
import static org.assertj.core.api.Assertions.assertThatException;

import feign.FeignException;
import io.camunda.client.CamundaClient;
import io.camunda.zeebe.management.cluster.BrokerState;
import io.camunda.zeebe.management.cluster.ExporterStateCode;
import io.camunda.zeebe.management.cluster.ExporterStatus;
import io.camunda.zeebe.management.cluster.ExporterStatus.StatusEnum;
import io.camunda.zeebe.management.cluster.Operation.OperationEnum;
import io.camunda.zeebe.management.cluster.PlannedOperationsResponse;
import io.camunda.zeebe.qa.util.actuator.ExportersActuator;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.qa.util.topology.ClusterActuatorAssert;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(2 * 60) // 2 minutes
@ZeebeIntegration
final class ExportersEndpointIT {
  @TestZeebe
  private final TestCluster cluster =
      TestCluster.builder()
          .useRecordingExporter(true)
          .withBrokersCount(1)
          .withPartitionsCount(1)
          .withReplicationFactor(1)
          .withEmbeddedGateway(true)
          .build();

  @AutoClose private CamundaClient client;

  @BeforeEach
  void setup() {
    client = cluster.newClientBuilder().build();
  }

  @Test
  void shouldDisableExporter() {
    // given
    // write some events
    client.newPublishMessageCommand().messageName("test").correlationKey("key").send().join();

    // verify recording exporter has seen the records
    final var recordsBeforeDisable =
        Awaitility.await()
            .atMost(Duration.ofSeconds(30))
            .during(Duration.ofSeconds(5))
            .until(RecordingExporter.getRecords()::size, hasStableValue());

    assertThat(recordsBeforeDisable).isGreaterThanOrEqualTo(2);

    // when
    final var response =
        ExportersActuator.of(cluster.anyGateway()).disableExporter("recordingExporter");
    assertThat(response.getPlannedChanges().getFirst().getOperation())
        .isEqualTo(OperationEnum.PARTITION_DISABLE_EXPORTER);
    assertThat(response.getExpectedTopology().stream().allMatch(this::hasExporterDisabled))
        .isTrue();

    waitUntilOperationIsApplied(response);

    // write more events
    client.newPublishMessageCommand().messageName("test2").correlationKey("key2").send().join();

    // then
    // verify no events are exported to recording exporter
    final var recordsAfterDisable =
        Awaitility.await()
            .atMost(Duration.ofSeconds(30))
            .during(Duration.ofSeconds(5))
            .until(RecordingExporter.getRecords()::size, hasStableValue());

    assertThat(recordsBeforeDisable).isEqualTo(recordsAfterDisable);

    assertThat(ExportersActuator.of(cluster.anyGateway()).getExporters())
        .describedAs("Exporter is disabled")
        .hasSize(1)
        .first()
        .isEqualTo(
            new ExporterStatus().exporterId("recordingExporter").status(StatusEnum.DISABLED));
  }

  private boolean hasExporterDisabled(final BrokerState b) {
    return b.getPartitions().stream()
        .allMatch(
            p ->
                p.getConfig().getExporting().getExporters().stream()
                    .allMatch(e -> e.getState().equals(ExporterStateCode.DISABLED)));
  }

  @Test
  void shouldFailRequestForNonExistingExporter() {
    // when - then
    assertThatException()
        .isThrownBy(
            () -> ExportersActuator.of(cluster.anyGateway()).disableExporter("nonExistingExporter"))
        .isInstanceOf(FeignException.class)
        .asInstanceOf(InstanceOfAssertFactories.type(FeignException.class))
        .extracting(FeignException::status)
        .isEqualTo(400);
  }

  @Test
  void shouldEnableExporter() {
    // given
    final var messageName = "enable-exporter-test";
    final var response =
        ExportersActuator.of(cluster.anyGateway()).disableExporter("recordingExporter");
    waitUntilOperationIsApplied(response);

    // when
    final var enableResponse =
        ExportersActuator.of(cluster.anyGateway()).enableExporter("recordingExporter");
    waitUntilOperationIsApplied(enableResponse);

    client.newPublishMessageCommand().messageName(messageName).correlationKey("key2").send().join();

    // then
    Awaitility.await("Record is exported")
        .until(
            () -> RecordingExporter.messageRecords().withName(messageName).findFirst().isPresent());

    assertThat(ExportersActuator.of(cluster.anyGateway()).getExporters())
        .describedAs("Exporter is enabled")
        .hasSize(1)
        .first()
        .isEqualTo(new ExporterStatus().exporterId("recordingExporter").status(StatusEnum.ENABLED));
  }

  private void waitUntilOperationIsApplied(final PlannedOperationsResponse response) {
    Awaitility.await()
        .timeout(Duration.ofSeconds(30))
        .untilAsserted(() -> ClusterActuatorAssert.assertThat(cluster).hasAppliedChanges(response));
  }
}
