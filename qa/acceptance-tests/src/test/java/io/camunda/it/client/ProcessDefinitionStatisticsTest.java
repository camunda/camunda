/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static io.camunda.client.api.search.enums.ProcessInstanceState.ACTIVE;
import static io.camunda.it.util.TestHelper.getScopedVariables;
import static io.camunda.it.util.TestHelper.waitForActiveScopedUserTasks;
import static io.camunda.it.util.TestHelper.waitForProcessInstances;
import static io.camunda.it.util.TestHelper.waitForScopedProcessInstancesToStart;
import static io.camunda.it.util.TestHelper.waitUntilJobWorkerHasFailedJob;
import static io.camunda.qa.util.multidb.CamundaMultiDBExtension.TIMEOUT_DATA_AVAILABILITY;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.DeploymentEvent;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.api.search.enums.ElementInstanceState;
import io.camunda.client.api.search.enums.IncidentState;
import io.camunda.client.api.search.enums.ProcessInstanceState;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.client.api.search.response.UserTask;
import io.camunda.client.api.worker.JobWorker;
import io.camunda.client.impl.statistics.response.ProcessElementStatisticsImpl;
import io.camunda.it.util.TestHelper;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.lang.reflect.Method;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

@MultiDbTest
public class ProcessDefinitionStatisticsTest {

  public static final String INCIDENT_ERROR_MESSAGE_V1 =
      "Expected result of the expression 'retriesA' to be 'NUMBER', but was 'STRING'.";
  public static final int INCIDENT_ERROR_HASH_CODE_V1 = -1932748810;
  public static final int INCIDENT_ERROR_HASH_CODE_V2 = 17551445;
  private static CamundaClient camundaClient;
  private static String testScopeId;

  @BeforeEach
  public void beforeEach(final TestInfo testInfo) {
    testScopeId =
        testInfo.getTestMethod().map(Method::toString).orElse(UUID.randomUUID().toString());
  }

  @Test
  void shouldGetEmptyStatisticsWithoutMatch() {
    // when
    final var actual = camundaClient.newProcessDefinitionElementStatisticsRequest(1L).send().join();

    // then
    assertThat(actual).hasSize(0);
  }

  @Test
  void shouldGetStatisticsAndFilterByProcessInstanceKeyOrFilters() {
    // given
    final var processDefinitionKey = deployCompleteBPMN();
    final var pi1 = createInstance(processDefinitionKey);
    final var pi2 = createInstance(processDefinitionKey);
    final var pi3 = createInstance(processDefinitionKey);
    createInstance(processDefinitionKey);
    waitForProcessInstances(
        camundaClient,
        f -> f.processDefinitionKey(processDefinitionKey).state(ProcessInstanceState.COMPLETED),
        4);

    // when
    final var actual =
        camundaClient
            .newProcessDefinitionElementStatisticsRequest(processDefinitionKey)
            .filter(
                f ->
                    f.orFilters(
                        List.of(
                            f1 -> f1.processInstanceKey(pi1.getProcessInstanceKey()),
                            f2 -> f2.processInstanceKey(pi2.getProcessInstanceKey()),
                            f3 -> f3.processInstanceKey(pi3.getProcessInstanceKey()))))
            .send()
            .join();

    // then
    assertThat(actual).hasSize(2);
    assertThat(actual)
        .containsExactlyInAnyOrder(
            new ProcessElementStatisticsImpl("StartEvent", 0L, 0L, 0L, 3L),
            new ProcessElementStatisticsImpl("EndEvent", 0L, 0L, 0L, 3L));
  }

  @Test
  void shouldGetStatisticsAndFilterByElementIdOrFilters() {
    // given
    final var processDefinitionKey = deployIncidentBPMN();
    final var pi1 = createInstance(processDefinitionKey);
    createInstance(processDefinitionKey);
    waitForProcessInstances(
        camundaClient, f -> f.processDefinitionKey(processDefinitionKey).hasIncident(true), 2);

    // when
    final var actual =
        camundaClient
            .newProcessDefinitionElementStatisticsRequest(processDefinitionKey)
            .filter(
                f ->
                    f.orFilters(
                        List.of(
                            f1 -> f1.elementId(b -> b.like("*Event")),
                            f2 ->
                                f2.processInstanceKey(pi1.getProcessInstanceKey())
                                    .hasElementInstanceIncident(true))))
            .send()
            .join();

    // then
    assertThat(actual).hasSize(2);
    assertThat(actual)
        .containsExactlyInAnyOrder(
            new ProcessElementStatisticsImpl("StartEvent", 0L, 0L, 0L, 2L),
            new ProcessElementStatisticsImpl("ScriptTask", 0L, 0L, 1L, 0L));
  }

