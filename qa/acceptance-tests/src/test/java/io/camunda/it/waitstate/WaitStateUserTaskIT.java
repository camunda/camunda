/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.waitstate;

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
import io.camunda.client.api.search.enums.WaitStateElementType;
import io.camunda.client.api.search.enums.WaitStateType;
import io.camunda.client.api.search.response.ElementInstanceWaitStateResult;
import io.camunda.client.api.search.response.UserTaskWaitStateDetails;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.time.OffsetDateTime;
import java.util.List;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Verifies the element instance wait-state search command against native Camunda user tasks (tasks
 * declared with {@code zeebeUserTask()}). A native user task does not create a BPMN_ELEMENT job, so
 * it produces a dedicated {@code USER_TASK} wait state carrying the task key and due date.
 *
 * <p>The shared {@code @BeforeAll} fixture starts a single-instance process and a root → child
 * call-activity hierarchy, exercising single-instance and cross-process-instance tracking.
 * Migration and removal (complete/cancel) are exercised in self-contained tests with dedicated
 * processes.
 */
@MultiDbTest
public class WaitStateUserTaskIT {

  private static final String SINGLE_PROCESS_ID = "waitStateUserTaskSingle";
  private static final String ROOT_PROCESS_ID = "waitStateUserTaskRoot";
  private static final String CHILD_PROCESS_ID = "waitStateUserTaskChild";

  private static final String SINGLE_TASK = "single-task";
  private static final String ROOT_TASK = "root-task";
  private static final String CHILD_TASK = "child-task";

  private static final String DUE_DATE = "2026-10-13T10:00:00Z";

  private static CamundaClient camundaClient;

  private static long rootProcessInstanceKey;

  @BeforeAll
  static void beforeAll() {
    final BpmnModelInstance singleProcess =
        Bpmn.createExecutableProcess(SINGLE_PROCESS_ID)
            .startEvent()
            .userTask(SINGLE_TASK, t -> t.zeebeUserTask().zeebeDueDate(DUE_DATE))
            .endEvent()
            .done();

    // Root process: one branch is a native user task, another calls the child process.
    final BpmnModelInstance rootProcess =
        Bpmn.createExecutableProcess(ROOT_PROCESS_ID)
            .startEvent()
            .parallelGateway("fork")
            .userTask(ROOT_TASK, t -> t.zeebeUserTask())
            .moveToNode("fork")
            .callActivity("call-activity")
            .zeebeProcessId(CHILD_PROCESS_ID)
            .done();
    final BpmnModelInstance childProcess =
        Bpmn.createExecutableProcess(CHILD_PROCESS_ID)
            .startEvent()
            .userTask(CHILD_TASK, t -> t.zeebeUserTask())
            .endEvent()
            .done();

    deployResource(camundaClient, singleProcess, "waitStateUserTaskSingle.bpmn");
    deployResource(camundaClient, rootProcess, "waitStateUserTaskRoot.bpmn");
    deployResource(camundaClient, childProcess, "waitStateUserTaskChild.bpmn");
    waitForProcessesToBeDeployed(camundaClient, 3);

    startProcessInstance(camundaClient, SINGLE_PROCESS_ID);
    rootProcessInstanceKey =
        startProcessInstance(camundaClient, ROOT_PROCESS_ID).getProcessInstanceKey();
    // single instance + root instance + child instance (via call activity)
    waitForProcessInstancesToStart(camundaClient, 3);

    // single-task + root-task + child-task
    waitForWaitStates(3);
  }

  @Test
  void shouldReturnUserTaskWaitStateForSingleInstance() {
    // when
    final var result =
        camundaClient
            .newElementInstanceWaitStateSearchRequest()
            .filter(f -> f.elementId(SINGLE_TASK))
            .send()
            .join();

    // then
    assertThat(result.items()).hasSize(1);
    final var item = result.items().getFirst();
    assertThat(item.getWaitStateType()).isEqualTo(WaitStateType.USER_TASK);
    assertThat(item.getElementType()).isEqualTo(WaitStateElementType.USER_TASK);
    assertThat(item.getElementId()).isEqualTo(SINGLE_TASK);
    assertThat(item.getDetails()).isInstanceOf(UserTaskWaitStateDetails.class);
    final var details = (UserTaskWaitStateDetails) item.getDetails();
    assertThat(details.getWaitStateType()).isEqualTo(WaitStateType.USER_TASK);
    assertThat(details.getTaskKey()).isNotNull();
    assertThat(OffsetDateTime.parse(details.getDueDate()))
        .isEqualTo(OffsetDateTime.parse(DUE_DATE));
  }

