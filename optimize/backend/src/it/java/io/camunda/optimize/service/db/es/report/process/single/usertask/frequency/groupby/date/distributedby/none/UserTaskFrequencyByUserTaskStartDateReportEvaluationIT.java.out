/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
// TODO recreate C8 IT equivalent of this with #13337
// package
// io.camunda.optimize.service.db.es.report.process.single.usertask.frequency.groupby.date.distributedby.none;
//
// import static io.camunda.optimize.test.util.DateModificationHelper.truncateToStartOfUnit;
// import static org.assertj.core.api.Assertions.assertThat;
//
// import io.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
// import io.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
// import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
// import io.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
// import
// io.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
// import io.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;
// import io.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
// import io.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
// import io.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
// import io.camunda.optimize.service.db.es.report.util.MapResultUtil;
// import io.camunda.optimize.service.util.ProcessReportDataType;
// import java.time.OffsetDateTime;
// import java.time.ZonedDateTime;
// import java.time.temporal.ChronoUnit;
// import java.util.List;
// import java.util.Map;
// import java.util.stream.Stream;
// import lombok.AllArgsConstructor;
// import lombok.Data;
// import org.junit.jupiter.api.Test;
// import org.junit.jupiter.params.ParameterizedTest;
// import org.junit.jupiter.params.provider.MethodSource;
//
// public class UserTaskFrequencyByUserTaskStartDateReportEvaluationIT
//     extends UserTaskFrequencyByUserTaskDateReportEvaluationIT {
//
//   @ParameterizedTest
//   @MethodSource("getFlowNodeStatusExpectedValues")
//   public void evaluateReportWithFlowNodeStatusFilter(
//       FlowNodeStatusTestValues flowNodeStatusTestValues) {
//     // given
//     final ProcessDefinitionEngineDto processDefinition = deployTwoModelElementDefinition();
//     final ProcessInstanceEngineDto processInstanceDto =
//         startAndCompleteInstance(processDefinition.getId());
//
//     engineIntegrationExtension.startProcessInstance(processDefinition.getId());
//     engineIntegrationExtension.finishAllRunningUserTasks(processInstanceDto.getId());
//
//     importAllEngineEntitiesFromScratch();
//
//     // when
//     final ProcessReportDataDto reportData =
//         createReportData(processDefinition, AggregateByDateUnit.DAY);
//     reportData.setFilter(flowNodeStatusTestValues.processFilter);
//     final ReportResultResponseDto<List<MapResultEntryDto>> result =
//         reportClient.evaluateMapReport(reportData).getResult();
//
//     // then
//
// assertThat(result.getInstanceCount()).isEqualTo(flowNodeStatusTestValues.expectedInstanceCount);
//     assertThat(result.getFirstMeasureData()).isNotNull();
//     assertThat(result.getFirstMeasureData()).hasSize(1);
//     ZonedDateTime startOfToday = truncateToStartOfUnit(OffsetDateTime.now(), ChronoUnit.DAYS);
//     assertThat(
//             MapResultUtil.getEntryForKey(
//                 result.getFirstMeasureData(), localDateTimeToString(startOfToday)))
//         .get()
//         .extracting(MapResultEntryDto::getValue)
//         .isEqualTo(flowNodeStatusTestValues.resultValue);
//   }
//
//   @Test
//   public void evaluateReportWithFlowNodeStatusFilterCanceled() {
//     // given
//     final ProcessDefinitionEngineDto processDefinition = deployTwoModelElementDefinition();
//     startAndCompleteInstance(processDefinition.getId());
//
//     final ProcessInstanceEngineDto secondInstance =
//         engineIntegrationExtension.startProcessInstance(processDefinition.getId());
//     engineIntegrationExtension.cancelActivityInstance(secondInstance.getId(), USER_TASK_1);
//
//     importAllEngineEntitiesFromScratch();
//
//     // when
//     final ProcessReportDataDto reportData =
//         createReportData(processDefinition, AggregateByDateUnit.DAY);
//
// reportData.setFilter(ProcessFilterBuilder.filter().canceledFlowNodesOnly().add().buildList());
//     final ReportResultResponseDto<List<MapResultEntryDto>> result =
//         reportClient.evaluateMapReport(reportData).getResult();
//
//     // then
//     assertThat(result.getInstanceCount()).isEqualTo(1L);
//     assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(2L);
//     assertThat(result.getFirstMeasureData()).isNotNull();
//     assertThat(result.getFirstMeasureData()).hasSize(1);
//     ZonedDateTime startOfToday = truncateToStartOfUnit(OffsetDateTime.now(), ChronoUnit.DAYS);
//     assertThat(
//             MapResultUtil.getEntryForKey(
//                 result.getFirstMeasureData(), localDateTimeToString(startOfToday)))
//         .get()
//         .extracting(MapResultEntryDto::getValue)
//         .isEqualTo(1.);
//   }
//
//   @Data
//   @AllArgsConstructor
//   static class FlowNodeStatusTestValues {
//     List<ProcessFilterDto<?>> processFilter;
//     Double resultValue;
//     Long expectedInstanceCount;
//   }
//
//   protected static Stream<FlowNodeStatusTestValues> getFlowNodeStatusExpectedValues() {
//     return Stream.of(
//         new FlowNodeStatusTestValues(
//             ProcessFilterBuilder.filter().runningFlowNodesOnly().add().buildList(), 1., 1L),
//         new FlowNodeStatusTestValues(
//             ProcessFilterBuilder.filter().completedFlowNodesOnly().add().buildList(), 2., 1L),
//         new FlowNodeStatusTestValues(
//             ProcessFilterBuilder.filter().completedOrCanceledFlowNodesOnly().add().buildList(),
//             2.,
//             1L));
//   }
//
//   @Override
//   protected ProcessReportDataType getReportDataType() {
//     return ProcessReportDataType.USER_TASK_FREQ_GROUP_BY_USER_TASK_START_DATE;
//   }
//
//   @Override
//   protected void changeModelElementDates(final Map<String, OffsetDateTime> updates) {
//     engineDatabaseExtension.changeAllFlowNodeStartDates(updates);
//   }
//
//   @Override
//   protected void changeModelElementDate(
//       final ProcessInstanceEngineDto processInstance,
//       final String modelElementId,
//       final OffsetDateTime dateToChangeTo) {
//     engineDatabaseExtension.changeFlowNodeStartDate(
//         processInstance.getId(), modelElementId, dateToChangeTo);
//   }
//
//   @Override
//   protected ProcessGroupByType getGroupByType() {
//     return ProcessGroupByType.START_DATE;
//   }
// }
