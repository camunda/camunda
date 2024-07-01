/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
// TODO recreate C8 IT equivalent of this with #13337
// package io.camunda.optimize.service.db.es.filter.process;
//
// import static
// io.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.ComparisonOperator.GREATER_THAN_EQUALS;
// import static
// io.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel.VIEW;
// import static io.camunda.optimize.util.BpmnModels.END_EVENT;
// import static io.camunda.optimize.util.BpmnModels.START_EVENT;
// import static io.camunda.optimize.util.BpmnModels.USER_TASK_1;
// import static io.camunda.optimize.util.BpmnModels.USER_TASK_2;
// import static org.assertj.core.api.Assertions.assertThat;
//
// import io.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
// import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DurationUnit;
// import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
// import
// io.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
// import io.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
// import
// io.camunda.optimize.dto.optimize.query.report.single.process.filter.data.DurationFilterDataDto;
// import
// io.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
// import io.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
// import io.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
// import io.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
// import io.camunda.optimize.service.db.es.report.util.MapResultUtil;
// import io.camunda.optimize.service.util.ProcessReportDataType;
// import io.camunda.optimize.service.util.TemplatedProcessReportDataBuilder;
// import java.util.List;
// import org.junit.jupiter.api.Test;
//
// public class FlowNodeStatusQueryFiltersIT extends AbstractFilterIT {
//
//   @Test
//   public void canceledFlowNodeStatusFilterWorks() {
//     // given
//     ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksProcessDefinition();
//     ProcessInstanceEngineDto firstTaskCanceledInstance =
//         engineIntegrationExtension.startProcessInstance(processDefinition.getId());
//     engineIntegrationExtension.cancelActivityInstance(
//         firstTaskCanceledInstance.getId(), USER_TASK_1);
//     ProcessInstanceEngineDto secondTaskCanceledInstance =
//         engineIntegrationExtension.startProcessInstance(processDefinition.getId());
//     engineIntegrationExtension.finishAllRunningUserTasks(secondTaskCanceledInstance.getId());
//     engineIntegrationExtension.cancelActivityInstance(
//         secondTaskCanceledInstance.getId(), USER_TASK_2);
//     engineIntegrationExtension.startProcessInstance(processDefinition.getId());
//     importAllEngineEntitiesFromScratch();
//
//     // when
//     List<ProcessFilterDto<?>> canceledFlowNodes =
//
// ProcessFilterBuilder.filter().canceledFlowNodesOnly().filterLevel(VIEW).add().buildList();
//     final ReportResultResponseDto<List<MapResultEntryDto>> result =
//         evaluateReport(processDefinition, canceledFlowNodes);
//
//     // then
//     assertThat(result.getInstanceCount()).isEqualTo(2L);
//     assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(3L);
//     assertThat(result.getFirstMeasureData()).hasSize(2);
//     assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), USER_TASK_1))
//         .isPresent()
//         .get()
//         .extracting(MapResultEntryDto::getValue)
//         .isEqualTo(1.);
//     assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), USER_TASK_2))
//         .isPresent()
//         .get()
//         .extracting(MapResultEntryDto::getValue)
//         .isEqualTo(1.);
//   }
//
//   @Test
//   public void completedOrCanceledFlowNodeStatusFilterWorks() {
//     // given
//     ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksProcessDefinition();
//     ProcessInstanceEngineDto firstTaskCanceledInstance =
//         engineIntegrationExtension.startProcessInstance(processDefinition.getId());
//     engineIntegrationExtension.cancelActivityInstance(
//         firstTaskCanceledInstance.getId(), USER_TASK_1);
//     ProcessInstanceEngineDto secondTaskCanceledInstance =
//         engineIntegrationExtension.startProcessInstance(processDefinition.getId());
//     engineIntegrationExtension.finishAllRunningUserTasks(secondTaskCanceledInstance.getId());
//     engineIntegrationExtension.cancelActivityInstance(
//         secondTaskCanceledInstance.getId(), USER_TASK_2);
//     engineIntegrationExtension.startProcessInstance(processDefinition.getId());
//     importAllEngineEntitiesFromScratch();
//
//     // when
//     List<ProcessFilterDto<?>> completedOrCanceledFlowNodes =
//         ProcessFilterBuilder.filter()
//             .completedOrCanceledFlowNodesOnly()
//             .filterLevel(VIEW)
//             .add()
//             .buildList();
//     final ReportResultResponseDto<List<MapResultEntryDto>> result =
//         evaluateReport(processDefinition, completedOrCanceledFlowNodes);
//
//     // then
//     assertThat(result.getInstanceCount()).isEqualTo(3L);
//     assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(3L);
//     assertThat(result.getFirstMeasureData()).hasSize(3);
//     assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), START_EVENT))
//         .isPresent()
//         .get()
//         .extracting(MapResultEntryDto::getValue)
//         .isEqualTo(3.);
//     assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), USER_TASK_1))
//         .isPresent()
//         .get()
//         .extracting(MapResultEntryDto::getValue)
//         .isEqualTo(2.);
//     assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), USER_TASK_2))
//         .isPresent()
//         .get()
//         .extracting(MapResultEntryDto::getValue)
//         .isEqualTo(1.);
//   }
//
//   @Test
//   public void completedFlowNodeStatusFilterWorks() {
//     // given
//     ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksProcessDefinition();
//     ProcessInstanceEngineDto runningInstance =
//         engineIntegrationExtension.startProcessInstance(processDefinition.getId());
//     engineIntegrationExtension.finishAllRunningUserTasks(runningInstance.getId());
//     engineIntegrationExtension.startProcessInstance(processDefinition.getId());
//     ProcessInstanceEngineDto finishedInstance =
//         engineIntegrationExtension.startProcessInstance(processDefinition.getId());
//     engineIntegrationExtension.finishAllRunningUserTasks(finishedInstance.getId());
//     engineIntegrationExtension.finishAllRunningUserTasks(finishedInstance.getId());
//     importAllEngineEntitiesFromScratch();
//
//     // when
//     List<ProcessFilterDto<?>> completedFlowNodes =
//
// ProcessFilterBuilder.filter().completedFlowNodesOnly().filterLevel(VIEW).add().buildList();
//     final ReportResultResponseDto<List<MapResultEntryDto>> result =
//         evaluateReport(processDefinition, completedFlowNodes);
//
//     // then
//     assertThat(result.getInstanceCount()).isEqualTo(3L);
//     assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(3L);
//     assertThat(result.getFirstMeasureData()).hasSize(4);
//     assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), START_EVENT))
//         .isPresent()
//         .get()
//         .extracting(MapResultEntryDto::getValue)
//         .isEqualTo(3.);
//     assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), USER_TASK_1))
//         .isPresent()
//         .get()
//         .extracting(MapResultEntryDto::getValue)
//         .isEqualTo(2.);
//     assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), USER_TASK_2))
//         .isPresent()
//         .get()
//         .extracting(MapResultEntryDto::getValue)
//         .isEqualTo(1.);
//     assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), END_EVENT))
//         .isPresent()
//         .get()
//         .extracting(MapResultEntryDto::getValue)
//         .isEqualTo(1.);
//   }
//
//   @Test
//   public void runningFlowNodeStatusFilterWorks() {
//     // given
//     ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksProcessDefinition();
//     ProcessInstanceEngineDto firstInstance =
//         engineIntegrationExtension.startProcessInstance(processDefinition.getId());
//     engineIntegrationExtension.finishAllRunningUserTasks(firstInstance.getId());
//     engineIntegrationExtension.startProcessInstance(processDefinition.getId());
//     ProcessInstanceEngineDto finishedInstance =
//         engineIntegrationExtension.startProcessInstance(processDefinition.getId());
//     engineIntegrationExtension.finishAllRunningUserTasks(finishedInstance.getId());
//     engineIntegrationExtension.finishAllRunningUserTasks(finishedInstance.getId());
//     importAllEngineEntitiesFromScratch();
//
//     // when
//     List<ProcessFilterDto<?>> runningFlowNodes =
//         ProcessFilterBuilder.filter().runningFlowNodesOnly().filterLevel(VIEW).add().buildList();
//     final ReportResultResponseDto<List<MapResultEntryDto>> result =
//         evaluateReport(processDefinition, runningFlowNodes);
//
//     // then
//     assertThat(result.getInstanceCount()).isEqualTo(2L);
//     assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(3L);
//     assertThat(result.getFirstMeasureData()).hasSize(2);
//     assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), USER_TASK_1))
//         .isPresent()
//         .get()
//         .extracting(MapResultEntryDto::getValue)
//         .isEqualTo(1.);
//     assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), USER_TASK_2))
//         .isPresent()
//         .get()
//         .extracting(MapResultEntryDto::getValue)
//         .isEqualTo(1.);
//   }
//
//   @Test
//   public void combinedFlowNodeStatusFiltersReturnNoData() {
//     // given
//     ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksProcessDefinition();
//     ProcessInstanceEngineDto firstInstance =
//         engineIntegrationExtension.startProcessInstance(processDefinition.getId());
//     engineIntegrationExtension.finishAllRunningUserTasks(firstInstance.getId());
//     engineIntegrationExtension.startProcessInstance(processDefinition.getId());
//     ProcessInstanceEngineDto finishedInstance =
//         engineIntegrationExtension.startProcessInstance(processDefinition.getId());
//     engineIntegrationExtension.finishAllRunningUserTasks(finishedInstance.getId());
//     engineIntegrationExtension.finishAllRunningUserTasks(finishedInstance.getId());
//     importAllEngineEntitiesFromScratch();
//
//     // when
//     List<ProcessFilterDto<?>> flowNodeFilter =
//         ProcessFilterBuilder.filter()
//             .runningFlowNodesOnly()
//             .filterLevel(VIEW)
//             .add()
//             .completedOrCanceledFlowNodesOnly()
//             .filterLevel(VIEW)
//             .add()
//             .buildList();
//     final ReportResultResponseDto<List<MapResultEntryDto>> result =
//         evaluateReport(processDefinition, flowNodeFilter);
//
//     // then
//     assertThat(result.getInstanceCount()).isZero();
//     assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(3L);
//     // but no data is contained as no flow node can be running AND completed/canceled
//     assertThat(result.getFirstMeasureData()).isEmpty();
//   }
//
//   @Test
//   public void duplicateFlowNodeStatusFilterWorksInSameWayAsSingle() {
//     // given
//     ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksProcessDefinition();
//     ProcessInstanceEngineDto firstTaskCanceledInstance =
//         engineIntegrationExtension.startProcessInstance(processDefinition.getId());
//     engineIntegrationExtension.cancelActivityInstance(
//         firstTaskCanceledInstance.getId(), USER_TASK_1);
//     ProcessInstanceEngineDto secondTaskCanceledInstance =
//         engineIntegrationExtension.startProcessInstance(processDefinition.getId());
//     engineIntegrationExtension.finishAllRunningUserTasks(secondTaskCanceledInstance.getId());
//     engineIntegrationExtension.cancelActivityInstance(
//         secondTaskCanceledInstance.getId(), USER_TASK_2);
//     engineIntegrationExtension.startProcessInstance(processDefinition.getId());
//     importAllEngineEntitiesFromScratch();
//
//     // when
//     List<ProcessFilterDto<?>> canceledFlowNodes =
//         ProcessFilterBuilder.filter()
//             .canceledFlowNodesOnly()
//             .filterLevel(VIEW)
//             .add()
//             .canceledFlowNodesOnly()
//             .filterLevel(VIEW)
//             .add()
//             .buildList();
//     final ReportResultResponseDto<List<MapResultEntryDto>> result =
//         evaluateReport(processDefinition, canceledFlowNodes);
//
//     // then
//     assertThat(result.getInstanceCount()).isEqualTo(2L);
//     assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(3L);
//     assertThat(result.getFirstMeasureData()).hasSize(2);
//     assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), USER_TASK_1))
//         .isPresent()
//         .get()
//         .extracting(MapResultEntryDto::getValue)
//         .isEqualTo(1.);
//     assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), USER_TASK_2))
//         .isPresent()
//         .get()
//         .extracting(MapResultEntryDto::getValue)
//         .isEqualTo(1.);
//   }
//
//   @Test
//   public void flowNodeStatusFilterWorksInCombinationWithOtherFilter() {
//     // given
//     ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksProcessDefinition();
//     ProcessInstanceEngineDto firstTaskCanceledInstance =
//         engineIntegrationExtension.startProcessInstance(processDefinition.getId());
//     engineIntegrationExtension.cancelActivityInstance(
//         firstTaskCanceledInstance.getId(), USER_TASK_1);
//     engineDatabaseExtension.changeFlowNodeTotalDuration(
//         firstTaskCanceledInstance.getId(), USER_TASK_1, 10000L);
//     ProcessInstanceEngineDto secondTaskCanceledInstance =
//         engineIntegrationExtension.startProcessInstance(processDefinition.getId());
//     engineIntegrationExtension.finishAllRunningUserTasks(secondTaskCanceledInstance.getId());
//     engineIntegrationExtension.cancelActivityInstance(
//         secondTaskCanceledInstance.getId(), USER_TASK_2);
//     engineIntegrationExtension.startProcessInstance(processDefinition.getId());
//     importAllEngineEntitiesFromScratch();
//
//     // when
//     List<ProcessFilterDto<?>> canceledFlowNodes =
//         ProcessFilterBuilder.filter()
//             .canceledFlowNodesOnly()
//             .filterLevel(VIEW)
//             .add()
//             .flowNodeDuration()
//             .flowNode(
//                 USER_TASK_1,
//                 DurationFilterDataDto.builder()
//                     .unit(DurationUnit.MILLIS)
//                     .value(10000L)
//                     .operator(GREATER_THAN_EQUALS)
//                     .build())
//             .filterLevel(FilterApplicationLevel.VIEW)
//             .add()
//             .buildList();
//     final ReportResultResponseDto<List<MapResultEntryDto>> result =
//         evaluateReport(processDefinition, canceledFlowNodes);
//
//     // then
//     assertThat(result.getInstanceCount()).isEqualTo(1L);
//     assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(3L);
//     assertThat(result.getFirstMeasureData()).hasSize(1);
//     assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), USER_TASK_1))
//         .isPresent()
//         .get()
//         .extracting(MapResultEntryDto::getValue)
//         .isEqualTo(1.);
//   }
//
//   private ReportResultResponseDto<List<MapResultEntryDto>> evaluateReport(
//       final ProcessDefinitionEngineDto definitionEngineDto, List<ProcessFilterDto<?>> filter) {
//     ProcessReportDataDto reportData =
//         TemplatedProcessReportDataBuilder.createReportData()
//             .setProcessDefinitionKey(definitionEngineDto.getKey())
//             .setProcessDefinitionVersion(definitionEngineDto.getVersionAsString())
//             .setReportDataType(ProcessReportDataType.FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE)
//             .setFilter(filter)
//             .build();
//     return reportClient.evaluateMapReport(reportData).getResult();
//   }
// }
