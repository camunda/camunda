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
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.qa.util.topology.ClusterActuatorAssert;
import io.camunda.zeebe.test.util.asserts.TopologyAssert;
import java.time.Duration;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@ZeebeIntegration
@Timeout(2 * 60)
final class ScaleDownBrokersTest {
  private static final int PARTITIONS_COUNT = 3;
  private static final String JOB_TYPE = "job";
  private static final int CLUSTER_SIZE = 3;
  @AutoClose CamundaClient camundaClient;

  @TestZeebe
  private final TestCluster cluster =
      TestCluster.builder()
          .useRecordingExporter(true)
          // Use standalone gateway because we will be shutting down some of the brokers
          // during the test
          .withGatewaysCount(1)
          .withEmbeddedGateway(false)
          .withBrokersCount(CLUSTER_SIZE)
          .withPartitionsCount(PARTITIONS_COUNT)
          .withReplicationFactor(1)
          .withBrokerConfig(
              b ->
                  b.brokerConfig()
                      .getCluster()
                      .getMembership()
                      // Decrease the timeouts for fast convergence of broker topology.
                      .setSyncInterval(Duration.ofSeconds(1)))
          .withGatewayConfig(
              g ->
                  g.gatewayConfig()
                      .getCluster()
                      .getMembership()
                      // Decrease the timeouts for fast convergence of gateway topology. When the
                      // broker is shutdown, the topology update takes at least 10 seconds with the
                      // default values.
                      .setSyncInterval(Duration.ofSeconds(1))
                      .setFailureTimeout(Duration.ofSeconds(2)))
          .build();

  @BeforeEach
  void createClient() {
    camundaClient = cluster.availableGateway().newClientBuilder().preferRestOverGrpc(false).build();
  }

  @Test
  void shouldScaleDownCluster() {
    // given
    final int brokerToShutdownId = CLUSTER_SIZE - 1;
    final var brokerToShutdown =
        cluster.brokers().get(MemberId.from(String.valueOf(brokerToShutdownId)));
    final var createdInstances =
        createInstanceWithAJobOnAllPartitions(camundaClient, JOB_TYPE, PARTITIONS_COUNT);

    // when
    final int newClusterSize = CLUSTER_SIZE - 1;
    scaleAndWait(cluster, newClusterSize);
    brokerToShutdown.close();

    // then
    // verify partition 2 is moved from broker 0 to 1
    ClusterActuatorAssert.assertThat(cluster)
        .brokerHasPartition(0, 1)
        .brokerHasPartition(0, 3)
        .brokerHasPartition(1, 2)
        .doesNotHaveBroker(brokerToShutdownId);

    // Changes are reflected in the topology returned by grpc query
    Awaitility.await()
        .untilAsserted(
            () ->
                TopologyAssert.assertThat(camundaClient.newTopologyRequest().send().join())
                    .hasLeaderForPartition(3, 0));
    cluster.awaitCompleteTopology(newClusterSize, PARTITIONS_COUNT, 1, Duration.ofSeconds(20));

    assertThatAllJobsCanBeCompleted(createdInstances, camundaClient, JOB_TYPE);
  }

  @Test
  void shouldScaleDownClusterAgain() {
    // given
    final var createdInstances =
        createInstanceWithAJobOnAllPartitions(camundaClient, JOB_TYPE, PARTITIONS_COUNT);

    scaleAndWait(cluster, CLUSTER_SIZE - 1);
    cluster.brokers().get(MemberId.from(String.valueOf(CLUSTER_SIZE - 1))).close();

    // when
    final int newClusterSize = CLUSTER_SIZE - 2;
    final int brokerToShutdownId = CLUSTER_SIZE - 2;
    final var brokerToShutdown =
        cluster.brokers().get(MemberId.from(String.valueOf(brokerToShutdownId)));
    scaleAndWait(cluster, newClusterSize);
    brokerToShutdown.close();

    // then
    // verify partition 2 is moved from broker 0 to 1
    ClusterActuatorAssert.assertThat(cluster)
        .brokerHasPartition(0, 1)
        .brokerHasPartition(0, 2)
        .brokerHasPartition(0, 3)
        .doesNotHaveBroker(brokerToShutdownId);

    // Changes are reflected in the topology returned by grpc query
    Awaitility.await()
        .untilAsserted(
            () ->
                TopologyAssert.assertThat(camundaClient.newTopologyRequest().send().join())
                    .hasLeaderForPartition(3, 0)
                    .hasLeaderForPartition(2, 0));
    cluster.awaitCompleteTopology(newClusterSize, PARTITIONS_COUNT, 1, Duration.ofSeconds(20));

    assertThatAllJobsCanBeCompleted(createdInstances, camundaClient, JOB_TYPE);
  }
}
