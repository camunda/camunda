/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.clustering.dynamic;

import static io.camunda.zeebe.it.clustering.dynamic.Utils.createInstanceWithAJobOnAllPartitions;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.zeebe.management.cluster.ClusterConfigPatchRequest;
import io.camunda.zeebe.management.cluster.ClusterConfigPatchRequestPartitions;
import io.camunda.zeebe.management.cluster.RequestHandlingAllPartitions;
import io.camunda.zeebe.qa.util.actuator.ClusterActuator;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.time.Duration;
import java.util.Objects;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
public class ScaleUpPartitionsTest {

  private static final int PARTITIONS_COUNT = 3;
  private static final String JOB_TYPE = "job";
  @AutoClose CamundaClient camundaClient;

  private ClusterActuator clusterActuator;

  @TestZeebe
  private final TestCluster cluster =
      TestCluster.builder()
          .useRecordingExporter(true)
          .withEmbeddedGateway(true)
          .withBrokersCount(3)
          .withPartitionsCount(PARTITIONS_COUNT)
          .withReplicationFactor(3)
          .withBrokerConfig(
              b ->
                  b.withBrokerConfig(
                      bb -> bb.getExperimental().getFeatures().setEnablePartitionScaling(true)))
          .build();

  @BeforeEach
  void createClient() {
    camundaClient = cluster.availableGateway().newClientBuilder().build();
    clusterActuator = ClusterActuator.of(cluster.availableGateway());
  }

  @Test
  void shouldDeployProcessesToNewPartitionsAndStartNewInstances() {
    // when
    final var response =
        clusterActuator.patchCluster(
            new ClusterConfigPatchRequest()
                .partitions(
                    new ClusterConfigPatchRequestPartitions().count(5).replicationFactor(3)),
            false,
            false);

    Awaitility.await("until scaling is done")
        .timeout(Duration.ofMinutes(5))
        .untilAsserted(
            () -> {
              final var topology = clusterActuator.getTopology();
              if (Objects.requireNonNull(topology.getRouting().getRequestHandling())
                  instanceof final RequestHandlingAllPartitions allPartitions) {
                assertThat(allPartitions.getPartitionCount()).isEqualTo(5);
              } else {
                throw new AssertionError(
                    "Unexpected request handling mode: "
                        + topology.getRouting().getRequestHandling());
              }
            });

    createInstanceWithAJobOnAllPartitions(camundaClient, JOB_TYPE, PARTITIONS_COUNT);
  }
}
