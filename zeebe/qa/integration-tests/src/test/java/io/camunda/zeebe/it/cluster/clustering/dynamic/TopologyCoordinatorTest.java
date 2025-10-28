/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.clustering.dynamic;

import static io.camunda.zeebe.it.cluster.clustering.dynamic.Utils.assertChangeIsPlanned;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.qa.util.actuator.ClusterActuator;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.qa.util.topology.ClusterActuatorAssert;
import java.time.Duration;
import java.util.List;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@ZeebeIntegration
@Timeout(2 * 60)
final class TopologyCoordinatorTest {
  private static final int PARTITIONS_COUNT = 3;
  private static final int CLUSTER_SIZE = 3;

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

  @Test
  void canHandleTopologyRequestsWhenBroker0IsRemoved() {
    // given
    final var broker0 = cluster.brokers().get(MemberId.from("0"));
    final var actuator = ClusterActuator.of(cluster.anyGateway());
    final var newBrokerSet = List.of(1, 2);
    final var response = actuator.scaleBrokers(newBrokerSet);
    assertChangeIsPlanned(response);

    Awaitility.await()
        .timeout(Duration.ofMinutes(2))
        .untilAsserted(() -> ClusterActuatorAssert.assertThat(cluster).hasAppliedChanges(response));
    // broker 0 is removed from cluster topology
    broker0.close();

    // when - then
    // No exception because the query will be forwarded to broker 1
    Awaitility.await("Query is forwarded to broker 1")
        .timeout(Duration.ofSeconds(30)) // give enough time for topology to be gossiped
        .ignoreExceptions() // query will fail if the broker is not reachable
        .untilAsserted(
            () ->
                ClusterActuatorAssert.assertThat(cluster)
                    .hasActiveBroker(1)
                    .hasActiveBroker(2)
                    .doesNotHaveBroker(0));
    // can also start a new topology change
    assertChangeIsPlanned(actuator.scaleBrokers(List.of(1, 2, 3)));
  }
}