  @Test
  void shouldTrackUserTaskWaitStatesAcrossCallActivity() {
    // when — both root and child user tasks share the root process instance key
    final var byRoot =
        camundaClient
            .newElementInstanceWaitStateSearchRequest()
            .filter(f -> f.rootProcessInstanceKey(rootProcessInstanceKey))
            .send()
            .join();

    // then
    assertThat(byRoot.items()).hasSize(2);
    assertThat(byRoot.items())
        .allSatisfy(
            item -> {
              assertThat(item.getWaitStateType()).isEqualTo(WaitStateType.USER_TASK);
              assertThat(item.getRootProcessInstanceKey())
                  .isEqualTo(String.valueOf(rootProcessInstanceKey));
            });
    assertThat(byRoot.items())
        .extracting(ElementInstanceWaitStateResult::getElementId)
        .containsExactlyInAnyOrder(ROOT_TASK, CHILD_TASK);

    // when — the child user task lives in its own (child) process instance
    final var child = byElementId(byRoot.items(), CHILD_TASK);
    final long childProcessInstanceKey = Long.parseLong(child.getProcessInstanceKey());
    assertThat(childProcessInstanceKey).isNotEqualTo(rootProcessInstanceKey);

    final var byChild =
        camundaClient
            .newElementInstanceWaitStateSearchRequest()
            .filter(f -> f.processInstanceKey(childProcessInstanceKey))
            .send()
            .join();

    // then
    assertThat(byChild.items()).hasSize(1);
    assertThat(byChild.items().getFirst().getElementId()).isEqualTo(CHILD_TASK);
  }

  @Test
  void shouldRemoveWaitStateWhenUserTaskIsCompleted() {
    // given — a dedicated single-task process, isolated from the shared @BeforeAll state
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("waitStateUserTaskRemoval")
            .startEvent()
            .userTask("removal-task", t -> t.zeebeUserTask())
            .endEvent()
            .done();
    deployProcessAndWaitForIt(camundaClient, process, "waitStateUserTaskRemoval.bpmn");

    final long pik =
        startProcessInstance(camundaClient, "waitStateUserTaskRemoval").getProcessInstanceKey();

    final long userTaskKey = awaitSingleWaitStateTaskKey(pik);

    // when — complete the user task
    camundaClient.newCompleteUserTaskCommand(userTaskKey).send().join();

    // then — wait state is removed
    awaitNoWaitState(pik);
  }

  @Test
  void shouldRemoveWaitStateWhenUserTaskInstanceIsCanceled() {
    // given — a dedicated single-task process, isolated from the shared @BeforeAll state
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("waitStateUserTaskCancellation")
            .startEvent()
            .userTask("cancellation-task", t -> t.zeebeUserTask())
            .endEvent()
            .done();
    deployProcessAndWaitForIt(camundaClient, process, "waitStateUserTaskCancellation.bpmn");

    final long pik =
        startProcessInstance(camundaClient, "waitStateUserTaskCancellation")
            .getProcessInstanceKey();
    awaitSingleWaitStateTaskKey(pik);

    // when — cancel the process instance, which cancels the active user task
    camundaClient.newCancelInstanceCommand(pik).send().join();

    // then — wait state is removed
    awaitNoWaitState(pik);
  }

  @Test
  void shouldUpdateWaitStateDueDateWhenUserTaskIsUpdated() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("waitStateUserTaskUpdate")
            .startEvent()
            .userTask("update-task", t -> t.zeebeUserTask().zeebeDueDate(DUE_DATE))
            .endEvent()
            .done();
    deployProcessAndWaitForIt(camundaClient, process, "waitStateUserTaskUpdate.bpmn");

