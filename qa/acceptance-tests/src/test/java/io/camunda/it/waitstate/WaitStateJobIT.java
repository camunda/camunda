/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.waitstate;

import static io.camunda.it.util.TestHelper.cancelInstance;
import static io.camunda.it.util.TestHelper.completeJob;
import static io.camunda.it.util.TestHelper.deployProcessAndWaitForIt;
import static io.camunda.it.util.TestHelper.deployResource;
import static io.camunda.it.util.TestHelper.startProcessInstance;
import static io.camunda.it.util.TestHelper.waitForProcessInstanceToBeTerminated;
import static io.camunda.it.util.TestHelper.waitForProcessInstancesToStart;
import static io.camunda.it.util.TestHelper.waitForProcessesToBeDeployed;
import static io.camunda.qa.util.multidb.CamundaMultiDBExtension.TIMEOUT_DATA_AVAILABILITY;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.MigrationPlan;
import io.camunda.client.api.search.enums.JobKind;
import io.camunda.client.api.search.enums.ListenerEventType;
import io.camunda.client.api.search.enums.WaitStateElementType;
import io.camunda.client.api.search.enums.WaitStateType;
import io.camunda.client.api.search.response.ElementInstanceWaitStateResult;
import io.camunda.client.api.search.response.JobWaitStateDetails;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.util.List;
import java.util.stream.Stream;
import org.awaitility.Awaitility;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Verifies the element instance wait-state search command against all job-based wait-state element
 * types. The main process ({@code waitStateProcess}) forks into ten parallel branches covering:
 * service task, send task, script task, business-rule task (all {@code BPMN_ELEMENT} jobs), a
 * subprocess with a start execution listener ({@code EXECUTION_LISTENER}), a Zeebe user task with a
 * creating task listener ({@code TASK_LISTENER}), a legacy job-based user task ({@code
 * BPMN_ELEMENT}), an intermediate message throw event ({@code BPMN_ELEMENT}), an end message throw
 * event ({@code BPMN_ELEMENT}), and an ad-hoc subprocess ({@code AD_HOC_SUB_PROCESS}). A second
 * process ({@code waitStateProcessWithListener}) exercises a process-level start execution listener
 * ({@code PROCESS} element type). Jobs are intentionally never activated or completed so all wait
 * states persist for the duration of the test.
 */
@MultiDbTest
public class WaitStateJobIT {

  private static final String PROCESS_ID = "waitStateProcess";
  private static final String PROCESS_LISTENER_PROCESS_ID = "waitStateProcessWithListener";

  private static final String SERVICE_TASK = "service-task";
  private static final String SEND_TASK = "send-task";
  private static final String SCRIPT_TASK = "script-task";
  private static final String BUSINESS_RULE_TASK = "business-rule-task";
  private static final String SUBPROCESS_EL = "subprocess-el";
  private static final String USER_TASK_TL = "user-task-tl";
  private static final String JOB_USER_TASK = "job-user-task";
  private static final String INT_MSG_THROW = "int-msg-throw";
  private static final String END_MSG_EVENT = "end-msg-event";
  private static final String AHSP = "ahsp";

  private static CamundaClient camundaClient;

  @BeforeAll
  static void beforeAll() {
    // Main process: 10 parallel branches, one per supported job-based element type.
    final BpmnModelInstance mainProcess =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .parallelGateway("fork")
            .serviceTask(SERVICE_TASK)
            .zeebeJobType("svc")
            .moveToNode("fork")
            .sendTask(SEND_TASK)
            .zeebeJobType("snd")
            .moveToNode("fork")
            .scriptTask(SCRIPT_TASK)
            .zeebeJobType("scr")
            .moveToNode("fork")
            .businessRuleTask(BUSINESS_RULE_TASK)
            .zeebeJobType("biz")
            .moveToNode("fork")
            .subProcess(
                SUBPROCESS_EL,
                s ->
                    s.zeebeExecutionListener(l -> l.start().type("sub-listener"))
                        .embeddedSubProcess()
                        .startEvent()
                        .endEvent())
            .moveToNode("fork")
            .userTask(
                USER_TASK_TL,
                t -> t.zeebeUserTask().zeebeTaskListener(l -> l.creating().type("task-listener")))
            .moveToNode("fork")
            .userTask(JOB_USER_TASK) // legacy job-worker user task; job type =
            // Protocol.USER_TASK_JOB_TYPE
            .moveToNode("fork")
            .intermediateThrowEvent(INT_MSG_THROW)
            .message("int-msg")
            .zeebeJobType("int-msg-svc")
            .endEvent()
            .moveToNode("fork")
            .endEvent(END_MSG_EVENT)
            .message("end-msg")
            .zeebeJobType("end-msg-svc")
            .moveToNode("fork")
            .adHocSubProcess(AHSP, a -> a.task("ahsp-inner"))
            .zeebeJobType("ahsp-job")
            .done();

    // Second process: process-level start execution listener only.
    final BpmnModelInstance listenerProcess =
        Bpmn.createExecutableProcess(PROCESS_LISTENER_PROCESS_ID)
            .zeebeExecutionListener(l -> l.start().type("process-listener"))
            .startEvent()
            .endEvent()
            .done();

    deployResource(camundaClient, mainProcess, "waitStateProcess.bpmn");
    deployResource(camundaClient, listenerProcess, "waitStateProcessWithListener.bpmn");
    waitForProcessesToBeDeployed(camundaClient, 2);

    startProcessInstance(camundaClient, PROCESS_ID);
    startProcessInstance(camundaClient, PROCESS_LISTENER_PROCESS_ID);
    waitForProcessInstancesToStart(camundaClient, 2);

    // 10 from the main process + 1 from the process-level listener process.
    waitForWaitStates(11);
  }