  @Test
  void shouldGetStatisticsAndFilterByElementIdLikeOrFilters() {
    // given
    final var processDefinitionKey = deployIncidentBPMN();
    createInstance(processDefinitionKey);
    createInstance(processDefinitionKey);
    waitForProcessInstances(
        camundaClient, f -> f.processDefinitionKey(processDefinitionKey).hasIncident(true), 2);

    // when
    final var actual =
        camundaClient
            .newProcessDefinitionElementStatisticsRequest(processDefinitionKey)
            .filter(
                f ->
                    f.orFilters(
                        List.of(
                            f1 -> f1.elementId(b -> b.like("*Event")),
                            f2 -> f2.hasElementInstanceIncident(false))))
            .send()
            .join();

    // then
    assertThat(actual).hasSize(1);
    assertThat(actual)
        .containsExactlyInAnyOrder(new ProcessElementStatisticsImpl("StartEvent", 0L, 0L, 0L, 2L));
  }

  @Test
  void shouldGetStatisticsAndFilterByErrorMessageOrFilters() {
    // given
    final var processDefinitionKey =
        TestHelper.deployResource(camundaClient, "process/incident_process_v1.bpmn")
            .getProcesses()
            .getFirst()
            .getProcessDefinitionKey();
    createInstance(processDefinitionKey);
    createInstance(processDefinitionKey);
    waitForProcessInstances(
        camundaClient, f -> f.processDefinitionKey(processDefinitionKey).hasIncident(true), 2);

    // when
    final var actual =
        camundaClient
            .newProcessDefinitionElementStatisticsRequest(processDefinitionKey)
            .filter(
                f ->
                    f.orFilters(
                        List.of(
                            f1 -> f1.elementId(b -> b.neq("start")),
                            f2 -> f2.errorMessage(INCIDENT_ERROR_MESSAGE_V1))))
            .send()
            .join();

    // then
    assertThat(actual).hasSize(2);
    assertThat(actual)
        .containsExactlyInAnyOrder(
            new ProcessElementStatisticsImpl("start", 0L, 0L, 0L, 2L),
            new ProcessElementStatisticsImpl("taskAIncident", 0L, 0L, 2L, 0L));
  }

  @Test
  void shouldGetStatisticsAndFilterByIncidentHashCodeOrFilters() {
    // given
    final var processDefinitionKey =
        TestHelper.deployResource(camundaClient, "process/incident_process_v1.bpmn")
            .getProcesses()
            .getFirst()
            .getProcessDefinitionKey();
    createInstance(processDefinitionKey);
    createInstance(processDefinitionKey);
    waitForProcessInstances(
        camundaClient, f -> f.hasIncident(true).variables(getScopedVariables(testScopeId)), 2);

    // when
    final var actual =
        camundaClient
            .newProcessDefinitionElementStatisticsRequest(processDefinitionKey)
            .filter(
                f ->
                    f.orFilters(
                            List.of(
                                f1 -> f1.elementId(b -> b.eq("start")),
                                f2 -> f2.incidentErrorHashCode(INCIDENT_ERROR_HASH_CODE_V1)))
                        .variables(getScopedVariables(testScopeId)))
            .send()
            .join();

    // then
    assertThat(actual).hasSize(2);
    assertThat(actual)
        .containsExactlyInAnyOrder(
            new ProcessElementStatisticsImpl("start", 0L, 0L, 0L, 2L),
            new ProcessElementStatisticsImpl("taskAIncident", 0L, 0L, 2L, 0L));
  }

