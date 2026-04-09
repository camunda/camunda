/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.it;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.store.TaskStore;
import io.camunda.tasklist.util.MockMvcHelper;
import io.camunda.tasklist.util.TasklistZeebeIntegrationTest;
import io.camunda.webapps.schema.entities.usertask.TaskEntity;
import io.camunda.webapps.schema.entities.usertask.TaskEntity.TaskImplementation;
import io.camunda.webapps.schema.entities.usertask.TaskState;
import io.camunda.zeebe.model.bpmn.builder.AbstractUserTaskBuilder;
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
