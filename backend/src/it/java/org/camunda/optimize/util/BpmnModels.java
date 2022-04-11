/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.util;

import com.google.common.collect.ImmutableList;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;

import java.util.List;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class BpmnModels {

  public static final String START_EVENT = "startEvent";
  public static final String START_EVENT_NAME = "startEventName";
  public static final String END_EVENT = "endEvent";
  public static final String END_EVENT_NAME = "endEventName";
  public static final String USER_TASK_1 = "userTask1";
  public static final String USER_TASK_2 = "userTask2";
  public static final String USER_TASK_3 = "userTask3";
  public static final String USER_TASK_4 = "userTask4";
  public static final String SERVICE_TASK = "serviceTask";

  public static final String DEFAULT_PROCESS_ID = "aProcess";
  public static final String VERSION_TAG = "aVersionTag";

  public static final String START_EVENT_ID = "startEvent";
  public static final String SPLITTING_GATEWAY_ID = "splittingGateway";
  public static final String SERVICE_TASK_ID_1 = "serviceTask1";
  public static final String SERVICE_TASK_NAME_1 = "serviceTask1Name";
  public static final String SERVICE_TASK_ID_2 = "serviceTask2";
  public static final String SERVICE_TASK_NAME_2 = "serviceTask2Name";
  public static final String MERGE_GATEWAY_ID = "mergeExclusiveGateway";
  public static final String DEFAULT_TOPIC = "MyTopic";

  public static final String END_EVENT_ID_1 = "endEvent1";
  public static final String END_EVENT_NAME_1 = "endEvent1Name";
  public static final String END_EVENT_ID_2 = "endEvent2";
  public static final String END_EVENT_NAME_2 = "endEvent2Name";

  public static final String START_LOOP = "mergeExclusiveGateway";
  public static final String END_LOOP = "splittingGateway";

  public static final String MULTI_INSTANCE_START = "miStart";
  public static final String MULTI_INSTANCE_END = "miEnd";
  public static final String PARALLEL_GATEWAY = "parallelGateway";
  public static final String CALL_ACTIVITY = "callActivity";

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
    return getSingleUserTaskDiagram(procDefKey, USER_TASK_1);
  }

  public static BpmnModelInstance getSingleUserTaskDiagram(String procDefKey, String userTaskName) {
    return getSingleUserTaskDiagram(procDefKey, START_EVENT, END_EVENT, userTaskName);
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

  public static BpmnModelInstance getDoubleUserTaskDiagram(String procDefKey, String userTask1Name,
                                                           String userTask2Name) {
    return getDoubleUserTaskDiagram(procDefKey, START_EVENT, END_EVENT, userTask1Name, userTask2Name);

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

  public static BpmnModelInstance getTripleUserTaskDiagram() {
    return getTripleUserTaskDiagram(DEFAULT_PROCESS_ID);
  }

  public static BpmnModelInstance getTripleUserTaskDiagram(String procDefKey) {
    return getTripleUserTaskDiagram(procDefKey, START_EVENT, END_EVENT, USER_TASK_1, USER_TASK_2, USER_TASK_3);
  }

  public static BpmnModelInstance getTripleUserTaskDiagram(final String procDefKey, final String startEventName,
                                                           final String endEventName, final String userTask1Name,
                                                           final String userTask2Name, final String userTask3Name) {
    return Bpmn.createExecutableProcess(procDefKey)
      .camundaVersionTag(VERSION_TAG)
      .startEvent(startEventName)
      .userTask(userTask1Name)
      .userTask(userTask2Name)
      .userTask(userTask3Name)
      .endEvent(endEventName)
      .done();
  }

  public static BpmnModelInstance getFourUserTaskDiagram(final String procDefKey) {
    return Bpmn.createExecutableProcess(procDefKey)
      .startEvent()
      .parallelGateway()
      .userTask(USER_TASK_1)
      .userTask(USER_TASK_2)
      .endEvent()
      .moveToLastGateway()
      .userTask(USER_TASK_3)
      .userTask(USER_TASK_4)
      .endEvent()
      .done();
  }

  public static BpmnModelInstance getUserTaskDiagramWithAssignee(final String assignee) {
    return getUserTaskDiagramWithAssignee(DEFAULT_PROCESS_ID, assignee);
  }

  public static BpmnModelInstance getUserTaskDiagramWithAssignee(final String procDefKey, final String assignee) {
    return Bpmn.createExecutableProcess(procDefKey)
      .camundaVersionTag(VERSION_TAG)
      .startEvent(START_EVENT)
      .userTask(USER_TASK_1).camundaAssignee(assignee)
      .endEvent(END_EVENT)
      .done();
  }

  public static BpmnModelInstance getDoubleUserTaskDiagramWithAssignees(final String assigneeFirstUserTask,
                                                                        final String assigneeSecondUserTask) {
    return Bpmn.createExecutableProcess(DEFAULT_PROCESS_ID)
      .camundaVersionTag(VERSION_TAG)
      .startEvent(START_EVENT)
      .userTask(USER_TASK_1).camundaAssignee(assigneeFirstUserTask)
      .userTask(USER_TASK_2).camundaAssignee(assigneeSecondUserTask)
      .endEvent(END_EVENT)
      .done();
  }

  public static BpmnModelInstance getUserTaskDiagramWithCandidateGroup(final String candidateGroup) {
    return getUserTaskDiagramWithCandidateGroup(DEFAULT_PROCESS_ID, candidateGroup);
  }

  public static BpmnModelInstance getUserTaskDiagramWithCandidateGroup(final String procDefKey,
                                                                       final String candidateGroup) {
    return getUserTaskDiagramWithMultipleCandidateGroups(procDefKey, ImmutableList.of(candidateGroup));
  }

  public static BpmnModelInstance getUserTaskDiagramWithMultipleCandidateGroups(final List<String> candidateGroups) {
    return getUserTaskDiagramWithMultipleCandidateGroups(DEFAULT_PROCESS_ID, candidateGroups);
  }

  public static BpmnModelInstance getUserTaskDiagramWithMultipleCandidateGroups(final String procDefKey,
                                                                                final List<String> candidateGroups) {
    return Bpmn.createExecutableProcess(procDefKey)
      .camundaVersionTag(VERSION_TAG)
      .startEvent(START_EVENT)
      .userTask(USER_TASK_1).camundaCandidateGroups(candidateGroups)
      .endEvent(END_EVENT)
      .done();
  }

  public static BpmnModelInstance getDoubleUserTaskDiagramWithCandidateGroups(final String candidateGroupFirstUserTask,
                                                                              final String candidateGroupSecondUserTask) {
    return Bpmn.createExecutableProcess(DEFAULT_PROCESS_ID)
      .camundaVersionTag(VERSION_TAG)
      .startEvent(START_EVENT)
      .userTask(USER_TASK_1).camundaCandidateGroups(candidateGroupFirstUserTask)
      .userTask(USER_TASK_2).camundaCandidateGroups(candidateGroupSecondUserTask)
      .endEvent(END_EVENT)
      .done();
  }

  public static BpmnModelInstance getSingleServiceTaskProcess(String procDefKey) {
    return getSingleServiceTaskProcess(procDefKey, SERVICE_TASK);
  }

  public static BpmnModelInstance getSingleServiceTaskProcess() {
    return getSingleServiceTaskProcess(DEFAULT_PROCESS_ID, SERVICE_TASK);
  }

  public static BpmnModelInstance getSingleServiceTaskProcess(String procDefKey, String serviceTaskId) {
    // @formatter:off
    return Bpmn.createExecutableProcess(procDefKey)
      .camundaVersionTag(VERSION_TAG)
      .name(procDefKey)
      .startEvent(START_EVENT)
      .serviceTask(serviceTaskId)
        .camundaExpression("${true}")
      .endEvent(END_EVENT)
      .done();
    // @formatter:on
  }

  public static BpmnModelInstance getTwoServiceTasksProcess(String procDefKey) {
    // @formatter:off
    return Bpmn.createExecutableProcess(procDefKey)
      .camundaVersionTag(VERSION_TAG)
      .name(procDefKey)
      .startEvent(START_EVENT)
      .serviceTask(SERVICE_TASK_ID_1)
          .camundaExpression("${true}")
      .serviceTask(SERVICE_TASK_ID_2)
        .camundaExpression("${true}")
      .endEvent(END_EVENT)
      .done();
    // @formatter:on
  }

  public static BpmnModelInstance getSimpleGatewayProcess(final String procDefKey) {
    // @formatter:off
    return Bpmn.createExecutableProcess(procDefKey)
      .startEvent(START_EVENT_ID)
      .exclusiveGateway(SPLITTING_GATEWAY_ID)
        .name("Should we go to task 1?")
        .condition("yes", "${goToTask1}")
        .serviceTask(SERVICE_TASK_ID_1)
        .camundaExpression("${true}")
      .exclusiveGateway(MERGE_GATEWAY_ID)
        .endEvent(END_EVENT)
      .moveToNode(SPLITTING_GATEWAY_ID)
        .condition("no", "${!goToTask1}")
        .serviceTask(SERVICE_TASK_ID_2)
        .camundaExpression("${true}")
        .connectTo(MERGE_GATEWAY_ID)
      .done();
    // @formatter:on
  }

  public static BpmnModelInstance getExternalTaskProcess() {
    return getExternalTaskProcess(DEFAULT_PROCESS_ID);
  }

  public static BpmnModelInstance getExternalTaskProcess(final String key) {
    // @formatter:off
    return Bpmn.createExecutableProcess(key)
      .camundaVersionTag(VERSION_TAG)
      .name(key)
      .startEvent(START_EVENT)
        .name(START_EVENT_NAME)
      .serviceTask(SERVICE_TASK_ID_1)
        .name(SERVICE_TASK_NAME_1)
        .camundaExternalTask(DEFAULT_TOPIC)
      .endEvent(END_EVENT)
        .name(END_EVENT_NAME)
      .done();
    // @formatter:on
  }

  public static BpmnModelInstance getTwoExternalTaskProcess() {
    return getTwoExternalTaskProcess(DEFAULT_PROCESS_ID);
  }

  public static BpmnModelInstance getTwoExternalTaskProcess(final String key) {
    // @formatter:off
    return Bpmn.createExecutableProcess(key)
      .camundaVersionTag(VERSION_TAG)
      .name(key)
      .startEvent(START_EVENT)
        .name(START_EVENT_NAME)
      .serviceTask(SERVICE_TASK_ID_1)
        .name(SERVICE_TASK_NAME_1)
        .camundaExternalTask(DEFAULT_TOPIC)
      .serviceTask(SERVICE_TASK_ID_2)
        .name(SERVICE_TASK_NAME_2)
        .camundaExternalTask(DEFAULT_TOPIC)
      .endEvent(END_EVENT)
        .name(END_EVENT_NAME)
      .done();
    // @formatter:on
  }

  public static BpmnModelInstance getTwoParallelExternalTaskProcess() {
    return getTwoParallelExternalTaskProcess(DEFAULT_PROCESS_ID);
  }

  public static BpmnModelInstance getTwoParallelExternalTaskProcess(final String key) {
    // @formatter:off
    return Bpmn.createExecutableProcess(key)
      .camundaVersionTag(VERSION_TAG)
      .name(key)
      .startEvent(START_EVENT)
       .name(START_EVENT_NAME)
      .parallelGateway(SPLITTING_GATEWAY_ID)
        .serviceTask(SERVICE_TASK_ID_1)
          .name(SERVICE_TASK_NAME_1)
          .camundaExternalTask(DEFAULT_TOPIC)
        .endEvent(END_EVENT_ID_1)
          .name(END_EVENT_NAME_1)
      .moveToNode(SPLITTING_GATEWAY_ID)
        .serviceTask(SERVICE_TASK_ID_2)
          .name(SERVICE_TASK_NAME_2)
          .camundaExternalTask(DEFAULT_TOPIC)
        .endEvent(END_EVENT_ID_2)
          .name(END_EVENT_NAME_2)
      .done();
    // @formatter:on
  }

  public static BpmnModelInstance getLoopingProcess() {
    // @formatter:off
    return Bpmn.createExecutableProcess()
      .startEvent(START_EVENT)
      .exclusiveGateway(START_LOOP)
      .serviceTask(SERVICE_TASK_ID_1)
        .camundaExpression("${true}")
      .exclusiveGateway(END_LOOP)
        .condition("End process", "${!anotherRound}")
      .endEvent(END_EVENT)
      .moveToLastGateway()
        .condition("Take another round", "${anotherRound}")
      .serviceTask(SERVICE_TASK_ID_2)
        .camundaExpression("${true}")
        .camundaInputParameter("anotherRound", "${anotherRound}")
        .camundaOutputParameter("anotherRound", "${!anotherRound}")
      .scriptTask("scriptTask")
        .scriptFormat("groovy")
        .scriptText("sleep(10)")
      .connectTo(START_LOOP)
      .done();
    // @formatter:on
  }

  public static BpmnModelInstance getMultiInstanceProcess(final String processId, final String subProcessKey) {
    // @formatter:off
    return Bpmn.createExecutableProcess(processId)
      .name("MultiInstance")
      .startEvent(MULTI_INSTANCE_START)
        .parallelGateway(PARALLEL_GATEWAY)
      .endEvent(END_EVENT)
        .moveToLastGateway()
          .callActivity(CALL_ACTIVITY)
          .calledElement(subProcessKey)
          .multiInstance()
          .cardinality("2")
        .multiInstanceDone()
      .endEvent(MULTI_INSTANCE_END)
      .done();
    // @formatter:on
  }

}