  @Test
  void shouldGetStatisticsAndFilterByProcessInstanceKey() {
    // given
    final var processDefinitionKey = deployCompleteBPMN();
    final var pi1 = createInstance(processDefinitionKey);
    createInstance(processDefinitionKey);
    waitForProcessInstances(
        camundaClient,
        f -> f.processDefinitionKey(processDefinitionKey).state(ProcessInstanceState.COMPLETED),
        2);

    // when
    final var actual =
        camundaClient
            .newProcessDefinitionElementStatisticsRequest(processDefinitionKey)
            .filter(f -> f.processInstanceKey(pi1.getProcessInstanceKey()))
            .send()
            .join();

    // then
    assertThat(actual).hasSize(2);
    assertThat(actual)
        .containsExactlyInAnyOrder(
            new ProcessElementStatisticsImpl("StartEvent", 0L, 0L, 0L, 1L),
            new ProcessElementStatisticsImpl("EndEvent", 0L, 0L, 0L, 1L));
  }

  @Test
  void shouldGetStatisticsAndFilterByProcessInstanceKeyIn() {
    // given
    final var processDefinitionKey = deployCompleteBPMN();
    final var pi1 = createInstance(processDefinitionKey);
    final var pi2 = createInstance(processDefinitionKey);
    createInstance(processDefinitionKey);
    waitForProcessInstances(
        camundaClient,
        f -> f.processDefinitionKey(processDefinitionKey).state(ProcessInstanceState.COMPLETED),
        3);

    // when
    final var actual =
        camundaClient
            .newProcessDefinitionElementStatisticsRequest(processDefinitionKey)
            .filter(
                f ->
                    f.processInstanceKey(
                        b -> b.in(pi1.getProcessInstanceKey(), pi2.getProcessInstanceKey())))
            .send()
            .join();

    // then
    assertThat(actual).hasSize(2);
    assertThat(actual)
        .containsExactlyInAnyOrder(
            new ProcessElementStatisticsImpl("StartEvent", 0L, 0L, 0L, 2L),
            new ProcessElementStatisticsImpl("EndEvent", 0L, 0L, 0L, 2L));
  }

  @Test
  void shouldGetStatisticsAndFilterByProcessInstanceKeyNotIn() {
    // given
    final var processDefinitionKey = deployCompleteBPMN();
    final var pi1 = createInstance(processDefinitionKey);
    final var pi2 = createInstance(processDefinitionKey);
    createInstance(processDefinitionKey);
    waitForProcessInstances(
        camundaClient,
        f -> f.processDefinitionKey(processDefinitionKey).state(ProcessInstanceState.COMPLETED),
        3);

    // when
    final var actual =
        camundaClient
            .newProcessDefinitionElementStatisticsRequest(processDefinitionKey)
            .filter(
                f ->
                    f.processInstanceKey(
                        b -> b.notIn(pi1.getProcessInstanceKey(), pi2.getProcessInstanceKey())))
            .send()
            .join();

    // then
    assertThat(actual).hasSize(2);
    assertThat(actual)
        .containsExactlyInAnyOrder(
            new ProcessElementStatisticsImpl("StartEvent", 0L, 0L, 0L, 1L),
            new ProcessElementStatisticsImpl("EndEvent", 0L, 0L, 0L, 1L));
  }

  @Test
  void shouldGetStatisticsAndFilterByTenantIdLike() {
    // given
    final var processDefinitionKey = deployCompleteBPMN();
    createInstance(processDefinitionKey);
    createInstance(processDefinitionKey);
    waitForProcessInstances(
        camundaClient,
        f -> f.processDefinitionKey(processDefinitionKey).state(ProcessInstanceState.COMPLETED),
        2);

    // when
    final var actual =
        camundaClient
            .newProcessDefinitionElementStatisticsRequest(processDefinitionKey)
            .filter(f -> f.tenantId(b -> b.like("*def*")))
            .send()
            .join();

    // then
    assertThat(actual).hasSize(2);
    assertThat(actual)
        .containsExactlyInAnyOrder(
            new ProcessElementStatisticsImpl("StartEvent", 0L, 0L, 0L, 2L),
            new ProcessElementStatisticsImpl("EndEvent", 0L, 0L, 0L, 2L));
  }

