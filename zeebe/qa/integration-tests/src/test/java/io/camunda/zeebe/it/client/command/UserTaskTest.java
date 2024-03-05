/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.client.command;

import static io.camunda.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.DeploymentEvent;
import io.camunda.zeebe.client.api.response.PartitionInfo;
import io.camunda.zeebe.client.api.response.Topology;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.CommandDistributionIntent;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestZeebePort;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.junit.AutoCloseResources;
import io.camunda.zeebe.test.util.junit.AutoCloseResources.AutoCloseResource;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;

@ZeebeIntegration
@AutoCloseResources
public abstract class UserTaskTest {

  @TestZeebe(clusterSize = 1, partitionCount = 1, replicationFactor = 1)
  private static final TestCluster CLUSTER =
      TestCluster.builder().withEmbeddedGateway(false).withGatewaysCount(1).build();

  protected long userTaskKey;

  @AutoCloseResource protected ZeebeClient client;

  @BeforeEach
  void initClientAndInstances() {
    final var gateway = CLUSTER.availableGateway();
    client =
        CLUSTER
            .newClientBuilder()
            .gatewayAddress(gateway.gatewayAddress())
            .gatewayRestApiPort(gateway.mappedPort(TestZeebePort.REST))
            .defaultRequestTimeout(Duration.ofSeconds(15))
            .build();
    userTaskKey = createSingleUserTask();
  }

  protected long createSingleUserTask() {
    final var modelInstance = createSingleUserTaskModelInstance();
    final var processDefinitionKey = deployProcess(modelInstance);
    final var processInstanceKey = createProcessInstance(processDefinitionKey);
    final var userTaskKey =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .filter(r -> processInstanceKey == r.getValue().getProcessInstanceKey())
            .map(Record::getKey)
            .findFirst()
            .orElse(-1L);

    assertThat(userTaskKey).describedAs("Expected a created user task").isGreaterThan(0L);

    return userTaskKey;
  }

  protected long deployProcess(final BpmnModelInstance modelInstance) {
    final DeploymentEvent deploymentEvent =
        client
            .newDeployResourceCommand()
            .addProcessModel(modelInstance, "process.bpmn")
            .send()
            .join();
    waitUntilDeploymentIsDone(deploymentEvent.getKey());
    return deploymentEvent.getProcesses().getFirst().getProcessDefinitionKey();
  }

  protected BpmnModelInstance createSingleUserTaskModelInstance() {
    return Bpmn.createExecutableProcess("process")
        .startEvent("start")
        .userTask("task")
        .zeebeUserTask()
        .endEvent("end")
        .done();
  }

  protected long createProcessInstance(final long processDefinitionKey) {
    return client
        .newCreateInstanceCommand()
        .processDefinitionKey(processDefinitionKey)
        .send()
        .join()
        .getProcessInstanceKey();
  }

  protected void waitUntilDeploymentIsDone(final long key) {
    if (getPartitions().size() > 1) {
      waitUntil(
          () ->
              RecordingExporter.commandDistributionRecords()
                  .withDistributionIntent(DeploymentIntent.CREATE)
                  .withRecordKey(key)
                  .withIntent(CommandDistributionIntent.FINISHED)
                  .exists());
    } else {
      waitUntil(
          () ->
              RecordingExporter.deploymentRecords()
                  .withIntent(DeploymentIntent.CREATED)
                  .withRecordKey(key)
                  .exists());
    }
  }

  protected List<Integer> getPartitions() {
    final Topology topology = client.newTopologyRequest().send().join();

    return topology.getBrokers().stream()
        .flatMap(i -> i.getPartitions().stream())
        .filter(PartitionInfo::isLeader)
        .map(PartitionInfo::getPartitionId)
        .collect(Collectors.toList());
  }
}
