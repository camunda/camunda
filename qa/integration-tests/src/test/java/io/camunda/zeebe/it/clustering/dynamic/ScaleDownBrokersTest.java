/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.clustering.dynamic;

import static io.camunda.zeebe.it.clustering.dynamic.Utils.assertChangeIsPlanned;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.qa.util.actuator.ClusterActuator;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.qa.util.topology.ClusterActuatorAssert;
import io.camunda.zeebe.test.util.junit.AutoCloseResources;
import io.camunda.zeebe.test.util.junit.AutoCloseResources.AutoCloseResource;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;
import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@ZeebeIntegration
@AutoCloseResources
@Timeout(2 * 60)
final class ScaleDownBrokersTest {
  private static final int PARTITIONS_COUNT = 3;
  private static final String JOB_TYPE = "job";
  private static final int CLUSTER_SIZE = 3;
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
          .withBrokerConfig(
              b ->
                  b.brokerConfig()
                      .getExperimental()
                      .getFeatures()
                      .setEnableDynamicClusterTopology(true))
          .build();

  @BeforeEach
  void createClient() {
    zeebeClient = cluster.availableGateway().newClientBuilder().build();
  }

  @Test
  void shouldScaleDownCluster() {
    // given
    final int brokerToShutdownId = CLUSTER_SIZE - 1;
    final var brokerToShutdown =
        cluster.brokers().get(MemberId.from(String.valueOf(brokerToShutdownId)));
    final var createdInstances = createInstanceWithAJobOnAllPartitions();

    // when
    final int newClusterSize = CLUSTER_SIZE - 1;
    scaleDown(newClusterSize);
    brokerToShutdown.close();

    // then
    // verify partition 2 is moved from broker 0 to 1
    ClusterActuatorAssert.assertThat(cluster).brokerHasPartition(0, 1);
    ClusterActuatorAssert.assertThat(cluster).brokerHasPartition(0, 3);
    ClusterActuatorAssert.assertThat(cluster).brokerHasPartition(1, 2);
    ClusterActuatorAssert.assertThat(cluster).doesNotHaveBroker(brokerToShutdownId);

    // Changes are reflected in the topology returned by grpc query
    cluster.awaitCompleteTopology(newClusterSize, PARTITIONS_COUNT, 1, Duration.ofSeconds(10));

    assertThatAllJobsCanBeCompleted(createdInstances);
  }

  @Test
  void shouldScaleDownClusterAgain() {
    // given
    final var createdInstances = createInstanceWithAJobOnAllPartitions();

    scaleDown(CLUSTER_SIZE - 1);
    cluster.brokers().get(MemberId.from(String.valueOf(CLUSTER_SIZE - 1))).close();

    // when
    final int newClusterSize = CLUSTER_SIZE - 2;
    final int brokerToShutdownId = CLUSTER_SIZE - 2;
    final var brokerToShutdown =
        cluster.brokers().get(MemberId.from(String.valueOf(brokerToShutdownId)));
    scaleDown(newClusterSize);
    brokerToShutdown.close();

    // then
    // verify partition 2 is moved from broker 0 to 1
    ClusterActuatorAssert.assertThat(cluster).brokerHasPartition(0, 1);
    ClusterActuatorAssert.assertThat(cluster).brokerHasPartition(0, 2);
    ClusterActuatorAssert.assertThat(cluster).brokerHasPartition(0, 3);
    ClusterActuatorAssert.assertThat(cluster).doesNotHaveBroker(brokerToShutdownId);

    // Changes are reflected in the topology returned by grpc query
    cluster.awaitCompleteTopology(newClusterSize, PARTITIONS_COUNT, 1, Duration.ofSeconds(10));

    assertThatAllJobsCanBeCompleted(createdInstances);
  }

  private void scaleDown(final int newClusterSize) {
    final var actuator = ClusterActuator.of(cluster.availableGateway());
    final var newBrokerSet = IntStream.range(0, newClusterSize).boxed().toList();

    // when
    final var response = actuator.scaleBrokers(newBrokerSet);
    assertChangeIsPlanned(response);

    // then - verify topology changes are completed
    Awaitility.await()
        .timeout(Duration.ofMinutes(2))
        .untilAsserted(() -> ClusterActuatorAssert.assertThat(cluster).hasAppliedChanges(response));
  }

  private void assertThatAllJobsCanBeCompleted(final List<Long> processInstanceKeys) {
    final var jobs =
        zeebeClient
            .newActivateJobsCommand()
            .jobType(JOB_TYPE)
            .maxJobsToActivate(2 * processInstanceKeys.size())
            .send()
            .join();

    Assertions.assertThat(jobs.getJobs().stream().map(ActivatedJob::getProcessInstanceKey).toList())
        .describedAs("Jobs from all partitions can be activated")
        .containsExactlyInAnyOrderElementsOf(processInstanceKeys);
    final var jobCompleteFutures =
        jobs.getJobs().stream()
            .map(job -> zeebeClient.newCompleteCommand(job).send().toCompletableFuture())
            .toList();
    Assertions.assertThat(
            CompletableFuture.allOf(jobCompleteFutures.toArray(CompletableFuture[]::new)))
        .describedAs("All jobs can be completed")
        .succeedsWithin(Duration.ofSeconds(2));
  }

  private List<Long> createInstanceWithAJobOnAllPartitions() {
    final var process =
        Bpmn.createExecutableProcess("processId")
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType(JOB_TYPE))
            .endEvent()
            .done();
    zeebeClient.newDeployResourceCommand().addProcessModel(process, "process.bpmn").send().join();

    final List<Long> createdProcessInstances = new ArrayList<>();
    Awaitility.await("Process instances are created in all partitions")
        .ignoreExceptions() // Might throw exception when a partition has not yet received
        // deployment distribution
        .until(
            () -> {
              final var result =
                  zeebeClient
                      .newCreateInstanceCommand()
                      .bpmnProcessId("processId")
                      .latestVersion()
                      .send()
                      .join();
              createdProcessInstances.add(result.getProcessInstanceKey());
              // repeat until all partitions have atleast one process instance
              return createdProcessInstances.stream()
                      .map(Protocol::decodePartitionId)
                      .distinct()
                      .count()
                  == PARTITIONS_COUNT;
            });

    return createdProcessInstances;
  }
}
