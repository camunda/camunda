/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static io.camunda.client.api.search.enums.ProcessInstanceState.ACTIVE;
import static io.camunda.qa.util.multidb.CamundaMultiDBExtension.TIMEOUT_DATA_AVAILABILITY;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.DeploymentEvent;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.api.search.enums.ProcessInstanceState;
import io.camunda.client.api.search.filter.ProcessInstanceFilter;
import io.camunda.client.impl.statistics.response.ProcessElementStatisticsImpl;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.util.function.Consumer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

@MultiDbTest
public class ProcessInstanceStatisticsTest {

  private static CamundaClient camundaClient;

  private static void waitForProcessInstances(
      final int count, final Consumer<ProcessInstanceFilter> fn) {
    Awaitility.await("should receive data from ES")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () ->
                assertThat(
                        camundaClient
                            .newProcessInstanceSearchRequest()
                            .filter(fn)
                            .send()
                            .join()
                            .items())
                    .hasSize(count));
  }

  private static void waitForUserTasks(final int count, final long processDefinitionKey) {
    Awaitility.await("should receive data from ES")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () ->
                assertThat(
                        camundaClient
                            .newUserTaskSearchRequest()
                            .filter(f -> f.processDefinitionKey(processDefinitionKey))
                            .send()
                            .join()
                            .items())
                    .hasSize(count));
  }

  @Test
  void shouldGetStatisticsForCompleted() {
    // given
    final var processDefinitionKey = deployCompleteBPMN();
    final var processInstanceKey = createInstance(processDefinitionKey).getProcessInstanceKey();
    waitForProcessInstances(
        1, f -> f.processDefinitionKey(processDefinitionKey).state(ProcessInstanceState.COMPLETED));

    // when
    final var actual =
        camundaClient.newProcessInstanceElementStatisticsRequest(processInstanceKey).send().join();

    // then
    assertThat(actual).hasSize(2);

    assertThat(actual)
        .containsExactlyInAnyOrder(
            new ProcessElementStatisticsImpl("EndEvent", 0L, 0L, 0L, 1L),
            new ProcessElementStatisticsImpl("StartEvent", 0L, 0L, 0L, 1L));
  }

  @Test
  void shouldGetStatisticsForActive() {
    // given
    final var processDefinitionKey = deployActiveBPMN();
    final var processInstanceKey = createInstance(processDefinitionKey).getProcessInstanceKey();
    waitForProcessInstances(1, f -> f.processDefinitionKey(processDefinitionKey).state(ACTIVE));
    waitForUserTasks(1, processDefinitionKey);

    // when
    final var actual =
        camundaClient.newProcessInstanceElementStatisticsRequest(processInstanceKey).send().join();

    // then
    assertThat(actual).hasSize(2);
    assertThat(actual)
        .containsExactlyInAnyOrder(
            new ProcessElementStatisticsImpl("UserTask", 1L, 0L, 0L, 0L),
            new ProcessElementStatisticsImpl("StartEvent", 0L, 0L, 0L, 1L));
  }

  @Test
  void shouldGetStatisticsForIncidents() {
    // given
    final var processModel =
        Bpmn.createExecutableProcess("process")
            .startEvent("StartEvent")
            .scriptTask(
                "ScriptTask",
                b -> b.zeebeExpression("assert(x, x != null)").zeebeResultVariable("res"))
            .zeebeResultVariable("res")
            .endEvent()
            .done();
    final var processDefinitionKey =
        deployResource(processModel, "script_task.bpmn")
            .getProcesses()
            .getFirst()
            .getProcessDefinitionKey();
    final var processInstanceKey = createInstance(processDefinitionKey).getProcessInstanceKey();
    waitForProcessInstances(1, f -> f.processDefinitionKey(processDefinitionKey).hasIncident(true));

    // when
    final var actual =
        camundaClient.newProcessInstanceElementStatisticsRequest(processInstanceKey).send().join();

    // then
    assertThat(actual).hasSize(2);
    assertThat(actual)
        .containsExactlyInAnyOrder(
            new ProcessElementStatisticsImpl("ScriptTask", 0L, 0L, 1L, 0L),
            new ProcessElementStatisticsImpl("StartEvent", 0L, 0L, 0L, 1L));
  }

  @Test
  void shouldReturnStatisticsForCanceled() {
    // given
    final var processModel =
        Bpmn.createExecutableProcess("process")
            .startEvent("StartEvent")
            .userTask("UserTask")
            .endEvent()
            .done();
    final var processDefinitionKey =
        deployResource(processModel, "manual_task_cancel.bpmn")
            .getProcesses()
            .getFirst()
            .getProcessDefinitionKey();
    final var processInstanceKey = createInstance(processDefinitionKey).getProcessInstanceKey();
    camundaClient.newCancelInstanceCommand(processInstanceKey).send().join();
    waitForProcessInstances(
        1,
        f -> f.processDefinitionKey(processDefinitionKey).state(ProcessInstanceState.TERMINATED));

    // when
    final var actual =
        camundaClient.newProcessInstanceElementStatisticsRequest(processInstanceKey).send().join();

    // then
    assertThat(actual).hasSize(2);
    assertThat(actual)
        .containsExactlyInAnyOrder(
            new ProcessElementStatisticsImpl("UserTask", 0L, 1L, 0L, 0L),
            new ProcessElementStatisticsImpl("StartEvent", 0L, 0L, 0L, 1L));
  }

  @Test
  void shouldGetAllStatisticsForMultiInstanceActivity() {
    // given
    final var processModel =
        Bpmn.createExecutableProcess("process")
            .startEvent("StartEvent")
            .userTask("UserTaskMultiInstance")
            .zeebeUserTask()
            .multiInstance()
            .parallel()
            .zeebeInputCollectionExpression("[1,2,3]")
            .multiInstanceDone()
            .endEvent("EndEvent")
            .done();
    final var processDefinitionKey =
        deployResource(processModel, "multi-instance.bpmn")
            .getProcesses()
            .getFirst()
            .getProcessDefinitionKey();

    final var processInstanceKey = createInstance(processDefinitionKey).getProcessInstanceKey();
    createInstance(processDefinitionKey).getProcessInstanceKey();
    waitForProcessInstances(2, f -> f.processDefinitionKey(processDefinitionKey).state(ACTIVE));
    waitForUserTasks(6, processDefinitionKey);

    // when
    final var actual =
        camundaClient.newProcessInstanceElementStatisticsRequest(processInstanceKey).send().join();

    // then
    assertThat(actual).hasSize(2);
    assertThat(actual)
        .containsExactlyInAnyOrder(
            new ProcessElementStatisticsImpl("UserTaskMultiInstance", 3L, 0L, 0L, 0L),
            new ProcessElementStatisticsImpl("StartEvent", 0L, 0L, 0L, 1L));
  }

  private static DeploymentEvent deployResource(
      final BpmnModelInstance processModel, final String resourceName) {
    return camundaClient
        .newDeployResourceCommand()
        .addProcessModel(processModel, resourceName)
        .send()
        .join();
  }

  private static long deployCompleteBPMN() {
    final var processModel =
        Bpmn.createExecutableProcess("process")
            .startEvent("StartEvent")
            .endEvent("EndEvent")
            .done();
    return deployResource(processModel, "complete.bpmn")
        .getProcesses()
        .getFirst()
        .getProcessDefinitionKey();
  }

  private static long deployActiveBPMN() {
    final var processModel =
        Bpmn.createExecutableProcess("process")
            .startEvent("StartEvent")
            .userTask("UserTask")
            .zeebeUserTask()
            .endEvent("EndEvent")
            .done();
    return deployResource(processModel, "manual_task.bpmn")
        .getProcesses()
        .getFirst()
        .getProcessDefinitionKey();
  }

  private static ProcessInstanceEvent createInstance(final long processDefinitionKey) {
    return camundaClient
        .newCreateInstanceCommand()
        .processDefinitionKey(processDefinitionKey)
        .send()
        .join();
  }
}
