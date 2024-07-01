/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
// TODO recreate C8 IT equivalent of this with #13337
// package
// io.camunda.optimize.service.db.es.report.process.single.usertask.duration.groupby.usertask.distributedby.candidategroup;
//
// import static
// io.camunda.optimize.service.util.ProcessReportDataType.USER_TASK_DUR_GROUP_BY_USER_TASK_BY_CANDIDATE_GROUP;
// import static org.junit.jupiter.api.Assertions.fail;
//
// import io.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
// import io.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType;
// import io.camunda.optimize.dto.optimize.query.report.single.configuration.UserTaskDurationTime;
// import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
// import
// io.camunda.optimize.dto.optimize.query.report.single.process.filter.CanceledFlowNodesOnlyFilterDto;
// import
// io.camunda.optimize.dto.optimize.query.report.single.process.filter.CompletedOrCanceledFlowNodesOnlyFilterDto;
// import io.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
// import
// io.camunda.optimize.dto.optimize.query.report.single.process.filter.RunningFlowNodesOnlyFilterDto;
// import io.camunda.optimize.dto.optimize.query.report.single.result.hyper.HyperMapResultEntryDto;
// import io.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
// import io.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
// import io.camunda.optimize.service.db.es.report.util.HyperMapAsserter;
// import io.camunda.optimize.service.util.TemplatedProcessReportDataBuilder;
// import java.util.List;
//
// public class UserTaskIdleDurationByUserTaskByCandidateGroupReportEvaluationIT
//     extends AbstractUserTaskDurationByUserTaskByCandidateGroupReportEvaluationIT {
//
//   @Override
//   protected UserTaskDurationTime getUserTaskDurationTime() {
//     return UserTaskDurationTime.IDLE;
//   }
//
//   @Override
//   protected void changeDuration(
//       final ProcessInstanceEngineDto processInstanceDto, final Double durationInMs) {
//     changeUserTaskIdleDuration(processInstanceDto, durationInMs);
//   }
//
//   @Override
//   protected void changeDuration(
//       final ProcessInstanceEngineDto processInstanceDto,
//       final String userTaskKey,
//       final Double durationInMs) {
//     changeUserTaskIdleDuration(processInstanceDto, userTaskKey, durationInMs);
//   }
//
//   @Override
//   protected ProcessReportDataDto createReport(
//       final String processDefinitionKey, final List<String> versions) {
//     return TemplatedProcessReportDataBuilder.createReportData()
//         .setProcessDefinitionKey(processDefinitionKey)
//         .setProcessDefinitionVersions(versions)
//         .setUserTaskDurationTime(UserTaskDurationTime.IDLE)
//         .setReportDataType(USER_TASK_DUR_GROUP_BY_USER_TASK_BY_CANDIDATE_GROUP)
//         .build();
//   }
//
//   @Override
//   protected void assertEvaluateReportWithFlowNodeStatusFilter(
//       final ReportResultResponseDto<List<HyperMapResultEntryDto>> result,
//       final List<ProcessFilterDto<?>> processFilter,
//       final long expectedInstanceCount) {
//     if (isSingleFilterOfType(processFilter, RunningFlowNodesOnlyFilterDto.class)) {
//       // @formatter:off
//       HyperMapAsserter.asserter()
//           .processInstanceCount(expectedInstanceCount)
//           .processInstanceCountWithoutFilters(2L)
//           .measure(ViewProperty.DURATION, AggregationType.AVERAGE, getUserTaskDurationTime())
//           .groupByContains(USER_TASK_1)
//           .distributedByContains(FIRST_CANDIDATE_GROUP_ID, 200., FIRST_CANDIDATE_GROUP_NAME)
//           .groupByContains(USER_TASK_2)
//           .distributedByContains(FIRST_CANDIDATE_GROUP_ID, 200., FIRST_CANDIDATE_GROUP_NAME)
//           .doAssert(result);
//       // @formatter:on
//     } else if (isSingleFilterOfType(
//         processFilter, CompletedOrCanceledFlowNodesOnlyFilterDto.class)) {
//       // @formatter:off
//       HyperMapAsserter.asserter()
//           .processInstanceCount(expectedInstanceCount)
//           .processInstanceCountWithoutFilters(2L)
//           .measure(ViewProperty.DURATION, AggregationType.AVERAGE, getUserTaskDurationTime())
//           .groupByContains(USER_TASK_1)
//           .distributedByContains(FIRST_CANDIDATE_GROUP_ID, 100., FIRST_CANDIDATE_GROUP_NAME)
//           .doAssert(result);
//       // @formatter:on
//     } else if (isSingleFilterOfType(processFilter, CanceledFlowNodesOnlyFilterDto.class)) {
//       // @formatter:off
//       HyperMapAsserter.asserter()
//           .processInstanceCount(expectedInstanceCount)
//           .processInstanceCountWithoutFilters(2L)
//           .measure(ViewProperty.DURATION, AggregationType.AVERAGE, getUserTaskDurationTime())
//           .groupByContains(USER_TASK_1)
//           .distributedByContains(FIRST_CANDIDATE_GROUP_ID, 700., FIRST_CANDIDATE_GROUP_NAME)
//           .groupByContains(USER_TASK_2)
//           .distributedByContains(FIRST_CANDIDATE_GROUP_ID, 700., FIRST_CANDIDATE_GROUP_NAME)
//           .doAssert(result);
//       // @formatter:on
//     } else {
//       fail("No assertions for execution state: " + processFilter);
//     }
//   }
// }
