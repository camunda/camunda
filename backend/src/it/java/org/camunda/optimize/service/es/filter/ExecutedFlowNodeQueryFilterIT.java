/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.filter;

import com.fasterxml.jackson.core.type.TypeReference;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessReportResultDto;
import org.camunda.optimize.dto.optimize.rest.report.ProcessReportEvaluationResultDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createProcessReportDataViewRawAsTable;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;


public class ExecutedFlowNodeQueryFilterIT {

  private static final String TEST_DEFINITION = "TestDefinition";
  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();
  @Rule
  public RuleChain chain = RuleChain
    .outerRule(elasticSearchRule).around(engineRule).around(embeddedOptimizeRule);

  private final static String USER_TASK_ACTIVITY_ID = "User-Task";
  private final static String USER_TASK_ACTIVITY_ID_2 = "User-Task2";
  private final static String END_EVENT_ACTIVITY_ID = "endEvent";

  private RawDataProcessReportResultDto evaluateReportWithFilter(ProcessDefinitionEngineDto processDefinition,
                                                                 List<ProcessFilterDto> filter) {
    ProcessReportDataDto reportData =
      createProcessReportDataViewRawAsTable(processDefinition.getKey(), String.valueOf(processDefinition.getVersion()));
    reportData.setFilter(filter);
    return evaluateReportAndReturnResult(reportData);
  }

