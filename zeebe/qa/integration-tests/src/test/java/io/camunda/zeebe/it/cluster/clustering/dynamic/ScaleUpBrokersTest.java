/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.clustering.dynamic;

import static io.camunda.zeebe.it.cluster.clustering.dynamic.Utils.assertThatAllJobsCanBeCompleted;
import static io.camunda.zeebe.it.cluster.clustering.dynamic.Utils.createInstanceWithAJobOnAllPartitions;
import static io.camunda.zeebe.it.cluster.clustering.dynamic.Utils.scaleAndWait;

import io.atomix.cluster.MemberId;
import io.camunda.client.CamundaClient;
import io.camunda.zeebe.qa.util.actuator.ClusterActuator;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.cluster.TestZeebePort;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.qa.util.topology.ClusterActuatorAssert;
import io.camunda.zeebe.test.util.asserts.TopologyAssert;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@ZeebeIntegration
final class ScaleUpBrokersTest {

  private static final int PARTITIONS_COUNT = 3;
  private static final String JOB_TYPE = "job";
  @AutoClose CamundaClient camundaClient;

  private final List<TestStandaloneBroker> newBrokers = new ArrayList<>();

  @TestZeebe
  private final TestCluster cluster =
      TestCluster.builder()
          .useRecordingExporter(true)
          .withEmbeddedGateway(true)
          .withBrokersCount(1)
          .withPartitionsCount(PARTITIONS_COUNT)
          .withReplicationFactor(1)
          .build();

  @BeforeEach
  void createClient() {
    camundaClient = cluster.availableGateway().newClientBuilder().build();
  }

  @AfterEach
  void shutdownBrokers() {
    newBrokers.forEach(TestStandaloneBroker::close);
    newBrokers.clear();
  }

  @Test
  void shouldScaleClusterByAddingOneBroker() {
    // given
    final int currentClusterSize = cluster.brokers().size();
    final int newClusterSize = currentClusterSize + 1;
    final int newBrokerId = newClusterSize - 1;

    final var processInstanceKeys =
        createInstanceWithAJobOnAllPartitions(camundaClient, JOB_TYPE, PARTITIONS_COUNT);

    // when
    createNewBroker(newClusterSize, newBrokerId);
    scaleAndWait(cluster, newClusterSize);

    // then
    // verify partition 2 is moved from broker 0 to 1
    ClusterActuatorAssert.assertThat(cluster)
        .brokerHasPartition(newBrokerId, 2)
        .brokerDoesNotHavePartition(0, 2)
        .brokerHasPartition(0, 1);

    // Changes are reflected in the topology returned by grpc query
    Awaitility.await()
        .untilAsserted(
            () ->
                TopologyAssert.assertThat(camundaClient.newTopologyRequest().send().join())
                    .hasLeaderForPartition(2, 1));
    cluster.awaitCompleteTopology(newClusterSize, 3, 1, Duration.ofSeconds(30));

    // then - verify the cluster can still process
    assertThatAllJobsCanBeCompleted(processInstanceKeys, camundaClient, JOB_TYPE);
  }

  @Test
  void shouldScaleUpAgain() {
    // given
    final int currentClusterSize = cluster.brokers().size();
    final int clusterSize2 = currentClusterSize + 1;
    final int broker2 = clusterSize2 - 1;

    // scale to clusterSize 2
    createNewBroker(clusterSize2, broker2);
    scaleAndWait(cluster, clusterSize2);

    // when - scale to clusterSize 3
    final int broker3 = broker2 + 1;
    final int finalClusterSize = clusterSize2 + 1;
    createNewBroker(finalClusterSize, broker3);
    scaleAndWait(cluster, finalClusterSize);

    // then -- partition 3 must be moved to new broker
    ClusterActuatorAssert.assertThat(cluster).brokerHasPartition(broker3, 3);

    // Changes are reflected in the topology returned by grpc query
    Awaitility.await()
        .untilAsserted(
            () ->
                TopologyAssert.assertThat(camundaClient.newTopologyRequest().send().join())
                    .hasLeaderForPartition(3, 2));
    cluster.awaitCompleteTopology(finalClusterSize, 3, 1, Duration.ofSeconds(30));
  }