  @Test
  void shouldGetStatisticsAndFilterByStartDateFilterGtLt() {
    // given
    final var processDefinitionKey = deployCompleteBPMN();
    createInstance(processDefinitionKey);
    final var piKey = createInstance(processDefinitionKey).getProcessInstanceKey();
    waitForProcessInstances(
        camundaClient,
        f -> f.processDefinitionKey(processDefinitionKey).state(ProcessInstanceState.COMPLETED),
        2);
    final var pi = getProcessInstance(piKey);
    final var startDate = pi.getStartDate();

    // when
    final var actual =
        camundaClient
            .newProcessDefinitionElementStatisticsRequest(processDefinitionKey)
            .filter(
                f ->
                    f.startDate(
                        b ->
                            b.gt(startDate.minus(1, ChronoUnit.MILLIS))
                                .lt(startDate.plus(1, ChronoUnit.MILLIS))))
            .send()
            .join();

    // then
    assertThat(actual).hasSize(2);
    assertThat(actual)
        .containsExactlyInAnyOrder(
            new ProcessElementStatisticsImpl("StartEvent", 0L, 0L, 0L, 1L),
            new ProcessElementStatisticsImpl("EndEvent", 0L, 0L, 0L, 1L));
  }

  @Test
  void shouldGetStatisticsAndFilterByStartDateFilterGteLte() {
    // given
    final var processDefinitionKey = deployCompleteBPMN();
    createInstance(processDefinitionKey);
    final var piKey = createInstance(processDefinitionKey).getProcessInstanceKey();
    waitForProcessInstances(
        camundaClient,
        f -> f.processDefinitionKey(processDefinitionKey).state(ProcessInstanceState.COMPLETED),
        2);
    final var pi = getProcessInstance(piKey);
    final var startDate = pi.getStartDate();

    // when
    final var actual =
        camundaClient
            .newProcessDefinitionElementStatisticsRequest(processDefinitionKey)
            .filter(f -> f.startDate(b -> b.gte(startDate).lte(startDate)))
            .send()
            .join();

    // then
    assertThat(actual).hasSize(2);
    assertThat(actual)
        .containsExactlyInAnyOrder(
            new ProcessElementStatisticsImpl("StartEvent", 0L, 0L, 0L, 1L),
            new ProcessElementStatisticsImpl("EndEvent", 0L, 0L, 0L, 1L));
  }

  @Test
  void shouldGetStatisticsAndFilterByEndDateExists() {
    // given
    final var processDefinitionKey = deployActiveBPMN();
    createInstance(processDefinitionKey);
    createInstance(processDefinitionKey);
    waitForProcessInstances(
        camundaClient, f -> f.processDefinitionKey(processDefinitionKey).state(ACTIVE), 2);
    waitForActiveScopedUserTasks(camundaClient, testScopeId, 2);
    final var userTask = getUserTask(processDefinitionKey);
    camundaClient.newCompleteUserTaskCommand(userTask.getUserTaskKey()).send().join();
    waitForProcessInstances(
        camundaClient,
        f ->
            f.processInstanceKey(userTask.getProcessInstanceKey())
                .state(ProcessInstanceState.COMPLETED),
        1);

    // when
    final var actual =
        camundaClient
            .newProcessDefinitionElementStatisticsRequest(processDefinitionKey)
            .filter(f -> f.endDate(b -> b.exists(true)))
            .send()
            .join();

    // then
    assertThat(actual).hasSize(3);
    assertThat(actual)
        .containsExactlyInAnyOrder(
            new ProcessElementStatisticsImpl("StartEvent", 0L, 0L, 0L, 1L),
            new ProcessElementStatisticsImpl("UserTask", 0L, 0L, 0L, 1L),
            new ProcessElementStatisticsImpl("EndEvent", 0L, 0L, 0L, 1L));
  }

