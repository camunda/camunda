/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.graphql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.graphql.spring.boot.test.GraphQLResponse;
import io.camunda.tasklist.util.TasklistZeebeIntegrationTest;
import java.io.IOException;
import org.junit.jupiter.api.Test;

public class FormIT extends TasklistZeebeIntegrationTest {

  @Test
  public void shouldReturnForm() throws IOException {
    final String formKey = "camunda-forms:bpmn:userTask:Form_1";
    final String formId = "userTask:Form_1";
    createData();

    final GraphQLResponse response = tester.when().getAllTasks();

    // then
    assertTrue(response.isOk());
    assertEquals("2", response.get("$.data.tasks.length()"));
    for (int i = 0; i < 2; i++) {
      final String taskJsonPath = String.format("$.data.tasks[%d]", i);
      assertEquals(formKey, response.get(taskJsonPath + ".formKey"));
      assertEquals(
          tester.getProcessDefinitionKey(), response.get(taskJsonPath + ".processDefinitionId"));
    }

    // when - get form
    final GraphQLResponse formResponse = tester.getForm(formId);

    // then
    assertTrue(formResponse.isOk());
    assertEquals(formId, formResponse.get("$.data.form.id"));
    assertEquals(
        tester.getProcessDefinitionKey(), formResponse.get("$.data.form.processDefinitionId"));
    assertNotNull(formResponse.get("$.data.form.schema"));
  }

  @Test
  public void shouldFailOnNonExistingForm() throws IOException {
    createData();

    // when
    final var bpmnFormId = "wrongId";
    final GraphQLResponse formResponse = tester.getForm(bpmnFormId);

    // then
    final var expectedErrorMessage = String.format("form with id %s was not found", bpmnFormId);
    assertEquals(expectedErrorMessage, formResponse.get("$.errors[0].message"));
  }

  private void createData() {
    final String bpmnProcessId = "userTaskFormProcess";
    final String flowNodeBpmnId = "taskA";

    tester
        .having()
        .deployProcess("userTaskForm.bpmn")
        .waitUntil()
        .processIsDeployed()
        .and()
        .startProcessInstances(bpmnProcessId, 2)
        .waitUntil()
        .tasksAreCreated(flowNodeBpmnId, 2);
  }
}
