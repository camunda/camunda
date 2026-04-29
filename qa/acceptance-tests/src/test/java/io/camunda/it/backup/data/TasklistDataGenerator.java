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
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.awaitility.Awaitility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TasklistDataGenerator implements AutoCloseable {

  static final String PROCESS_BPMN_PROCESS_ID = "tasklist-backup-process";
  static final String PROCESS_BPMN_PROCESS_ID_2 = "tasklist-backup-process-2";
  static final int PROCESS_INSTANCE_COUNT = 49;
  static final int COMPLETED_TASKS_COUNT = 11;

  private static final Logger LOGGER = LoggerFactory.getLogger(TasklistDataGenerator.class);
  private static final Duration DATA_TIMEOUT = Duration.ofSeconds(90);
  private static final int SEARCH_LIMIT = 200;
  private static final String ASSIGNEE = "demo";

  private CamundaClient camundaClient;

  public TasklistDataGenerator(final CamundaClient camundaClient) {
    this.camundaClient = camundaClient;
  }

  public void setCamundaClient(final CamundaClient camundaClient) {
    this.camundaClient = camundaClient;
  }

  public void createData() {
    LOGGER.info("Starting Tasklist backup test data generation");

    deployProcess(createModel1(), PROCESS_BPMN_PROCESS_ID);
    startProcessInstances(PROCESS_BPMN_PROCESS_ID);

    waitForTaskCount();
    assignAllTasks();
  }

  public void assertData() {
    Awaitility.await("should expose the backed up Tasklist data")
        .atMost(DATA_TIMEOUT)
        .ignoreExceptions()
        .untilAsserted(this::assertDataOneAttempt);
  }

  public void changeData() {
    LOGGER.info("Changing Tasklist backup test data");

    completeTasks();
    deployProcess(createModel2(), PROCESS_BPMN_PROCESS_ID_2);
    startProcessInstances(PROCESS_BPMN_PROCESS_ID_2);
  }

  public void assertDataAfterChange() {
    Awaitility.await("should expose the changed Tasklist data")
        .atMost(DATA_TIMEOUT)
        .ignoreExceptions()
        .untilAsserted(this::assertDataAfterChangeOneAttempt);
  }

  @Override
  public void close() {
    camundaClient = null;
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

  private void startProcessInstances(final String bpmnProcessId) {
    for (int i = 0; i < PROCESS_INSTANCE_COUNT; i++) {
      final long processInstanceKey =
          camundaClient
              .newCreateInstanceCommand()
              .bpmnProcessId(bpmnProcessId)
              .latestVersion()
              .variables(Map.of("var1", "value1"))
              .send()
              .join()
              .getProcessInstanceKey();
      LOGGER.debug("Started process instance {} for process {}", processInstanceKey, bpmnProcessId);
    }
  }

  private void deployProcess(
      final BpmnModelInstance bpmnModelInstance, final String bpmnProcessId) {
    final var deploymentEvent =
        camundaClient
            .newDeployResourceCommand()
            .addProcessModel(bpmnModelInstance, bpmnProcessId + ".bpmn")
            .send()
            .join();
    LOGGER.info("Deployed process {} with key {}", bpmnProcessId, deploymentEvent.getKey());
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

  private BpmnModelInstance createModel2() {
    return Bpmn.createExecutableProcess(PROCESS_BPMN_PROCESS_ID_2)
        .startEvent("start")
        .userTask("task2", AbstractUserTaskBuilder::zeebeUserTask)
        .endEvent()
        .done();
  }
}
