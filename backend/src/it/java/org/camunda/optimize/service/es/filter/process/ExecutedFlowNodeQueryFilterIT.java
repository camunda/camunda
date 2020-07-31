/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.filter.process;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessReportResultDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.util.BpmnModels;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.camunda.optimize.util.BpmnModels.END_EVENT;
import static org.camunda.optimize.util.BpmnModels.USER_TASK_2;
import static org.camunda.optimize.util.BpmnModels.getSingleUserTaskDiagram;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class ExecutedFlowNodeQueryFilterIT extends AbstractFilterIT {

  @Test
  public void filterByOneFlowNode() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleUserTaskProcessDefinition();
    ProcessInstanceEngineDto instanceEngineDto = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(instanceEngineDto.getId());
    engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessFilterDto<?>> executedFlowNodes = ProcessFilterBuilder
      .filter()
      .executedFlowNodes()
      .id(END_EVENT)
      .inOperator()
      .add()
      .buildList();

    RawDataProcessReportResultDto result = evaluateReportWithFilter(processDefinition, executedFlowNodes);
    // then
    assertResults(result, 1);
  }

  @Test
  public void filterByOneFlowNodeWithUnequalOperator() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleUserTaskProcessDefinition();
    ProcessInstanceEngineDto instanceEngineDto = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(instanceEngineDto.getId());
    engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessFilterDto<?>> executedFlowNodes = ProcessFilterBuilder
      .filter()
      .executedFlowNodes()
      .id(END_EVENT)
      .notInOperator()
      .add()
      .buildList();

    RawDataProcessReportResultDto result = evaluateReportWithFilter(processDefinition, executedFlowNodes);

    // then
    assertResults(result, 1);
  }

  @Test
  public void filterMultipleProcessInstancesByOneFlowNode() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleUserTaskProcessDefinition();
    ProcessInstanceEngineDto instanceEngineDto = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(instanceEngineDto.getId());
    engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessFilterDto<?>> executedFlowNodes = ProcessFilterBuilder
      .filter()
      .executedFlowNodes()
      .id(END_EVENT)
      .inOperator()
      .add()
      .buildList();
    RawDataProcessReportResultDto result = evaluateReportWithFilter(processDefinition, executedFlowNodes);

    // then
    assertResults(result, 3);
  }

  @Test
  public void filterMultipleProcessInstancesByOneFlowNodeWithUnequalOperator() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleUserTaskProcessDefinition();
    ProcessInstanceEngineDto instanceEngineDto = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(instanceEngineDto.getId());
    engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessFilterDto<?>> executedFlowNodes = ProcessFilterBuilder
      .filter()
      .executedFlowNodes()
      .id(END_EVENT)
      .notInOperator()
      .add()
      .buildList();
    RawDataProcessReportResultDto result = evaluateReportWithFilter(processDefinition, executedFlowNodes);

    // then
    assertResults(result, 2);
  }

  @Test
  public void filterByMultipleAndCombinedFlowNodes() {
    // given
    ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksProcessDefinition();
    ProcessInstanceEngineDto instanceEngineDto = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(instanceEngineDto.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(instanceEngineDto.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(instanceEngineDto.getId());
    engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    importAllEngineEntitiesFromScratch();
    // when

    List<ProcessFilterDto<?>> executedFlowNodes = ProcessFilterBuilder.filter()
      .executedFlowNodes()
      .id(USER_TASK_2)
      .add()
      .executedFlowNodes()
      .id(END_EVENT)
      .add()
      .buildList();

    RawDataProcessReportResultDto result = evaluateReportWithFilter(processDefinition, executedFlowNodes);

    // then
    assertResults(result, 2);
  }

  @Test
  public void filterByMultipleAndCombinedFlowNodesWithUnequalOperator() {
    // given
    ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksProcessDefinition();
    ProcessInstanceEngineDto instanceEngineDto = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(instanceEngineDto.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(instanceEngineDto.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(instanceEngineDto.getId());
    engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessFilterDto<?>> executedFlowNodes = ProcessFilterBuilder.filter().executedFlowNodes()
      .id(USER_TASK_2)
      .add()
      .executedFlowNodes()
      .id(END_EVENT)
      .notInOperator()
      .add()
      .buildList();
    RawDataProcessReportResultDto result = evaluateReportWithFilter(processDefinition, executedFlowNodes);

    // then
    assertResults(result, 1);

    // when
    executedFlowNodes = ProcessFilterBuilder
      .filter()
      .executedFlowNodes()
      .id(USER_TASK_2)
      .notInOperator()
      .add()
      .executedFlowNodes()
      .id(END_EVENT)
      .notInOperator()
      .add()
      .buildList();
    result = evaluateReportWithFilter(processDefinition, executedFlowNodes);

    // then
    assertResults(result, 2);
  }

  @Test
  public void filterByMultipleOrCombinedFlowNodes() {
    // given
    ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksProcessDefinition();
    ProcessInstanceEngineDto instanceEngineDto = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(instanceEngineDto.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(instanceEngineDto.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(instanceEngineDto.getId());
    engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessFilterDto<?>> executedFlowNodes = ProcessFilterBuilder.filter()
      .executedFlowNodes()
      .ids(USER_TASK_2, END_EVENT)
      .inOperator()
      .add()
      .buildList();
    RawDataProcessReportResultDto result = evaluateReportWithFilter(processDefinition, executedFlowNodes);

    // then
    assertResults(result, 3);
  }

  @Test
  public void filterByMultipleOrCombinedFlowNodesWithUnequalOperator() {
    // given
    ProcessDefinitionEngineDto processDefinition = deployProcessWIthGatewayAndOneUserTaskEachBranch();

    Map<String, Object> takePathA = new HashMap<>();
    takePathA.put("takePathA", true);
    Map<String, Object> takePathB = new HashMap<>();
    takePathB.put("takePathA", false);
    ProcessInstanceEngineDto instanceEngineDto = engineIntegrationExtension.startProcessInstance(processDefinition.getId(), takePathA);
    engineIntegrationExtension.finishAllRunningUserTasks(instanceEngineDto.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineIntegrationExtension.startProcessInstance(processDefinition.getId(), takePathB);
    engineIntegrationExtension.finishAllRunningUserTasks(instanceEngineDto.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineIntegrationExtension.startProcessInstance(processDefinition.getId(), takePathA);
    engineIntegrationExtension.finishAllRunningUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineIntegrationExtension.startProcessInstance(processDefinition.getId(), takePathB);
    engineIntegrationExtension.finishAllRunningUserTasks(instanceEngineDto.getId());
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), takePathA);
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), takePathB);
    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessFilterDto<?>> executedFlowNodes = ProcessFilterBuilder
      .filter()
      .executedFlowNodes()
      .ids("UserTask-PathA", "FinalUserTask")
      .notInOperator()
      .add()
      .buildList();
    RawDataProcessReportResultDto result = evaluateReportWithFilter(processDefinition, executedFlowNodes);

    // then
    assertResults(result, 1);
  }

  @Test
  public void filterByMultipleAndOrCombinedFlowNodes() {
    // given
    ProcessDefinitionEngineDto processDefinition = deployProcessWIthGatewayAndOneUserTaskEachBranch();

    Map<String, Object> takePathA = new HashMap<>();
    takePathA.put("takePathA", true);
    Map<String, Object> takePathB = new HashMap<>();
    takePathB.put("takePathA", false);
    ProcessInstanceEngineDto instanceEngineDto = engineIntegrationExtension.startProcessInstance(processDefinition.getId(), takePathA);
    engineIntegrationExtension.finishAllRunningUserTasks(instanceEngineDto.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineIntegrationExtension.startProcessInstance(processDefinition.getId(), takePathA);
    engineIntegrationExtension.finishAllRunningUserTasks(instanceEngineDto.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineIntegrationExtension.startProcessInstance(processDefinition.getId(), takePathB);
    engineIntegrationExtension.finishAllRunningUserTasks(instanceEngineDto.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineIntegrationExtension.startProcessInstance(processDefinition.getId(), takePathB);
    engineIntegrationExtension.finishAllRunningUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineIntegrationExtension.startProcessInstance(processDefinition.getId(), takePathB);
    engineIntegrationExtension.finishAllRunningUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineIntegrationExtension.startProcessInstance(processDefinition.getId(), takePathA);
    engineIntegrationExtension.finishAllRunningUserTasks(instanceEngineDto.getId());
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), takePathA);
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), takePathB);
    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessFilterDto<?>> executedFlowNodes =
      ProcessFilterBuilder
        .filter()
        .executedFlowNodes()
        .ids("UserTask-PathA", "UserTask-PathB")
        .inOperator()
        .add()
        .executedFlowNodes()
        .id(END_EVENT)
        .add()
      .buildList();
    RawDataProcessReportResultDto result = evaluateReportWithFilter(processDefinition, executedFlowNodes);

    // then
    assertResults(result, 3);
  }

  @Test
  public void equalAndUnequalOperatorCombined() {
    // given
    ProcessDefinitionEngineDto processDefinition = deployProcessWIthGatewayAndOneUserTaskEachBranch();

    Map<String, Object> takePathA = new HashMap<>();
    takePathA.put("takePathA", true);
    Map<String, Object> takePathB = new HashMap<>();
    takePathB.put("takePathA", false);
    ProcessInstanceEngineDto instanceEngineDto = engineIntegrationExtension.startProcessInstance(processDefinition.getId(), takePathA);
    engineIntegrationExtension.finishAllRunningUserTasks(instanceEngineDto.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineIntegrationExtension.startProcessInstance(processDefinition.getId(), takePathB);
    engineIntegrationExtension.finishAllRunningUserTasks(instanceEngineDto.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineIntegrationExtension.startProcessInstance(processDefinition.getId(), takePathA);
    engineIntegrationExtension.finishAllRunningUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineIntegrationExtension.startProcessInstance(processDefinition.getId(), takePathB);
    engineIntegrationExtension.finishAllRunningUserTasks(instanceEngineDto.getId());
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), takePathA);
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), takePathB);
    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessFilterDto<?>> executedFlowNodes =
      ProcessFilterBuilder
        .filter()
        .executedFlowNodes()
        .ids("UserTask-PathA", "FinalUserTask")
        .notInOperator()
        .add()
        .executedFlowNodes()
        .id("UserTask-PathB")
        .inOperator()
        .add()
      .buildList();
    RawDataProcessReportResultDto result = evaluateReportWithFilter(processDefinition, executedFlowNodes);

    // then
    assertResults(result, 1);
  }

  private ProcessDefinitionEngineDto deployProcessWIthGatewayAndOneUserTaskEachBranch() {
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess()
      .startEvent()
      .exclusiveGateway("splittingGateway")
      .condition("Take path A", "${takePathA}")
      .userTask("UserTask-PathA")
      .exclusiveGateway("mergeExclusiveGateway")
      .userTask("FinalUserTask")
      .endEvent(END_EVENT)
      .moveToLastGateway()
      .moveToLastGateway()
      .condition("Take path B", "${!takePathA}")
      .userTask("UserTask-PathB")
      .connectTo("mergeExclusiveGateway")
      .done();
    return engineIntegrationExtension.deployProcessAndGetProcessDefinition(modelInstance);
  }

  @Test
  public void sameFlowNodeInDifferentProcessDefinitionDoesNotDistortResult() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleUserTaskProcessDefinition();
    ProcessDefinitionEngineDto processDefinition2 = deploySimpleUserTaskProcessDefinition();
    ProcessInstanceEngineDto instanceEngineDto = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineIntegrationExtension.startProcessInstance(processDefinition2.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(instanceEngineDto.getId());
    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessFilterDto<?>> executedFlowNodes = ProcessFilterBuilder
      .filter()
      .executedFlowNodes()
      .id(END_EVENT)
      .add()
      .buildList();
    RawDataProcessReportResultDto result = evaluateReportWithFilter(processDefinition, executedFlowNodes);

    // then
    assertResults(result, 2);
  }

  @Test
  public void validationExceptionOnNullOperatorField() {
    //given
    List<ProcessFilterDto<?>> filterDtos = ProcessFilterBuilder
      .filter()
      .executedFlowNodes()
      .id("foo")
      .operator(null)
      .add()
      .buildList();

    // when
    Response response = evaluateReportAndReturnResponse(filterDtos);

    // then
    assertThat(response.getStatus(), is(Response.Status.BAD_REQUEST.getStatusCode()));
  }

  @Test
  public void validationExceptionOnNullValueField() {
    //given
    List<ProcessFilterDto<?>> filterDtos = ProcessFilterBuilder
      .filter()
      .executedFlowNodes()
      .id(null)
      .add()
      .buildList();

    // when
    Response response = evaluateReportAndReturnResponse(filterDtos);

    // then
    assertThat(response.getStatus(), is(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode()));
  }

  private void assertResults(RawDataProcessReportResultDto resultDto, int piCount) {
    assertThat(resultDto.getData().size(), is(piCount));
  }

  private ProcessDefinitionEngineDto deploySimpleUserTaskProcessDefinition() {
    BpmnModelInstance modelInstance = BpmnModels.getSingleUserTaskDiagram();
    return engineIntegrationExtension.deployProcessAndGetProcessDefinition(modelInstance);
  }

  private Response evaluateReportAndReturnResponse(List<ProcessFilterDto<?>> filterDto) {
    ProcessReportDataDto reportData = createReport(TEST_DEFINITION, "1");
    reportData.setFilter(filterDto);
    return reportClient.evaluateReportAndReturnResponse(reportData);
  }

}