  @Test
  void shouldReturnAllJobWaitStates() {
    // when
    final var result = camundaClient.newElementInstanceWaitStateSearchRequest().send().join();

    // then
    assertThat(result.items()).hasSize(11);
    assertThat(result.items())
        .extracting(ElementInstanceWaitStateResult::getElementType)
        .containsExactlyInAnyOrder(
            WaitStateElementType.SERVICE_TASK,
            WaitStateElementType.SEND_TASK,
            WaitStateElementType.SCRIPT_TASK,
            WaitStateElementType.BUSINESS_RULE_TASK,
            WaitStateElementType.SUB_PROCESS, // subprocess execution listener
            WaitStateElementType.SUB_PROCESS, // ad-hoc subprocess
            WaitStateElementType.USER_TASK, // native user task task listener
            WaitStateElementType.USER_TASK, // job-based user task
            WaitStateElementType.INTERMEDIATE_THROW_EVENT,
            WaitStateElementType.END_EVENT,
            WaitStateElementType.PROCESS);
  }

  @ParameterizedTest(name = "{1} / {3} / listenerEventType={4}")
  @MethodSource("elementTypeTestCases")
  void shouldFilterByElementType(
      final WaitStateElementType elementType,
      final String expectedElementId,
      final String expectedJobType,
      final JobKind expectedJobKind,
      final @Nullable ListenerEventType expectedListenerEventType,
      final int expectedRetries) {
    assertSingleJobWaitState(
        elementType,
        expectedElementId,
        expectedJobType,
        expectedJobKind,
        expectedListenerEventType,
        expectedRetries);
  }

  static Stream<Arguments> elementTypeTestCases() {
    return Stream.of(
        Arguments.of(
            WaitStateElementType.SERVICE_TASK, SERVICE_TASK, "svc", JobKind.BPMN_ELEMENT, null, 3),
        Arguments.of(
            WaitStateElementType.SEND_TASK, SEND_TASK, "snd", JobKind.BPMN_ELEMENT, null, 3),
        Arguments.of(
            WaitStateElementType.SCRIPT_TASK, SCRIPT_TASK, "scr", JobKind.BPMN_ELEMENT, null, 3),
        Arguments.of(
            WaitStateElementType.BUSINESS_RULE_TASK,
            BUSINESS_RULE_TASK,
            "biz",
            JobKind.BPMN_ELEMENT,
            null,
            3),
        Arguments.of(
            WaitStateElementType.SUB_PROCESS,
            SUBPROCESS_EL,
            "sub-listener",
            JobKind.EXECUTION_LISTENER,
            ListenerEventType.START,
            3),
        Arguments.of(
            WaitStateElementType.USER_TASK,
            USER_TASK_TL,
            "task-listener",
            JobKind.TASK_LISTENER,
            ListenerEventType.CREATING,
            3),
        Arguments.of(
            WaitStateElementType.USER_TASK,
            JOB_USER_TASK,
            "io.camunda.zeebe:userTask",
            JobKind.BPMN_ELEMENT,
            null,
            1),
        Arguments.of(
            WaitStateElementType.INTERMEDIATE_THROW_EVENT,
            INT_MSG_THROW,
            "int-msg-svc",
            JobKind.BPMN_ELEMENT,
            null,
            3),
        Arguments.of(
            WaitStateElementType.END_EVENT,
            END_MSG_EVENT,
            "end-msg-svc",
            JobKind.BPMN_ELEMENT,
            null,
            3),
        Arguments.of(
            WaitStateElementType.SUB_PROCESS,
            AHSP,
            "ahsp-job",
            JobKind.AD_HOC_SUB_PROCESS,
            null,
            3),
        Arguments.of(
            WaitStateElementType.PROCESS,
            PROCESS_LISTENER_PROCESS_ID,
            "process-listener",
            JobKind.EXECUTION_LISTENER,
            ListenerEventType.START,
            3));
  }

