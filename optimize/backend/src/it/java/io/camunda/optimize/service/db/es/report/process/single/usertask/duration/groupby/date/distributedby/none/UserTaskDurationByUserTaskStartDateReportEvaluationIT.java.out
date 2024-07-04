/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
// TODO recreate C8 IT equivalent of this with #13337
// package
// io.camunda.optimize.service.db.es.report.process.single.usertask.duration.groupby.date.distributedby.none;
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
// import io.camunda.optimize.service.security.util.LocalDateUtil;
// import io.camunda.optimize.service.util.ProcessReportDataType;
// import java.time.OffsetDateTime;
// import java.time.ZonedDateTime;
// import java.time.temporal.ChronoUnit;
// import java.util.List;
// import java.util.stream.Stream;
// import lombok.Data;
// import org.junit.jupiter.params.ParameterizedTest;
// import org.junit.jupiter.params.provider.MethodSource;
//
// public abstract class UserTaskDurationByUserTaskStartDateReportEvaluationIT
//     extends UserTaskDurationByUserTaskDateReportEvaluationIT {
//
//   @Data
//   static class FlowNodeStatusTestValues {
//     List<ProcessFilterDto<?>> processFilter;
//     Double expectedIdleDurationValue;
//     Double expectedWorkDurationValue;
//     Double expectedTotalDurationValue;
//     Long expectedInstanceCount;
//   }
//
//   protected static Stream<FlowNodeStatusTestValues> getFlowNodeStatusExpectedValues() {
//     FlowNodeStatusTestValues runningStateValues = new FlowNodeStatusTestValues();
//     runningStateValues.processFilter =
//         ProcessFilterBuilder.filter().runningFlowNodesOnly().add().buildList();
//     runningStateValues.expectedIdleDurationValue = 200.;
//     runningStateValues.expectedWorkDurationValue = 500.;
//     runningStateValues.expectedTotalDurationValue = 700.;
//     runningStateValues.expectedInstanceCount = 1L;
//
//     FlowNodeStatusTestValues completedStateValues = new FlowNodeStatusTestValues();
//     completedStateValues.processFilter =
//         ProcessFilterBuilder.filter().completedOrCanceledFlowNodesOnly().add().buildList();
//     completedStateValues.expectedIdleDurationValue = 100.;
//     completedStateValues.expectedWorkDurationValue = 100.;
//     completedStateValues.expectedTotalDurationValue = 100.;
//     completedStateValues.expectedInstanceCount = 1L;
//
//     FlowNodeStatusTestValues completedOrCanceled = new FlowNodeStatusTestValues();
//     completedOrCanceled.processFilter =
//         ProcessFilterBuilder.filter().completedOrCanceledFlowNodesOnly().add().buildList();
//     completedOrCanceled.expectedIdleDurationValue = 100.;
//     completedOrCanceled.expectedWorkDurationValue = 100.;
//     completedOrCanceled.expectedTotalDurationValue = 100.;
//     completedOrCanceled.expectedInstanceCount = 1L;
//
//     return Stream.of(runningStateValues, completedStateValues, completedOrCanceled);
//   }
//
//   @ParameterizedTest
//   @MethodSource("getFlowNodeStatusExpectedValues")
//   public void evaluateReportWithFlowNodeStatusFilter(
//       FlowNodeStatusTestValues flowNodeStatusTestValues) {
//     // given
//     OffsetDateTime now = OffsetDateTime.now();
//     LocalDateUtil.setCurrentTime(now);
//     final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();
//     final ProcessInstanceEngineDto processInstanceDto1 =
//         engineIntegrationExtension.startProcessInstance(processDefinition.getId());
//     engineIntegrationExtension.finishAllRunningUserTasks(processInstanceDto1.getId());
//     engineIntegrationExtension.finishAllRunningUserTasks(processInstanceDto1.getId());
//     changeDuration(processInstanceDto1, 100.);
//
//     final ProcessInstanceEngineDto processInstanceDto2 =
//         engineIntegrationExtension.startProcessInstance(processDefinition.getId());
//     engineIntegrationExtension.claimAllRunningUserTasks(processInstanceDto2.getId());
//
//     changeUserTaskStartDate(processInstanceDto2, now, USER_TASK_1, 700.);
//     changeUserTaskClaimDate(processInstanceDto2, now, USER_TASK_1, 500.);
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
//         .isEqualTo(getCorrectTestExecutionValue(flowNodeStatusTestValues));
//   }
//
//   protected abstract Double getCorrectTestExecutionValue(
//       final FlowNodeStatusTestValues flowNodeStatusTestValues);
//
//   @Override
//   protected ProcessGroupByType getGroupByType() {
//     return ProcessGroupByType.START_DATE;
//   }
//
//   @Override
//   protected ProcessReportDataType getReportDataType() {
//     return ProcessReportDataType.USER_TASK_DUR_GROUP_BY_USER_TASK_START_DATE;
//   }
// }
