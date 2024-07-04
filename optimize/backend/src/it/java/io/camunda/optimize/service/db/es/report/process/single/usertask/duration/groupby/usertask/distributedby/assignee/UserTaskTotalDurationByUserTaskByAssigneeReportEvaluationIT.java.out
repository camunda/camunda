/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
// TODO recreate C8 IT equivalent of this with #13337
// package
// io.camunda.optimize.service.db.es.report.process.single.usertask.duration.groupby.usertask.distributedby.assignee;
//
// import static io.camunda.optimize.rest.RestTestConstants.DEFAULT_USERNAME;
// import static
// io.camunda.optimize.service.util.ProcessReportDataType.USER_TASK_DUR_GROUP_BY_USER_TASK_BY_ASSIGNEE;
// import static io.camunda.optimize.test.it.extension.EngineIntegrationExtension.DEFAULT_FULLNAME;
// import static org.junit.jupiter.api.Assertions.fail;
//
// import io.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
// import io.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType;
// import io.camunda.optimize.dto.optimize.query.report.single.configuration.UserTaskDurationTime;
// import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
// import
// io.camunda.optimize.dto.optimize.query.report.single.process.filter.CompletedFlowNodesOnlyFilterDto;
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
// public class UserTaskTotalDurationByUserTaskByAssigneeReportEvaluationIT
//     extends AbstractUserTaskDurationByUserTaskByAssigneeReportEvaluationIT {
//
//   @Override
//   protected UserTaskDurationTime getUserTaskDurationTime() {
//     return UserTaskDurationTime.TOTAL;
//   }
//
//   @Override
//   protected void changeDuration(
//       final ProcessInstanceEngineDto processInstanceDto,
//       final String userTaskKey,
//       final Double durationInMs) {
//     changeUserTaskTotalDuration(processInstanceDto, userTaskKey, durationInMs);
//   }
//
//   @Override
//   protected void changeDuration(
//       final ProcessInstanceEngineDto processInstanceDto, final Double durationInMs) {
//     changeUserTaskTotalDuration(processInstanceDto, durationInMs);
//   }
//
//   @Override
//   protected ProcessReportDataDto createReport(
//       final String processDefinitionKey, final List<String> versions) {
//     return TemplatedProcessReportDataBuilder.createReportData()
//         .setProcessDefinitionKey(processDefinitionKey)
//         .setProcessDefinitionVersions(versions)
//         .setUserTaskDurationTime(UserTaskDurationTime.TOTAL)
//         .setReportDataType(USER_TASK_DUR_GROUP_BY_USER_TASK_BY_ASSIGNEE)
//         .build();
//   }
//
//   @Override
//   protected void assertEvaluateReportWithFlowNodeStatusFilter(
//       final ReportResultResponseDto<List<HyperMapResultEntryDto>> result,
//       final List<ProcessFilterDto<?>> filter,
//       final long expectedInstanceCount) {
//     if (isSingleFilterOfType(filter, RunningFlowNodesOnlyFilterDto.class)) {
//       // @formatter:off
//       HyperMapAsserter.asserter()
//           .processInstanceCount(expectedInstanceCount)
//           .processInstanceCountWithoutFilters(2L)
//           .measure(ViewProperty.DURATION, AggregationType.AVERAGE, getUserTaskDurationTime())
//           .groupByContains(USER_TASK_1)
//           .distributedByContains(DEFAULT_USERNAME, 700., DEFAULT_FULLNAME)
//           .groupByContains(USER_TASK_2)
//           .distributedByContains(DEFAULT_USERNAME, 700., DEFAULT_FULLNAME)
//           .doAssert(result);
//       // @formatter:on
//     } else if (isSingleFilterOfType(filter, CompletedFlowNodesOnlyFilterDto.class)) {
//       // @formatter:off
//       HyperMapAsserter.asserter()
//           .processInstanceCount(expectedInstanceCount)
//           .processInstanceCountWithoutFilters(2L)
//           .measure(ViewProperty.DURATION, AggregationType.AVERAGE, getUserTaskDurationTime())
//           .groupByContains(USER_TASK_1)
//           .distributedByContains(DEFAULT_USERNAME, 100., DEFAULT_FULLNAME)
//           .doAssert(result);
//       // @formatter:on
//     } else if (isSingleFilterOfType(filter, CompletedOrCanceledFlowNodesOnlyFilterDto.class)) {
//       // @formatter:off
//       HyperMapAsserter.asserter()
//           .processInstanceCount(expectedInstanceCount)
//           .processInstanceCountWithoutFilters(2L)
//           .measure(ViewProperty.DURATION, AggregationType.AVERAGE, getUserTaskDurationTime())
//           .groupByContains(USER_TASK_1)
//           .distributedByContains(DEFAULT_USERNAME, 100., DEFAULT_FULLNAME)
//           .doAssert(result);
//       // @formatter:on
//     } else {
//       fail("Not a valid flow node status filter for test");
//     }
//   }
// }
