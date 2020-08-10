/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class BpmnModels {

  public static final String START_EVENT = "startEvent";
  public static final String END_EVENT = "endEvent";
  public static final String USER_TASK_1 = "userTask1";
  public static final String USER_TASK_2 = "userTask2";
  public static final String SERVICE_TASK = "serviceTask";

  public static final String DEFAULT_PROCESS_ID = "aProcess";
  public static final String VERSION_TAG = "aVersionTag";


  public static BpmnModelInstance getSimpleBpmnDiagram() {
    return getSimpleBpmnDiagram(DEFAULT_PROCESS_ID, START_EVENT, END_EVENT);
  }

  public static BpmnModelInstance getSimpleBpmnDiagram(String procDefKey) {
    return getSimpleBpmnDiagram(procDefKey, START_EVENT, END_EVENT);
  }

  public static BpmnModelInstance getSimpleBpmnDiagram(String procDefKey, String startEventId, String endEventId) {
    return Bpmn.createExecutableProcess(procDefKey)
      .camundaVersionTag(VERSION_TAG)
      .name(procDefKey)
      .startEvent(startEventId)
      .endEvent(endEventId)
      .done();
  }

  public static BpmnModelInstance getSingleUserTaskDiagram() {
    return getSingleUserTaskDiagram(DEFAULT_PROCESS_ID);
  }

  public static BpmnModelInstance getSingleUserTaskDiagram(String procDefKey) {
    return getSingleUserTaskDiagram(procDefKey, START_EVENT, END_EVENT, USER_TASK_1);
  }

  public static BpmnModelInstance getSingleUserTaskDiagram(String procDefKey, String startEventName,
                                                           String endEventName, String userTaskName) {
    return Bpmn.createExecutableProcess(procDefKey)
      .camundaVersionTag(VERSION_TAG)
      .startEvent(startEventName)
      .userTask(userTaskName)
      .endEvent(endEventName)
      .done();
  }

  public static BpmnModelInstance getDoubleUserTaskDiagram() {
    return getDoubleUserTaskDiagram(DEFAULT_PROCESS_ID);
  }

  public static BpmnModelInstance getDoubleUserTaskDiagram(String procDefKey) {
    return getDoubleUserTaskDiagram(procDefKey, START_EVENT, END_EVENT, USER_TASK_1, USER_TASK_2);
  }

  public static BpmnModelInstance getDoubleUserTaskDiagram(String procDefKey, String startEventName,
                                                           String endEventName, String userTask1Name,
                                                           String userTask2Name) {
    return Bpmn.createExecutableProcess(procDefKey)
      .camundaVersionTag(VERSION_TAG)
      .startEvent(startEventName)
      .userTask(userTask1Name)
      .userTask(userTask2Name)
      .endEvent(endEventName)
      .done();
  }


  public static BpmnModelInstance getSingleServiceTaskProcess(String procDefKey) {
    return getSingleServiceTaskProcess(procDefKey, SERVICE_TASK);
  }

  public static BpmnModelInstance getSingleServiceTaskProcess() {
    return getSingleServiceTaskProcess(DEFAULT_PROCESS_ID, SERVICE_TASK);
  }

  public static BpmnModelInstance getSingleServiceTaskProcess(String procDefKey, String serviceTaskId) {
    return Bpmn.createExecutableProcess(procDefKey)
      .camundaVersionTag(VERSION_TAG)
      .name(procDefKey)
      .startEvent(START_EVENT)
      .serviceTask(serviceTaskId)
      .camundaExpression("${true}")
      .endEvent(END_EVENT)
      .done();
  }
}
