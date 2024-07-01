/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
// TODO recreate C8 IT equivalent of this with #13337
// package
// io.camunda.optimize.service.db.es.report.process.single.usertask.duration.groupby.usertask.distributedby.none;
//
// import static org.assertj.core.api.Assertions.assertThat;
//
// import io.camunda.optimize.dto.optimize.query.report.single.configuration.UserTaskDurationTime;
// import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
// import io.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
// import io.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
// import io.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
// import io.camunda.optimize.service.db.es.report.util.MapResultUtil;
// import io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex;
// import io.camunda.optimize.service.util.ProcessReportDataType;
// import io.camunda.optimize.service.util.TemplatedProcessReportDataBuilder;
// import java.util.List;
// import java.util.Optional;
//
// public class UserTaskIdleDurationByUserTaskReportEvaluationIT
//     extends AbstractUserTaskDurationByUserTaskReportEvaluationIT {
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
//   protected void setDurationFieldToNullInElasticsearch(final String processInstanceId) {
//     setUserTaskDurationToNull(processInstanceId, ProcessInstanceIndex.USER_TASK_IDLE_DURATION);
//   }
//
//   @Override
//   protected ProcessReportDataDto createReport(
//       final String processDefinitionKey, final List<String> versions) {
//     return TemplatedProcessReportDataBuilder.createReportData()
//         .setProcessDefinitionKey(processDefinitionKey)
//         .setProcessDefinitionVersions(versions)
//         .setUserTaskDurationTime(UserTaskDurationTime.IDLE)
//         .setReportDataType(ProcessReportDataType.USER_TASK_DUR_GROUP_BY_USER_TASK)
//         .build();
//   }
//
//   @Override
//   protected void assertEvaluateReportWithFlowNodeStatusFilter(
//       final ReportResultResponseDto<List<MapResultEntryDto>> result,
//       final FlowNodeStatusTestValues expectedValues) {
//     Optional.ofNullable(expectedValues.getExpectedIdleDurationValues().get(USER_TASK_1))
//         .ifPresent(
//             expectedVal ->
//                 assertThat(
//                         MapResultUtil.getEntryForKey(result.getFirstMeasureData(), USER_TASK_1)
//                             .get()
//                             .getValue())
//                     .isEqualTo(expectedVal));
//     Optional.ofNullable(expectedValues.getExpectedIdleDurationValues().get(USER_TASK_2))
//         .ifPresent(
//             expectedVal ->
//                 assertThat(
//                         MapResultUtil.getEntryForKey(result.getFirstMeasureData(), USER_TASK_2)
//                             .get()
//                             .getValue())
//                     .isEqualTo(expectedVal));
//   }
// }
