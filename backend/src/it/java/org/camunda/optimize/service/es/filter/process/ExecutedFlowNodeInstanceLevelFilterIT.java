/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.filter.process;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessInstanceDto;
import org.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.util.BpmnModels;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.util.BpmnModels.END_EVENT;
import static org.camunda.optimize.util.BpmnModels.USER_TASK_2;

public class ExecutedFlowNodeInstanceLevelFilterIT extends AbstractFilterIT {

  @Test
  public void filterByOneFlowNode() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleUserTaskProcessDefinition();
    ProcessInstanceEngineDto instanceEngineDto =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
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

    ReportResultResponseDto<List<RawDataProcessInstanceDto>> result = evaluateReportWithFilter(processDefinition, executedFlowNodes);

    // then
    assertThat(result.getInstanceCount()).isEqualTo(1);
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(2);
    assertThat(result.getData()).singleElement()
      .satisfies(data -> assertThat(data.getProcessInstanceId()).isEqualTo(instanceEngineDto.getId()));
  }

  @Test
  public void filterByOneFlowNodeWithUnequalOperator() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleUserTaskProcessDefinition();
    ProcessInstanceEngineDto completedInstance =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(completedInstance.getId());
    final ProcessInstanceEngineDto runningInstance = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessFilterDto<?>> executedFlowNodes = ProcessFilterBuilder
      .filter()
      .executedFlowNodes()
      .id(END_EVENT)
      .notInOperator()
      .add()
      .buildList();

    ReportResultResponseDto<List<RawDataProcessInstanceDto>> result = evaluateReportWithFilter(processDefinition, executedFlowNodes);

    // then
    assertThat(result.getInstanceCount()).isEqualTo(1);
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(2);
    assertThat(result.getData()).singleElement()
      .satisfies(data -> assertThat(data.getProcessInstanceId()).isEqualTo(runningInstance.getId()));
  }

  @Test
  public void filterMultipleProcessInstancesByOneFlowNode() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleUserTaskProcessDefinition();
    ProcessInstanceEngineDto firstCompletedInstance =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(firstCompletedInstance.getId());
    ProcessInstanceEngineDto secondCompletedInstance = engineIntegrationExtension.startProcessInstance(processDefinition
                                                                                                         .getId());
    engineIntegrationExtension.finishAllRunningUserTasks(secondCompletedInstance.getId());
    ProcessInstanceEngineDto thirdCompletedInstance =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(thirdCompletedInstance.getId());
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
    ReportResultResponseDto<List<RawDataProcessInstanceDto>> result = evaluateReportWithFilter(processDefinition, executedFlowNodes);

    // then
    assertThat(result.getInstanceCount()).isEqualTo(3);
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(4);
    assertThat(result.getData()).hasSize(3)
      .extracting(RawDataProcessInstanceDto::getProcessInstanceId)
      .containsExactlyInAnyOrder(
        firstCompletedInstance.getId(),
        secondCompletedInstance.getId(),
        thirdCompletedInstance.getId()
      );
  }

  @Test
  public void filterMultipleProcessInstancesByOneFlowNodeWithUnequalOperator() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleUserTaskProcessDefinition();
    ProcessInstanceEngineDto instanceEngineDto =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(instanceEngineDto.getId());
    final ProcessInstanceEngineDto firstRunningInstance = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    final ProcessInstanceEngineDto secondRunningInstance = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessFilterDto<?>> executedFlowNodes = ProcessFilterBuilder
      .filter()
      .executedFlowNodes()
      .id(END_EVENT)
      .notInOperator()
      .add()
      .buildList();
    ReportResultResponseDto<List<RawDataProcessInstanceDto>> result = evaluateReportWithFilter(processDefinition, executedFlowNodes);

    // then
    assertThat(result.getInstanceCount()).isEqualTo(2);
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(5);
    assertThat(result.getData()).hasSize(2)
      .extracting(RawDataProcessInstanceDto::getProcessInstanceId)
      .containsExactlyInAnyOrder(
        firstRunningInstance.getId(),
        secondRunningInstance.getId()
      );
  }

  @Test
  public void filterByMultipleAndCombinedFlowNodes() {
    // given
    ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksProcessDefinition();
    ProcessInstanceEngineDto firstCompletedInstance =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(firstCompletedInstance.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(firstCompletedInstance.getId());
    ProcessInstanceEngineDto secondCompletedInstance =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(secondCompletedInstance.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(secondCompletedInstance.getId());
    ProcessInstanceEngineDto firstRunningInstance =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(firstRunningInstance.getId());
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

    ReportResultResponseDto<List<RawDataProcessInstanceDto>> result = evaluateReportWithFilter(processDefinition, executedFlowNodes);

    // then
    assertThat(result.getInstanceCount()).isEqualTo(2);
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(4);
    assertThat(result.getData()).hasSize(2)
      .extracting(RawDataProcessInstanceDto::getProcessInstanceId)
      .containsExactlyInAnyOrder(
        firstCompletedInstance.getId(),
        secondCompletedInstance.getId()
      );
  }

  @Test
  public void filterByMultipleAndCombinedFlowNodesWithUnequalOperator() {
    // given
    ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksProcessDefinition();
    ProcessInstanceEngineDto firstCompletedInstance =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(firstCompletedInstance.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(firstCompletedInstance.getId());
    ProcessInstanceEngineDto secondCompletedInstance =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(secondCompletedInstance.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(secondCompletedInstance.getId());
    ProcessInstanceEngineDto firstRunningInstance =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(firstRunningInstance.getId());
    ProcessInstanceEngineDto secondRunningInstance =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    ProcessInstanceEngineDto thirdRunningInstance =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessFilterDto<?>> executedFlowNodes = ProcessFilterBuilder.filter()
      .executedFlowNodes()
      .id(USER_TASK_2)
      .inOperator()
      .add()
      .executedFlowNodes()
      .id(END_EVENT)
      .notInOperator()
      .add()
      .buildList();
    ReportResultResponseDto<List<RawDataProcessInstanceDto>> result = evaluateReportWithFilter(processDefinition, executedFlowNodes);

    // then
    assertThat(result.getInstanceCount()).isEqualTo(1);
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(5);
    assertThat(result.getData()).singleElement()
      .satisfies(data -> assertThat(data.getProcessInstanceId()).isEqualTo(firstRunningInstance.getId()));

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
    assertThat(result.getInstanceCount()).isEqualTo(2);
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(5);
    assertThat(result.getData()).hasSize(2)
      .extracting(RawDataProcessInstanceDto::getProcessInstanceId)
      .containsExactlyInAnyOrder(
        secondRunningInstance.getId(),
        thirdRunningInstance.getId()
      );
  }

  @Test
  public void filterByMultipleOrCombinedFlowNodes() {
    // given
    ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksProcessDefinition();
    ProcessInstanceEngineDto firstCompletedInstance =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(firstCompletedInstance.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(firstCompletedInstance.getId());
    ProcessInstanceEngineDto secondCompletedInstance =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(secondCompletedInstance.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(secondCompletedInstance.getId());
    ProcessInstanceEngineDto firstRunningInstance =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(firstRunningInstance.getId());
    ProcessInstanceEngineDto secondRunningInstance =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    ProcessInstanceEngineDto thirdRunningInstance =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessFilterDto<?>> executedFlowNodes = ProcessFilterBuilder.filter()
      .executedFlowNodes()
      .ids(USER_TASK_2, END_EVENT)
      .inOperator()
      .add()
      .buildList();
    ReportResultResponseDto<List<RawDataProcessInstanceDto>> result = evaluateReportWithFilter(processDefinition, executedFlowNodes);

    // then
    assertThat(result.getInstanceCount()).isEqualTo(3);
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(5);
    assertThat(result.getData()).hasSize(3)
      .extracting(RawDataProcessInstanceDto::getProcessInstanceId)
      .containsExactlyInAnyOrder(
        firstCompletedInstance.getId(),
        secondCompletedInstance.getId(),
        firstRunningInstance.getId()
      );
  }

  @Test
  public void filterByMultipleOrCombinedFlowNodesWithUnequalOperator() {
    // given
    ProcessDefinitionEngineDto processDefinition = deployProcessWithGatewayAndOneUserTaskEachBranch();

    Map<String, Object> takePathA = new HashMap<>();
    takePathA.put("takePathA", true);
    Map<String, Object> takePathB = new HashMap<>();
    takePathB.put("takePathA", false);
    ProcessInstanceEngineDto instanceEngineDto = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId(),
      takePathA
    );
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
    final ProcessInstanceEngineDto filteredInstance = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId(),
      takePathB
    );
    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessFilterDto<?>> executedFlowNodes = ProcessFilterBuilder
      .filter()
      .executedFlowNodes()
      .ids("UserTask-PathA", "FinalUserTask")
      .notInOperator()
      .add()
      .buildList();
    ReportResultResponseDto<List<RawDataProcessInstanceDto>> result = evaluateReportWithFilter(processDefinition, executedFlowNodes);

    // then
    assertThat(result.getInstanceCount()).isEqualTo(1);
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(6);
    assertThat(result.getData()).singleElement()
      .satisfies(data -> assertThat(data.getProcessInstanceId()).isEqualTo(filteredInstance.getId()));
  }

  @Test
  public void filterByMultipleAndOrCombinedFlowNodes() {
    // given
    ProcessDefinitionEngineDto processDefinition = deployProcessWithGatewayAndOneUserTaskEachBranch();

    Map<String, Object> takePathA = new HashMap<>();
    takePathA.put("takePathA", true);
    Map<String, Object> takePathB = new HashMap<>();
    takePathB.put("takePathA", false);
    ProcessInstanceEngineDto firstCompletedInstance =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId(), takePathA);
    engineIntegrationExtension.finishAllRunningUserTasks(firstCompletedInstance.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(firstCompletedInstance.getId());
    ProcessInstanceEngineDto secondCompletedInstance =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId(), takePathA);
    engineIntegrationExtension.finishAllRunningUserTasks(secondCompletedInstance.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(secondCompletedInstance.getId());
    ProcessInstanceEngineDto thirdCompletedInstance =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId(), takePathB);
    engineIntegrationExtension.finishAllRunningUserTasks(thirdCompletedInstance.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(thirdCompletedInstance.getId());
    ProcessInstanceEngineDto firstRunningInstance =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId(), takePathB);
    engineIntegrationExtension.finishAllRunningUserTasks(firstRunningInstance.getId());
    ProcessInstanceEngineDto secondRunningInstance =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId(), takePathB);
    engineIntegrationExtension.finishAllRunningUserTasks(secondRunningInstance.getId());
    ProcessInstanceEngineDto thirdRunningInstance =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId(), takePathA);
    engineIntegrationExtension.finishAllRunningUserTasks(thirdRunningInstance.getId());
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
    ReportResultResponseDto<List<RawDataProcessInstanceDto>> result = evaluateReportWithFilter(processDefinition, executedFlowNodes);

    // then
    assertThat(result.getInstanceCount()).isEqualTo(3);
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(8);
    assertThat(result.getData()).hasSize(3)
      .extracting(RawDataProcessInstanceDto::getProcessInstanceId)
      .containsExactlyInAnyOrder(
        firstCompletedInstance.getId(),
        secondCompletedInstance.getId(),
        thirdCompletedInstance.getId()
      );
  }

  @Test
  public void equalAndUnequalOperatorCombined() {
    // given
    ProcessDefinitionEngineDto processDefinition = deployProcessWithGatewayAndOneUserTaskEachBranch();

    Map<String, Object> takePathA = new HashMap<>();
    takePathA.put("takePathA", true);
    Map<String, Object> takePathB = new HashMap<>();
    takePathB.put("takePathA", false);
    ProcessInstanceEngineDto instanceEngineDto = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId(),
      takePathA
    );
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
    final ProcessInstanceEngineDto filteredInstance =
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
    ReportResultResponseDto<List<RawDataProcessInstanceDto>> result = evaluateReportWithFilter(processDefinition, executedFlowNodes);

    // then
    assertThat(result.getInstanceCount()).isEqualTo(1);
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(6);
    assertThat(result.getData()).singleElement()
      .satisfies(data -> assertThat(data.getProcessInstanceId()).isEqualTo(filteredInstance.getId()));
  }

  @Test
  public void sameFlowNodeInDifferentProcessDefinitionDoesNotDistortResult() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleUserTaskProcessDefinition();
    ProcessDefinitionEngineDto processDefinition2 = deploySimpleUserTaskProcessDefinition();
    ProcessInstanceEngineDto firstInstance =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(firstInstance.getId());
    ProcessInstanceEngineDto secondInstance =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(secondInstance.getId());
    ProcessInstanceEngineDto otherDefInstance =
      engineIntegrationExtension.startProcessInstance(processDefinition2.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(otherDefInstance.getId());
    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessFilterDto<?>> executedFlowNodes = ProcessFilterBuilder
      .filter()
      .executedFlowNodes()
      .id(END_EVENT)
      .add()
      .buildList();
    ReportResultResponseDto<List<RawDataProcessInstanceDto>> result = evaluateReportWithFilter(processDefinition, executedFlowNodes);

    // then
    assertThat(result.getInstanceCount()).isEqualTo(2);
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(2);
    assertThat(result.getData()).hasSize(2)
      .extracting(RawDataProcessInstanceDto::getProcessInstanceId)
      .containsExactlyInAnyOrder(
        firstInstance.getId(),
        secondInstance.getId()
      );
  }

  @Test
  public void validationExceptionOnNullOperatorField() {
    // given
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
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void validationExceptionOnNullValueField() {
    // given
    List<ProcessFilterDto<?>> filterDtos = ProcessFilterBuilder
      .filter()
      .executedFlowNodes()
      .id(null)
      .add()
      .buildList();

    // when
    Response response = evaluateReportAndReturnResponse(filterDtos);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
  }

  private ProcessDefinitionEngineDto deployProcessWithGatewayAndOneUserTaskEachBranch() {
    // @formatter:off
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess()
      .startEvent()
      .exclusiveGateway("splittingGateway").condition("Take path A", "${takePathA}")
        .userTask("UserTask-PathA")
      .exclusiveGateway("mergeExclusiveGateway")
      .userTask("FinalUserTask")
      .endEvent(END_EVENT)
      .moveToLastGateway()
      .moveToLastGateway().condition("Take path B", "${!takePathA}")
        .userTask("UserTask-PathB")
        .connectTo("mergeExclusiveGateway")
      .done();
    // @formatter:on
    return engineIntegrationExtension.deployProcessAndGetProcessDefinition(modelInstance);
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
