/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.exporter;

import static io.camunda.zeebe.test.StableValuePredicate.hasStableValue;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.zeebe.it.util.ZeebeResourcesHelper;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.actuator.ExportersActuator;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.qa.util.topology.ClusterActuatorAssert;
import io.camunda.zeebe.test.util.asserts.TopologyAssert;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(2 * 60) // 2 minutes
@ZeebeIntegration
final class ExporterDisableTest {
  private static final int PARTITIONS_COUNT = 2;

  @TestZeebe
  private final TestCluster cluster =
      TestCluster.builder()
          .useRecordingExporter(true)
          .withBrokersCount(3)
          .withPartitionsCount(PARTITIONS_COUNT)
          .withReplicationFactor(3)
          .withEmbeddedGateway(false)
          // We have to restart brokers in the test. So use standalone gateway to avoid potentially
          // accessing an unavailable broker
          .withGatewaysCount(1)
          .build();

  @AutoClose private CamundaClient client;
  private ExportersActuator actuator;

  @BeforeEach
  void setup() {
    client = cluster.newClientBuilder().build();
    actuator = ExportersActuator.of(cluster.availableGateway());

    final var deploymentKey =
        client
            .newDeployResourceCommand()
            .addProcessModel(
                Bpmn.createExecutableProcess("processId").startEvent().endEvent().done(),
                "process.bpmn")
            .send()
            .join()
            .getKey();

    new ZeebeResourcesHelper(client).waitUntilDeploymentIsDone(deploymentKey);
  }

  @Test
  void shouldDisableExportingOnAllPartitions() {
    // given
    generateEventsOnAllPartitions();

    final var recordsBeforeDisable =
        Awaitility.await()
            .atMost(Duration.ofSeconds(30))
            .during(Duration.ofSeconds(5))
            .until(RecordingExporter.getRecords()::size, hasStableValue());

    // when
    disableExporterAndWait();
    generateEventsOnAllPartitions();

    // then
    final var recordsAfterDisable =
        Awaitility.await()
            .atMost(Duration.ofSeconds(30))
            .during(Duration.ofSeconds(5))
            .until(RecordingExporter.getRecords()::size, hasStableValue());

    assertThat(recordsAfterDisable)
        .describedAs("No new records are exported after disabling the exporter.")
        .isEqualTo(recordsBeforeDisable);
  }

  @Test
  void exporterStaysDisabledAfterRestart() {
    // given
    generateEventsOnAllPartitions();

    final var recordsBeforeDisable =
        Awaitility.await()
            .atMost(Duration.ofSeconds(30))
            .during(Duration.ofSeconds(5))
            .until(RecordingExporter.getRecords()::size, hasStableValue());

    disableExporterAndWait();

    // when
    cluster.shutdown();
    cluster.start().awaitCompleteTopology();

    generateEventsOnAllPartitions();

    // then
    final var recordsAfterDisable =
        Awaitility.await()
            .atMost(Duration.ofSeconds(30))
            .during(Duration.ofSeconds(5))
            .until(RecordingExporter.getRecords()::size, hasStableValue());

    assertThat(recordsAfterDisable)
        .describedAs(
            "No new records are exported after disabling the exporter and restarting the cluster.")
        .isEqualTo(recordsBeforeDisable);
  }

  @Test
  void exporterStaysDisabledAfterLeaderChange() {
    // given
    generateEventsOnAllPartitions();

    final var recordsBeforeDisable =
        Awaitility.await()
            .atMost(Duration.ofSeconds(30))
            .during(Duration.ofSeconds(5))
            .until(RecordingExporter.getRecords()::size, hasStableValue());

    disableExporterAndWait();

    // when
    shutdownLeaderOfPartition2();
    generateEventsOnAllPartitions();

    // then
    final var recordsAfterDisable =
        Awaitility.await()
            .atMost(Duration.ofSeconds(30))
            .during(Duration.ofSeconds(5))
            .until(RecordingExporter.getRecords()::size, hasStableValue());

    assertThat(recordsAfterDisable)
        .describedAs("No new records are exported after disabling the exporter and leader change.")
        .isEqualTo(recordsBeforeDisable);
  }

  private void shutdownLeaderOfPartition2() {
    final TestStandaloneBroker brokerToStop = cluster.leaderForPartition(2);
    brokerToStop.stop();

    Awaitility.await()
        .timeout(Duration.ofSeconds(30))
        .untilAsserted(
            () ->
                TopologyAssert.assertThat(client.newTopologyRequest().send().join())
                    .doesNotContainBroker(Integer.parseInt(brokerToStop.nodeId().id())));

    Awaitility.await()
        .untilAsserted(
            () ->
                TopologyAssert.assertThat(client.newTopologyRequest().send().join())
                    .hasLeaderForEachPartition(PARTITIONS_COUNT));
  }

  private void generateEventsOnAllPartitions() {
    // create an instance on each partition. This works only because gateway round-robins the
    // request among available partitions.
    for (int i = 0; i < PARTITIONS_COUNT; i++) {
      client.newCreateInstanceCommand().bpmnProcessId("processId").latestVersion().send().join();
    }
  }

  private void disableExporterAndWait() {
    final var response = actuator.disableExporter("recordingExporter");

    Awaitility.await()
        .timeout(Duration.ofSeconds(30))
        .untilAsserted(() -> ClusterActuatorAssert.assertThat(cluster).hasAppliedChanges(response));
  }
}