    final long pik =
        startProcessInstance(camundaClient, "waitStateUserTaskUpdate").getProcessInstanceKey();
    final long userTaskKey = awaitSingleWaitStateTaskKey(pik);

    final String newDueDate = "2027-01-01T00:00:00Z";

    // when — update the user task due date
    camundaClient.newUpdateUserTaskCommand(userTaskKey).dueDate(newDueDate).send().join();

    // then — the wait state details reflect the new due date
    Awaitility.await("wait state due date should be updated")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .untilAsserted(
            () -> {
              final var items =
                  camundaClient
                      .newElementInstanceWaitStateSearchRequest()
                      .filter(f -> f.processInstanceKey(pik))
                      .send()
                      .join()
                      .items();
              assertThat(items).hasSize(1);
              assertThat(items.getFirst().getDetails())
                  .isInstanceOfSatisfying(
                      UserTaskWaitStateDetails.class,
                      d ->
                          assertThat(OffsetDateTime.parse(d.getDueDate()))
                              .isEqualTo(OffsetDateTime.parse(newDueDate)));
            });

    // cleanup
    camundaClient.newCancelInstanceCommand(pik).send().join();
    waitForProcessInstanceToBeTerminated(camundaClient, pik);
    awaitNoWaitState(pik);
  }

  @Test
  void shouldUpdateWaitStateElementIdWhenProcessInstanceIsMigrated() {
    // given — V1 has a user task "source-task"; V2 has the same task renamed to "target-task"
    final BpmnModelInstance v1 =
        Bpmn.createExecutableProcess("waitStateUserTaskMigrationV1")
            .startEvent()
            .userTask("source-task", t -> t.zeebeUserTask())
            .endEvent()
            .done();
    final BpmnModelInstance v2 =
        Bpmn.createExecutableProcess("waitStateUserTaskMigrationV2")
            .startEvent()
            .userTask("target-task", t -> t.zeebeUserTask())
            .endEvent()
            .done();

    deployProcessAndWaitForIt(camundaClient, v1, "waitStateUserTaskMigrationV1.bpmn");
    final long v2Key =
        deployProcessAndWaitForIt(camundaClient, v2, "waitStateUserTaskMigrationV2.bpmn")
            .getProcessDefinitionKey();

    final long pik =
        startProcessInstance(camundaClient, "waitStateUserTaskMigrationV1").getProcessInstanceKey();

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

    // cleanup — cancel the instance so the wait state doesn't leak into other assertions
    camundaClient.newCancelInstanceCommand(pik).send().join();
    waitForProcessInstanceToBeTerminated(camundaClient, pik);
    awaitNoWaitState(pik);
  }

  private static long awaitSingleWaitStateTaskKey(final long processInstanceKey) {
    Awaitility.await("wait state should appear")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .untilAsserted(
            () ->
                assertThat(
                        camundaClient
                            .newElementInstanceWaitStateSearchRequest()
                            .filter(f -> f.processInstanceKey(processInstanceKey))
                            .send()
                            .join()
                            .items())
                    .hasSize(1));
    final var item =
        camundaClient
            .newElementInstanceWaitStateSearchRequest()
            .filter(f -> f.processInstanceKey(processInstanceKey))
            .send()
            .join()
            .items()
            .getFirst();
    return Long.parseLong(((UserTaskWaitStateDetails) item.getDetails()).getTaskKey());
  }

  private static void awaitNoWaitState(final long processInstanceKey) {
    Awaitility.await("wait state should be removed")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .untilAsserted(
            () ->
                assertThat(
                        camundaClient
                            .newElementInstanceWaitStateSearchRequest()
                            .filter(f -> f.processInstanceKey(processInstanceKey))
                            .send()
                            .join()
                            .items())
                    .isEmpty());
  }

  private static ElementInstanceWaitStateResult byElementId(
      final List<ElementInstanceWaitStateResult> items, final String elementId) {
    return items.stream().filter(i -> elementId.equals(i.getElementId())).findFirst().orElseThrow();
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
