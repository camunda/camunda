/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.zeebeimport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.camunda.tasklist.entities.TaskEntity;
import io.camunda.tasklist.entities.TaskImplementation;
import io.camunda.tasklist.entities.TaskState;
import io.camunda.tasklist.store.TaskStore;
import io.camunda.tasklist.util.TasklistZeebeIntegrationTest;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class ZeebeUserTaskImportIT extends TasklistZeebeIntegrationTest {

  @Autowired private TaskStore taskStore;

  @Test
  public void shouldImportZeebeUserTask() throws IOException {
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
}