  @Test
  void shouldScaleClusterByAddingMultipleBroker() {
    // given
    final int currentClusterSize = cluster.brokers().size();
    final int newClusterSize = currentClusterSize + 2;

    final var processInstanceKeys =
        createInstanceWithAJobOnAllPartitions(camundaClient, JOB_TYPE, PARTITIONS_COUNT);

    // when
    createNewBroker(newClusterSize, currentClusterSize);
    createNewBroker(newClusterSize, currentClusterSize + 1);
    scaleAndWait(cluster, newClusterSize);

    // then
    // Changes are reflected in the topology returned by grpc query
    Awaitility.await()
        .untilAsserted(
            () ->
                TopologyAssert.assertThat(camundaClient.newTopologyRequest().send().join())
                    .hasLeaderForPartition(2, 1)
                    .hasLeaderForPartition(3, 2));
    cluster.awaitCompleteTopology(newClusterSize, 3, 1, Duration.ofSeconds(30));

    // then - verify the cluster can still process
    assertThatAllJobsCanBeCompleted(processInstanceKeys, camundaClient, JOB_TYPE);
  }

  @Test
  void shouldScaleAfterRestart(@TempDir final Path dataDirectory) {
    // given
    final int currentClusterSize = cluster.brokers().size();
    final int newClusterSize = currentClusterSize + 1;
    final int newBrokerId = newClusterSize - 1;

    // Add new broker to the cluster without moving partitions
    final var newBroker = createNewBroker(newClusterSize, newBrokerId, Optional.of(dataDirectory));
    final var brokerAdded = ClusterActuator.of(cluster.availableGateway()).addBroker(newBrokerId);
    Awaitility.await()
        .timeout(Duration.ofMinutes(2))
        .untilAsserted(
            () -> ClusterActuatorAssert.assertThat(cluster).hasAppliedChanges(brokerAdded));

    // when
    newBroker.stop();

    // scale
    final var actuator = ClusterActuator.of(cluster.availableGateway());
    final var newBrokerSet = IntStream.range(0, newClusterSize).boxed().toList();
    final var response = actuator.scaleBrokers(newBrokerSet);

    newBroker.start();

    // then
    // Scale operation is completed after the broker is started
    Awaitility.await()
        .timeout(Duration.ofMinutes(2))
        .untilAsserted(() -> ClusterActuatorAssert.assertThat(cluster).hasAppliedChanges(response));
    // verify partition 2 is moved from broker 0 to 1
    ClusterActuatorAssert.assertThat(cluster)
        .brokerHasPartition(newBrokerId, 2)
        .brokerDoesNotHavePartition(0, 2)
        .brokerHasPartition(0, 1);

    // Changes are reflected in the topology returned by grpc query
    cluster.awaitCompleteTopology(newClusterSize, 3, 1, Duration.ofSeconds(30));
  }

  @Test
  void shouldScaleReplicationFactor() {
    // given
    final int currentClusterSize = cluster.brokers().size();
    final int newClusterSize = currentClusterSize + 1;
    final int newBrokerId = newClusterSize - 1;
    final var actuator = ClusterActuator.of(cluster.anyGateway());

    // when
    createNewBroker(newClusterSize, newBrokerId);
    final var response = actuator.scaleBrokers(List.of(0, newBrokerId), 2);
    Awaitility.await()
        .timeout(Duration.ofMinutes(2))
        .untilAsserted(() -> ClusterActuatorAssert.assertThat(cluster).hasAppliedChanges(response));

    // then
    // verify replicationFactor is increased
    IntStream.rangeClosed(1, PARTITIONS_COUNT)
        .forEach(
            partitionId ->
                ClusterActuatorAssert.assertThat(cluster)
                    .describedAs("Partition %d has two replicas", partitionId)
                    .brokerHasPartition(newBrokerId, partitionId)
                    .brokerHasPartition(0, partitionId));

    // Changes are reflected in the topology returned by grpc query
    cluster.awaitCompleteTopology(newClusterSize, PARTITIONS_COUNT, 2, Duration.ofSeconds(30));
  }

  private void createNewBroker(final int newClusterSize, final int newBrokerId) {
    createNewBroker(newClusterSize, newBrokerId, Optional.empty());
  }

  private TestStandaloneBroker createNewBroker(
      final int newClusterSize, final int newBrokerId, final Optional<Path> dataDirectory) {
    final var newBroker =
        new TestStandaloneBroker()
            .withUnifiedConfig(
                b -> {
                  b.getCluster().setSize(newClusterSize);
                  b.getCluster().setNodeId(newBrokerId);
                  b.getCluster()
                      .setInitialContactPoints(
                          List.of(
                              cluster
                                  .brokers()
                                  .get(MemberId.from("0"))
                                  .address(TestZeebePort.CLUSTER)));
                });
    dataDirectory.ifPresent(newBroker::withWorkingDirectory);
    newBrokers.add(newBroker);
    newBroker.start();
    return newBroker;
  }
}
