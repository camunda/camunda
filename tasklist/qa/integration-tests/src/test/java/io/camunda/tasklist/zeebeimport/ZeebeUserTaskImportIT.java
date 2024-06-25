/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.zeebeimport;

import static io.camunda.tasklist.util.assertions.CustomAssertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.tuple;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.entities.TaskEntity;
import io.camunda.tasklist.entities.TaskImplementation;
import io.camunda.tasklist.entities.TaskState;
import io.camunda.tasklist.store.TaskStore;
import io.camunda.tasklist.util.MockMvcHelper;
import io.camunda.tasklist.util.TasklistZeebeIntegrationTest;
import io.camunda.tasklist.webapp.api.rest.v1.entities.VariableSearchResponse;
import io.camunda.tasklist.webapp.security.TasklistURIs;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

public class ZeebeUserTaskImportIT extends TasklistZeebeIntegrationTest {

  @Autowired private TaskStore taskStore;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private WebApplicationContext context;

  private MockMvcHelper mockMvcHelper;

  @BeforeEach
  public void setUp() {
    mockMvcHelper =
        new MockMvcHelper(MockMvcBuilders.webAppContextSetup(context).build(), objectMapper);
  }

  @Test
  public void shouldImportZeebeUserTask() {
    final String bpmnProcessId = "testProcess";
    final String flowNodeBpmnId = "taskA";

    final String taskId =
        tester
            .createAndDeploySimpleProcessWithZeebeUserTask(bpmnProcessId, flowNodeBpmnId)
            .waitUntil()
            .processIsDeployed()
            .startProcessInstance(bpmnProcessId)
            .waitUntil()
            .taskIsCreated(flowNodeBpmnId)
            .getTaskId();

    // then
    assertNotNull(taskId);
    final TaskEntity taskEntity = taskStore.getTask(taskId);
    assertEquals(TaskImplementation.ZEEBE_USER_TASK, taskEntity.getImplementation());
    assertEquals(TaskState.CREATED, taskEntity.getState());
    assertNotNull(taskEntity.getCreationTime());
    assertEquals(bpmnProcessId, taskEntity.getBpmnProcessId());
    assertEquals(flowNodeBpmnId, taskEntity.getFlowNodeBpmnId());
    assertEquals(tester.getProcessDefinitionKey(), taskEntity.getProcessDefinitionId());
    assertEquals(tester.getProcessInstanceId(), taskEntity.getProcessInstanceId());
  }

  @Test
  public void shouldImportZeebeUserTaskWithNullVariableValue() {
    final String bpmnProcessId = "testProcess";
    final String flowNodeBpmnId1 = "taskA";
    final String flowNodeBpmnId2 = "taskB";

    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(bpmnProcessId)
            .startEvent("start")
            .userTask(flowNodeBpmnId1)
            .zeebeUserTask()
            .userTask(flowNodeBpmnId2)
            .zeebeUserTask()
            .endEvent()
            .done();

    final String taskId2 =
        tester
            .createAndDeployProcess(process)
            .waitUntil()
            .processIsDeployed()
            .startProcessInstance(bpmnProcessId)
            .waitUntil()
            .taskIsCreated(flowNodeBpmnId1)
            .completeZeebeUserTask(flowNodeBpmnId1, Map.of("varA", objectMapper.nullNode()))
            .waitUntil()
            .taskIsCreated(flowNodeBpmnId2)
            .getTaskId();

    assertNotNull(taskId2);

    final var result =
        mockMvcHelper.doRequest(
            post(TasklistURIs.TASKS_URL_V1.concat("/{taskId}/variables/search"), taskId2));

    // then
    assertThat(result)
        .hasOkHttpStatus()
        .hasApplicationJsonContentType()
        .extractingListContent(objectMapper, VariableSearchResponse.class)
        .extracting("name", "previewValue", "value")
        .containsExactly(tuple("varA", "null", "null"));
  }
}
