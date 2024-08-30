/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
// TODO recreate C8 IT equivalent of this with #13337
// package
// io.camunda.optimize.service.db.es.report.process.single.usertask.duration.groupby.assignee.distributedby.none;
//
// import static io.camunda.optimize.rest.RestTestConstants.DEFAULT_USERNAME;
// import static
// io.camunda.optimize.service.util.ProcessReportDataType.USER_TASK_DUR_GROUP_BY_ASSIGNEE;
// import static org.assertj.core.api.Assertions.assertThat;
//
// import io.camunda.optimize.dto.optimize.query.report.single.configuration.UserTaskDurationTime;
// import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
// import io.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
// import io.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
// import io.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
// import io.camunda.optimize.service.db.es.report.util.MapResultUtil;
// import io.camunda.optimize.service.util.TemplatedProcessReportDataBuilder;
// import java.util.List;
//
// public class UserTaskIdleDurationByAssigneeReportEvaluationIT
//     extends AbstractUserTaskDurationByAssigneeReportEvaluationIT {
//
//   @Override
//   protected UserTaskDurationTime getUserTaskDurationTime() {
//     return UserTaskDurationTime.IDLE;
//   }
//
//   @Override
//   protected void changeDuration(
//       final ProcessInstanceEngineDto processInstanceDto, final Number durationInMs) {
//     changeUserTaskIdleDuration(processInstanceDto, durationInMs);
//   }
//
//   @Override
//   protected void changeDuration(
//       final ProcessInstanceEngineDto processInstanceDto,
//       final String userTaskKey,
//       final Number durationInMs) {
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
//         .setReportDataType(USER_TASK_DUR_GROUP_BY_ASSIGNEE)
//         .build();
//   }
//
//   @Override
//   protected void assertEvaluateReportWithFlowNodeStatusFilter(
//       final ReportResultResponseDto<List<MapResultEntryDto>> result,
//       final FlowNodeStateTestValues expectedValues) {
//     assertThat(
//             MapResultUtil.getEntryForKey(result.getFirstMeasureData(), DEFAULT_USERNAME)
//                 .map(MapResultEntryDto::getValue)
//                 .orElse(null))
//         .isEqualTo(expectedValues.getExpectedIdleDurationValues().get(DEFAULT_USERNAME));
//     assertThat(
//             MapResultUtil.getEntryForKey(result.getFirstMeasureData(), SECOND_USER)
//                 .map(MapResultEntryDto::getValue)
//                 .orElse(null))
//         .isEqualTo(expectedValues.getExpectedIdleDurationValues().get(SECOND_USER));
//   }
// }
