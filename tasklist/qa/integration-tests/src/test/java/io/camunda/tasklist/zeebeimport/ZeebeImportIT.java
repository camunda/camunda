/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.zeebeimport;

import static io.camunda.tasklist.util.TestCheck.PROCESS_IS_DEPLOYED_CHECK;
import static io.camunda.tasklist.util.TestCheck.TASK_IS_CREATED_BY_FLOW_NODE_BPMN_ID_CHECK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.graphql.spring.boot.test.GraphQLResponse;
import io.camunda.tasklist.store.TaskStore;
import io.camunda.tasklist.util.TasklistZeebeIntegrationTest;
import io.camunda.tasklist.util.TestCheck;
import io.camunda.tasklist.v86.entities.TaskEntity;
import io.camunda.tasklist.v86.entities.TaskState;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

public class ZeebeImportIT extends TasklistZeebeIntegrationTest {

  @Autowired
  @Qualifier(TASK_IS_CREATED_BY_FLOW_NODE_BPMN_ID_CHECK)
  private TestCheck taskIsCreatedCheck;

  @Autowired
  @Qualifier(PROCESS_IS_DEPLOYED_CHECK)
  private TestCheck processIsDeployedCheck;

  @Autowired private RecordsReaderHolder recordsReaderHolder;

  @Autowired private TaskStore taskStore;

  @Test
  public void shouldImportAllTasks() throws IOException {
    final String bpmnProcessId = "testProcess";
    final String flowNodeBpmnId = "taskA";

    final GraphQLResponse response =
        tester
            .createAndDeploySimpleProcess(bpmnProcessId, flowNodeBpmnId)
            .waitUntil()
            .processIsDeployed()
            .startProcessInstances(bpmnProcessId, 3)
            .waitUntil()
            .tasksAreCreated(flowNodeBpmnId, 3)
            .getAllTasks();

    // then
    assertTrue(response.isOk());
    assertEquals("3", response.get("$.data.tasks.length()"));
    for (int i = 0; i < 3; i++) {
      final String taskJsonPath = String.format("$.data.tasks[%d]", i);
      assertNotNull(response.get(taskJsonPath + ".id"));

      // process does not contain task name and process name
      assertEquals(flowNodeBpmnId, response.get(taskJsonPath + ".name"));
      assertEquals(bpmnProcessId, response.get(taskJsonPath + ".processName"));

      assertNotNull(response.get(taskJsonPath + ".creationTime"));
      assertNull(response.get(taskJsonPath + ".completionTime"));
      assertEquals(TaskState.CREATED.name(), response.get(taskJsonPath + ".taskState"));
      assertNull(response.get(taskJsonPath + ".assignee"));
      assertEquals("0", response.get(taskJsonPath + ".variables.length()"));
    }
  }

  @Test
  public void shouldImportUserTaskWithCustomFormKey() {
    final String bpmnProcessId = "testProcess";
    final String flowNodeBpmnId = "taskA";

    final String taskId =
        tester
            .createAndDeploySimpleProcess(
                bpmnProcessId, flowNodeBpmnId, t -> t.zeebeFormKey("customFormKey"))
            .waitUntil()
            .processIsDeployed()
            .startProcessInstance(bpmnProcessId)
            .then()
            .taskIsCreated(flowNodeBpmnId)
            .getTaskId();

    final TaskEntity task = taskStore.getTask(taskId);
    assertNotNull(task);
    assertNull(task.getFormId());
    assertEquals("customFormKey", task.getFormKey());
    assertFalse(task.getIsFormEmbedded());
  }

  @Test
  public void shouldImportUserTaskWithEmbeddedForm() {
    final String embeddedFormPrefix = "camunda-forms:bpmn:";
    final String bpmnProcessId = "testProcess";
    final String flowNodeBpmnId = "taskA";
    final String embeddedForm =
        """
                            {
                              "components": [
                                {
                                  "label": "Field 1",
                                  "type": "textfield",
                                  "layout": {
                                    "row": "Row_15ghdy6",
                                    "columns": null
                                  },
                                  "id": "Field_0ibsmz4",
                                  "key": "field_1lfayry"
                                },
                                {
                                  "label": "Field 2",
                                  "type": "textfield",
                                  "layout": {
                                    "row": "Row_1klibf9",
                                    "columns": null
                                  },
                                  "id": "Field_0msuoi3",
                                  "key": "field_1prtdvl"
                                }
                              ],
                              "type": "default",
                              "id": "Field1",
                              "executionPlatform": "Camunda Cloud",
                              "executionPlatformVersion": "8.6.0",
                              "exporter": {
                                "name": "Camunda Modeler",
                                "version": "5.10.0"
                              },
                              "schemaVersion": 8
                            }""";

    final String taskId =
        tester
            .createAndDeploySimpleProcess(
                bpmnProcessId,
                flowNodeBpmnId,
                t -> t.zeebeUserTaskForm("embeddedForm", embeddedForm))
            .waitUntil()
            .processIsDeployed()
            .startProcessInstance(bpmnProcessId)
            .then()
            .taskIsCreated(flowNodeBpmnId)
            .getTaskId();

    final TaskEntity task = taskStore.getTask(taskId);
    assertNotNull(task);
    assertNull(task.getFormId());
    assertEquals(embeddedFormPrefix + "embeddedForm", task.getFormKey());
    assertTrue(task.getIsFormEmbedded());
  }

  protected void processAllRecordsAndWait(final TestCheck testCheck, final Object... arguments) {
    databaseTestExtension.processRecordsAndWaitFor(
        recordsReaderHolder.getAllRecordsReaders(), testCheck, null, arguments);
  }
}
