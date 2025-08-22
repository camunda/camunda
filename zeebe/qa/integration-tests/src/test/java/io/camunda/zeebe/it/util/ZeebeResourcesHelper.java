/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.util;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.DeployResourceCommandStep1;
import io.camunda.client.api.response.DeploymentEvent;
import io.camunda.client.api.response.PartitionInfo;
import io.camunda.client.api.response.Topology;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.ServiceTaskBuilder;
import io.camunda.zeebe.model.bpmn.builder.UserTaskBuilder;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.CommandDistributionIntent;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.util.CloseableSilently;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.awaitility.Awaitility;

public class ZeebeResourcesHelper implements CloseableSilently {

  private final CamundaClient client;

  public ZeebeResourcesHelper(final CamundaClient client) {
    this.client = client;
  }

  @Override
  public void close() {
    client.close();
  }

  public void waitUntilDeploymentIsDone(final long key) {
    if (getPartitions().size() > 1) {
      Awaitility.await("deployment is distributed")
          .atMost(Duration.ofSeconds(getPartitions().size() * 10L))
          .until(
              () ->
                  RecordingExporter.commandDistributionRecords()
                      .withDistributionIntent(DeploymentIntent.CREATE)
                      .withRecordKey(key)
                      .withIntent(CommandDistributionIntent.FINISHED)
                      .exists());
    } else {
      Awaitility.await("deployment is created")
          .until(
              () ->
                  RecordingExporter.deploymentRecords()
                      .withIntent(DeploymentIntent.CREATED)
                      .withRecordKey(key)
                      .exists());
    }
  }

  public List<Integer> getPartitions() {
    final Topology topology = client.newTopologyRequest().send().join();

    return topology.getBrokers().stream()
        .flatMap(i -> i.getPartitions().stream())
        .filter(PartitionInfo::isLeader)
        .map(PartitionInfo::getPartitionId)
        .collect(Collectors.toList());
  }

  public long createSingleJob(final String type) {
    return createSingleJob(type, b -> {}, "{}");
  }

  public long createSingleJob(final String type, final Set<String> tags) {
    return createJobs(type, b -> {}, "{}", 1, tags).getFirst();
  }

  public long createSingleJob(final String type, final Consumer<ServiceTaskBuilder> consumer) {
    return createSingleJob(type, consumer, "{}");
  }

  public long createSingleJob(
      final String type, final Consumer<ServiceTaskBuilder> consumer, final String variables) {
    return createJobs(type, consumer, variables, 1, Collections.emptySet()).get(0);
  }

  public List<Long> createJobs(final String type, final int amount) {
    return createJobs(type, b -> {}, "{}", amount, Collections.emptySet());
  }

  public List<Long> createJobs(
      final String type,
      final Consumer<ServiceTaskBuilder> consumer,
      final String variables,
      final int amount) {
    return createJobs(type, b -> {}, variables, amount, Collections.emptySet());
  }

  public List<Long> createJobs(
      final String type,
      final Consumer<ServiceTaskBuilder> consumer,
      final String variables,
      final int amount,
      final Set<String> tags) {

    final BpmnModelInstance modelInstance = createSingleJobModelInstance(type, consumer);
    final long processDefinitionKey = deployProcess(modelInstance);

    final var processInstanceKeys =
        IntStream.range(0, amount)
            .boxed()
            .map(i -> createProcessInstance(processDefinitionKey, variables, tags))
            .toList();

    final List<Long> jobKeys =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withType(type)
            .filter(r -> processInstanceKeys.contains(r.getValue().getProcessInstanceKey()))
            .limit(amount)
            .map(Record::getKey)
            .collect(Collectors.toList());

    assertThat(jobKeys).describedAs("Expected %d created jobs", amount).hasSize(amount);

    return jobKeys;
  }

  public BpmnModelInstance createSingleJobModelInstance(
      final String jobType, final Consumer<ServiceTaskBuilder> taskBuilderConsumer) {
    return Bpmn.createExecutableProcess("process")
        .startEvent("start")
        .serviceTask(
            "task",
            t -> {
              t.zeebeJobType(jobType);
              taskBuilderConsumer.accept(t);
            })
        .endEvent("end")
        .done();
  }

