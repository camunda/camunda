/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
// TODO recreate C8 IT equivalent of this with #13337
// package
// io.camunda.optimize.service.db.es.report.process.single.usertask.duration.groupby.date.distributedby.assignee;
//
// import static io.camunda.optimize.rest.RestTestConstants.DEFAULT_PASSWORD;
// import static io.camunda.optimize.rest.RestTestConstants.DEFAULT_USERNAME;
// import static
// io.camunda.optimize.service.db.es.report.command.modules.distributed_by.process.identity.ProcessDistributedByIdentity.DISTRIBUTE_BY_IDENTITY_MISSING_KEY;
// import static io.camunda.optimize.test.it.extension.EngineIntegrationExtension.DEFAULT_FULLNAME;
// import static org.assertj.core.api.Assertions.assertThat;
//
// import io.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
// import io.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
// import io.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType;
// import io.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
// import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
// import
// io.camunda.optimize.dto.optimize.query.report.single.process.filter.CanceledFlowNodesOnlyFilterDto;
// import io.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
// import
// io.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
// import io.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;
// import io.camunda.optimize.dto.optimize.query.report.single.result.hyper.HyperMapResultEntryDto;
// import io.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
// import io.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
// import io.camunda.optimize.exception.OptimizeIntegrationTestException;
// import io.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
// import io.camunda.optimize.service.db.es.report.util.HyperMapAsserter;
// import io.camunda.optimize.service.security.util.LocalDateUtil;
// import io.camunda.optimize.service.util.ProcessReportDataType;
// import java.sql.SQLException;
// import java.time.OffsetDateTime;
// import java.time.temporal.ChronoUnit;
// import java.util.List;
// import java.util.stream.Collectors;
// import java.util.stream.Stream;
// import lombok.AllArgsConstructor;
// import lombok.Data;
// import org.junit.jupiter.api.Test;
// import org.junit.jupiter.params.ParameterizedTest;
// import org.junit.jupiter.params.provider.Arguments;
// import org.junit.jupiter.params.provider.MethodSource;
//
// public abstract class UserTaskDurationByUserTaskStartDateByAssigneeReportEvaluationIT
//     extends UserTaskDurationByUserTaskDateByAssigneeReportEvaluationIT {
//
//   @Test
//   public void reportEvaluationForOneProcessInstanceWithUnassignedTasks() {
//     // given
//     ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();
//     engineIntegrationExtension.startProcessInstance(processDefinition.getId());
//     engineIntegrationExtension.finishAllRunningUserTasks(DEFAULT_USERNAME, DEFAULT_PASSWORD);
//
//     importAllEngineEntitiesFromScratch();
//
//     // when
//     final ProcessReportDataDto reportData = createGroupedByDayReport(processDefinition);
//     final ReportResultResponseDto<List<HyperMapResultEntryDto>> result =
//         reportClient.evaluateHyperMapReport(reportData).getResult();
//
//     // then
//     // @formatter:off
//     final List<String> collect =
//         result.getFirstMeasureData().stream()
//             .flatMap(entry -> entry.getValue().stream())
//             .map(MapResultEntryDto::getKey)
//             .collect(Collectors.toList());
//     assertThat(collect).contains(DISTRIBUTE_BY_IDENTITY_MISSING_KEY);
//     // @formatter:on
//   }
//
//   @ParameterizedTest
//   @MethodSource("getFlowNodeStatusExpectedValues")
//   public void evaluateReportWithFlowNodeStatusFilter(
//       final List<ProcessFilterDto<?>> processFilter,
//       final FlowNodeStatusTestValues flowNodeStatusValues) {
//     // given
//     OffsetDateTime now = OffsetDateTime.now();
//     LocalDateUtil.setCurrentTime(now);
//     final ProcessDefinitionEngineDto processDefinition = deployOneUserTaskDefinition();
//     final ProcessInstanceEngineDto processInstance1 =
//         engineIntegrationExtension.startProcessInstance(processDefinition.getId());
//     engineIntegrationExtension.finishAllRunningUserTasks(DEFAULT_USERNAME, DEFAULT_PASSWORD);
//     changeUserTaskDate(processInstance1, USER_TASK_1, now);
//     changeDuration(processInstance1, USER_TASK_1, 100.);
//
//     final ProcessInstanceEngineDto processInstance2 =
//         engineIntegrationExtension.startProcessInstance(processDefinition.getId());
//     engineIntegrationExtension.claimAllRunningUserTasks(
//         DEFAULT_USERNAME, DEFAULT_PASSWORD, processInstance2.getId());
//     if (isSingleFilterOfType(processFilter, CanceledFlowNodesOnlyFilterDto.class)) {
//       engineIntegrationExtension.cancelActivityInstance(processInstance2.getId(), USER_TASK_1);
//       changeDuration(processInstance2, USER_TASK_1, 100.);
//     } else {
//       changeUserTaskStartDate(processInstance2, now, USER_TASK_1, 700.);
//       changeUserTaskClaimDate(processInstance2, now, USER_TASK_1, 500.);
//     }
//
//     importAllEngineEntitiesFromScratch();
//
//     // when
//     final ProcessReportDataDto reportData =
//         createReportData(processDefinition, AggregateByDateUnit.DAY);
//     reportData.setFilter(processFilter);
//     final ReportResultResponseDto<List<HyperMapResultEntryDto>> result =
//         reportClient.evaluateHyperMapReport(reportData).getResult();
//
//     // then
//     // @formatter:off
//     HyperMapAsserter.asserter()
//         .processInstanceCount(flowNodeStatusValues.expectedInstanceCount)
//         .processInstanceCountWithoutFilters(2L)
//         .measure(ViewProperty.DURATION, AggregationType.AVERAGE, getUserTaskDurationTime())
//         .groupByContains(groupedByDayDateAsString(OffsetDateTime.now()))
//         .distributedByContains(
//             DEFAULT_USERNAME, getCorrectTestExecutionValue(flowNodeStatusValues),
// DEFAULT_FULLNAME)
//         .doAssert(result);
//     // @formatter:on
//   }
//
//   @Data
//   @AllArgsConstructor
//   static class FlowNodeStatusTestValues {
//     Double expectedIdleDurationValue;
//     Double expectedWorkDurationValue;
//     Double expectedTotalDurationValue;
//     Long expectedInstanceCount;
//   }
//
//   protected static Stream<Arguments> getFlowNodeStatusExpectedValues() {
//     return Stream.of(
//         Arguments.of(
//             ProcessFilterBuilder.filter().runningFlowNodesOnly().add().buildList(),
//             new FlowNodeStatusTestValues(200., 500., 700., 1L)),
//         Arguments.of(
//             ProcessFilterBuilder.filter().completedFlowNodesOnly().add().buildList(),
//             new FlowNodeStatusTestValues(100., 100., 100., 1L)),
//         Arguments.of(
//             ProcessFilterBuilder.filter().completedOrCanceledFlowNodesOnly().add().buildList(),
//             new FlowNodeStatusTestValues(100., 100., 100., 1L)),
//         Arguments.of(
//             ProcessFilterBuilder.filter().canceledFlowNodesOnly().add().buildList(),
//             new FlowNodeStatusTestValues(100., 100., 100., 1L)));
//   }
//
//   protected void changeUserTaskClaimDate(
//       final ProcessInstanceEngineDto processInstanceDto,
//       final OffsetDateTime now,
//       final String userTaskKey,
//       final long offsetDurationInMs) {
//
//     engineIntegrationExtension
//         .getHistoricTaskInstances(processInstanceDto.getId(), userTaskKey)
//         .forEach(
//             historicUserTaskInstanceDto -> {
//               try {
//                 engineDatabaseExtension.changeUserTaskAssigneeClaimOperationTimestamp(
//                     historicUserTaskInstanceDto.getId(),
//                     now.minus(offsetDurationInMs, ChronoUnit.MILLIS));
//               } catch (SQLException e) {
//                 throw new OptimizeIntegrationTestException(e);
//               }
//             });
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
//     return ProcessReportDataType.USER_TASK_DUR_GROUP_BY_USER_TASK_START_DATE_BY_ASSIGNEE;
//   }
// }
