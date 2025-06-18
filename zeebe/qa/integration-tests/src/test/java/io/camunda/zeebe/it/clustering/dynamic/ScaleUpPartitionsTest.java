/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.clustering.dynamic;

import static io.camunda.zeebe.it.clustering.dynamic.Utils.createInstanceWithAJobOnAllPartitions;
import static io.camunda.zeebe.it.clustering.dynamic.Utils.deployProcessModel;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.zeebe.management.cluster.ClusterConfigPatchRequest;
import io.camunda.zeebe.management.cluster.ClusterConfigPatchRequestPartitions;
import io.camunda.zeebe.management.cluster.PlannedOperationsResponse;
import io.camunda.zeebe.management.cluster.RequestHandlingAllPartitions;
import io.camunda.zeebe.qa.util.actuator.ClusterActuator;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Objects;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ZeebeIntegration
public class ScaleUpPartitionsTest {
  private static final Logger LOG = LoggerFactory.getLogger(ScaleUpPartitionsTest.class);

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
    final var desiredPartitionCount = PARTITIONS_COUNT + 1;
    cluster.awaitHealthyTopology();
    // when
    scaleToPartitions(desiredPartitionCount);

    awaitScaleUpCompletion(desiredPartitionCount);
    createInstanceWithAJobOnAllPartitions(camundaClient, JOB_TYPE, desiredPartitionCount);
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 3})
  public void shouldStartProcessInstancesDeployedBeforeScaleUp(final int partitionsToAdd) {
    // given
    final var desiredPartitionCount = PARTITIONS_COUNT + partitionsToAdd;
    cluster.awaitHealthyTopology();

    // when
    deployProcessModel(camundaClient, JOB_TYPE, "processId");

    scaleToPartitions(desiredPartitionCount);
    awaitScaleUpCompletion(desiredPartitionCount);

    for (int i = 0; i < 20; i++) {
      createInstanceWithAJobOnAllPartitions(
          camundaClient, JOB_TYPE, desiredPartitionCount, false, "processId");
    }

    cluster.awaitHealthyTopology();
  }

  @Test
  public void shouldStartProcessInstancesDeployedWhenScalingUp() {
    // given
    final var desiredPartitionCount = PARTITIONS_COUNT + 3;
    cluster.awaitHealthyTopology();

    deployProcessModel(camundaClient, JOB_TYPE, "baseProcess");
    // when
    scaleToPartitions(desiredPartitionCount);

    final var processIds = new ArrayList<String>();
    for (int i = 0; i < 10; i++) {
      final var id = "processId-" + i;
      processIds.add(id);
      // do not wait for the deployment to be distributed to all partitions
      final var deploymentKey = deployProcessModel(camundaClient, JOB_TYPE, id, false);
      LOG.debug("Deployed process model with id: {}, key: {}", id, deploymentKey);
    }

    awaitScaleUpCompletion(desiredPartitionCount);

    for (final var processId : processIds) {
      createInstanceWithAJobOnAllPartitions(
          camundaClient, JOB_TYPE, desiredPartitionCount, false, processId);
    }

    cluster.awaitHealthyTopology();
  }

  @Test
  private void awaitScaleUpCompletion(final int desiredPartitionCount) {
    Awaitility.await("until scaling is done")
        .timeout(Duration.ofMinutes(5))
        .untilAsserted(
            () -> {
              final var topology = clusterActuator.getTopology();
              if (Objects.requireNonNull(topology.getRouting().getRequestHandling())
                  instanceof final RequestHandlingAllPartitions allPartitions) {
                assertThat(allPartitions.getPartitionCount()).isEqualTo(desiredPartitionCount);
              } else {
                throw new AssertionError(
                    "Unexpected request handling mode: "
                        + topology.getRouting().getRequestHandling());
              }
            });
  }

  private PlannedOperationsResponse scaleToPartitions(final int desiredPartitionCount) {
    return clusterActuator.patchCluster(
        new ClusterConfigPatchRequest()
            .partitions(
                new ClusterConfigPatchRequestPartitions()
                    .count(desiredPartitionCount)
                    .replicationFactor(3)),
        false,
        false);
  }
}
