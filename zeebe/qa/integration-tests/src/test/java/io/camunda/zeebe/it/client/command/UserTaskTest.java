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
import io.camunda.zeebe.model.bpmn.builder.UserTaskBuilder;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.CommandDistributionIntent;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestZeebePort;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.junit.AutoCloseResources;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@ZeebeIntegration
@AutoCloseResources
public abstract class UserTaskTest {

  @TestZeebe(autoStart = false)
  private static final TestCluster CLUSTER =
      TestCluster.builder()
          .withEmbeddedGateway(false)
          .withGatewaysCount(1)
          .withBrokersCount(3)
          .withPartitionsCount(3)
          .withReplicationFactor(3)
          .build();

  protected long userTaskKey;
  protected ZeebeClient client;

  @BeforeAll
  static void init() {
    CLUSTER.start().awaitCompleteTopology();
  }

  @BeforeEach
  void initClientAndInstances() {
    final var gateway = CLUSTER.anyGateway();
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
    return createUserTasks(b -> {}, "{}", 1).getFirst();
  }

  protected List<Long> createUserTasks(
      final Consumer<UserTaskBuilder> consumer, final String variables, final int amount) {

    final BpmnModelInstance modelInstance = createSingleUserTaskModelInstance(consumer);
    final long processDefinitionKey = deployProcess(modelInstance);

    final var processInstanceKeys =
        IntStream.range(0, amount)
            .boxed()
            .map(i -> createProcessInstance(processDefinitionKey, variables))
            .toList();

    final List<Long> userTaskKeys =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .filter(r -> processInstanceKeys.contains(r.getValue().getProcessInstanceKey()))
            .limit(amount)
            .map(Record::getKey)
            .collect(Collectors.toList());

    assertThat(userTaskKeys).describedAs("Expected %d created user tasks", amount).hasSize(amount);

    return userTaskKeys;
  }

  protected long deployProcess(final BpmnModelInstance modelInstance) {
    final DeploymentEvent deploymentEvent =
        client
            .newDeployResourceCommand()
            .addProcessModel(modelInstance, "process.bpmn")
            .send()
            .join();
    //    waitUntilDeploymentIsDone(deploymentEvent.getKey());
    return deploymentEvent.getProcesses().get(0).getProcessDefinitionKey();
  }

  protected BpmnModelInstance createSingleUserTaskModelInstance(
      final Consumer<UserTaskBuilder> taskBuilderConsumer) {
    return Bpmn.createExecutableProcess("process")
        .startEvent("start")
        .userTask("task", taskBuilderConsumer)
        .zeebeUserTask()
        .endEvent("end")
        .done();
  }

  protected long createProcessInstance(final long processDefinitionKey, final String variables) {
    return client
        .newCreateInstanceCommand()
        .processDefinitionKey(processDefinitionKey)
        .variables(variables)
        .send()
        .join()
        .getProcessInstanceKey();
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
