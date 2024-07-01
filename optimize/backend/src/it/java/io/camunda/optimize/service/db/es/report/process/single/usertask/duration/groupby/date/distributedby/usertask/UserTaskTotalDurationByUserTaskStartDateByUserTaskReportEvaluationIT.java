/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
// TODO recreate C8 IT equivalent of this with #13337
// package
// io.camunda.optimize.service.db.es.report.process.single.usertask.duration.groupby.date.distributedby.usertask;
//
// import io.camunda.optimize.dto.optimize.query.report.single.configuration.UserTaskDurationTime;
// import io.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
//
// public class UserTaskTotalDurationByUserTaskStartDateByUserTaskReportEvaluationIT
//     extends UserTaskDurationByUserTaskStartDateByUserTaskReportEvaluationIT {
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
//   protected Double getCorrectTestExecutionValue(
//       final FlowNodeStatusTestValues flowNodeStatusTestValues) {
//     return flowNodeStatusTestValues.expectedTotalDurationValue;
//   }
// }
