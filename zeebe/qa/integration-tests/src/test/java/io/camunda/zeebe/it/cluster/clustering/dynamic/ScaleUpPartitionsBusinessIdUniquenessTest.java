/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.clustering.dynamic;

import static io.camunda.zeebe.it.cluster.clustering.dynamic.Utils.DEFAULT_PROCESS_ID;
import static io.camunda.zeebe.it.cluster.clustering.dynamic.Utils.deployProcessModel;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ClientStatusException;
import io.camunda.zeebe.management.cluster.ClusterConfigPatchRequest;
import io.camunda.zeebe.management.cluster.ClusterConfigPatchRequestPartitions;
import io.camunda.zeebe.management.cluster.RequestHandlingAllPartitions;
import io.camunda.zeebe.qa.util.actuator.ClusterActuator;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.time.Duration;
import java.util.List;
import java.util.stream.IntStream;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@ZeebeIntegration
public class ScaleUpPartitionsBusinessIdUniquenessTest {

  private static final int PARTITIONS_COUNT = 3;
  private static final String JOB_TYPE = "job";
  private static final String PROCESS_ID = DEFAULT_PROCESS_ID;

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
                  b.withUnifiedConfig(
                      cfg -> {
                        cfg.getProcessInstanceCreation().setBusinessIdUniquenessEnabled(true);

                        final var membership = cfg.getCluster().getMembership();
                        membership.setSyncInterval(Duration.ofSeconds(1));
                        membership.setGossipInterval(Duration.ofMillis(500));

                        final var distribution = cfg.getProcessing().getEngine().getDistribution();
                        distribution.setMaxBackoffDuration(Duration.ofSeconds(1));
                        distribution.setRedistributionInterval(Duration.ofMillis(200));
                      }))
          .build();

  @BeforeEach
  void setUp() {
    camundaClient = cluster.availableGateway().newClientBuilder().build();
    clusterActuator = ClusterActuator.of(cluster.availableGateway());
  }

  @Test
  void shouldEnforceBusinessIdUniquenessAfterScalingUp() {
    // given -- deploy a process and create 10 instances with distinct business IDs
    cluster.awaitHealthyTopology();
    deployProcessModel(camundaClient, JOB_TYPE, PROCESS_ID);

    final List<String> initialBusinessIds =
        IntStream.range(0, 10).mapToObj(i -> "biz-initial-" + i).toList();

    for (final var bizId : initialBusinessIds) {
      final var result =
          camundaClient
              .newCreateInstanceCommand()
              .bpmnProcessId(PROCESS_ID)
              .latestVersion()
              .businessId(bizId)
              .execute();
      assertThat(result.getProcessInstanceKey()).isPositive();
    }

    // when -- scale from 3 to 4 partitions
    final var desiredPartitionCount = PARTITIONS_COUNT + 1;
    scaleToPartitions(desiredPartitionCount);
    awaitScaleUpCompletion(desiredPartitionCount);

    // then -- duplicate business IDs are still rejected
    for (final var bizId : initialBusinessIds) {
      assertThatThrownBy(
              () ->
                  camundaClient
                      .newCreateInstanceCommand()
                      .bpmnProcessId(PROCESS_ID)
                      .latestVersion()
                      .businessId(bizId)
                      .execute())
          .isInstanceOf(ClientStatusException.class)
          .hasMessageContaining("ALREADY_EXISTS");
    }

    // and -- new business IDs still work
    final List<String> newBusinessIds =
        IntStream.range(0, 10).mapToObj(i -> "biz-new-" + i).toList();

    for (final var bizId : newBusinessIds) {
      final var result =
          camundaClient
              .newCreateInstanceCommand()
              .bpmnProcessId(PROCESS_ID)
              .latestVersion()
              .businessId(bizId)
              .execute();
      assertThat(result.getProcessInstanceKey()).isPositive();
    }
  }

  private void scaleToPartitions(
      @SuppressWarnings("SameParameterValue") final int desiredPartitionCount) {
    final var request =
        new ClusterConfigPatchRequest()
            .partitions(
                new ClusterConfigPatchRequestPartitions()
                    .count(desiredPartitionCount)
                    .replicationFactor(3));
    final var dryRun = false;
    final var force = false;
    clusterActuator.patchCluster(request, dryRun, force);
  }

  private void awaitScaleUpCompletion(
      @SuppressWarnings("SameParameterValue") final int desiredPartitionCount) {
    Awaitility.await("until scaling is done")
        .atMost(Duration.ofMinutes(2))
        .catchUncaughtExceptions()
        .pollInterval(Duration.ofMillis(500))
        .untilAsserted(
            () -> {
              final var topology = clusterActuator.getTopology();
              assertThat(topology.getRouting()).isNotNull();
              final var requestHandling = topology.getRouting().getRequestHandling();
              assertThat(requestHandling).isInstanceOf(RequestHandlingAllPartitions.class);
              final var allPartitions = (RequestHandlingAllPartitions) requestHandling;
              assertThat(allPartitions.getPartitionCount()).isEqualTo(desiredPartitionCount);
            });
  }
}
