/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.filter.process;

import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DurationUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.data.DurationFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.report.util.MapResultUtil;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.ComparisonOperator.GREATER_THAN_EQUALS;
import static org.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel.VIEW;
import static org.camunda.optimize.util.BpmnModels.END_EVENT;
import static org.camunda.optimize.util.BpmnModels.START_EVENT;
import static org.camunda.optimize.util.BpmnModels.USER_TASK_1;
import static org.camunda.optimize.util.BpmnModels.USER_TASK_2;

public class FlowNodeStatusQueryFiltersIT extends AbstractFilterIT {

  @Test
  public void canceledFlowNodeStatusFilterWorks() {
    // given
    ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksProcessDefinition();
    ProcessInstanceEngineDto firstTaskCanceledInstance =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.cancelActivityInstance(firstTaskCanceledInstance.getId(), USER_TASK_1);
    ProcessInstanceEngineDto secondTaskCanceledInstance =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(secondTaskCanceledInstance.getId());
    engineIntegrationExtension.cancelActivityInstance(secondTaskCanceledInstance.getId(), USER_TASK_2);
    engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessFilterDto<?>> canceledFlowNodes = ProcessFilterBuilder
      .filter().canceledFlowNodesOnly().filterLevel(VIEW).add().buildList();
    final ReportResultResponseDto<List<MapResultEntryDto>> result = evaluateReport(
      processDefinition,
      canceledFlowNodes
    );

    // then
    assertThat(result.getInstanceCount()).isEqualTo(2L);
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(3L);
    assertThat(result.getFirstMeasureData()).hasSize(2);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), USER_TASK_1)).isPresent()
      .get()
      .extracting(MapResultEntryDto::getValue)
      .isEqualTo(1.);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), USER_TASK_2)).isPresent()
      .get()
      .extracting(MapResultEntryDto::getValue)
      .isEqualTo(1.);
  }

  @Test
  public void completedOrCanceledFlowNodeStatusFilterWorks() {
    // given
    ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksProcessDefinition();
    ProcessInstanceEngineDto firstTaskCanceledInstance =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.cancelActivityInstance(firstTaskCanceledInstance.getId(), USER_TASK_1);
    ProcessInstanceEngineDto secondTaskCanceledInstance =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(secondTaskCanceledInstance.getId());
    engineIntegrationExtension.cancelActivityInstance(secondTaskCanceledInstance.getId(), USER_TASK_2);
    engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessFilterDto<?>> completedOrCanceledFlowNodes = ProcessFilterBuilder
      .filter().completedOrCanceledFlowNodesOnly().filterLevel(VIEW).add().buildList();
    final ReportResultResponseDto<List<MapResultEntryDto>> result = evaluateReport(
      processDefinition,
      completedOrCanceledFlowNodes
    );

    // then
    assertThat(result.getInstanceCount()).isEqualTo(3L);
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(3L);
    assertThat(result.getFirstMeasureData()).hasSize(3);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), START_EVENT)).isPresent()
      .get()
      .extracting(MapResultEntryDto::getValue)
      .isEqualTo(3.);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), USER_TASK_1)).isPresent()
      .get()
      .extracting(MapResultEntryDto::getValue)
      .isEqualTo(2.);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), USER_TASK_2)).isPresent()
      .get()
      .extracting(MapResultEntryDto::getValue)
      .isEqualTo(1.);
  }

  @Test
  public void completedFlowNodeStatusFilterWorks() {
    // given
    ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksProcessDefinition();
    ProcessInstanceEngineDto runningInstance =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(runningInstance.getId());
    engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    ProcessInstanceEngineDto finishedInstance =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(finishedInstance.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(finishedInstance.getId());
    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessFilterDto<?>> completedFlowNodes = ProcessFilterBuilder
      .filter().completedFlowNodesOnly().filterLevel(VIEW).add().buildList();
    final ReportResultResponseDto<List<MapResultEntryDto>> result = evaluateReport(
      processDefinition,
      completedFlowNodes
    );

    // then
    assertThat(result.getInstanceCount()).isEqualTo(3L);
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(3L);
    assertThat(result.getFirstMeasureData()).hasSize(4);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), START_EVENT)).isPresent()
      .get()
      .extracting(MapResultEntryDto::getValue)
      .isEqualTo(3.);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), USER_TASK_1)).isPresent()
      .get()
      .extracting(MapResultEntryDto::getValue)
      .isEqualTo(2.);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), USER_TASK_2)).isPresent()
      .get()
      .extracting(MapResultEntryDto::getValue)
      .isEqualTo(1.);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), END_EVENT)).isPresent()
      .get()
      .extracting(MapResultEntryDto::getValue)
      .isEqualTo(1.);
  }

  @Test
  public void runningFlowNodeStatusFilterWorks() {
    // given
    ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksProcessDefinition();
    ProcessInstanceEngineDto firstInstance =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(firstInstance.getId());
    engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    ProcessInstanceEngineDto finishedInstance =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(finishedInstance.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(finishedInstance.getId());
    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessFilterDto<?>> runningFlowNodes = ProcessFilterBuilder
      .filter().runningFlowNodesOnly().filterLevel(VIEW).add().buildList();
    final ReportResultResponseDto<List<MapResultEntryDto>> result = evaluateReport(processDefinition, runningFlowNodes);

    // then
    assertThat(result.getInstanceCount()).isEqualTo(2L);
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(3L);
    assertThat(result.getFirstMeasureData()).hasSize(2);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), USER_TASK_1)).isPresent()
      .get()
      .extracting(MapResultEntryDto::getValue)
      .isEqualTo(1.);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), USER_TASK_2)).isPresent()
      .get()
      .extracting(MapResultEntryDto::getValue)
      .isEqualTo(1.);
  }

  @Test
  public void combinedFlowNodeStatusFiltersReturnNoData() {
    // given
    ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksProcessDefinition();
    ProcessInstanceEngineDto firstInstance =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(firstInstance.getId());
    engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    ProcessInstanceEngineDto finishedInstance =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(finishedInstance.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(finishedInstance.getId());
    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessFilterDto<?>> flowNodeFilter = ProcessFilterBuilder.filter()
      .runningFlowNodesOnly().filterLevel(VIEW).add()
      .completedOrCanceledFlowNodesOnly().filterLevel(VIEW).add()
      .buildList();
    final ReportResultResponseDto<List<MapResultEntryDto>> result = evaluateReport(processDefinition, flowNodeFilter);

    // then
    assertThat(result.getInstanceCount()).isZero();
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(3L);
    // but no data is contained as no flow node can be running AND completed/canceled
    assertThat(result.getFirstMeasureData()).isEmpty();
  }

  @Test
  public void duplicateFlowNodeStatusFilterWorksInSameWayAsSingle() {
    // given
    ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksProcessDefinition();
    ProcessInstanceEngineDto firstTaskCanceledInstance =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.cancelActivityInstance(firstTaskCanceledInstance.getId(), USER_TASK_1);
    ProcessInstanceEngineDto secondTaskCanceledInstance =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(secondTaskCanceledInstance.getId());
    engineIntegrationExtension.cancelActivityInstance(secondTaskCanceledInstance.getId(), USER_TASK_2);
    engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessFilterDto<?>> canceledFlowNodes = ProcessFilterBuilder.filter()
      .canceledFlowNodesOnly().filterLevel(VIEW).add()
      .canceledFlowNodesOnly().filterLevel(VIEW).add()
      .buildList();
    final ReportResultResponseDto<List<MapResultEntryDto>> result = evaluateReport(
      processDefinition,
      canceledFlowNodes
    );

    // then
    assertThat(result.getInstanceCount()).isEqualTo(2L);
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(3L);
    assertThat(result.getFirstMeasureData()).hasSize(2);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), USER_TASK_1)).isPresent()
      .get()
      .extracting(MapResultEntryDto::getValue)
      .isEqualTo(1.);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), USER_TASK_2)).isPresent()
      .get()
      .extracting(MapResultEntryDto::getValue)
      .isEqualTo(1.);
  }

  @Test
  public void flowNodeStatusFilterWorksInCombinationWithOtherFilter() {
    // given
    ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksProcessDefinition();
    ProcessInstanceEngineDto firstTaskCanceledInstance =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.cancelActivityInstance(firstTaskCanceledInstance.getId(), USER_TASK_1);
    engineDatabaseExtension.changeFlowNodeTotalDuration(firstTaskCanceledInstance.getId(), USER_TASK_1, 10000L);
    ProcessInstanceEngineDto secondTaskCanceledInstance =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(secondTaskCanceledInstance.getId());
    engineIntegrationExtension.cancelActivityInstance(secondTaskCanceledInstance.getId(), USER_TASK_2);
    engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessFilterDto<?>> canceledFlowNodes = ProcessFilterBuilder.filter()
      .canceledFlowNodesOnly().filterLevel(VIEW).add()
      .flowNodeDuration()
      .flowNode(
        USER_TASK_1,
        DurationFilterDataDto.builder()
          .unit(DurationUnit.MILLIS)
          .value(10000L)
          .operator(GREATER_THAN_EQUALS)
          .build()
      )
      .filterLevel(FilterApplicationLevel.VIEW)
      .add()
      .buildList();
    final ReportResultResponseDto<List<MapResultEntryDto>> result = evaluateReport(
      processDefinition,
      canceledFlowNodes
    );

    // then
    assertThat(result.getInstanceCount()).isEqualTo(1L);
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(3L);
    assertThat(result.getFirstMeasureData()).hasSize(1);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), USER_TASK_1)).isPresent()
      .get()
      .extracting(MapResultEntryDto::getValue)
      .isEqualTo(1.);
  }

  private ReportResultResponseDto<List<MapResultEntryDto>> evaluateReport(final ProcessDefinitionEngineDto definitionEngineDto,
                                                                          List<ProcessFilterDto<?>> filter) {
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(definitionEngineDto.getKey())
      .setProcessDefinitionVersion(definitionEngineDto.getVersionAsString())
      .setReportDataType(ProcessReportDataType.FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE)
      .setFilter(filter)
      .build();
    return reportClient.evaluateMapReport(reportData).getResult();
  }

}
