/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.qa.backup.generator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.camunda.client.CamundaClient;
import io.camunda.tasklist.qa.backup.BackupRestoreTestContext;
import io.camunda.tasklist.qa.backup.TasklistAPICaller;
import io.camunda.tasklist.qa.util.ZeebeTestUtil;
import io.camunda.tasklist.util.ThreadUtil;
import io.camunda.tasklist.webapp.api.rest.v1.entities.SaveVariablesRequest;
import io.camunda.tasklist.webapp.dto.VariableInputDTO;
import io.camunda.webapps.schema.descriptors.template.DraftTaskVariableTemplate;
import io.camunda.webapps.schema.entities.usertask.TaskState;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.io.IOException;
import java.time.Duration;
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
  public static final int COMPLETED_TASKS_COUNT = 11;
  private static final int ALL_DRAFT_TASK_VARIABLES_COUNT = PROCESS_INSTANCE_COUNT * 2;
  private static final int DRAFT_TASK_VARIABLES_COUNT_AFTER_TASKS_COMPLETION =
      ALL_DRAFT_TASK_VARIABLES_COUNT - COMPLETED_TASKS_COUNT * 2;
  private static final Logger LOGGER =
      LoggerFactory.getLogger(AbstractBackupRestoreDataGenerator.class);
  protected String indexPrefix;

  /**
   * ZeebeClient must not be reused between different test fixtures, as this may be different
   * versions of client in the future.
   */
  private CamundaClient camundaClient;

  @Autowired private TasklistAPICaller tasklistAPICaller;
  private List<Long> processInstanceKeys = new ArrayList<>();

  private void init(final BackupRestoreTestContext testContext) {
    camundaClient =
        CamundaClient.newClientBuilder()
            .gatewayAddress(testContext.getExternalZeebeContactPoint())
            .usePlaintext()
            .build();

    initClient(testContext);

    indexPrefix = testContext.getZeebeIndexPrefix();
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
    await()
        .atMost(Duration.ofSeconds(30))
        .pollDelay(Duration.ofMillis(500))
        .untilAsserted(
            () -> {
              final var response = tasklistAPICaller.getAllTasks();
              assertThat(response).hasSize(PROCESS_INSTANCE_COUNT);
              assertThat(response.getFirst().getName()).isEqualTo("task1");
              assertThat(response.getFirst().getTaskState()).isEqualTo(TaskState.CREATED);
              assertThat(countEntitiesFor(DraftTaskVariableTemplate.INDEX_NAME))
                  .isEqualTo(ALL_DRAFT_TASK_VARIABLES_COUNT);
            });
  }

  @Override
  public void assertDataAfterChange() throws IOException {
    await()
        .atMost(Duration.ofSeconds(30))
        .pollDelay(Duration.ofMillis(500))
        .untilAsserted(
            () -> {
              var tasks = tasklistAPICaller.getTasks("task1");
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
            });
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
    final var tasks = tasklistAPICaller.getAllTasks();
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
    if (camundaClient != null) {
      camundaClient.close();
      camundaClient = null;
    }
  }

  private void waitUntilAllDataAreImported() throws IOException {
    LOGGER.info("Wait till data is imported.");
    int loadedUserTasks = 0;
    int count = 0;
    final int maxWait = 101;
    while (PROCESS_INSTANCE_COUNT > loadedUserTasks && count < maxWait) {
      count++;
      loadedUserTasks = tasklistAPICaller.getAllTasks().size();
      LOGGER.info(
          "Imported '{}' process instances of '{}'", loadedUserTasks, PROCESS_INSTANCE_COUNT);
      assertThat(loadedUserTasks).isLessThanOrEqualTo(PROCESS_INSTANCE_COUNT);
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
          ZeebeTestUtil.startProcessInstance(
              camundaClient, bpmnProcessId, "{\"var1\": \"value1\"}");
      LOGGER.debug("Started processInstance {} for process {}", processInstanceKey, bpmnProcessId);
      processInstanceKeys.add(processInstanceKey);
    }
    LOGGER.info("{} processInstances started", processInstanceKeys.size());
    return processInstanceKeys;
  }

  private void deployProcess(
      final BpmnModelInstance bpmnModelInstance, final String bpmnProcessId) {
    final String processDefinitionKey =
        ZeebeTestUtil.deployProcess(camundaClient, bpmnModelInstance, bpmnProcessId + ".bpmn");
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
    return String.format(indexPrefix + "-tasklist-%s-*_alias", index);
  }

  protected String getMainIndexNameFor(final String index) {
    return String.format(indexPrefix + "-tasklist-%s-*_", index);
  }

  private void completeTasks(final String taskBpmnId, final int completedTasksCount)
      throws IOException {
    final var tasks = tasklistAPICaller.getTasks(taskBpmnId);
    for (int i = 0; i < completedTasksCount; i++) {
      tasklistAPICaller.completeTask(
          tasks.get(i).getId(), new VariableInputDTO().setName("varOut").setValue("123"));
    }
  }
}
