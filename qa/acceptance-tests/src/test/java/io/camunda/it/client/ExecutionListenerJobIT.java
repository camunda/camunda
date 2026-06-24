/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static io.camunda.it.util.TestHelper.deployProcessAndWaitForIt;
import static io.camunda.it.util.TestHelper.startProcessInstance;
import static io.camunda.qa.util.multidb.CamundaMultiDBExtension.TIMEOUT_DATA_AVAILABILITY;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.enums.JobKind;
import io.camunda.client.api.search.enums.JobState;
import io.camunda.client.api.search.enums.ListenerEventType;
import io.camunda.client.api.search.filter.JobFilter;
import io.camunda.client.api.search.response.Job;
import io.camunda.qa.util.compatibility.CompatibilityTest;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@MultiDbTest
@CompatibilityTest
public class ExecutionListenerJobIT {

  private static final String PROCESS_ID = "before_all_el_process";
  private static final String SERVICE_TASK_ID = "mi_service_task";
  private static final String SERVICE_TASK_JOB_TYPE = "mi-service-task-job";
  private static final String BEFORE_ALL_EL_JOB_TYPE = "before-all-execution-listener";
  private static final List<Integer> ITEMS = List.of(1, 2, 3);

  private static CamundaClient camundaClient;
  private static long processInstanceKey;

  @BeforeAll
  static void beforeAll() {
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask(
                SERVICE_TASK_ID,
                t ->
                    t.zeebeJobType(SERVICE_TASK_JOB_TYPE)
                        .zeebeBeforeAllExecutionListener(BEFORE_ALL_EL_JOB_TYPE)
                        .multiInstance(
                            m ->
                                m.parallel()
                                    .zeebeInputCollectionExpression("items")
                                    .zeebeInputElement("item")))
            .endEvent()
            .done();

    deployProcessAndWaitForIt(camundaClient, process, "before_all_el_process.bpmn");
    processInstanceKey = startProcessInstance(camundaClient, PROCESS_ID).getProcessInstanceKey();

    waitUntilJobAvailable(
        f -> f.processInstanceKey(processInstanceKey).type(BEFORE_ALL_EL_JOB_TYPE), 1);
  }

  @Test
  void shouldSearchBeforeAllExecutionListenerJobByListenerEventType() {
    // when
    final var result =
        camundaClient
            .newJobSearchRequest()
            .filter(
                f ->
                    f.processInstanceKey(processInstanceKey)
                        .listenerEventType(ListenerEventType.BEFORE_ALL))
            .send()
            .join();

    // then
    assertThat(result.items()).hasSize(1);
    final Job beforeAllJob = result.items().getFirst();
    assertThat(beforeAllJob.getType()).isEqualTo(BEFORE_ALL_EL_JOB_TYPE);
    assertThat(beforeAllJob.getKind()).isEqualTo(JobKind.EXECUTION_LISTENER);
    assertThat(beforeAllJob.getListenerEventType()).isEqualTo(ListenerEventType.BEFORE_ALL);
    assertThat(beforeAllJob.getState()).isEqualTo(JobState.CREATED);
    assertThat(beforeAllJob.getElementId()).isEqualTo(SERVICE_TASK_ID);
  }

  @Test
  void shouldSearchCompletedBeforeAllExecutionListenerJob() {
    // given — a fresh process instance whose beforeAll listener is then completed
    final long localProcessInstanceKey =
        startProcessInstance(camundaClient, PROCESS_ID).getProcessInstanceKey();
    waitUntilJobAvailable(
        f -> f.processInstanceKey(localProcessInstanceKey).type(BEFORE_ALL_EL_JOB_TYPE), 1);

    final long beforeAllJobKey =
        camundaClient
            .newJobSearchRequest()
            .filter(f -> f.processInstanceKey(localProcessInstanceKey).type(BEFORE_ALL_EL_JOB_TYPE))
            .send()
            .join()
            .items()
            .getFirst()
            .getJobKey();

    // when — the beforeAll listener is completed
    camundaClient
        .newCompleteCommand(beforeAllJobKey)
        .variables(Map.of("items", ITEMS))
        .send()
        .join();

    // then — the completed listener job is searchable in secondary storage
    Awaitility.await("completed beforeAll listener job should be searchable")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var items =
                  camundaClient
                      .newJobSearchRequest()
                      .filter(
                          f ->
                              f.processInstanceKey(localProcessInstanceKey)
                                  .listenerEventType(ListenerEventType.BEFORE_ALL)
                                  .state(JobState.COMPLETED))
                      .send()
                      .join()
                      .items();
              assertThat(items).hasSize(1);
              final Job completed = items.getFirst();
              assertThat(completed.getJobKey()).isEqualTo(beforeAllJobKey);
              assertThat(completed.getType()).isEqualTo(BEFORE_ALL_EL_JOB_TYPE);
              assertThat(completed.getKind()).isEqualTo(JobKind.EXECUTION_LISTENER);
              assertThat(completed.getListenerEventType()).isEqualTo(ListenerEventType.BEFORE_ALL);
              assertThat(completed.getState()).isEqualTo(JobState.COMPLETED);
            });
  }

  private static void waitUntilJobAvailable(
      final Consumer<JobFilter> filter, final int expectedCount) {
    Awaitility.await("should wait until expected jobs are available")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var result = camundaClient.newJobSearchRequest().filter(filter).send().join();
              assertThat(result.items()).hasSize(expectedCount);
            });
  }
}
