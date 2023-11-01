/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.clustering.dynamic;

import static io.camunda.zeebe.it.clustering.dynamic.Utils.assertBrokerDoesNotHavePartition;
import static io.camunda.zeebe.it.clustering.dynamic.Utils.assertBrokerHasPartition;
import static io.camunda.zeebe.it.clustering.dynamic.Utils.assertChangeIsApplied;
import static io.camunda.zeebe.it.clustering.dynamic.Utils.assertChangeIsPlanned;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.qa.util.actuator.ClusterActuator;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.cluster.TestZeebePort;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.time.Duration;
import java.util.List;
import java.util.stream.IntStream;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
class ScaleUpBrokersTest {

  @TestZeebe
  private static final TestCluster CLUSTER =
      TestCluster.builder()
          .useRecordingExporter(true)
          .withEmbeddedGateway(true)
          .withBrokersCount(2)
          .withPartitionsCount(3)
          .withReplicationFactor(1)
          .withBrokerConfig(
              b ->
                  b.brokerConfig()
                      .getExperimental()
                      .getFeatures()
                      .setEnableDynamicClusterTopology(true))
          .build();

  @Test
  void shouldScaleClusterByAddingOneBroker() {
    // given
    final int currentClusterSize = CLUSTER.brokers().size();
    final int newClusterSize = currentClusterSize + 1;
    final int newBrokerId = newClusterSize - 1;
    try (final var newBroker = createNewBroker(newClusterSize, newBrokerId).start()) {

      final var actuator = ClusterActuator.of(CLUSTER.availableGateway());
      final var newBrokerSet = IntStream.range(0, currentClusterSize + 1).boxed().toList();

      // when
      final var response = actuator.scaleBrokers(newBrokerSet);
      assertChangeIsPlanned(response);

      // then
      Awaitility.await()
          .timeout(Duration.ofMinutes(2))
          .untilAsserted(() -> assertChangeIsApplied(CLUSTER, response));

      // verify partition 3 is moved from broker 0 to 2
      assertBrokerHasPartition(CLUSTER, newBrokerId, 3);
      assertBrokerDoesNotHavePartition(CLUSTER, 0, 3);
      // verify partition 2 remains in broker 1
      assertBrokerHasPartition(CLUSTER, 1, 2);

      // Changes are reflected in the topology returned by grpc query
      CLUSTER.awaitCompleteTopology(newClusterSize, 3, 1, Duration.ofSeconds(10));
    }
  }

  private static TestStandaloneBroker createNewBroker(
      final int newClusterSize, final int newBrokerId) {
    return new TestStandaloneBroker()
        .withBrokerConfig(
            b -> {
              b.getExperimental().getFeatures().setEnableDynamicClusterTopology(true);
              b.getCluster().setClusterSize(newClusterSize);
              b.getCluster().setNodeId(newBrokerId);
              b.getCluster()
                  .setInitialContactPoints(
                      List.of(
                          CLUSTER
                              .brokers()
                              .get(MemberId.from("0"))
                              .address(TestZeebePort.CLUSTER)));
            });
  }
}