  public long deployProcess(final BpmnModelInstance modelInstance, final boolean useRest) {
    return deployProcess(modelInstance, "", useRest);
  }

  public long deployProcess(final BpmnModelInstance modelInstance) {
    return deployProcess(modelInstance, "");
  }

  public long deployProcess(final BpmnModelInstance modelInstance, final String tenantId) {
    return deployProcess(modelInstance, tenantId, false);
  }

  public long deployProcess(
      final BpmnModelInstance modelInstance, final String tenantId, final boolean useRest) {
    final DeploymentEvent deploymentEvent =
        getDeployCommand(useRest)
            .addProcessModel(modelInstance, "process.bpmn")
            .tenantId(tenantId)
            .send()
            .join();
    waitUntilDeploymentIsDone(deploymentEvent.getKey());
    return deploymentEvent.getProcesses().getFirst().getProcessDefinitionKey();
  }

  private DeployResourceCommandStep1 getDeployCommand(final boolean useRest) {
    final DeployResourceCommandStep1 deployResourceCommand = client.newDeployResourceCommand();
    return useRest ? deployResourceCommand.useRest() : deployResourceCommand.useGrpc();
  }

  public long createProcessInstance(final long processDefinitionKey, final String variables) {
    return createProcessInstance(processDefinitionKey, variables, "");
  }

  public long createProcessInstance(
      final long processDefinitionKey, final String variables, final Set<String> tags) {
    return createProcessInstance(processDefinitionKey, variables, "", tags);
  }

  public long createProcessInstance(
      final long processDefinitionKey, final String variables, final String tenantId) {
    return createProcessInstance(processDefinitionKey, variables, tenantId, Collections.emptySet());
  }

  public long createProcessInstance(
      final long processDefinitionKey,
      final String variables,
      final String tenantId,
      final Set<String> tags) {
    return client
        .newCreateInstanceCommand()
        .processDefinitionKey(processDefinitionKey)
        .variables(variables)
        .tenantId(tenantId)
        .tags(tags)
        .send()
        .join()
        .getProcessInstanceKey();
  }

  public long createProcessInstance(final long processDefinitionKey) {
    return client
        .newCreateInstanceCommand()
        .processDefinitionKey(processDefinitionKey)
        .send()
        .join()
        .getProcessInstanceKey();
  }

  public long createSingleUserTask(final UnaryOperator<UserTaskBuilder> userTaskBuilderFunction) {
    return createSingleUserTask("", userTaskBuilderFunction);
  }

  public long createSingleUserTask() {
    return createSingleUserTask("");
  }

  public long createSingleUserTask(final String tenantId) {
    return createSingleUserTask(tenantId, UnaryOperator.identity());
  }

  public long createSingleUserTask(
      final String tenantId, final UnaryOperator<UserTaskBuilder> userTaskBuilderFunction) {
    final var modelInstance = createSingleUserTaskModelInstance(userTaskBuilderFunction);
    final var processDefinitionKey = deployProcess(modelInstance, tenantId);
    final var processInstanceKey = createProcessInstance(processDefinitionKey, "{}", tenantId);
    final var userTaskKey =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .filter(r -> processInstanceKey == r.getValue().getProcessInstanceKey())
            .map(Record::getKey)
            .findFirst()
            .orElse(-1L);

    assertThat(userTaskKey).describedAs("Expected a created user task").isGreaterThan(0L);

    return userTaskKey;
  }

  public BpmnModelInstance createSingleUserTaskModelInstance(
      final UnaryOperator<UserTaskBuilder> userTaskBuilderFunction) {
    return Bpmn.createExecutableProcess("process")
        .startEvent("start")
        .userTask("task", t -> userTaskBuilderFunction.apply(t.zeebeUserTask()))
        .zeebeUserTask()
        .endEvent("end")
        .done();
  }
}
