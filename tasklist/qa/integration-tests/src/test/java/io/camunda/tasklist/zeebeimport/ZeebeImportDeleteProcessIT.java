/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.zeebeimport;

import static io.camunda.tasklist.util.assertions.CustomAssertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.util.MockMvcHelper;
import io.camunda.tasklist.util.TasklistZeebeIntegrationTest;
import io.camunda.tasklist.webapp.api.rest.v1.entities.ProcessResponse;
import io.camunda.tasklist.webapp.security.TasklistURIs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

public class ZeebeImportDeleteProcessIT extends TasklistZeebeIntegrationTest {

  @Autowired private WebApplicationContext context;

  @Autowired private ObjectMapper objectMapper;

  private MockMvcHelper mockMvcHelper;

  @BeforeEach
  public void setUp() {
    mockMvcHelper =
        new MockMvcHelper(MockMvcBuilders.webAppContextSetup(context).build(), objectMapper);
  }

  @Test
  public void shouldDeleteProcessDefinition() {
    final String bpmnProcessId = "userTaskFormProcess";
    final String flowNodeBpmnId = "taskA";
    final String formId = "userTask:Form_1";
    tester
        .deployProcess("userTaskForm.bpmn")
        .waitUntil()
        .processIsDeployed()
        .startProcessInstance(bpmnProcessId)
        .waitUntil()
        .taskIsCreated(flowNodeBpmnId)
        .claimAndCompleteHumanTask(flowNodeBpmnId, "creditor", "\"someone\"")
        .waitUntil()
        .taskIsCompleted(flowNodeBpmnId);
    final String processDefinitionId = tester.getProcessDefinitionKey();
    final String taskId = tester.getTaskId();
    final String strVarVariableId = taskId.concat("-creditor");
    assertThat(
            mockMvcHelper.doRequest(
                get(TasklistURIs.PROCESSES_URL_V1).param("query", processDefinitionId)))
        .hasOkHttpStatus()
        .extractingListContent(objectMapper, ProcessResponse.class)
        .isNotEmpty();
    assertThat(
            mockMvcHelper.doRequest(
                get(TasklistURIs.FORMS_URL_V1.concat("/{formId}"), formId)
                    .param("processDefinitionKey", tester.getProcessDefinitionKey())))
        .hasOkHttpStatus();
    assertThat(mockMvcHelper.doRequest(get(TasklistURIs.TASKS_URL_V1.concat("/{taskId}"), taskId)))
        .hasOkHttpStatus();
    assertThat(
            mockMvcHelper.doRequest(
                get(TasklistURIs.VARIABLES_URL_V1.concat("/{variableId}"), strVarVariableId)))
        .hasOkHttpStatus();

    // when
    tester.deleteResource(tester.getProcessDefinitionKey()).waitUntil().processIsDeleted();

    // then
    assertThat(
            mockMvcHelper.doRequest(
                get(TasklistURIs.PROCESSES_URL_V1).param("query", processDefinitionId)))
        .hasOkHttpStatus()
        .extractingListContent(objectMapper, ProcessResponse.class)
        .isEmpty();
    assertThat(
            mockMvcHelper.doRequest(
                get(TasklistURIs.FORMS_URL_V1.concat("/{formId}"), formId)
                    .param("processDefinitionKey", tester.getProcessDefinitionKey())))
        .hasHttpStatus(HttpStatus.NOT_FOUND);
    assertThat(mockMvcHelper.doRequest(get(TasklistURIs.TASKS_URL_V1.concat("/{taskId}"), taskId)))
        .hasHttpStatus(HttpStatus.NOT_FOUND);
    assertThat(
            mockMvcHelper.doRequest(
                get(TasklistURIs.VARIABLES_URL_V1.concat("/{variableId}"), strVarVariableId)))
        .hasHttpStatus(HttpStatus.NOT_FOUND);
  }
}
