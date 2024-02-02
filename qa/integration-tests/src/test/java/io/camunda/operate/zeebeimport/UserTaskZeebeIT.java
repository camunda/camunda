/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.zeebeimport;

import io.camunda.operate.entities.*;
import io.camunda.operate.schema.templates.EventTemplate;
import io.camunda.operate.util.OperateZeebeAbstractIT;
import io.camunda.operate.webapp.reader.UserTaskReader;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

public class UserTaskZeebeIT extends OperateZeebeAbstractIT {

  @Autowired
  private UserTaskReader userTaskReader;

  @Autowired
  private EventTemplate eventTemplate;

  @Test
  public void shouldCreateProcessWithZeebeUserTasks() {
    tester.deployProcess("three-zeebe-user-tasks.bpmn")
        .waitUntil().processIsDeployed();
    assertThat(tester.getProcessDefinitionKey()).isNotNull();

    tester.startProcessInstance("Three-Zeebe-User-Tasks")
        .waitUntil().processInstanceIsStarted()
        .and()
        .userTasksAreCreated(3);
    assertThat(tester.getProcessInstanceKey()).isNotNull();

    var userTasks = userTaskReader.getUserTasks();
    assertThat(userTasks).hasSize(3);
  }

  @Test
  public void shouldImportZeebeUserTask() {
    tester.deployProcess("user-task-annual-leave.bpmn")
        .waitUntil().processIsDeployed();
    assertThat(tester.getProcessDefinitionKey()).isNotNull();
    tester.startProcessInstance("processAnnualLeave")
        .waitUntil().processInstanceIsStarted()
        .and()
        .userTasksAreCreated(1);
    assertThat(tester.getProcessInstanceKey()).isNotNull();

    var userTasks = userTaskReader.getUserTasks();
    assertThat(userTasks).hasSize(1);

    UserTaskEntity userTask = userTasks.get(0);

    assertThat(userTask.getElementId()).isEqualTo("taskRequestLeave");
    assertThat(userTask.getBpmnProcessId()).isEqualTo("processAnnualLeave");
    assertThat(userTask.getProcessDefinitionKey()).isEqualTo(tester.getProcessDefinitionKey());
    assertThat(userTask.getProcessInstanceKey()).isEqualTo(tester.getProcessInstanceKey());
  }

  @Test
  public void shouldCreateFlowNodeForZeebeUserTask() {
    tester.deployProcess("user-task-annual-leave.bpmn")
        .waitUntil().processIsDeployed();
    assertThat(tester.getProcessDefinitionKey()).isNotNull();
    tester.startProcessInstance("processAnnualLeave")
        .waitUntil().processInstanceIsStarted()
        .and()
        .userTasksAreCreated(1)
        .and()
        .flowNodesExist("taskRequestLeave", 1);
    assertThat(tester.getProcessInstanceKey()).isNotNull();

    var userTasks = userTaskReader.getUserTasks();
    assertThat(userTasks).hasSize(1);

    var flowNodeInstanceEntities = tester.getAllFlowNodeInstances();
    FlowNodeInstanceEntity flowNodeUserTask = flowNodeInstanceEntities.stream().filter(x -> Objects.equals(x.getFlowNodeId(), userTasks.get(0).getElementId())).findFirst().orElse(null);

    assertThat(flowNodeUserTask).isNotNull();
    assertThat(flowNodeUserTask.getState()).isEqualTo(FlowNodeState.ACTIVE);
    assertThat(flowNodeUserTask.getType()).isEqualTo(FlowNodeType.USER_TASK);
  }

  @Test
  public void shouldImportEventForTerminatedZeebeUserTask() {
    tester.deployProcess("user-task-annual-leave-timer.bpmn")
        .waitUntil().processIsDeployed();
    assertThat(tester.getProcessDefinitionKey()).isNotNull();
    tester.startProcessInstance("processAnnualLeaveTimer")
        .waitUntil().processInstanceIsStarted()
        .and()
        .eventIsImportedForFlowNode("taskRequestLeaveTimer", EventType.ELEMENT_TERMINATED);
    assertThat(tester.getProcessInstanceKey()).isNotNull();

    var userTasks = userTaskReader.getUserTasks();
    assertThat(userTasks).hasSize(1);

    var events = searchAllDocuments(eventTemplate.getAlias(), EventEntity.class);
    var userTaskEvents = events.stream().filter(x -> Objects.equals(x.getFlowNodeId(), userTasks.get(0).getElementId())).toList();
    assertThat(userTaskEvents).hasSize(1);
    assertThat(userTaskEvents.get(0).getFlowNodeId()).isEqualTo(userTasks.get(0).getElementId());
    assertThat(userTaskEvents.get(0).getEventType()).isEqualTo(EventType.ELEMENT_TERMINATED);
  }
}
