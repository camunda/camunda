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
import io.camunda.zeebe.exporter.api.Exporter;
import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.exporter.api.context.Controller;
import io.camunda.zeebe.management.cluster.PlannedOperationsResponse;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.qa.util.actuator.ExportersActuator;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.qa.util.topology.ClusterActuatorAssert;
import io.camunda.zeebe.test.util.asserts.TopologyAssert;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.IntStream;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(2 * 60) // 2 minutes
@ZeebeIntegration
final class ExporterEnableTest {
  private static final int PARTITIONS_COUNT = 2;
  private static final String EXPORTER_ID_1 = "exporter-1";
  private static final String EXPORTER_ID_2 = "exporter-2";

  @TestZeebe
  private final TestCluster cluster =
      TestCluster.builder()
          .withBrokersCount(3)
          .withPartitionsCount(PARTITIONS_COUNT)
          .withReplicationFactor(3)
          .withEmbeddedGateway(false)
          // We have to restart brokers in the test. So use standalone gateway to avoid potentially
          // accessing an unavailable broker
          .withGatewaysCount(1)
          .withBrokerConfig(
              b ->
                  b.withExporter(
                          EXPORTER_ID_1,
                          config -> config.setClassName(TestExporter.class.getName()))
                      .withExporter(
                          EXPORTER_ID_2,
                          config -> config.setClassName(TestExporter.class.getName())))
          .build();

  @AutoClose private CamundaClient client;
  private ExportersActuator actuator;

  @BeforeEach
  void setup() {
    client = cluster.newClientBuilder().build();
    actuator = ExportersActuator.of(cluster.availableGateway());
  }

  @Test
  void shouldEnableExporterOnAllPartitions() {
    // given
    waitUntilOperationCompleted(actuator.disableExporter(EXPORTER_ID_2));
    TestExporter.RECORDS.get(EXPORTER_ID_2).clear();
    TestExporter.METADATA.get(EXPORTER_ID_2).clear();

    // when
    waitUntilOperationCompleted(actuator.enableExporter(EXPORTER_ID_2));
    createDeploymentOnAllPartitions("process-1");

    // then
    final var recordsAfterEnable =
        Awaitility.await()
            .atMost(Duration.ofSeconds(30))
            .during(Duration.ofSeconds(5))
            .until(TestExporter.RECORDS.get(EXPORTER_ID_2)::size, hasStableValue());

    assertThat(recordsAfterEnable).isNotZero();
    IntStream.rangeClosed(1, PARTITIONS_COUNT)
        .forEach(
            p ->
                assertThat(TestExporter.METADATA.get(EXPORTER_ID_2).get(p))
                    .describedAs("Exporter has exported from all partitions.")
                    .isGreaterThan(0L));
  }

  @Test
  void shouldEnableExporterOnAllPartitionsAndInitializeMetadata() {
    // given
    waitUntilOperationCompleted(actuator.disableExporter(EXPORTER_ID_2));
    TestExporter.RECORDS.get(EXPORTER_ID_2).clear();
    TestExporter.METADATA.get(EXPORTER_ID_2).clear();

    createDeploymentOnAllPartitions("process-1");
    Awaitility.await("Exporter 1 has exported all records")
        .atMost(Duration.ofSeconds(30))
        .during(Duration.ofSeconds(10))
        .until(TestExporter.RECORDS.get(EXPORTER_ID_1)::size, hasStableValue());

    // when
    final var response = actuator.enableExporter(EXPORTER_ID_2, EXPORTER_ID_1);
    waitUntilOperationCompleted(response);
    createDeploymentOnAllPartitions("process-2");

    // then
    Awaitility.await("Exporter 1 has exported all records")
        .atMost(Duration.ofSeconds(30))
        .during(Duration.ofSeconds(10))
        .until(TestExporter.RECORDS.get(EXPORTER_ID_1)::size, hasStableValue());
    Awaitility.await("Exporter 2 has exported all records")
        .atMost(Duration.ofSeconds(30))
        .during(Duration.ofSeconds(10))
        .until(TestExporter.RECORDS.get(EXPORTER_ID_1)::size, hasStableValue());

    IntStream.rangeClosed(1, PARTITIONS_COUNT)
        .forEach(
            p ->
                assertThat(TestExporter.METADATA.get(EXPORTER_ID_2).get(p))
                    .describedAs(
                        "Exporter 2 has same sequence position as Exporter 1 in partition %d", p)
                    .isEqualTo(TestExporter.METADATA.get(EXPORTER_ID_1).get(p)));
  }

  @Test
  void exporterStaysEnabledAfterLeaderChange() {
    // given
    waitUntilOperationCompleted(actuator.disableExporter(EXPORTER_ID_2));
    TestExporter.RECORDS.get(EXPORTER_ID_2).clear();
    TestExporter.METADATA.get(EXPORTER_ID_2).clear();

    // re-enable
    waitUntilOperationCompleted(actuator.enableExporter(EXPORTER_ID_2));

    // when
    shutdownLeaderOfPartition2();
    createDeploymentOnAllPartitions("process-1");

    // then
    Awaitility.await()
        .atMost(Duration.ofSeconds(30))
        .during(Duration.ofSeconds(5))
        .until(TestExporter.RECORDS.get(EXPORTER_ID_2)::size, hasStableValue());

    assertThat(TestExporter.METADATA.get(EXPORTER_ID_2).get(2))
        .describedAs("Exporter 2 has exported records from partition 2")
        .isGreaterThan(0);
  }

  private void waitUntilOperationCompleted(final PlannedOperationsResponse disableResponse) {
    Awaitility.await()
        .timeout(Duration.ofSeconds(30))
        .untilAsserted(
            () -> ClusterActuatorAssert.assertThat(cluster).hasAppliedChanges(disableResponse));
  }

  private void createDeploymentOnAllPartitions(final String processId) {
    // Deployment will be distributed to other partitions as well, ensuring all partitions have some
    // records to export.
    client
        .newDeployResourceCommand()
        .addProcessModel(
            Bpmn.createExecutableProcess(processId).startEvent().endEvent().done(), "process.bpmn")
        .send()
        .join();
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

  public static class TestExporter implements Exporter {
    static final Map<String, List<Record<?>>> RECORDS = new ConcurrentHashMap<>();
    static final Map<String, Map<Integer, Long>> METADATA = new ConcurrentHashMap<>();
    private Controller controller;
    private String exporterId;
    private int partitionId;

    @Override
    public void configure(final Context context) throws Exception {
      exporterId = context.getConfiguration().getId();
      partitionId = context.getPartitionId();
    }

    @Override
    public void open(final Controller controller) {
      this.controller = controller;
      final var metadata =
          controller.readMetadata().map(b -> ByteBuffer.wrap(b).getLong()).orElse(0L);
      METADATA.putIfAbsent(exporterId, new ConcurrentHashMap<>());
      METADATA.get(exporterId).put(partitionId, metadata);
      RECORDS.putIfAbsent(exporterId, new CopyOnWriteArrayList<>());
    }

    @Override
    public void export(final Record<?> record) {
      RECORDS.get(exporterId).add(record.copyOf());
      final var newSeqPos = METADATA.get(exporterId).compute(partitionId, (p, pos) -> pos + 1);
      controller.updateLastExportedRecordPosition(
          record.getPosition(), ByteBuffer.allocate(8).putLong(newSeqPos).array());
    }
  }
}