  @Test
  void shouldGetStatisticsAndFilterByEndDateNotExists() {
    // given
    final var processDefinitionKey = deployActiveBPMN();
    createInstance(processDefinitionKey);
    createInstance(processDefinitionKey);
    waitForProcessInstances(
        camundaClient, f -> f.processDefinitionKey(processDefinitionKey).state(ACTIVE), 2);
    waitForActiveScopedUserTasks(camundaClient, testScopeId, 2);
    final var userTask = getUserTask(processDefinitionKey);
    camundaClient.newCompleteUserTaskCommand(userTask.getUserTaskKey()).send().join();
    waitForProcessInstances(
        camundaClient,
        f ->
            f.processInstanceKey(userTask.getProcessInstanceKey())
                .state(ProcessInstanceState.COMPLETED),
        1);

    // when
    final var actual =
        camundaClient
            .newProcessDefinitionElementStatisticsRequest(processDefinitionKey)
            .filter(f -> f.endDate(b -> b.exists(false)))
            .send()
            .join();

    // then
    assertThat(actual).hasSize(2);
    assertThat(actual)
        .containsExactlyInAnyOrder(
            new ProcessElementStatisticsImpl("StartEvent", 0L, 0L, 0L, 1L),
            new ProcessElementStatisticsImpl("UserTask", 1L, 0L, 0L, 0L));
  }

  @Test
  void shouldGetStatisticsAndFilterByStateActive() {
    // given
    final var processDefinitionKey = deployActiveBPMN();
    createInstance(processDefinitionKey);
    createInstance(processDefinitionKey);
    createInstance(processDefinitionKey);
    waitForProcessInstances(
        camundaClient, f -> f.processDefinitionKey(processDefinitionKey).state(ACTIVE), 3);
    // waitForUserTasks(3, processDefinitionKey);
    waitForActiveScopedUserTasks(camundaClient, testScopeId, 3);
    final var userTask = getUserTask(processDefinitionKey);
    camundaClient.newCompleteUserTaskCommand(userTask.getUserTaskKey()).send().join();
    waitForProcessInstances(
        camundaClient,
        f ->
            f.processInstanceKey(userTask.getProcessInstanceKey())
                .state(ProcessInstanceState.COMPLETED),
        1);

    // when
    final var actual =
        camundaClient
            .newProcessDefinitionElementStatisticsRequest(processDefinitionKey)
            .filter(f -> f.state(ACTIVE))
            .send()
            .join();

    // then
    assertThat(actual).hasSize(2);
    assertThat(actual)
        .containsExactlyInAnyOrder(
            new ProcessElementStatisticsImpl("StartEvent", 0L, 0L, 0L, 2L),
            new ProcessElementStatisticsImpl("UserTask", 2L, 0L, 0L, 0L));
  }

  @Test
  void shouldGetDistinctStatisticsForMultiInstanceActivity() {
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

    createInstance(processDefinitionKey);
    createInstance(processDefinitionKey);
    waitForProcessInstances(
        camundaClient, f -> f.processDefinitionKey(processDefinitionKey).state(ACTIVE), 2);
    // waitForUserTasks(6, processDefinitionKey);
    waitForActiveScopedUserTasks(camundaClient, testScopeId, 6);

    // when
    final var actual =
        camundaClient
            .newProcessDefinitionElementStatisticsRequest(processDefinitionKey)
            .filter(f -> f.state(ACTIVE))
            .send()
            .join();

    // then
    assertThat(actual).hasSize(2);
    assertThat(actual)
        .containsExactlyInAnyOrder(
            new ProcessElementStatisticsImpl("StartEvent", 0L, 0L, 0L, 2L),
            new ProcessElementStatisticsImpl("UserTaskMultiInstance", 2L, 0L, 0L, 0L));
  }

  @Test
  void shouldGetStatisticsAndFilterByStateNotEq() {
    // given
    final var processDefinitionKey = deployActiveBPMN();
    createInstance(processDefinitionKey);
    createInstance(processDefinitionKey);
    waitForProcessInstances(
        camundaClient, f -> f.processDefinitionKey(processDefinitionKey).state(ACTIVE), 2);
    waitForActiveScopedUserTasks(camundaClient, testScopeId, 2);
    final var userTask = getUserTask(processDefinitionKey);
    camundaClient.newCompleteUserTaskCommand(userTask.getUserTaskKey()).send().join();
    waitForProcessInstances(
        camundaClient,
        f ->
            f.processInstanceKey(userTask.getProcessInstanceKey())
                .state(ProcessInstanceState.COMPLETED),
        1);

    // when
    final var actual =
        camundaClient
            .newProcessDefinitionElementStatisticsRequest(processDefinitionKey)
            .filter(f -> f.state(b -> b.neq(ACTIVE)))
            .send()
            .join();

    // then
    assertThat(actual).hasSize(3);
    assertThat(actual)
        .containsExactlyInAnyOrder(
            new ProcessElementStatisticsImpl("StartEvent", 0L, 0L, 0L, 1L),
            new ProcessElementStatisticsImpl("UserTask", 0L, 0L, 0L, 1L),
            new ProcessElementStatisticsImpl("EndEvent", 0L, 0L, 0L, 1L));
  }