  private RawDataProcessReportResultDto evaluateReportAndReturnResult(final ProcessReportDataDto reportData) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      // @formatter:off
      .execute(new TypeReference<ProcessReportEvaluationResultDto<RawDataProcessReportResultDto>>() {})
      // @formatter:on
      .getResult();
  }

  private Response evaluateReportAndReturnResponse(List<ProcessFilterDto> filterDto) {
    ProcessReportDataDto reportData = createProcessReportDataViewRawAsTable(
      ExecutedFlowNodeQueryFilterIT.TEST_DEFINITION,
      "1"
    );
    reportData.setFilter(filterDto);
    return evaluateReportAndReturnResponse(reportData);
  }

  private Response evaluateReportAndReturnResponse(ProcessReportDataDto reportData) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      .execute();
  }

  @Test
  public void filterByOneFlowNode() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleUserTaskProcessDefinition();
    ProcessInstanceEngineDto instanceEngineDto = engineRule.startProcessInstance(processDefinition.getId());
    engineRule.finishAllRunningUserTasks(instanceEngineDto.getId());
    engineRule.startProcessInstance(processDefinition.getId());
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    List<ProcessFilterDto> executedFlowNodes = ProcessFilterBuilder
      .filter()
      .executedFlowNodes()
      .id(END_EVENT_ACTIVITY_ID)
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
    ProcessInstanceEngineDto instanceEngineDto = engineRule.startProcessInstance(processDefinition.getId());
    engineRule.finishAllRunningUserTasks(instanceEngineDto.getId());
    engineRule.startProcessInstance(processDefinition.getId());
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    List<ProcessFilterDto> executedFlowNodes = ProcessFilterBuilder
      .filter()
      .executedFlowNodes()
      .id(END_EVENT_ACTIVITY_ID)
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
    ProcessInstanceEngineDto instanceEngineDto = engineRule.startProcessInstance(processDefinition.getId());
    engineRule.finishAllRunningUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineRule.startProcessInstance(processDefinition.getId());
    engineRule.finishAllRunningUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineRule.startProcessInstance(processDefinition.getId());
    engineRule.finishAllRunningUserTasks(instanceEngineDto.getId());
    engineRule.startProcessInstance(processDefinition.getId());
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    List<ProcessFilterDto> executedFlowNodes = ProcessFilterBuilder
      .filter()
      .executedFlowNodes()
      .id(END_EVENT_ACTIVITY_ID)
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
    ProcessInstanceEngineDto instanceEngineDto = engineRule.startProcessInstance(processDefinition.getId());
    engineRule.finishAllRunningUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineRule.startProcessInstance(processDefinition.getId());
    engineRule.finishAllRunningUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineRule.startProcessInstance(processDefinition.getId());
    engineRule.finishAllRunningUserTasks(instanceEngineDto.getId());
    engineRule.startProcessInstance(processDefinition.getId());
    engineRule.startProcessInstance(processDefinition.getId());
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    List<ProcessFilterDto> executedFlowNodes = ProcessFilterBuilder
      .filter()
      .executedFlowNodes()
      .id(END_EVENT_ACTIVITY_ID)
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
    ProcessDefinitionEngineDto processDefinition = deployProcessDefinitionWithTwoUserTasks();
    ProcessInstanceEngineDto instanceEngineDto = engineRule.startProcessInstance(processDefinition.getId());
    engineRule.finishAllRunningUserTasks(instanceEngineDto.getId());
    engineRule.finishAllRunningUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineRule.startProcessInstance(processDefinition.getId());
    engineRule.finishAllRunningUserTasks(instanceEngineDto.getId());
    engineRule.finishAllRunningUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineRule.startProcessInstance(processDefinition.getId());
    engineRule.finishAllRunningUserTasks(instanceEngineDto.getId());
    engineRule.startProcessInstance(processDefinition.getId());
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();
    // when

    List<ProcessFilterDto> executedFlowNodes = ProcessFilterBuilder.filter()
      .executedFlowNodes()
      .id(USER_TASK_ACTIVITY_ID_2)
      .add()
      .executedFlowNodes()
      .id(END_EVENT_ACTIVITY_ID)
      .add()
      .buildList();

    RawDataProcessReportResultDto result = evaluateReportWithFilter(processDefinition, executedFlowNodes);

    // then
    assertResults(result, 2);
  }

  @Test
  public void filterByMultipleAndCombinedFlowNodesWithUnequalOperator() {
    // given
    ProcessDefinitionEngineDto processDefinition = deployProcessDefinitionWithTwoUserTasks();
    ProcessInstanceEngineDto instanceEngineDto = engineRule.startProcessInstance(processDefinition.getId());
    engineRule.finishAllRunningUserTasks(instanceEngineDto.getId());
    engineRule.finishAllRunningUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineRule.startProcessInstance(processDefinition.getId());
    engineRule.finishAllRunningUserTasks(instanceEngineDto.getId());
    engineRule.finishAllRunningUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineRule.startProcessInstance(processDefinition.getId());
    engineRule.finishAllRunningUserTasks(instanceEngineDto.getId());
    engineRule.startProcessInstance(processDefinition.getId());
    engineRule.startProcessInstance(processDefinition.getId());
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    List<ProcessFilterDto> executedFlowNodes = ProcessFilterBuilder.filter().executedFlowNodes()
      .id(USER_TASK_ACTIVITY_ID_2)
      .add()
      .executedFlowNodes()
      .id(END_EVENT_ACTIVITY_ID)
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
      .id(USER_TASK_ACTIVITY_ID_2)
      .notInOperator()
      .add()
      .executedFlowNodes()
      .id(END_EVENT_ACTIVITY_ID)
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
    ProcessDefinitionEngineDto processDefinition = deployProcessDefinitionWithTwoUserTasks();
    ProcessInstanceEngineDto instanceEngineDto = engineRule.startProcessInstance(processDefinition.getId());
    engineRule.finishAllRunningUserTasks(instanceEngineDto.getId());
    engineRule.finishAllRunningUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineRule.startProcessInstance(processDefinition.getId());
    engineRule.finishAllRunningUserTasks(instanceEngineDto.getId());
    engineRule.finishAllRunningUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineRule.startProcessInstance(processDefinition.getId());
    engineRule.finishAllRunningUserTasks(instanceEngineDto.getId());
    engineRule.startProcessInstance(processDefinition.getId());
    engineRule.startProcessInstance(processDefinition.getId());
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    List<ProcessFilterDto> executedFlowNodes = ProcessFilterBuilder.filter()
      .executedFlowNodes()
      .ids(USER_TASK_ACTIVITY_ID_2, END_EVENT_ACTIVITY_ID)
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
    ProcessInstanceEngineDto instanceEngineDto = engineRule.startProcessInstance(processDefinition.getId(), takePathA);
    engineRule.finishAllRunningUserTasks(instanceEngineDto.getId());
    engineRule.finishAllRunningUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineRule.startProcessInstance(processDefinition.getId(), takePathB);
    engineRule.finishAllRunningUserTasks(instanceEngineDto.getId());
    engineRule.finishAllRunningUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineRule.startProcessInstance(processDefinition.getId(), takePathA);
    engineRule.finishAllRunningUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineRule.startProcessInstance(processDefinition.getId(), takePathB);
    engineRule.finishAllRunningUserTasks(instanceEngineDto.getId());
    engineRule.startProcessInstance(processDefinition.getId(), takePathA);
    engineRule.startProcessInstance(processDefinition.getId(), takePathB);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    List<ProcessFilterDto> executedFlowNodes = ProcessFilterBuilder
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
    ProcessInstanceEngineDto instanceEngineDto = engineRule.startProcessInstance(processDefinition.getId(), takePathA);
    engineRule.finishAllRunningUserTasks(instanceEngineDto.getId());
    engineRule.finishAllRunningUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineRule.startProcessInstance(processDefinition.getId(), takePathA);
    engineRule.finishAllRunningUserTasks(instanceEngineDto.getId());
    engineRule.finishAllRunningUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineRule.startProcessInstance(processDefinition.getId(), takePathB);
    engineRule.finishAllRunningUserTasks(instanceEngineDto.getId());
    engineRule.finishAllRunningUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineRule.startProcessInstance(processDefinition.getId(), takePathB);
    engineRule.finishAllRunningUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineRule.startProcessInstance(processDefinition.getId(), takePathB);
    engineRule.finishAllRunningUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineRule.startProcessInstance(processDefinition.getId(), takePathA);
    engineRule.finishAllRunningUserTasks(instanceEngineDto.getId());
    engineRule.startProcessInstance(processDefinition.getId(), takePathA);
    engineRule.startProcessInstance(processDefinition.getId(), takePathB);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    List<ProcessFilterDto> executedFlowNodes =
      ProcessFilterBuilder
        .filter()
        .executedFlowNodes()
        .ids("UserTask-PathA", "UserTask-PathB")
        .inOperator()
        .add()
        .executedFlowNodes()
        .id(END_EVENT_ACTIVITY_ID)
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
    ProcessInstanceEngineDto instanceEngineDto = engineRule.startProcessInstance(processDefinition.getId(), takePathA);
    engineRule.finishAllRunningUserTasks(instanceEngineDto.getId());
    engineRule.finishAllRunningUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineRule.startProcessInstance(processDefinition.getId(), takePathB);
    engineRule.finishAllRunningUserTasks(instanceEngineDto.getId());
    engineRule.finishAllRunningUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineRule.startProcessInstance(processDefinition.getId(), takePathA);
    engineRule.finishAllRunningUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineRule.startProcessInstance(processDefinition.getId(), takePathB);
    engineRule.finishAllRunningUserTasks(instanceEngineDto.getId());
    engineRule.startProcessInstance(processDefinition.getId(), takePathA);
    engineRule.startProcessInstance(processDefinition.getId(), takePathB);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    List<ProcessFilterDto> executedFlowNodes =
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
      .endEvent(END_EVENT_ACTIVITY_ID)
      .moveToLastGateway()
      .moveToLastGateway()
      .condition("Take path B", "${!takePathA}")
      .userTask("UserTask-PathB")
      .connectTo("mergeExclusiveGateway")
      .done();
    return engineRule.deployProcessAndGetProcessDefinition(modelInstance);
  }

  @Test
  public void sameFlowNodeInDifferentProcessDefinitionDoesNotDistortResult() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleUserTaskProcessDefinition();
    ProcessDefinitionEngineDto processDefinition2 = deploySimpleUserTaskProcessDefinition();
    ProcessInstanceEngineDto instanceEngineDto = engineRule.startProcessInstance(processDefinition.getId());
    engineRule.finishAllRunningUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineRule.startProcessInstance(processDefinition.getId());
    engineRule.finishAllRunningUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineRule.startProcessInstance(processDefinition2.getId());
    engineRule.finishAllRunningUserTasks(instanceEngineDto.getId());
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    List<ProcessFilterDto> executedFlowNodes = ProcessFilterBuilder
      .filter()
      .executedFlowNodes()
      .id(END_EVENT_ACTIVITY_ID)
      .add()
      .buildList();
    RawDataProcessReportResultDto result = evaluateReportWithFilter(processDefinition, executedFlowNodes);

    // then
    assertResults(result, 2);
  }

  @Test
  public void validationExceptionOnNullOperatorField() {
    //given
    List<ProcessFilterDto> filterDtos = ProcessFilterBuilder
      .filter()
      .executedFlowNodes()
      .id("foo")
      .operator(null)
      .add()
      .buildList();

    // when
    Response response = evaluateReportAndReturnResponse(filterDtos);

    // then
    assertThat(response.getStatus(), is(500));
  }

  @Test
  public void validationExceptionOnNullValueField() {
    //given
    List<ProcessFilterDto> filterDtos = ProcessFilterBuilder
      .filter()
      .executedFlowNodes()
      .id(null)
      .add()
      .buildList();

    // when
    Response response = evaluateReportAndReturnResponse(filterDtos);

    // then
    assertThat(response.getStatus(), is(500));
  }

  private void assertResults(RawDataProcessReportResultDto resultDto, int piCount) {
    assertThat(resultDto.getData().size(), is(piCount));
  }


  private ProcessDefinitionEngineDto deployProcessDefinitionWithTwoUserTasks() {
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess()
      .startEvent()
      .userTask(USER_TASK_ACTIVITY_ID)
      .userTask(USER_TASK_ACTIVITY_ID_2)
      .endEvent(END_EVENT_ACTIVITY_ID)
      .done();
    return engineRule.deployProcessAndGetProcessDefinition(modelInstance);
  }

  private ProcessDefinitionEngineDto deploySimpleUserTaskProcessDefinition() {
    return deploySimpleUserTaskProcessDefinition(USER_TASK_ACTIVITY_ID);
  }

  private ProcessDefinitionEngineDto deploySimpleUserTaskProcessDefinition(String userTaskActivityId) {
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess("ASimpleUserTaskProcess" + System
      .currentTimeMillis())
      .startEvent()
      .userTask(userTaskActivityId)
      .endEvent(END_EVENT_ACTIVITY_ID)
      .done();
    return engineRule.deployProcessAndGetProcessDefinition(modelInstance);
  }


}
