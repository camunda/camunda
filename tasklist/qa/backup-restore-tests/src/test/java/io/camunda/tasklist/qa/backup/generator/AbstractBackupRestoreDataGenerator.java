/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.qa.backup.generator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.graphql.spring.boot.test.GraphQLResponse;
import io.camunda.tasklist.entities.TaskState;
import io.camunda.tasklist.qa.backup.BackupRestoreTestContext;
import io.camunda.tasklist.qa.backup.TasklistAPICaller;
import io.camunda.tasklist.qa.util.ZeebeTestUtil;
import io.camunda.tasklist.schema.v86.templates.DraftTaskVariableTemplate;
import io.camunda.tasklist.schema.v86.templates.TaskTemplate;
import io.camunda.tasklist.util.ThreadUtil;
import io.camunda.tasklist.webapp.api.rest.v1.entities.SaveVariablesRequest;
import io.camunda.tasklist.webapp.graphql.entity.TaskDTO;
import io.camunda.tasklist.webapp.graphql.entity.VariableInputDTO;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.RandomStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public abstract class AbstractBackupRestoreDataGenerator implements BackupRestoreDataGenerator {

  public static final String PROCESS_BPMN_PROCESS_ID = "basicProcess";
  public static final String PROCESS_BPMN_PROCESS_ID_2 = "basicProcess2";
  public static final int PROCESS_INSTANCE_COUNT = 49;
  private static final int ALL_DRAFT_TASK_VARIABLES_COUNT = PROCESS_INSTANCE_COUNT * 2;
  private static final int COMPLETED_TASKS_COUNT = 11;
  private static final int DRAFT_TASK_VARIABLES_COUNT_AFTER_TASKS_COMPLETION =
      ALL_DRAFT_TASK_VARIABLES_COUNT - COMPLETED_TASKS_COUNT * 2;
  private static final Logger LOGGER =
      LoggerFactory.getLogger(AbstractBackupRestoreDataGenerator.class);

  /**
   * ZeebeClient must not be reused between different test fixtures, as this may be different
   * versions of client in the future.
   */
  private ZeebeClient zeebeClient;

  @Autowired private TasklistAPICaller tasklistAPICaller;

  private List<Long> processInstanceKeys = new ArrayList<>();

  private void init(final BackupRestoreTestContext testContext) {
    zeebeClient =
        ZeebeClient.newClientBuilder()
            .gatewayAddress(testContext.getExternalZeebeContactPoint())
            .usePlaintext()
            .build();

    initClient(testContext);
  }

  protected abstract void initClient(BackupRestoreTestContext testContext);

  @Override
  public void createData(final BackupRestoreTestContext testContext) throws Exception {
    init(testContext);
    try {
      final OffsetDateTime dataGenerationStart = OffsetDateTime.now();
      LOGGER.info("Starting generating data for process {}", PROCESS_BPMN_PROCESS_ID);

      deployProcess(createModel1(PROCESS_BPMN_PROCESS_ID), PROCESS_BPMN_PROCESS_ID);
      processInstanceKeys = startProcessInstances(PROCESS_BPMN_PROCESS_ID, PROCESS_INSTANCE_COUNT);

      waitUntilAllDataAreImported();

      claimAllTasks();

      addDraftVariablesForAllTasks();

      refreshIndices();
      LOGGER.info(
          "Data generation completed in: {} s",
          ChronoUnit.SECONDS.between(dataGenerationStart, OffsetDateTime.now()));
    } finally {
      closeClients();
    }
  }

  @Override
  public void assertData() throws IOException {
    try {
      final GraphQLResponse response = tasklistAPICaller.getAllTasks();
      assertTrue(response.isOk());
      assertEquals(String.valueOf(PROCESS_INSTANCE_COUNT), response.get("$.data.tasks.length()"));
      assertEquals("task1", response.get("$.data.tasks[0].name"));
      assertEquals("CREATED", response.get("$.data.tasks[0].taskState"));
      assertThat(countEntitiesFor(DraftTaskVariableTemplate.INDEX_NAME))
          .isEqualTo(ALL_DRAFT_TASK_VARIABLES_COUNT);
    } catch (final AssertionError er) {
      LOGGER.warn("Error when asserting data: " + er.getMessage());
      throw er;
    }
  }

  @Override
  public void assertDataAfterChange() throws IOException {
    try {
      List<TaskDTO> tasks = tasklistAPICaller.getTasks("task1");
      assertThat(tasks).hasSize(PROCESS_INSTANCE_COUNT);
      assertThat(tasks)
          .filteredOn(t -> t.getTaskState().equals(TaskState.COMPLETED))
          .hasSize(COMPLETED_TASKS_COUNT);
      assertThat(tasks)
          .filteredOn(t -> t.getTaskState().equals(TaskState.CREATED))
          .hasSize(PROCESS_INSTANCE_COUNT - COMPLETED_TASKS_COUNT);

      tasks = tasklistAPICaller.getTasks("task2");
      assertThat(tasks).hasSize(PROCESS_INSTANCE_COUNT);
      assertThat(tasks).extracting("taskState").containsOnly(TaskState.CREATED);

      // after task completion all draft variables associated with a task will be deleted
      assertThat(countEntitiesFor(DraftTaskVariableTemplate.INDEX_NAME))
          .isEqualTo(DRAFT_TASK_VARIABLES_COUNT_AFTER_TASKS_COMPLETION);
    } catch (final AssertionError er) {
      LOGGER.warn("Error when asserting data: " + er.getMessage());
      throw er;
    }
  }

  @Override
  public void changeData(final BackupRestoreTestContext testContext) throws IOException {
    init(testContext);
    try {
      // complete tasks (they are already claimed)
      completeTasks("task1", COMPLETED_TASKS_COUNT);

      // start other process instance
      deployProcess(createModel2(PROCESS_BPMN_PROCESS_ID_2), PROCESS_BPMN_PROCESS_ID_2);

      processInstanceKeys =
          startProcessInstances(PROCESS_BPMN_PROCESS_ID_2, PROCESS_INSTANCE_COUNT);
    } finally {
      closeClients();
    }
  }

  protected abstract void refreshIndices();

  private void addDraftVariablesForAllTasks() throws IOException {
    final var tasks = tasklistAPICaller.getAllTasks().getList("$.data.tasks", TaskDTO.class);
    LOGGER.info("Found '{}' tasks, adding 2 draft variables to each task.", tasks.size());
    tasks.stream()
        .parallel()
        .forEach(
            task ->
                tasklistAPICaller.saveDraftTaskVariables(
                    task.getId(),
                    new SaveVariablesRequest()
                        .setVariables(
                            List.of(
                                new VariableInputDTO()
                                    .setName("var1")
                                    .setValue("\"updatedDraftVarValue\""),
                                new VariableInputDTO()
                                    .setName("draftVar")
                                    .setValue(
                                        "\"" + RandomStringUtils.randomAlphanumeric(10) + "\"")))));
  }

  protected abstract void claimAllTasks();

  private void closeClients() {
    if (zeebeClient != null) {
      zeebeClient.close();
      zeebeClient = null;
    }
  }

  private void waitUntilAllDataAreImported() throws IOException {
    LOGGER.info("Wait till data is imported.");
    long loadedProcessInstances = 0;
    int count = 0;
    final int maxWait = 101;
    while (PROCESS_INSTANCE_COUNT > loadedProcessInstances && count < maxWait) {
      count++;
      loadedProcessInstances = countEntitiesFor(TaskTemplate.INDEX_NAME);
      LOGGER.info(
          "Imported '{}' process instances of '{}'",
          loadedProcessInstances,
          PROCESS_INSTANCE_COUNT);
      ThreadUtil.sleepFor(1000L);
    }
    if (count == maxWait) {
      throw new RuntimeException("Waiting for loading process instances failed: Timeout");
    }
  }

  private List<Long> startProcessInstances(
      final String bpmnProcessId, final int numberOfProcessInstances) {
    for (int i = 0; i < numberOfProcessInstances; i++) {
      final long processInstanceKey =
          ZeebeTestUtil.startProcessInstance(zeebeClient, bpmnProcessId, "{\"var1\": \"value1\"}");
      LOGGER.debug("Started processInstance {} for process {}", processInstanceKey, bpmnProcessId);
      processInstanceKeys.add(processInstanceKey);
    }
    LOGGER.info("{} processInstances started", processInstanceKeys.size());
    return processInstanceKeys;
  }

  private void deployProcess(
      final BpmnModelInstance bpmnModelInstance, final String bpmnProcessId) {
    final String processDefinitionKey =
        ZeebeTestUtil.deployProcess(zeebeClient, bpmnModelInstance, bpmnProcessId + ".bpmn");
    LOGGER.info("Deployed process {} with key {}", bpmnProcessId, processDefinitionKey);
  }

  private BpmnModelInstance createModel1(final String bpmnProcessId) {
    return Bpmn.createExecutableProcess(bpmnProcessId)
        .startEvent("start")
        .userTask("task1")
        .zeebeInput("=var1", "varIn")
        .zeebeOutput("=varOut", "var2")
        .serviceTask("task5")
        .zeebeJobType("task5")
        .endEvent()
        .done();
  }

  private BpmnModelInstance createModel2(final String bpmnProcessId) {
    return Bpmn.createExecutableProcess(bpmnProcessId)
        .startEvent("start")
        .userTask("task2")
        .endEvent()
        .done();
  }

  private long countEntitiesFor(final String indexName) throws IOException {
    return countEntitiesForAlias(getAliasFor(indexName));
  }

  protected abstract long countEntitiesForAlias(String alias) throws IOException;

  private String getAliasFor(final String index) {
    return String.format("tasklist-%s-*_alias", index);
  }

  protected String getMainIndexNameFor(final String index) {
    return String.format("tasklist-%s-*_", index);
  }

  private void completeTasks(final String taskBpmnId, final int completedTasksCount)
      throws IOException {
    final List<TaskDTO> tasks = tasklistAPICaller.getTasks(taskBpmnId);
    for (int i = 0; i < completedTasksCount; i++) {
      tasklistAPICaller.completeTask(tasks.get(i).getId(), "{name: \"varOut\", value: \"123\"}");
    }
  }
}
