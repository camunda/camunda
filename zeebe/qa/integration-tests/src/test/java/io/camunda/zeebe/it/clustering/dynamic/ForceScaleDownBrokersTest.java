/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.clustering.dynamic;

import static io.camunda.zeebe.it.clustering.dynamic.Utils.assertThatAllJobsCanBeCompleted;
import static io.camunda.zeebe.it.clustering.dynamic.Utils.createInstanceWithAJobOnAllPartitions;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.qa.util.actuator.ClusterActuator;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.qa.util.topology.ClusterActuatorAssert;
import io.camunda.zeebe.test.util.junit.AutoCloseResources;
import io.camunda.zeebe.test.util.junit.AutoCloseResources.AutoCloseResource;
import java.time.Duration;
import java.util.List;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@ZeebeIntegration
@AutoCloseResources
@Timeout(2 * 60)
class ForceScaleDownBrokersTest {

  private static final int PARTITIONS_COUNT = 2;
  private static final int CLUSTER_SIZE = 2;
  private static final String JOB_TYPE = "job";
  @AutoCloseResource ZeebeClient zeebeClient;

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
          .withReplicationFactor(2)
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
    zeebeClient = cluster.availableGateway().newClientBuilder().build();
  }

  @Test
  void shouldForceRemoveBroker() {
    // given
    final var brokerToShutdown = cluster.brokers().get(MemberId.from("1"));
    final var actuator = ClusterActuator.of(cluster.anyGateway());

    final var createdInstances =
        createInstanceWithAJobOnAllPartitions(zeebeClient, JOB_TYPE, PARTITIONS_COUNT);

    // when
    brokerToShutdown.close();
    final var response = actuator.scaleBrokers(List.of(0), false, true);
    Awaitility.await()
        .timeout(Duration.ofMinutes(2))
        .untilAsserted(() -> ClusterActuatorAssert.assertThat(cluster).hasAppliedChanges(response));

    // then
    // verify broker 1 is removed from the cluster topology
    ClusterActuatorAssert.assertThat(cluster)
        .brokerHasPartition(0, 1)
        .brokerHasPartition(0, 2)
        .doesNotHaveBroker(1);

    // Changes are reflected in the topology returned by grpc query
    cluster.awaitCompleteTopology(1, PARTITIONS_COUNT, 1, Duration.ofSeconds(20));

    assertThatAllJobsCanBeCompleted(createdInstances, zeebeClient, JOB_TYPE);
  }
}
