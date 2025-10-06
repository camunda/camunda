/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.it;

import static io.camunda.tasklist.util.assertions.CustomAssertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.tuple;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.store.TaskStore;
import io.camunda.tasklist.util.MockMvcHelper;
import io.camunda.tasklist.util.TasklistZeebeIntegrationTest;
import io.camunda.tasklist.webapp.api.rest.v1.entities.VariableSearchResponse;
import io.camunda.tasklist.webapp.security.TasklistURIs;
import io.camunda.webapps.schema.entities.usertask.TaskEntity;
import io.camunda.webapps.schema.entities.usertask.TaskEntity.TaskImplementation;
import io.camunda.webapps.schema.entities.usertask.TaskState;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.AbstractUserTaskBuilder;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

public class ZeebeUserTaskIT extends TasklistZeebeIntegrationTest {

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
            .createAndDeploySimpleProcess(
                bpmnProcessId, flowNodeBpmnId, AbstractUserTaskBuilder::zeebeUserTask)
            .waitUntil()
            .processIsDeployed()
            .startProcessInstance(bpmnProcessId)
            .waitUntil()
            .taskIsCreated(flowNodeBpmnId)
            .getTaskId();

    // then
    org.assertj.core.api.Assertions.assertThat(taskId).isNotNull();
    final TaskEntity taskEntity = taskStore.getTask(taskId);
    org.assertj.core.api.Assertions.assertThat(taskEntity.getImplementation())
        .isEqualTo(TaskImplementation.ZEEBE_USER_TASK);
    org.assertj.core.api.Assertions.assertThat(taskEntity.getState()).isEqualTo(TaskState.CREATED);
    org.assertj.core.api.Assertions.assertThat(taskEntity.getCreationTime()).isNotNull();
    org.assertj.core.api.Assertions.assertThat(taskEntity.getBpmnProcessId())
        .isEqualTo(bpmnProcessId);
    org.assertj.core.api.Assertions.assertThat(taskEntity.getFlowNodeBpmnId())
        .isEqualTo(flowNodeBpmnId);
    org.assertj.core.api.Assertions.assertThat(taskEntity.getProcessDefinitionId())
        .isEqualTo(tester.getProcessDefinitionKey());
    org.assertj.core.api.Assertions.assertThat(taskEntity.getProcessInstanceId())
        .isEqualTo(tester.getProcessInstanceId());
    org.assertj.core.api.Assertions.assertThat(Integer.valueOf(TaskStore.DEFAULT_PRIORITY))
        .isEqualTo(taskEntity.getPriority());
  }

  @Test
  public void shouldImportCompletedZeebeUserTaskWithVariables() {
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
            .completeZeebeUserTask(
                flowNodeBpmnId1,
                Map.of(
                    "varA",
                    objectMapper.nullNode(),
                    "varB",
                    Long.MAX_VALUE,
                    "varC",
                    Integer.MAX_VALUE,
                    "varD",
                    "str"))
            .waitUntil()
            .taskIsCreated(flowNodeBpmnId2)
            .getTaskId();

    org.assertj.core.api.Assertions.assertThat(taskId2).isNotNull();

    final var result =
        mockMvcHelper.doRequest(
            post(TasklistURIs.TASKS_URL_V1.concat("/{taskId}/variables/search"), taskId2));

    // then
    assertThat(result)
        .hasOkHttpStatus()
        .hasApplicationJsonContentType()
        .extractingListContent(objectMapper, VariableSearchResponse.class)
        .extracting("name", "previewValue", "value")
        .containsExactlyInAnyOrder(
            tuple("varA", "null", "null"),
            tuple("varB", "9223372036854775807", "9223372036854775807"),
            tuple("varC", "2147483647", "2147483647"),
            tuple("varD", "\"str\"", "\"str\""));
  }

  @Test
  public void shouldImportZeebeUserTaskWithCustomHeaders() {
    final String bpmnProcessId = "testProcess";
    final String flowNodeBpmnId = "taskA";
    final String taskId =
        tester
            .createAndDeploySimpleProcess(
                bpmnProcessId,
                flowNodeBpmnId,
                AbstractUserTaskBuilder::zeebeUserTask,
                t -> t.zeebeTaskHeader("testKey", "testValue"))
            .processIsDeployed()
            .then()
            .startProcessInstance(bpmnProcessId)
            .then()
            .taskIsCreated(flowNodeBpmnId)
            .getTaskId();
    // then
    org.assertj.core.api.Assertions.assertThat(taskId).isNotNull();
    final TaskEntity taskEntity = taskStore.getTask(taskId);
    org.assertj.core.api.Assertions.assertThat(taskEntity.getImplementation())
        .isEqualTo(TaskImplementation.ZEEBE_USER_TASK);
    org.assertj.core.api.Assertions.assertThat(taskEntity.getCustomHeaders().containsKey("testKey"))
        .isTrue();
    org.assertj.core.api.Assertions.assertThat(taskEntity.getCustomHeaders().get("testKey"))
        .isEqualTo("testValue");
  }

  private static Stream<Arguments> priorityOptions() {
    return Stream.of(Arguments.of(null, 50), Arguments.of("13", 13), Arguments.of("", 50));
  }

  @ParameterizedTest
  @MethodSource("priorityOptions")
  public void shouldImportZeebeUserTaskWithPriority(
      final String definedPriority, final int taskEntityPriority) {
    final String bpmnProcessId = "testProcess";
    final String flowNodeBpmnId = "taskA";
    final String taskId =
        tester
            .createAndDeploySimpleProcess(
                bpmnProcessId,
                flowNodeBpmnId,
                AbstractUserTaskBuilder::zeebeUserTask,
                t -> t.zeebeTaskPriority(definedPriority))
            .processIsDeployed()
            .then()
            .startProcessInstance(bpmnProcessId)
            .then()
            .taskIsCreated(flowNodeBpmnId)
            .getTaskId();
    // then
    org.assertj.core.api.Assertions.assertThat(taskId).isNotNull();
    final TaskEntity taskEntity = taskStore.getTask(taskId);
    org.assertj.core.api.Assertions.assertThat(taskEntity.getImplementation())
        .isEqualTo(TaskImplementation.ZEEBE_USER_TASK);
    org.assertj.core.api.Assertions.assertThat(taskEntityPriority)
        .isEqualTo(taskEntity.getPriority());
  }
}
