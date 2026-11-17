/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.backup.data;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.enums.UserTaskState;
import io.camunda.client.api.search.filter.UserTaskFilter;
import io.camunda.client.api.search.response.UserTask;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.AbstractUserTaskBuilder;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.awaitility.Awaitility;

public final class TasklistDataGenerator extends AbstractBackupDataGenerator {

  static final String PROCESS_BPMN_PROCESS_ID = "tasklist-backup-process";
  static final String PROCESS_BPMN_PROCESS_ID_2 = "tasklist-backup-process-2";
  static final int PROCESS_INSTANCE_COUNT = 49;
  static final int COMPLETED_TASKS_COUNT = 11;

  private static final String ASSIGNEE = "demo";

  public TasklistDataGenerator(final CamundaClient camundaClient) {
    super(camundaClient);
  }

  @Override
  public void createData() {
    logger.info("Starting Tasklist backup test data generation");

    deployProcess(PROCESS_BPMN_PROCESS_ID, createModel1());
    startProcessInstances(PROCESS_BPMN_PROCESS_ID, PROCESS_INSTANCE_COUNT);

    waitForTaskCount();
    assignAllTasks();
  }

  @Override
  public void assertData() {
    Awaitility.await("should expose the backed up Tasklist data")
        .atMost(DATA_TIMEOUT)
        .ignoreExceptions()
        .untilAsserted(this::assertDataOneAttempt);
  }

  @Override
  public void changeData() {
    logger.info("Changing Tasklist backup test data");

    completeTasks();
    deployProcess(PROCESS_BPMN_PROCESS_ID_2, createModel2());
    startProcessInstances(PROCESS_BPMN_PROCESS_ID_2, PROCESS_INSTANCE_COUNT);
  }

  @Override
  public void assertDataAfterChange() {
    Awaitility.await("should expose the changed Tasklist data")
        .atMost(DATA_TIMEOUT)
        .ignoreExceptions()
        .untilAsserted(this::assertDataAfterChangeOneAttempt);
  }

  private void assertDataOneAttempt() {
    final var originalTasks =
        searchUserTasks(
            filter ->
                filter
                    .bpmnProcessId(PROCESS_BPMN_PROCESS_ID)
                    .elementId("task1")
                    .assignee(ASSIGNEE));

    assertThat(originalTasks).hasSize(PROCESS_INSTANCE_COUNT);
    assertThat(originalTasks).extracting(UserTask::getElementId).containsOnly("task1");
    assertThat(originalTasks).extracting(UserTask::getState).containsOnly(UserTaskState.CREATED);
    assertThat(
            searchUserTasks(
                filter -> filter.bpmnProcessId(PROCESS_BPMN_PROCESS_ID_2).elementId("task2")))
        .isEmpty();
  }

  private void assertDataAfterChangeOneAttempt() {
    final var task1Tasks =
        searchUserTasks(filter -> filter.bpmnProcessId(PROCESS_BPMN_PROCESS_ID).elementId("task1"));
    assertThat(task1Tasks).hasSize(PROCESS_INSTANCE_COUNT);
    assertThat(task1Tasks)
        .filteredOn(task -> UserTaskState.COMPLETED.equals(task.getState()))
        .hasSize(COMPLETED_TASKS_COUNT);
    assertThat(task1Tasks)
        .filteredOn(task -> UserTaskState.CREATED.equals(task.getState()))
        .hasSize(PROCESS_INSTANCE_COUNT - COMPLETED_TASKS_COUNT);

    final var task2Tasks =
        searchUserTasks(
            filter ->
                filter
                    .bpmnProcessId(PROCESS_BPMN_PROCESS_ID_2)
                    .elementId("task2")
                    .state(UserTaskState.CREATED));
    assertThat(task2Tasks).hasSize(PROCESS_INSTANCE_COUNT);
  }

  private void assignAllTasks() {
    final var tasks =
        searchUserTasks(filter -> filter.bpmnProcessId(PROCESS_BPMN_PROCESS_ID).elementId("task1"));
    assertThat(tasks).hasSize(PROCESS_INSTANCE_COUNT);

    tasks.forEach(
        task ->
            camundaClient
                .newAssignUserTaskCommand(task.getUserTaskKey())
                .assignee(ASSIGNEE)
                .send()
                .join());
  }

  private void completeTasks() {
    final var tasks =
        searchUserTasks(
            filter ->
                filter
                    .bpmnProcessId(PROCESS_BPMN_PROCESS_ID)
                    .elementId("task1")
                    .state(UserTaskState.CREATED));
    assertThat(tasks).hasSize(PROCESS_INSTANCE_COUNT);

    tasks.stream()
        .limit(COMPLETED_TASKS_COUNT)
        .forEach(
            task ->
                camundaClient
                    .newCompleteUserTaskCommand(task.getUserTaskKey())
                    .variables(Map.of("varOut", 123))
                    .send()
                    .join());
  }

  private void waitForTaskCount() {
    Awaitility.await("should import all generated Tasklist user tasks")
        .atMost(DATA_TIMEOUT)
        .ignoreExceptions()
        .untilAsserted(
            () ->
                assertThat(
                        searchUserTasks(
                            filter ->
                                filter.bpmnProcessId(PROCESS_BPMN_PROCESS_ID).elementId("task1")))
                    .hasSize(PROCESS_INSTANCE_COUNT));
  }

  private List<UserTask> searchUserTasks(final Consumer<UserTaskFilter> filter) {
    return camundaClient
        .newUserTaskSearchRequest()
        .filter(filter)
        .page(page -> page.limit(SEARCH_LIMIT).from(0))
        .send()
        .join()
        .items();
  }

  private BpmnModelInstance createModel1() {
    return Bpmn.createExecutableProcess(PROCESS_BPMN_PROCESS_ID)
        .startEvent("start")
        .userTask(
            "task1",
            task ->
                task.zeebeUserTask().zeebeInput("=var1", "varIn").zeebeOutput("=varOut", "var2"))
        .serviceTask("task5")
        .zeebeJobType("task5")
        .endEvent()
        .done();
  }

  private static BpmnModelInstance createModel2() {
    return Bpmn.createExecutableProcess(PROCESS_BPMN_PROCESS_ID_2)
        .startEvent("start")
        .userTask("task2", AbstractUserTaskBuilder::zeebeUserTask)
        .endEvent()
        .done();
  }
}