  @Test
  void shouldGetStatisticsForCompleted() {
    // given
    final var processDefinitionKey = deployCompleteBPMN();
    createInstance(processDefinitionKey);
    createInstance(processDefinitionKey);
    createInstance(processDefinitionKey);
    waitForProcessInstances(
        camundaClient,
        f -> f.processDefinitionKey(processDefinitionKey).state(ProcessInstanceState.COMPLETED),
        3);

    // when
    final var actual =
        camundaClient
            .newProcessDefinitionElementStatisticsRequest(processDefinitionKey)
            .send()
            .join();

    // then
    assertThat(actual).hasSize(2);
    assertThat(actual)
        .containsExactlyInAnyOrder(
            new ProcessElementStatisticsImpl("StartEvent", 0L, 0L, 0L, 3L),
            new ProcessElementStatisticsImpl("EndEvent", 0L, 0L, 0L, 3L));
  }

  @Test
  void shouldGetStatisticsForActive() {
    // given
    final var processDefinitionKey = deployActiveBPMN();
    createInstance(processDefinitionKey);
    createInstance(processDefinitionKey);
    waitForProcessInstances(
        camundaClient, f -> f.processDefinitionKey(processDefinitionKey).state(ACTIVE), 2);

    // when
    final var actual =
        camundaClient
            .newProcessDefinitionElementStatisticsRequest(processDefinitionKey)
            .send()
            .join();

    // then
    assertThat(actual).hasSize(2);
    assertThat(actual)
        .containsExactlyInAnyOrder(
            new ProcessElementStatisticsImpl("StartEvent", 0L, 0L, 0L, 2L),
            new ProcessElementStatisticsImpl("UserTask", 2L, 0L, 0L, 0L));
  }

  @Test
  void shouldGetStatisticsForIncidentsAndFilterByHasIncident() {
    // given
    final var processDefinitionKey = deployIncidentBPMN();
    createInstance(processDefinitionKey);
    createInstance(processDefinitionKey);
    createInstance(processDefinitionKey);
    waitForProcessInstances(
        camundaClient, f -> f.processDefinitionKey(processDefinitionKey).hasIncident(true), 3);

    // when
    final var actual =
        camundaClient
            .newProcessDefinitionElementStatisticsRequest(processDefinitionKey)
            .filter(f -> f.hasIncident(true))
            .send()
            .join();

    // then
    assertThat(actual).hasSize(2);
    assertThat(actual)
        .containsExactlyInAnyOrder(
            new ProcessElementStatisticsImpl("StartEvent", 0L, 0L, 0L, 3L),
            new ProcessElementStatisticsImpl("ScriptTask", 0L, 0L, 3L, 0L));
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
    final var pi1 = createInstance(processDefinitionKey);
    createInstance(processDefinitionKey);
    camundaClient.newCancelInstanceCommand(pi1.getProcessInstanceKey()).send().join();
    waitForProcessInstances(camundaClient, f -> f.processDefinitionKey(processDefinitionKey), 2);

    // when
    final var actual =
        camundaClient
            .newProcessDefinitionElementStatisticsRequest(processDefinitionKey)
            .send()
            .join();

    // then
    assertThat(actual).hasSize(2);
    assertThat(actual)
        .containsExactlyInAnyOrder(
            new ProcessElementStatisticsImpl("StartEvent", 0L, 0L, 0L, 2L),
            new ProcessElementStatisticsImpl("UserTask", 1L, 1L, 0L, 0L));
  }

