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
import static io.camunda.it.util.TestHelper.waitForMessageSubscriptions;
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
import io.camunda.client.api.search.enums.MessageSubscriptionState;
import io.camunda.client.api.search.enums.ProcessInstanceState;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.client.api.search.response.UserTask;
import io.camunda.client.api.worker.JobWorker;
import io.camunda.client.impl.statistics.response.ProcessElementStatisticsImpl;
import io.camunda.it.util.TestHelper;
import io.camunda.qa.util.compatibility.CompatibilityTest;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.lang.reflect.Method;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@MultiDbTest
@CompatibilityTest
@Execution(ExecutionMode.SAME_THREAD)
public class ProcessDefinitionStatisticsIT {

  public static final String INCIDENT_ERROR_MESSAGE_V1 =
      "Expected result of the expression 'retriesA' to be 'NUMBER', but was 'STRING'.";
  public static final int INCIDENT_ERROR_HASH_CODE_V1 = -1932748810;
  public static final int INCIDENT_ERROR_HASH_CODE_V2 = 17551445;
  private static CamundaClient camundaClient;
  private static String testScopeId;
  private static final String PROCESS_WITH_SUBSCRIPTIONS_PROCESS_ID_1 =
      "process_with_subscriptions_1";
  private static final String PROCESS_WITH_SUBSCRIPTIONS_PROCESS_ID_2 =
      "process_with_subscriptions_2";
  private static long processWithSubscriptionsProcessId1Pdk1;
  private static long processWithSubscriptionsProcessId1Pdk2;
  private static long processWithSubscriptionsProcessId2Pdk1;

  @BeforeEach
  public void beforeEach(final TestInfo testInfo) {
    testScopeId =
        testInfo.getTestMethod().map(Method::toString).orElse(UUID.randomUUID().toString());
  }

  @BeforeAll
  public static void beforeAll() {
    final var processModel1V1 =
        createBpmnModelVersion("v1", PROCESS_WITH_SUBSCRIPTIONS_PROCESS_ID_1);
    final var processModel1V2 =
        createBpmnModelVersion("v2", PROCESS_WITH_SUBSCRIPTIONS_PROCESS_ID_1);
    final var processModel2 = createBpmnModelVersion("v1", PROCESS_WITH_SUBSCRIPTIONS_PROCESS_ID_2);

    processWithSubscriptionsProcessId1Pdk1 =
        deployResource(processModel1V1, "multi-sub-process.bpmn")
            .getProcesses()
            .getFirst()
            .getProcessDefinitionKey();
    processWithSubscriptionsProcessId1Pdk2 =
        deployResource(processModel1V2, "multi-sub-process.bpmn")
            .getProcesses()
            .getFirst()
            .getProcessDefinitionKey();
    processWithSubscriptionsProcessId2Pdk1 =
        deployResource(processModel2, "multi-sub-process-v2.bpmn")
            .getProcesses()
            .getFirst()
            .getProcessDefinitionKey();

    // 2 instances for process 1 version 1 = 4 subscriptions
    createInstance(processWithSubscriptionsProcessId1Pdk1, Map.of("key1", "A", "key2", "B"));
    createInstance(processWithSubscriptionsProcessId1Pdk1, Map.of("key1", "A", "key2", "B"));
    // 1 instance for process 1 version 2 = 2 subscriptions
    createInstance(processWithSubscriptionsProcessId1Pdk2, Map.of("key1", "A", "key2", "B"));
    // 1 instance for process 2 version 1 = 2 subscriptions
    createInstance(processWithSubscriptionsProcessId2Pdk1, Map.of("key1", "A", "key2", "B"));

    waitForProcessInstances(
        camundaClient,
        f ->
            f.processDefinitionKey(
                    p ->
                        p.in(
                            processWithSubscriptionsProcessId1Pdk1,
                            processWithSubscriptionsProcessId1Pdk2,
                            processWithSubscriptionsProcessId2Pdk1))
                .state(ProcessInstanceState.ACTIVE),
        4);

    waitForMessageSubscriptions(
        camundaClient, f -> f.messageSubscriptionState(MessageSubscriptionState.CREATED), 8);
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

    Awaitility.await()
        .untilAsserted(
            () -> {
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
            });
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

    Awaitility.await()
        .untilAsserted(
            () -> {
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
            });
  }