  @Test
  void shouldFilterByWaitStateTypeJob() {
    // when
    final var result =
        camundaClient
            .newElementInstanceWaitStateSearchRequest()
            .filter(f -> f.waitStateType(WaitStateType.JOB))
            .send()
            .join();

    // then
    assertThat(result.items()).hasSize(11);
  }

  @Test
  void shouldFilterByElementId() {
    // when
    final var result =
        camundaClient
            .newElementInstanceWaitStateSearchRequest()
            .filter(f -> f.elementId(SCRIPT_TASK))
            .send()
            .join();

    // then
    assertThat(result.items()).hasSize(1);
    assertThat(result.items().getFirst().getElementType())
        .isEqualTo(WaitStateElementType.SCRIPT_TASK);
  }

  @Test
  void shouldFilterByElementTypeIn() {
    // when
    final var result =
        camundaClient
            .newElementInstanceWaitStateSearchRequest()
            .filter(
                f ->
                    f.elementType(
                        t ->
                            t.in(WaitStateElementType.SCRIPT_TASK, WaitStateElementType.SEND_TASK)))
            .send()
            .join();

    // then
    assertThat(result.items()).hasSize(2);
    assertThat(result.items())
        .extracting(ElementInstanceWaitStateResult::getElementType)
        .containsExactlyInAnyOrder(
            WaitStateElementType.SCRIPT_TASK, WaitStateElementType.SEND_TASK);
  }

  @Test
  void shouldRemoveWaitStateWhenJobIsCompleted() {
    // given — a dedicated single-task process, isolated from the shared @BeforeAll state
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("waitStateRemovalProcess")
            .startEvent()
            .serviceTask("removal-task")
            .zeebeJobType("removal-svc")
            .endEvent()
            .done();
    deployProcessAndWaitForIt(camundaClient, process, "waitStateRemovalProcess.bpmn");

    final long pik =
        startProcessInstance(camundaClient, "waitStateRemovalProcess").getProcessInstanceKey();

    Awaitility.await("wait state should appear")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .untilAsserted(
            () ->
                assertThat(
                        camundaClient
                            .newElementInstanceWaitStateSearchRequest()
                            .filter(f -> f.processInstanceKey(pik))
                            .send()
                            .join()
                            .items())
                    .hasSize(1));

    // when — complete the job
    final long jobKey =
        camundaClient
            .newActivateJobsCommand()
            .jobType("removal-svc")
            .maxJobsToActivate(1)
            .send()
            .join()
            .getJobs()
            .getFirst()
            .getKey();
    completeJob(camundaClient, jobKey);

    // then — wait state is removed
    Awaitility.await("wait state should be removed after job completion")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .untilAsserted(
            () ->
                assertThat(
                        camundaClient
                            .newElementInstanceWaitStateSearchRequest()
                            .filter(f -> f.processInstanceKey(pik))
                            .send()
                            .join()
                            .items())
                    .isEmpty());
  }

  @Test
  void shouldRemoveWaitStateWhenJobIsCanceled() {
    // given — a dedicated single-task process, isolated from the shared @BeforeAll state
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("waitStateCancellationProcess")
            .startEvent()
            .serviceTask("cancellation-task")
            .zeebeJobType("cancellation-svc")
            .endEvent()
            .done();
    deployProcessAndWaitForIt(camundaClient, process, "waitStateCancellationProcess.bpmn");

    final var instance = startProcessInstance(camundaClient, "waitStateCancellationProcess");
    final long pik = instance.getProcessInstanceKey();

    Awaitility.await("wait state should appear")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .untilAsserted(
            () ->
                assertThat(
                        camundaClient
                            .newElementInstanceWaitStateSearchRequest()
                            .filter(f -> f.processInstanceKey(pik))
                            .send()
                            .join()
                            .items())
                    .hasSize(1));

    // when — cancel the process instance, which cancels the active job
    cancelInstance(camundaClient, instance);

    // then — wait state is removed
    Awaitility.await("wait state should be removed after job cancellation")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .untilAsserted(
            () ->
                assertThat(
                        camundaClient
                            .newElementInstanceWaitStateSearchRequest()
                            .filter(f -> f.processInstanceKey(pik))
                            .send()
                            .join()
                            .items())
                    .isEmpty());
  }