  @Test
  void shouldReturnStatisticsAndFilterByHasRetriesLeft() {
    // given
    final var processDefinitionKey =
        TestHelper.deployResource(camundaClient, "process/service_tasks_v2.bpmn")
            .getProcesses()
            .getFirst()
            .getProcessDefinitionKey();
    TestHelper.startProcessInstance(camundaClient, "service_tasks_v2", "{\"path\":222}");

    try (final JobWorker ignored =
        camundaClient
            .newWorker()
            .jobType("taskC")
            .handler((client, job) -> client.newFailCommand(job).retries(1).send().join())
            .open()) {

      waitUntilJobWorkerHasFailedJob(camundaClient, 1);

      // when
      final var actual =
          camundaClient
              .newProcessDefinitionElementStatisticsRequest(processDefinitionKey)
              .filter(f -> f.hasRetriesLeft(true))
              .send()
              .join();

      // then
      assertThat(actual).hasSize(3);
      assertThat(actual)
          .containsExactlyInAnyOrder(
              new ProcessElementStatisticsImpl("startEvent", 0L, 0L, 0L, 1L),
              new ProcessElementStatisticsImpl("exclusiveGateway", 0L, 0L, 0L, 1L),
              new ProcessElementStatisticsImpl("taskC", 1L, 0L, 0L, 0L));
    }
  }

  @Test
  void shouldReturnStatisticsAndFilterByElementId() {
    // given
    final var processDefinitionKey = deployActiveBPMN();
    createInstance(processDefinitionKey);
    createInstance(processDefinitionKey);
    // waitForProcessInstances(2, f -> f.processDefinitionKey(processDefinitionKey).state(ACTIVE));
    waitForScopedProcessInstancesToStart(camundaClient, testScopeId, 2);
    waitForActiveScopedUserTasks(camundaClient, testScopeId, 2);
    final var userTask = getUserTask(processDefinitionKey);
    camundaClient.newCompleteUserTaskCommand(userTask.getUserTaskKey()).send().join();
    waitForProcessInstances(
        camundaClient,
        f ->
            f.processInstanceKey(userTask.getProcessInstanceKey())
                .state(ProcessInstanceState.COMPLETED)
                .variables(getScopedVariables(testScopeId)),
        1);

    // when
    final var actual =
        camundaClient
            .newProcessDefinitionElementStatisticsRequest(processDefinitionKey)
            .filter(f -> f.elementId("UserTask"))
            .send()
            .join();

    // then
    assertThat(actual).hasSize(1);
    assertThat(actual)
        .containsExactlyInAnyOrder(new ProcessElementStatisticsImpl("UserTask", 1L, 0L, 0L, 1L));
  }

  @Test
  void shouldReturnStatisticsAndFilterByElementInstanceState() {
    // given
    final var processDefinitionKey = deployActiveBPMN();
    createInstance(processDefinitionKey);
    createInstance(processDefinitionKey);
    waitForProcessInstances(
        camundaClient, f -> f.processDefinitionKey(processDefinitionKey).state(ACTIVE), 2);
    waitForActiveScopedUserTasks(camundaClient, testScopeId, 2);
    final var userTask = getUserTask(processDefinitionKey);
    camundaClient.newCompleteUserTaskCommand(userTask.getUserTaskKey()).send().join();
    waitForProcessInstances(
        camundaClient,
        f ->
            f.processInstanceKey(userTask.getProcessInstanceKey())
                .state(ProcessInstanceState.COMPLETED),
        1);

    // when
    final var actual =
        camundaClient
            .newProcessDefinitionElementStatisticsRequest(processDefinitionKey)
            .filter(f -> f.elementInstanceState(ElementInstanceState.COMPLETED))
            .send()
            .join();

    // then
    assertThat(actual).hasSize(3);
    assertThat(actual)
        .containsExactlyInAnyOrder(
            new ProcessElementStatisticsImpl("StartEvent", 0L, 0L, 0L, 2L),
            new ProcessElementStatisticsImpl("UserTask", 0L, 0L, 0L, 1L),
            new ProcessElementStatisticsImpl("EndEvent", 0L, 0L, 0L, 1L));
  }

