/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.clustering.dynamic;

import static io.camunda.zeebe.it.clustering.dynamic.Utils.assertThatAllJobsCanBeCompleted;
import static io.camunda.zeebe.it.clustering.dynamic.Utils.createInstanceWithAJobOnAllPartitions;
import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.qa.util.actuator.ClusterActuator;
import io.camunda.zeebe.qa.util.cluster.TestApplication;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.topology.ClusterActuatorAssert;
import java.time.Duration;
import java.util.List;
import java.util.stream.IntStream;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@Timeout(2 * 60)
class ForceScaleDownBrokersTest {
  private static final int PARTITIONS_COUNT = 1;
  private static final String JOB_TYPE = "job";

  @ParameterizedTest
  @CsvSource({"2, 1", "4, 2", "6, 3"})
  void shouldForceRemoveBrokers(final int oldClusterSize, final int newClusterSize) {
    // given
    try (final var cluster = createCluster(oldClusterSize, oldClusterSize);
        final var camundaClient = cluster.availableGateway().newClientBuilder().build()) {
      final var brokersToShutdown =
          IntStream.range(newClusterSize, oldClusterSize)
              .mapToObj(String::valueOf)
              .map(MemberId::from)
              .map(cluster.brokers()::get)
              .toList();

      final var brokersToKeep = IntStream.range(0, newClusterSize).boxed().toList();

      final var createdInstances =
          createInstanceWithAJobOnAllPartitions(camundaClient, JOB_TYPE, PARTITIONS_COUNT);

      // when
      brokersToShutdown.forEach(TestApplication::close);
      final var response =
          ClusterActuator.of(cluster.availableGateway()).scaleBrokers(brokersToKeep, false, true);
      Awaitility.await()
          .timeout(Duration.ofMinutes(2))
          .untilAsserted(
              () -> ClusterActuatorAssert.assertThat(cluster).hasAppliedChanges(response));

      // then
      brokersToKeep.forEach(
          brokerId -> ClusterActuatorAssert.assertThat(cluster).brokerHasPartition(brokerId, 1));
      brokersToShutdown.forEach(
          brokerId ->
              ClusterActuatorAssert.assertThat(cluster)
                  .doesNotHaveBroker(brokerId.brokerConfig().getCluster().getNodeId()));

      // Changes are reflected in the topology returned by grpc query
      cluster.awaitCompleteTopology(
          newClusterSize, PARTITIONS_COUNT, newClusterSize, Duration.ofSeconds(20));

      assertThatAllJobsCanBeCompleted(createdInstances, camundaClient, JOB_TYPE);
    }
  }

  TestCluster createCluster(final int clusterSize, final int replicationFactor) {
    final var cluster =
        TestCluster.builder()
            .useRecordingExporter(true)
            // Use standalone gateway because we will be shutting down some of the brokers
            // during the test
            .withGatewaysCount(1)
            .withEmbeddedGateway(false)
            .withBrokersCount(clusterSize)
            .withBrokerConfig(broker -> broker.withCreateSchema(false))
            .withPartitionsCount(PARTITIONS_COUNT)
            .withReplicationFactor(replicationFactor)
            .withGatewayConfig(
                g ->
                    g.withCreateSchema(false)
                        .gatewayConfig()
                        .getCluster()
                        .getMembership()
                        // Decrease the timeouts for fast convergence of gateway topology. When the
                        // broker is shutdown, the topology update takes at least 10 seconds with
                        // the default values.
                        .setSyncInterval(Duration.ofSeconds(1))
                        .setFailureTimeout(Duration.ofSeconds(2)))
            .build()
            .start();
    cluster.awaitCompleteTopology();
    return cluster;
  }

  @Test
  void shouldForceRemoveBrokerWhenBrokerIsUp() {
    // given
    try (final var cluster = createCluster(2, 2)) {
      final var brokerToRemove = cluster.brokers().get(MemberId.from("1"));
      final var actuator = ClusterActuator.of(cluster.anyGateway());

      // when
      final var response = actuator.scaleBrokers(List.of(0), false, true);
      Awaitility.await()
          .timeout(Duration.ofMinutes(2))
          .untilAsserted(
              () -> ClusterActuatorAssert.assertThat(cluster).hasAppliedChanges(response));

      // then
      // Changes are reflected in the topology returned by grpc query
      cluster.awaitCompleteTopology(1, PARTITIONS_COUNT, 1, Duration.ofSeconds(20));

      assertThat(brokerToRemove.isStarted())
          .describedAs("Broker is shutdown because inconsistent topology is detected.")
          .isFalse();
    }
  }
}
