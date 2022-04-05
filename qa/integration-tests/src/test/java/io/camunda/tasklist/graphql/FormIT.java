/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.graphql;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.graphql.spring.boot.test.GraphQLResponse;
import io.camunda.tasklist.util.TasklistZeebeIntegrationTest;
import java.io.IOException;
import org.junit.Test;

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
    final GraphQLResponse formResponse = tester.getForm("wrongId");

    // then
    assertEquals("No task form found with id wrongId", formResponse.get("$.errors[0].message"));
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
