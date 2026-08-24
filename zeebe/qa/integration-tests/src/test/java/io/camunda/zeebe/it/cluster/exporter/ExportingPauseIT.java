/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.exporter;

import static io.camunda.zeebe.test.StableValuePredicate.hasStableValue;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.zeebe.it.util.ZeebeResourcesHelper;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.qa.util.restapi.ExportingRestClient;
import io.camunda.zeebe.test.util.asserts.TopologyAssert;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Covers pausing exporting across a leader change, which the restart-based tests in {@code
 * ExportingEndpointIT} cannot: a restart re-reads the exporting state during partition bootstrap,
 * whereas a leader change reuses the already-running partition and only closes and reopens the
 * exporter director. A pause that only reached the live director, and not the state the director
 * reopens from, resumes exporting here while the API still reports the cluster as paused.
 */
@Timeout(2 * 60) // 2 minutes
@ZeebeIntegration
final class ExportingPauseIT {
  private static final int PARTITIONS_COUNT = 2;
  private static final int MAX_CREATION_ATTEMPTS = 10 * PARTITIONS_COUNT;
  private static final String CLUSTER_ADMIN_USER = "cluster-operator";
  private static final String CLUSTER_ADMIN_PASSWORD = "cluster-secret";

  @TestZeebe
  private final TestCluster cluster =
      TestCluster.builder()
          .useRecordingExporter(true)
          .withBrokersCount(3)
          .withPartitionsCount(PARTITIONS_COUNT)
          .withReplicationFactor(3)
          .withEmbeddedGateway(false)
          // We have to stop a broker in the test. So use a standalone gateway to avoid potentially
          // accessing an unavailable broker
          .withGatewaysCount(1)
          .withNodeConfig(
              node ->
                  node.withProperty(
                          "camunda.security.cluster-admin.basic.users[0].name", CLUSTER_ADMIN_USER)
                      .withProperty(
                          "camunda.security.cluster-admin.basic.users[0].password",
                          CLUSTER_ADMIN_PASSWORD))
          .build();

  @AutoClose private CamundaClient client;
  private ExportingRestClient exportingClient;

  @BeforeEach
  void setup() {
    client = cluster.newClientBuilder().build();
    exportingClient =
        ExportingRestClient.of(
            cluster.availableGateway(), CLUSTER_ADMIN_USER, CLUSTER_ADMIN_PASSWORD);

    final var deploymentKey =
        client
            .newDeployResourceCommand()
            .addProcessModel(
                Bpmn.createExecutableProcess("processId").startEvent().endEvent().done(),
                "process.bpmn")
            .send()
            .join();

    new ZeebeResourcesHelper(client).waitUntilDeploymentIsDone(deploymentKey.getKey());
  }

  @Test
  void exportingStaysPausedAfterLeaderChange() {
    // given
    generateEventsOnAllPartitions();

    exportingClient.pause();

    final var recordsBeforeLeaderChange =
        Awaitility.await()
            .atMost(Duration.ofSeconds(30))
            .during(Duration.ofSeconds(5))
            .until(RecordingExporter.getRecords()::size, hasStableValue());

    // when
    shutdownLeaderOfPartition2();
    generateEventsOnAllPartitions();

    // then
    final var recordsAfterLeaderChange =
        Awaitility.await()
            .atMost(Duration.ofSeconds(30))
            .during(Duration.ofSeconds(5))
            .until(RecordingExporter.getRecords()::size, hasStableValue());

    assertThat(recordsAfterLeaderChange)
        .describedAs("No new records are exported after pausing exporting and a leader change.")
        .isEqualTo(recordsBeforeLeaderChange);
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
    // The gateway round-robins requests across partitions, but nothing guarantees that
    // PARTITIONS_COUNT requests land one per partition. Since the assertion compares record counts
    // before and after a leader change, a partition that never saw a record would let a resumed
    // exporter go unnoticed. So keep creating instances, deriving the partition from the returned
    // key, until every partition has produced at least one record.
    final Set<Integer> coveredPartitions = new HashSet<>();
    for (int attempt = 0; attempt < MAX_CREATION_ATTEMPTS; attempt++) {
      final var processInstanceKey =
          client
              .newCreateInstanceCommand()
              .bpmnProcessId("processId")
              .latestVersion()
              .send()
              .join()
              .getProcessInstanceKey();
      coveredPartitions.add(Protocol.decodePartitionId(processInstanceKey));

      if (coveredPartitions.size() == PARTITIONS_COUNT) {
        return;
      }
    }

    throw new AssertionError(
        "Expected to create a process instance on each of the %d partitions within %d attempts, but only reached %s"
            .formatted(PARTITIONS_COUNT, MAX_CREATION_ATTEMPTS, coveredPartitions));
  }
}
