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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.util.BpmnModels.END_EVENT;
import static org.camunda.optimize.util.BpmnModels.START_EVENT_ID;
import static org.camunda.optimize.util.BpmnModels.USER_TASK_1;
import static org.camunda.optimize.util.BpmnModels.USER_TASK_2;

public class CanceledFlowNodeQueryFilterIT extends AbstractFilterIT {

  private final static String USER_TASK_3 = "UserTask3";

  @Test
  public void filterForOneCanceledFlowNode() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleUserTaskProcessDefinition();
    ProcessInstanceEngineDto instanceEngineDto =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.cancelActivityInstance(instanceEngineDto.getId(), USER_TASK_1);
    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessFilterDto<?>> canceledFlowNodes = ProcessFilterBuilder
      .filter()
      .canceledFlowNodes()
      .id(USER_TASK_1)
      .add()
      .buildList();

    final ReportResultResponseDto<List<RawDataProcessInstanceDto>> resultDto = evaluateReportWithFilter(processDefinition, canceledFlowNodes);

    // then
    assertThat(resultDto.getInstanceCount()).isEqualTo(1);
    assertThat(resultDto.getInstanceCountWithoutFilters()).isEqualTo(2);
    assertThat(resultDto.getData()).singleElement()
      .satisfies(data -> assertThat(data.getProcessInstanceId()).isEqualTo(instanceEngineDto.getId()));
  }

  @Test
  public void filterMultipleProcessInstancesByOneFlowNode() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleUserTaskProcessDefinition();
    final ProcessInstanceEngineDto firstCanceledInstance =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.cancelActivityInstance(firstCanceledInstance.getId(), USER_TASK_1);
    final ProcessInstanceEngineDto secondCanceledInstance =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.cancelActivityInstance(secondCanceledInstance.getId(), USER_TASK_1);
    final ProcessInstanceEngineDto completedInstance =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(completedInstance.getId());
    engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessFilterDto<?>> canceledFlowNodes = ProcessFilterBuilder
      .filter()
      .canceledFlowNodes()
      .id(USER_TASK_1)
      .add()
      .buildList();
    ReportResultResponseDto<List<RawDataProcessInstanceDto>> result = evaluateReportWithFilter(processDefinition, canceledFlowNodes);

    // then
    assertThat(result.getInstanceCount()).isEqualTo(2);
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(4);
    assertThat(result.getData()).hasSize(2)
      .extracting(RawDataProcessInstanceDto::getProcessInstanceId)
      .containsExactlyInAnyOrder(
        firstCanceledInstance.getId(),
        secondCanceledInstance.getId()
      );
  }

  @Test
  public void filterByMultipleOrCombinedFlowNodes() {
    // given
    ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksProcessDefinition();
    final ProcessInstanceEngineDto firstCanceledInstance =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.cancelActivityInstance(firstCanceledInstance.getId(), USER_TASK_1);
    final ProcessInstanceEngineDto secondCanceledInstance =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(secondCanceledInstance.getId());
    engineIntegrationExtension.cancelActivityInstance(secondCanceledInstance.getId(), USER_TASK_2);
    final ProcessInstanceEngineDto completedInstance =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(completedInstance.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(completedInstance.getId());
    engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessFilterDto<?>> canceledFlowNodes = ProcessFilterBuilder.filter()
      .canceledFlowNodes()
      .ids(USER_TASK_1, USER_TASK_2)
      .add()
      .buildList();

    ReportResultResponseDto<List<RawDataProcessInstanceDto>> result = evaluateReportWithFilter(processDefinition, canceledFlowNodes);

    // then
    assertThat(result.getInstanceCount()).isEqualTo(2);
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(4);
    assertThat(result.getData()).hasSize(2)
      .extracting(RawDataProcessInstanceDto::getProcessInstanceId)
      .containsExactlyInAnyOrder(
        firstCanceledInstance.getId(),
        secondCanceledInstance.getId()
      );
  }

  @Test
  public void filterByMultipleAndCombinedFlowNodes() {
    // given
    ProcessDefinitionEngineDto processDefinition = deployProcessWithThreeParallelUserTasks();
    final ProcessInstanceEngineDto twoCancelsInstance =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.cancelActivityInstance(twoCancelsInstance.getId(), USER_TASK_1);
    engineIntegrationExtension.cancelActivityInstance(twoCancelsInstance.getId(), USER_TASK_2);
    final ProcessInstanceEngineDto oneCancelInstance =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.cancelActivityInstance(oneCancelInstance.getId(), USER_TASK_1);
    final ProcessInstanceEngineDto completedInstance =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(completedInstance.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(completedInstance.getId());
    engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessFilterDto<?>> canceledFlowNodes = ProcessFilterBuilder.filter()
      .canceledFlowNodes()
      .id(USER_TASK_1)
      .add()
      .canceledFlowNodes()
      .id(USER_TASK_2)
      .add()
      .buildList();

    ReportResultResponseDto<List<RawDataProcessInstanceDto>> result = evaluateReportWithFilter(processDefinition, canceledFlowNodes);

    // then
    assertThat(result.getInstanceCount()).isEqualTo(1);
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(4);
    assertThat(result.getData()).singleElement()
      .satisfies(data -> assertThat(data.getProcessInstanceId()).isEqualTo(twoCancelsInstance.getId()));
  }

  @Test
  public void filterByMultipleAndOrCombinedFlowNodes() {
    // given
    ProcessDefinitionEngineDto processDefinition = deployProcessWithThreeParallelUserTasks();
    final ProcessInstanceEngineDto tasksOneAndTwoCanceled =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.cancelActivityInstance(tasksOneAndTwoCanceled.getId(), USER_TASK_1);
    engineIntegrationExtension.cancelActivityInstance(tasksOneAndTwoCanceled.getId(), USER_TASK_3);
    final ProcessInstanceEngineDto tasksTwoAndThreeCanceled =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.cancelActivityInstance(tasksTwoAndThreeCanceled.getId(), USER_TASK_2);
    engineIntegrationExtension.cancelActivityInstance(tasksTwoAndThreeCanceled.getId(), USER_TASK_3);
    final ProcessInstanceEngineDto taskOneCanceled =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.cancelActivityInstance(taskOneCanceled.getId(), USER_TASK_1);
    final ProcessInstanceEngineDto taskThreeCanceled =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.cancelActivityInstance(taskThreeCanceled.getId(), USER_TASK_3);
    engineIntegrationExtension.finishAllRunningUserTasks(taskOneCanceled.getId());
    engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    importAllEngineEntitiesFromScratch();

    // when we filter for tasks (1 OR 2) AND task 3 to be canceled
    List<ProcessFilterDto<?>> canceledFlowNodes =
      ProcessFilterBuilder
        .filter()
        .canceledFlowNodes()
        .ids(USER_TASK_1, USER_TASK_2)
        .add()
        .canceledFlowNodes()
        .id(USER_TASK_3)
        .add()
        .buildList();
    ReportResultResponseDto<List<RawDataProcessInstanceDto>> result = evaluateReportWithFilter(processDefinition, canceledFlowNodes);

    // then
    assertThat(result.getInstanceCount()).isEqualTo(2);
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(5);
    assertThat(result.getData()).hasSize(2)
      .extracting(RawDataProcessInstanceDto::getProcessInstanceId)
      .containsExactlyInAnyOrder(
        tasksOneAndTwoCanceled.getId(),
        tasksTwoAndThreeCanceled.getId()
      );
  }

  @Test
  public void sameFlowNodeInDifferentProcessDefinitionDoesNotDistortResult() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleUserTaskProcessDefinition();
    ProcessDefinitionEngineDto processDefinition2 = deploySimpleUserTaskProcessDefinition();
    final ProcessInstanceEngineDto firstDefCanceledInstanceOne =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.cancelActivityInstance(firstDefCanceledInstanceOne.getId(), USER_TASK_1);
    final ProcessInstanceEngineDto firstDefCanceledInstanceTwo =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.cancelActivityInstance(firstDefCanceledInstanceTwo.getId(), USER_TASK_1);
    final ProcessInstanceEngineDto secondDefCanceledInstance =
      engineIntegrationExtension.startProcessInstance(processDefinition2.getId());
    engineIntegrationExtension.cancelActivityInstance(secondDefCanceledInstance.getId(), USER_TASK_1);
    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessFilterDto<?>> canceledFlowNodes = ProcessFilterBuilder
      .filter()
      .canceledFlowNodes()
      .id(USER_TASK_1)
      .add()
      .buildList();
    ReportResultResponseDto<List<RawDataProcessInstanceDto>> result = evaluateReportWithFilter(processDefinition, canceledFlowNodes);

    // then
    assertThat(result.getInstanceCount()).isEqualTo(2);
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(2);
    assertThat(result.getData()).hasSize(2)
      .extracting(RawDataProcessInstanceDto::getProcessInstanceId)
      .containsExactlyInAnyOrder(
        firstDefCanceledInstanceOne.getId(),
        firstDefCanceledInstanceTwo.getId()
      );
  }

  @Test
  public void filterForOneCanceledFlowNodeWithDeletedInstance() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleUserTaskProcessDefinition();
    ProcessInstanceEngineDto instanceEngineDto =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.deleteProcessInstance(instanceEngineDto.getId());
    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessFilterDto<?>> canceledFlowNodes = ProcessFilterBuilder
      .filter()
      .canceledFlowNodes()
      .id(USER_TASK_1)
      .add()
      .buildList();

    final ReportResultResponseDto<List<RawDataProcessInstanceDto>> resultDto = evaluateReportWithFilter(processDefinition, canceledFlowNodes);

    // then
    assertThat(resultDto.getInstanceCount()).isEqualTo(1);
    assertThat(resultDto.getInstanceCountWithoutFilters()).isEqualTo(2);
    assertThat(resultDto.getData()).singleElement()
      .satisfies(data -> assertThat(data.getProcessInstanceId()).isEqualTo(instanceEngineDto.getId()));
  }

  @Test
  public void filterForOneCanceledFlowNodeCombinedWithOtherFilter() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleUserTaskProcessDefinition();
    ProcessInstanceEngineDto instanceEngineDto =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.deleteProcessInstance(instanceEngineDto.getId());
    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessFilterDto<?>> canceledFlowNodes = ProcessFilterBuilder
      .filter()
      .canceledFlowNodes()
      .id(USER_TASK_1)
      .add()
      .executedFlowNodes()
      .id(START_EVENT_ID)
      .add()
      .buildList();

    final ReportResultResponseDto<List<RawDataProcessInstanceDto>> resultDto = evaluateReportWithFilter(processDefinition, canceledFlowNodes);

    // then
    assertThat(resultDto.getInstanceCount()).isEqualTo(1);
    assertThat(resultDto.getInstanceCountWithoutFilters()).isEqualTo(2);
    assertThat(resultDto.getData()).singleElement()
      .satisfies(data -> assertThat(data.getProcessInstanceId()).isEqualTo(instanceEngineDto.getId()));
  }

  @Test
  public void validationExceptionOnNullValueField() {
    // given
    List<ProcessFilterDto<?>> filterDtos = ProcessFilterBuilder
      .filter()
      .canceledFlowNodes()
      .id(null)
      .add()
      .buildList();

    // when
    Response response = evaluateReportAndReturnResponse(filterDtos);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
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

  private ProcessDefinitionEngineDto deployProcessWithThreeParallelUserTasks() {
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess()
      .startEvent()
      .parallelGateway("splittingGateway")
      .userTask(USER_TASK_1)
      .parallelGateway("mergeParallelGateway")
      .endEvent(END_EVENT)
      .moveToNode("splittingGateway")
      .userTask(USER_TASK_2)
      .connectTo("mergeParallelGateway")
      .moveToNode("splittingGateway")
      .userTask(USER_TASK_3)
      .connectTo("mergeParallelGateway")
      .done();
    return engineIntegrationExtension.deployProcessAndGetProcessDefinition(modelInstance);
  }

}
