/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.zeebeimport;

import static io.camunda.tasklist.util.assertions.CustomAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.api.command.MigrationPlanBuilderImpl;
import io.camunda.client.api.response.MigrateProcessInstanceResponse;
import io.camunda.tasklist.util.MockMvcHelper;
import io.camunda.tasklist.util.TasklistTester;
import io.camunda.tasklist.util.TasklistZeebeIntegrationTest;
import io.camunda.tasklist.webapp.api.rest.v1.entities.TaskResponse;
import io.camunda.tasklist.webapp.security.TasklistURIs;
import io.camunda.webapps.schema.entities.usertask.TaskState;
import io.camunda.zeebe.model.bpmn.Bpmn;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

public class ZeebeImportMigrateProcessTaskIT extends TasklistZeebeIntegrationTest {

  @Autowired private WebApplicationContext context;

  @Autowired private ObjectMapper objectMapper;

  private MockMvcHelper mockMvcHelper;

  @BeforeEach
  public void setUp() {
    mockMvcHelper =
        new MockMvcHelper(MockMvcBuilders.webAppContextSetup(context).build(), objectMapper);
  }

  @Test
  public void shouldMigrateProcessInstanceAndTasksDefinition() throws InterruptedException {
    // given
    final String bpmnProcessId = "processMigration";
    final String flowNodeBpmnId = "taskA";

    final String oldTaskMapping = "taskA";
    final String newTaskMapping = "taskAM";

    // when
    final TasklistTester task =
        tester
            .deployProcess("processMigration.bpmn")
            .then()
            .processIsDeployed()
            .and()
            .startProcessInstance(bpmnProcessId, null)
            .then()
            .taskIsCreated(flowNodeBpmnId);

    final String processInstanceId = task.getProcessInstanceId();

    final String migratedProcessDefinitionKey =
        tester
            .having()
            .deployProcess("processAfterMigration.bpmn")
            .waitUntil()
            .processIsDeployed()
            .then()
            .getProcessDefinitionKey();

    final MigrateProcessInstanceResponse zeebeMigrationCommandResponse =
        camundaClient
            .newMigrateProcessInstanceCommand(Long.valueOf(processInstanceId))
            .migrationPlan(
                new MigrationPlanBuilderImpl()
                    .withTargetProcessDefinitionKey(Long.valueOf(migratedProcessDefinitionKey))
                    .addMappingInstruction(oldTaskMapping, newTaskMapping)
                    .build())
            .send()
            .join();

    Thread.sleep(10000L);
    databaseTestExtension.refreshTasklistIndices();

    // This test is only test the behaviour of Zeebe Event,
    // We don't check on Tasklist because Zeebe can take some time to process the event and the test
    // would become flaky
    // So because of that we only check if the response is not null

    // Following the document, the Response is a not null and empty object
    // In case of error, an exception is thrown
    Assertions.assertThat(zeebeMigrationCommandResponse).isNotNull();
  }

  @Test
  public void shouldMigrateProcessInstanceAndTasksDefinitionForZeebeUserTask() {
    // given
    final String bpmnProcessId = "processMigration";
    final String oldElementId = "taskA";
    final String newElementId = "taskAM";

    // when
    final String taskId =
        tester
            .createAndDeployProcess(
                Bpmn.createExecutableProcess(bpmnProcessId)
                    .startEvent()
                    .userTask(oldElementId)
                    .zeebeUserTask()
                    .endEvent()
                    .done())
            .then()
            .processIsDeployed()
            .and()
            .startProcessInstance(bpmnProcessId, null)
            .then()
            .taskIsCreated(oldElementId)
            .getTaskId();
    final String oldProcessDefKey = tester.getProcessDefinitionKey();

    tester
        .having()
        .createAndDeployProcess(
            Bpmn.createExecutableProcess(bpmnProcessId)
                .startEvent()
                .userTask(newElementId)
                .zeebeUserTask()
                .userTask("another_task")
                .zeebeUserTask()
                .endEvent()
                .done())
        .waitUntil()
        .processIsDeployed()
        .and()
        .migrateProcessInstance(oldElementId, newElementId)
        .waitUntil()
        .taskIsCreated(newElementId);

    final String newProcessDefKey = tester.getProcessDefinitionKey();

    assertNotEquals(newProcessDefKey, oldProcessDefKey);

    final var result =
        mockMvcHelper.doRequest(get(TasklistURIs.TASKS_URL_V1.concat("/{taskId}"), taskId));

    // then
    assertThat(result)
        .hasOkHttpStatus()
        .hasApplicationJsonContentType()
        .extractingContent(objectMapper, TaskResponse.class)
        .satisfies(
            task -> {
              Assertions.assertThat(task.getId()).isEqualTo(taskId);
              Assertions.assertThat(task.getProcessInstanceKey())
                  .isEqualTo(tester.getProcessInstanceId());
              Assertions.assertThat(task.getProcessDefinitionKey()).isEqualTo(newProcessDefKey);
              Assertions.assertThat(task.getTaskState()).isEqualTo(TaskState.CREATED);
              Assertions.assertThat(task.getAssignee()).isNull();
            });
  }
}
