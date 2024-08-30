/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
// TODO recreate C8 IT equivalent of this with #13337
// package
// io.camunda.optimize.service.db.es.report.process.single.usertask.duration.groupby.date.distributedby.candidategroup;
//
// import static
// io.camunda.optimize.service.db.es.report.command.modules.distributed_by.process.identity.ProcessDistributedByIdentity.DISTRIBUTE_BY_IDENTITY_MISSING_KEY;
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
// import io.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
// import io.camunda.optimize.service.db.es.report.util.HyperMapAsserter;
// import io.camunda.optimize.service.security.util.LocalDateUtil;
// import io.camunda.optimize.service.util.ProcessReportDataType;
// import java.time.OffsetDateTime;
// import java.util.List;
// import java.util.stream.Collectors;
// import java.util.stream.Stream;
// import lombok.AllArgsConstructor;
// import lombok.Data;
// import lombok.NoArgsConstructor;
// import org.junit.jupiter.api.Test;
// import org.junit.jupiter.params.ParameterizedTest;
// import org.junit.jupiter.params.provider.Arguments;
// import org.junit.jupiter.params.provider.MethodSource;
//
// public abstract class UserTaskDurationByUserTaskStartDateByCandidateGroupReportEvaluationIT
//     extends UserTaskDurationByUserTaskDateByCandidateGroupReportEvaluationIT {
//
//   @Test
//   public void reportEvaluationForOneProcessInstanceWithUnassignedTasks() {
//     // given
//     ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();
//     engineIntegrationExtension.startProcessInstance(processDefinition.getId());
//     engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP_ID);
//     engineIntegrationExtension.finishAllRunningUserTasks();
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
//   public void evaluateReportWithFlowNodeStatusFilters(
//       final List<ProcessFilterDto<?>> processFilters,
//       final long expectedInstanceCount,
//       final FlowNodeStatusTestValues candidateGroup1Count,
//       final FlowNodeStatusTestValues candidateGroup2Count) {
//     // given
//     OffsetDateTime now = OffsetDateTime.now();
//     LocalDateUtil.setCurrentTime(now);
//
//     final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();
//     final ProcessInstanceEngineDto processInstance1 =
//         engineIntegrationExtension.startProcessInstance(processDefinition.getId());
//     // finish first running task, second now runs but unclaimed
//     engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP_ID);
//     engineIntegrationExtension.finishAllRunningUserTasks(processInstance1.getId());
//     changeDuration(processInstance1, USER_TASK_1, 100.);
//
// engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(SECOND_CANDIDATE_GROUP_ID);
//     engineIntegrationExtension.claimAllRunningUserTasks(processInstance1.getId());
//     if (isSingleFilterOfType(processFilters, CanceledFlowNodesOnlyFilterDto.class)) {
//       engineIntegrationExtension.cancelActivityInstance(processInstance1.getId(), USER_TASK_2);
//       changeDuration(processInstance1, USER_TASK_2, 100.);
//     } else {
//       changeUserTaskStartDate(processInstance1, now, USER_TASK_2, 700.);
//       changeUserTaskClaimDate(processInstance1, now, USER_TASK_2, 500.);
//     }
//
//     final ProcessInstanceEngineDto processInstance2 =
//         engineIntegrationExtension.startProcessInstance(processDefinition.getId());
//     // claim first running task
//     engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(
//         processInstance2.getId(), FIRST_CANDIDATE_GROUP_ID);
//     engineIntegrationExtension.claimAllRunningUserTasks(processInstance2.getId());
//     if (isSingleFilterOfType(processFilters, CanceledFlowNodesOnlyFilterDto.class)) {
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
//     reportData.setFilter(processFilters);
//     final ReportResultResponseDto<List<HyperMapResultEntryDto>> result =
//         reportClient.evaluateHyperMapReport(reportData).getResult();
//
//     // then
//     final HyperMapAsserter.GroupByAdder groupByAsserter =
//         HyperMapAsserter.asserter()
//             .processInstanceCount(expectedInstanceCount)
//             .processInstanceCountWithoutFilters(2L)
//             .measure(ViewProperty.DURATION, AggregationType.AVERAGE, getUserTaskDurationTime())
//             .groupByContains(groupedByDayDateAsString(OffsetDateTime.now()));
//     if (candidateGroup1Count != null) {
//       groupByAsserter.distributedByContains(
//           FIRST_CANDIDATE_GROUP_ID,
//           getCorrectTestExecutionValue(candidateGroup1Count),
//           FIRST_CANDIDATE_GROUP_NAME);
//     }
//     if (candidateGroup2Count != null) {
//       groupByAsserter.distributedByContains(
//           SECOND_CANDIDATE_GROUP_ID,
//           getCorrectTestExecutionValue(candidateGroup2Count),
//           SECOND_CANDIDATE_GROUP_NAME);
//     }
//     groupByAsserter.doAssert(result);
//   }
//
//   @Data
//   @AllArgsConstructor
//   @NoArgsConstructor
//   static class FlowNodeStatusTestValues {
//     Double expectedIdleDurationValue;
//     Double expectedWorkDurationValue;
//     Double expectedTotalDurationValue;
//   }
//
//   protected static Stream<Arguments> getFlowNodeStatusExpectedValues() {
//     return Stream.of(
//         Arguments.of(
//             ProcessFilterBuilder.filter().runningFlowNodesOnly().add().buildList(),
//             2L,
//             new FlowNodeStatusTestValues(200., 500., 700.),
//             new FlowNodeStatusTestValues(200., 500., 700.)),
//         Arguments.of(
//             ProcessFilterBuilder.filter().completedFlowNodesOnly().add().buildList(),
//             1L,
//             new FlowNodeStatusTestValues(100., 100., 100.),
//             null),
//         Arguments.of(
//             ProcessFilterBuilder.filter().completedOrCanceledFlowNodesOnly().add().buildList(),
//             1L,
//             new FlowNodeStatusTestValues(100., 100., 100.),
//             null),
//         Arguments.of(
//             ProcessFilterBuilder.filter().canceledFlowNodesOnly().add().buildList(),
//             2L,
//             new FlowNodeStatusTestValues(100., 100., 100.),
//             new FlowNodeStatusTestValues(100., 100., 100.)));
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
//     return ProcessReportDataType.USER_TASK_DUR_GROUP_BY_USER_TASK_START_DATE_BY_CANDIDATE_GROUP;
//   }
// }