  @Test
  void shouldReturnStatisticsAndFilterByElementInstanceIncident() {
    // given
    final var processDefinitionKey = deployIncidentBPMN();
    createInstance(processDefinitionKey);
    waitForProcessInstances(
        camundaClient, f -> f.processDefinitionKey(processDefinitionKey).hasIncident(true), 1);

    // when
    final var actual =
        camundaClient
            .newProcessDefinitionElementStatisticsRequest(processDefinitionKey)
            .filter(f -> f.hasElementInstanceIncident(true))
            .send()
            .join();

    // then
    assertThat(actual).hasSize(1);
    assertThat(actual)
        .containsExactly(new ProcessElementStatisticsImpl("ScriptTask", 0L, 0L, 1L, 0L));
  }

  @Test
  void shouldReturnStatisticsAndFilterByErrorHashCode() {
    // given
    final var processDefinitionKey =
        TestHelper.deployResource(camundaClient, "process/incident_process_v2.bpmn")
            .getProcesses()
            .getFirst()
            .getProcessDefinitionKey();
    createInstance(processDefinitionKey);
    waitForProcessInstances(
        camundaClient, f -> f.processDefinitionKey(processDefinitionKey).hasIncident(true), 1);
    waitForIncidents(processDefinitionKey);

    // when
    final var actual =
        camundaClient
            .newProcessDefinitionElementStatisticsRequest(processDefinitionKey)
            .filter(f -> f.incidentErrorHashCode(INCIDENT_ERROR_HASH_CODE_V2))
            .send()
            .join();

    // then
    assertThat(actual).hasSize(2);
    assertThat(actual)
        .containsExactlyInAnyOrder(
            new ProcessElementStatisticsImpl("start", 0L, 0L, 0L, 1L),
            new ProcessElementStatisticsImpl("taskAIncident", 0L, 0L, 1L, 0L));
  }

  @Test
  void shouldReturnEmptyResultForIncorrectIncidentErrorHashCode() {
    final var processDefinitionKey = deployIncidentBPMN();
    createInstance(processDefinitionKey);
    waitForProcessInstances(
        camundaClient, f -> f.processDefinitionKey(processDefinitionKey).hasIncident(true), 1);

    final var result =
        camundaClient
            .newProcessDefinitionElementStatisticsRequest(processDefinitionKey)
            .filter(f -> f.incidentErrorHashCode(123456))
            .send()
            .join();

    assertThat(result).isEmpty();
  }

  private static void waitForIncidents(final long processDefinitionKey) {
    Awaitility.await("should receive data from ES")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () ->
                assertThat(
                        camundaClient
                            .newIncidentSearchRequest()
                            .filter(
                                f ->
                                    f.processDefinitionKey(processDefinitionKey)
                                        .state(IncidentState.ACTIVE))
                            .send()
                            .join()
                            .items())
                    .hasSize(1));
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

  private static long deployIncidentBPMN() {
    final var processModel =
        Bpmn.createExecutableProcess("process")
            .startEvent("StartEvent")
            .scriptTask(
                "ScriptTask",
                b -> b.zeebeExpression("assert(x, x != null)").zeebeResultVariable("res"))
            .zeebeResultVariable("res")
            .endEvent()
            .done();
    return deployResource(processModel, "script_task.bpmn")
        .getProcesses()
        .getFirst()
        .getProcessDefinitionKey();
  }

  private static ProcessInstanceEvent createInstance(final long processDefinitionKey) {
    return TestHelper.startScopedProcessInstance(
        camundaClient, processDefinitionKey, testScopeId, Map.of());
  }

  private static ProcessInstance getProcessInstance(final long piKey) {
    return camundaClient
        .newProcessInstanceSearchRequest()
        .filter(f -> f.processInstanceKey(piKey))
        .page(p -> p.limit(1))
        .send()
        .join()
        .items()
        .getFirst();
  }

  private static UserTask getUserTask(final long processDefinitionKey) {
    return camundaClient
        .newUserTaskSearchRequest()
        .filter(f -> f.processDefinitionKey(processDefinitionKey))
        .send()
        .join()
        .items()
        .getFirst();
  }
}
