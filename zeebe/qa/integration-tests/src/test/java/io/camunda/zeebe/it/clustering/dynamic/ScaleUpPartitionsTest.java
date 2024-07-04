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

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.it.util.ZeebeResourcesHelper;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.qa.util.actuator.ClusterActuator;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.qa.util.topology.ClusterActuatorAssert;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(2 * 60) // 2 minutes
@ZeebeIntegration
final class ScaleUpPartitionsTest {
  private static final int BROKER_COUNT = 3;
  private static final int PARTITION_COUNT = 1;

  @TestZeebe
  private final TestCluster cluster =
      TestCluster.builder()
          .withGatewaysCount(1)
          .withGatewayConfig(
              g ->
                  g.gatewayConfig()
                      .getCluster()
                      .getMembership()
                      // Reduce timeouts so that the test is faster
                      .setProbeInterval(Duration.ofMillis(100))
                      .setFailureTimeout(Duration.ofSeconds(2)))
          .withEmbeddedGateway(false)
          .withBrokersCount(BROKER_COUNT)
          .withPartitionsCount(PARTITION_COUNT)
          .withReplicationFactor(3)
          .build();

  private ZeebeClient client;
  private ClusterActuator actuator;

  @BeforeEach
  void setup() {
    client = cluster.newClientBuilder().build();
    actuator = ClusterActuator.of(cluster.availableGateway());
  }

  @AfterEach
  void tearDown() {
    client.close();
  }

  @Test
  void canCreateInstancesOnNewPartitions() {
    // when
    final var response = actuator.scalePartitions(2);
    Awaitility.await()
        .untilAsserted(
            () -> ClusterActuatorAssert.assertThat(actuator).hasAppliedChanges(response));

    // then
    // can create instance on all partitions
    final var processInstanceKeys = createInstanceWithAJobOnAllPartitions(client, "test-job", 2);

    assertThatAllJobsCanBeCompleted(processInstanceKeys, client, "test-job");
  }

  @Test
  void canCorrelateMessagesAfterScaleUp() {
    final var messageName = "scaleup-test";
    // publish several messages with different correlation key
    IntStream.range(0, 10)
        .forEach(
            i -> {
              client
                  .newPublishMessageCommand()
                  .messageName(messageName)
                  .correlationKey("test" + i)
                  .timeToLive(Duration.ofMinutes(5))
                  .send()
                  .join();
            });

    final var response = actuator.scalePartitions(2);
    Awaitility.await()
        .untilAsserted(
            () -> ClusterActuatorAssert.assertThat(actuator).hasAppliedChanges(response));

    final var processInstanceKeys =
        createInstanceWithAMessageAndJobOnAllPartitions(client, messageName, "test-job", 2);

    assertThatAllJobsCanBeCompleted(processInstanceKeys, client, "test-job");
  }

  private void deployProcessWithMessageFollowedByJob(
      final String messageName, final String jobType) {
    final var process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .intermediateCatchEvent()
            .message(m -> m.name(messageName).zeebeCorrelationKeyExpression("key"))
            .serviceTask("task", t -> t.zeebeJobType(jobType))
            .endEvent("end")
            .done();

    final var deploymentKey =
        client
            .newDeployResourceCommand()
            .addProcessModel(process, "process.bpmn")
            .send()
            .join()
            .getKey();
    new ZeebeResourcesHelper(client).waitUntilDeploymentIsDone(deploymentKey);
  }

  List<Long> createInstanceWithAMessageAndJobOnAllPartitions(
      final ZeebeClient zeebeClient,
      final String messageName,
      final String jobType,
      final int partitionsCount) {
    deployProcessWithMessageFollowedByJob(messageName, jobType);

    final AtomicInteger correlationKeyIter = new AtomicInteger();

    final List<Long> createdProcessInstances = new ArrayList<>();
    Awaitility.await("Process instances are created in all partitions")
        // Might throw exception when a partition has not yet received deployment distribution
        .ignoreExceptions()
        .timeout(Duration.ofSeconds(20))
        .until(
            () -> {
              final var result =
                  zeebeClient
                      .newCreateInstanceCommand()
                      .bpmnProcessId("process")
                      .latestVersion()
                      .variables(Map.of("key", "test" + correlationKeyIter.incrementAndGet()))
                      .send()
                      .join();
              createdProcessInstances.add(result.getProcessInstanceKey());
              // repeat until all partitions have atleast one process instance
              return createdProcessInstances.stream()
                      .map(Protocol::decodePartitionId)
                      .distinct()
                      .count()
                  == partitionsCount;
            });

    return createdProcessInstances;
  }
}