  @Test
  void shouldUpdateWaitStateElementIdWhenProcessInstanceIsMigrated() {
    // given — V1 has a service task "source-task"; V2 has the same task renamed to "target-task"
    final BpmnModelInstance v1 =
        Bpmn.createExecutableProcess("waitStateMigrationProcessV1")
            .startEvent()
            .serviceTask("source-task")
            .zeebeJobType("migration-svc")
            .endEvent()
            .done();
    final BpmnModelInstance v2 =
        Bpmn.createExecutableProcess("waitStateMigrationProcessV2")
            .startEvent()
            .serviceTask("target-task")
            .zeebeJobType("migration-svc")
            .endEvent()
            .done();

    final long v1Key =
        deployProcessAndWaitForIt(camundaClient, v1, "waitStateMigrationProcessV1.bpmn")
            .getProcessDefinitionKey();
    final long v2Key =
        deployProcessAndWaitForIt(camundaClient, v2, "waitStateMigrationProcessV2.bpmn")
            .getProcessDefinitionKey();

    final long pik =
        startProcessInstance(camundaClient, "waitStateMigrationProcessV1").getProcessInstanceKey();

    Awaitility.await("wait state with source-task should appear")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .untilAsserted(
            () ->
                assertThat(
                        camundaClient
                            .newElementInstanceWaitStateSearchRequest()
                            .filter(f -> f.processInstanceKey(pik))
                            .send()
                            .join()
                            .items())
                    .hasSize(1)
                    .allSatisfy(item -> assertThat(item.getElementId()).isEqualTo("source-task")));

    // when — migrate the process instance, remapping source-task → target-task
    camundaClient
        .newMigrateProcessInstanceCommand(pik)
        .migrationPlan(
            MigrationPlan.newBuilder()
                .withTargetProcessDefinitionKey(v2Key)
                .addMappingInstruction("source-task", "target-task")
                .build())
        .send()
        .join();

    // then — wait state is updated to reflect the new element id
    Awaitility.await("wait state should be updated with target-task element id")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .untilAsserted(
            () ->
                assertThat(
                        camundaClient
                            .newElementInstanceWaitStateSearchRequest()
                            .filter(f -> f.processInstanceKey(pik))
                            .send()
                            .join()
                            .items())
                    .hasSize(1)
                    .allSatisfy(item -> assertThat(item.getElementId()).isEqualTo("target-task")));

    // cleanup — cancel the instance so the wait state is removed and doesn't leak into other tests
    camundaClient.newCancelInstanceCommand(pik).execute();
    waitForProcessInstanceToBeTerminated(camundaClient, pik);
    Awaitility.await("wait state should be removed after cleanup")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .untilAsserted(
            () ->
                assertThat(
                        camundaClient
                            .newElementInstanceWaitStateSearchRequest()
                            .filter(f -> f.processInstanceKey(pik))
                            .send()
                            .join()
                            .items())
                    .isEmpty());
  }

  /**
   * Filters by both element type and element id, then asserts the wait state type, element type,
   * element id, and all fields of the typed {@link JobWaitStateDetails}.
   */
  private static void assertSingleJobWaitState(
      final WaitStateElementType elementType,
      final String expectedElementId,
      final String expectedJobType,
      final JobKind expectedJobKind,
      final @Nullable ListenerEventType expectedListenerEventType,
      final int expectedRetries) {
    // when
    final var result =
        camundaClient
            .newElementInstanceWaitStateSearchRequest()
            .filter(f -> f.elementType(elementType).elementId(expectedElementId))
            .send()
            .join();

    // then
    assertThat(result.items()).hasSize(1);
    final var item = result.items().getFirst();
    assertThat(item.getElementId()).isEqualTo(expectedElementId);
    assertThat(item.getElementType()).isEqualTo(elementType);
    assertThat(item.getDetails()).isInstanceOf(JobWaitStateDetails.class);
    final var jobDetails = (JobWaitStateDetails) item.getDetails();
    assertThat(jobDetails.getWaitStateType()).isEqualTo(WaitStateType.JOB);
    assertThat(jobDetails.getJobType()).isEqualTo(expectedJobType);
    assertThat(jobDetails.getJobKind()).isEqualTo(expectedJobKind);
    assertThat(jobDetails.getListenerEventType()).isEqualTo(expectedListenerEventType);
    assertThat(jobDetails.getRetries()).isEqualTo(expectedRetries);
    assertThat(jobDetails.getJobKey()).isNotNull();
  }

  private static void waitForWaitStates(final int expectedCount) {
    Awaitility.await("should export %d wait states".formatted(expectedCount))
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .untilAsserted(
            () -> {
              final List<ElementInstanceWaitStateResult> items =
                  camundaClient.newElementInstanceWaitStateSearchRequest().send().join().items();
              assertThat(items).hasSize(expectedCount);
            });
  }
}