  @Test
  void shouldReturnStatisticsAndFilterByHasRetriesLeft() {
    // given
    final var processDefinitionKey =
        TestHelper.deployResource(camundaClient, "process/service_tasks_v2.bpmn")
            .getProcesses()
            .getFirst()
            .getProcessDefinitionKey();
    final Map<String, Object> variables = Map.of("path", 222);
    TestHelper.startProcessInstance(camundaClient, "service_tasks_v2", variables);

    try (final JobWorker ignored =
        camundaClient
            .newWorker()
            .jobType("taskC")
            .handler((client, job) -> client.newFailCommand(job).retries(1).send().join())
            .open()) {

      waitUntilJobWorkerHasFailedJob(camundaClient, variables, 1);

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

  @Test
  void shouldCountMultipleSubscriptionsInSameProcessInstance() {
    // when
    final var actual =
        camundaClient
            .newProcessDefinitionMessageSubscriptionStatisticsRequest()
            .filter(f -> f.processDefinitionId(PROCESS_WITH_SUBSCRIPTIONS_PROCESS_ID_2))
            .send()
            .join();

    // then
    assertThat(actual.items()).hasSize(1);
    final var stats = actual.items().getFirst();
    assertThat(stats.getActiveSubscriptions()).isEqualTo(2L);
    // But only 1 process instance has subscriptions
    assertThat(stats.getProcessInstancesWithActiveSubscriptions()).isEqualTo(1L);
    assertThat(stats.getProcessDefinitionKey())
        .isEqualTo(String.valueOf(processWithSubscriptionsProcessId2Pdk1));
    assertThat(stats.getProcessDefinitionId()).isEqualTo(PROCESS_WITH_SUBSCRIPTIONS_PROCESS_ID_2);
  }

  @Test
  void shouldCountMultipleSubscriptionsInMultipleProcessInstance() {
    // when
    final var actual =
        camundaClient
            .newProcessDefinitionMessageSubscriptionStatisticsRequest()
            .filter(f -> f.processDefinitionKey(processWithSubscriptionsProcessId1Pdk1))
            .send()
            .join();

    // then
    assertThat(actual.items()).hasSize(1);
    final var stats = actual.items().getFirst();
    assertThat(stats.getProcessDefinitionKey())
        .isEqualTo(String.valueOf(processWithSubscriptionsProcessId1Pdk1));
    assertThat(stats.getProcessDefinitionId()).isEqualTo(PROCESS_WITH_SUBSCRIPTIONS_PROCESS_ID_1);
    assertThat(stats.getActiveSubscriptions()).isEqualTo(4L);
    // But only 2 process instances have subscriptions
    assertThat(stats.getProcessInstancesWithActiveSubscriptions()).isEqualTo(2L);
  }

  @Test
  void shouldCountMultipleSubscriptionsInMultipleProcessInstancePaginated() {
    // when
    final var actual =
        camundaClient
            .newProcessDefinitionMessageSubscriptionStatisticsRequest()
            .page(p -> p.limit(1))
            .send()
            .join();

    // then
    assertThat(actual.items()).hasSize(1);
    final var endCursor = actual.page().endCursor();
    assertThat(endCursor).isNotBlank();
    final var stats = actual.items().getFirst();
    assertThat(stats.getProcessDefinitionKey())
        .isEqualTo(String.valueOf(processWithSubscriptionsProcessId1Pdk1));
    assertThat(stats.getProcessDefinitionId()).isEqualTo(PROCESS_WITH_SUBSCRIPTIONS_PROCESS_ID_1);
    assertThat(stats.getActiveSubscriptions()).isEqualTo(4L);
    // But only 2 process instances have subscriptions
    assertThat(stats.getProcessInstancesWithActiveSubscriptions()).isEqualTo(2L);

    // when - fetch next page
    final var actualPage2 =
        camundaClient
            .newProcessDefinitionMessageSubscriptionStatisticsRequest()
            .page(p -> p.limit(1).after(endCursor))
            .send()
            .join();
    // then
    assertThat(actualPage2.items()).hasSize(1);
    final var statsPage2 = actualPage2.items().getFirst();
    assertThat(statsPage2.getProcessDefinitionKey())
        .isEqualTo(String.valueOf(processWithSubscriptionsProcessId1Pdk2));
    assertThat(statsPage2.getProcessDefinitionId())
        .isEqualTo(PROCESS_WITH_SUBSCRIPTIONS_PROCESS_ID_1);
    assertThat(statsPage2.getActiveSubscriptions()).isEqualTo(2L);
    // But only 1 process instance has subscriptions
    assertThat(statsPage2.getProcessInstancesWithActiveSubscriptions()).isEqualTo(1L);
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
    return createInstance(processDefinitionKey, Map.of());
  }

  private static ProcessInstanceEvent createInstance(
      final long processDefinitionKey, final Map<String, Object> variables) {
    return TestHelper.startScopedProcessInstance(
        camundaClient, processDefinitionKey, testScopeId, variables);
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

  private static BpmnModelInstance createBpmnModelVersion(
      final String version, final String processId) {
    // Allows creating different versions of the same process
    final var versionedId = "versionedId" + version;
    return Bpmn.createExecutableProcess(processId)
        .startEvent()
        .parallelGateway(versionedId)
        .intermediateCatchEvent(
            "catch1", e -> e.message(m -> m.name("msg1").zeebeCorrelationKeyExpression("key1")))
        .endEvent("end1")
        .moveToNode(versionedId)
        .intermediateCatchEvent(
            "catch2", e -> e.message(m -> m.name("msg2").zeebeCorrelationKeyExpression("key2")))
        .endEvent("end2")
        .done();
  }
}
