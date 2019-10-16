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
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResultDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.test.it.extension.ElasticSearchIntegrationTestExtensionRule;
import org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtensionRule;
import org.camunda.optimize.test.it.extension.EngineIntegrationExtensionRule;
import org.camunda.optimize.test.util.ProcessReportDataBuilder;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.camunda.optimize.test.util.ProcessReportDataType.RAW_DATA;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class ExecutedFlowNodeQueryFilterIT {

  private static final String TEST_DEFINITION = "TestDefinition";
  private final static String USER_TASK_ACTIVITY_ID = "User-Task";
  private final static String USER_TASK_ACTIVITY_ID_2 = "User-Task2";
  private final static String END_EVENT_ACTIVITY_ID = "endEvent";
  @RegisterExtension
  @Order(1)
  public ElasticSearchIntegrationTestExtensionRule elasticSearchIntegrationTestExtensionRule = new ElasticSearchIntegrationTestExtensionRule();
  @RegisterExtension
  @Order(2)
  public EngineIntegrationExtensionRule engineIntegrationExtensionRule = new EngineIntegrationExtensionRule();
  @RegisterExtension
  @Order(3)
  public EmbeddedOptimizeExtensionRule embeddedOptimizeExtensionRule = new EmbeddedOptimizeExtensionRule();

  private RawDataProcessReportResultDto evaluateReportWithFilter(ProcessDefinitionEngineDto processDefinition,
                                                                 List<ProcessFilterDto> filter) {
    ProcessReportDataDto reportData =
      createReport(processDefinition.getKey(), processDefinition.getVersionAsString());
    reportData.setFilter(filter);
    return evaluateReportAndReturnResult(reportData);
  }

  private ProcessReportDataDto createReport(String key, String versionAsString) {
    return ProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(key)
      .setProcessDefinitionVersion(versionAsString)
      .setReportDataType(RAW_DATA)
      .build();
  }

  private RawDataProcessReportResultDto evaluateReportAndReturnResult(final ProcessReportDataDto reportData) {
    return embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      // @formatter:off
      .execute(new TypeReference<AuthorizedProcessReportEvaluationResultDto<RawDataProcessReportResultDto>>() {})
      // @formatter:on
      .getResult();
  }

  private Response evaluateReportAndReturnResponse(List<ProcessFilterDto> filterDto) {
    ProcessReportDataDto reportData = createReport(ExecutedFlowNodeQueryFilterIT.TEST_DEFINITION, "1");
    reportData.setFilter(filterDto);
    return evaluateReportAndReturnResponse(reportData);
  }

  private Response evaluateReportAndReturnResponse(ProcessReportDataDto reportData) {
    return embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      .execute();
  }

  @Test
  public void filterByOneFlowNode() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleUserTaskProcessDefinition();
    ProcessInstanceEngineDto instanceEngineDto = engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId());
    engineIntegrationExtensionRule.finishAllRunningUserTasks(instanceEngineDto.getId());
    engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId());
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

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
    ProcessInstanceEngineDto instanceEngineDto = engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId());
    engineIntegrationExtensionRule.finishAllRunningUserTasks(instanceEngineDto.getId());
    engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId());
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

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
    ProcessInstanceEngineDto instanceEngineDto = engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId());
    engineIntegrationExtensionRule.finishAllRunningUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId());
    engineIntegrationExtensionRule.finishAllRunningUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId());
    engineIntegrationExtensionRule.finishAllRunningUserTasks(instanceEngineDto.getId());
    engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId());
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

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
    ProcessInstanceEngineDto instanceEngineDto = engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId());
    engineIntegrationExtensionRule.finishAllRunningUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId());
    engineIntegrationExtensionRule.finishAllRunningUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId());
    engineIntegrationExtensionRule.finishAllRunningUserTasks(instanceEngineDto.getId());
    engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId());
    engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId());
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

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
    ProcessInstanceEngineDto instanceEngineDto = engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId());
    engineIntegrationExtensionRule.finishAllRunningUserTasks(instanceEngineDto.getId());
    engineIntegrationExtensionRule.finishAllRunningUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId());
    engineIntegrationExtensionRule.finishAllRunningUserTasks(instanceEngineDto.getId());
    engineIntegrationExtensionRule.finishAllRunningUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId());
    engineIntegrationExtensionRule.finishAllRunningUserTasks(instanceEngineDto.getId());
    engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId());
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();
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
    ProcessInstanceEngineDto instanceEngineDto = engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId());
    engineIntegrationExtensionRule.finishAllRunningUserTasks(instanceEngineDto.getId());
    engineIntegrationExtensionRule.finishAllRunningUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId());
    engineIntegrationExtensionRule.finishAllRunningUserTasks(instanceEngineDto.getId());
    engineIntegrationExtensionRule.finishAllRunningUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId());
    engineIntegrationExtensionRule.finishAllRunningUserTasks(instanceEngineDto.getId());
    engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId());
    engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId());
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

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
    ProcessInstanceEngineDto instanceEngineDto = engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId());
    engineIntegrationExtensionRule.finishAllRunningUserTasks(instanceEngineDto.getId());
    engineIntegrationExtensionRule.finishAllRunningUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId());
    engineIntegrationExtensionRule.finishAllRunningUserTasks(instanceEngineDto.getId());
    engineIntegrationExtensionRule.finishAllRunningUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId());
    engineIntegrationExtensionRule.finishAllRunningUserTasks(instanceEngineDto.getId());
    engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId());
    engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId());
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

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
    ProcessInstanceEngineDto instanceEngineDto = engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId(), takePathA);
    engineIntegrationExtensionRule.finishAllRunningUserTasks(instanceEngineDto.getId());
    engineIntegrationExtensionRule.finishAllRunningUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId(), takePathB);
    engineIntegrationExtensionRule.finishAllRunningUserTasks(instanceEngineDto.getId());
    engineIntegrationExtensionRule.finishAllRunningUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId(), takePathA);
    engineIntegrationExtensionRule.finishAllRunningUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId(), takePathB);
    engineIntegrationExtensionRule.finishAllRunningUserTasks(instanceEngineDto.getId());
    engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId(), takePathA);
    engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId(), takePathB);
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

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
    ProcessInstanceEngineDto instanceEngineDto = engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId(), takePathA);
    engineIntegrationExtensionRule.finishAllRunningUserTasks(instanceEngineDto.getId());
    engineIntegrationExtensionRule.finishAllRunningUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId(), takePathA);
    engineIntegrationExtensionRule.finishAllRunningUserTasks(instanceEngineDto.getId());
    engineIntegrationExtensionRule.finishAllRunningUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId(), takePathB);
    engineIntegrationExtensionRule.finishAllRunningUserTasks(instanceEngineDto.getId());
    engineIntegrationExtensionRule.finishAllRunningUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId(), takePathB);
    engineIntegrationExtensionRule.finishAllRunningUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId(), takePathB);
    engineIntegrationExtensionRule.finishAllRunningUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId(), takePathA);
    engineIntegrationExtensionRule.finishAllRunningUserTasks(instanceEngineDto.getId());
    engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId(), takePathA);
    engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId(), takePathB);
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

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
    ProcessInstanceEngineDto instanceEngineDto = engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId(), takePathA);
    engineIntegrationExtensionRule.finishAllRunningUserTasks(instanceEngineDto.getId());
    engineIntegrationExtensionRule.finishAllRunningUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId(), takePathB);
    engineIntegrationExtensionRule.finishAllRunningUserTasks(instanceEngineDto.getId());
    engineIntegrationExtensionRule.finishAllRunningUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId(), takePathA);
    engineIntegrationExtensionRule.finishAllRunningUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId(), takePathB);
    engineIntegrationExtensionRule.finishAllRunningUserTasks(instanceEngineDto.getId());
    engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId(), takePathA);
    engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId(), takePathB);
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

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
    return engineIntegrationExtensionRule.deployProcessAndGetProcessDefinition(modelInstance);
  }

  @Test
  public void sameFlowNodeInDifferentProcessDefinitionDoesNotDistortResult() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleUserTaskProcessDefinition();
    ProcessDefinitionEngineDto processDefinition2 = deploySimpleUserTaskProcessDefinition();
    ProcessInstanceEngineDto instanceEngineDto = engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId());
    engineIntegrationExtensionRule.finishAllRunningUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId());
    engineIntegrationExtensionRule.finishAllRunningUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineIntegrationExtensionRule.startProcessInstance(processDefinition2.getId());
    engineIntegrationExtensionRule.finishAllRunningUserTasks(instanceEngineDto.getId());
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

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
    return engineIntegrationExtensionRule.deployProcessAndGetProcessDefinition(modelInstance);
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
    return engineIntegrationExtensionRule.deployProcessAndGetProcessDefinition(modelInstance);
  }


}
