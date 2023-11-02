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
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.qa.util.actuator.ClusterActuator;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.cluster.TestZeebePort;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.junit.AutoCloseResources.AutoCloseResource;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;
import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
class ScaleUpBrokersTest {

  private static final int PARTITIONS_COUNT = 3;

  @TestZeebe
  private static final TestCluster CLUSTER =
      TestCluster.builder()
          .useRecordingExporter(true)
          .withEmbeddedGateway(true)
          .withBrokersCount(2)
          .withPartitionsCount(PARTITIONS_COUNT)
          .withReplicationFactor(1)
          .withBrokerConfig(
              b ->
                  b.brokerConfig()
                      .getExperimental()
                      .getFeatures()
                      .setEnableDynamicClusterTopology(true))
          .build();

  private static final String JOB_TYPE = "job";

  @AutoCloseResource
  final ZeebeClient zeebeClient = CLUSTER.availableGateway().newClientBuilder().build();

  @Test
  void shouldScaleClusterByAddingOneBroker() {
    // given
    final int currentClusterSize = CLUSTER.brokers().size();
    final int newClusterSize = currentClusterSize + 1;
    final int newBrokerId = newClusterSize - 1;

    final var processInstanceKeys = createInstanceWithAJobOnAllPartitions();

    try (final var newBroker = createNewBroker(newClusterSize, newBrokerId).start()) {

      final var actuator = ClusterActuator.of(CLUSTER.availableGateway());
      final var newBrokerSet = IntStream.range(0, currentClusterSize + 1).boxed().toList();

      // when
      final var response = actuator.scaleBrokers(newBrokerSet);
      assertChangeIsPlanned(response);

      // then - verify topology changes are completed
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

      // then - verify the cluster can still process
      assertThatAllJobsCanBeCompleted(processInstanceKeys);
    }
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
