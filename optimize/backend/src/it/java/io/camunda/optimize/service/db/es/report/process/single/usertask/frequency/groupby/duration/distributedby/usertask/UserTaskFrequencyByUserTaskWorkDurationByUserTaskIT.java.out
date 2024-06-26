/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
// TODO recreate C8 IT equivalent of this with #13337
// package
// io.camunda.optimize.service.db.es.report.process.single.usertask.frequency.groupby.duration.distributedby.usertask;
//
// import io.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
// import io.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
// import io.camunda.optimize.dto.optimize.query.report.single.configuration.UserTaskDurationTime;
// import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
// import io.camunda.optimize.dto.optimize.query.report.single.result.hyper.HyperMapResultEntryDto;
// import io.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResponseDto;
// import io.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
// import io.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
// import io.camunda.optimize.service.db.es.report.util.HyperMapAsserter;
// import java.time.OffsetDateTime;
// import java.util.List;
// import org.junit.jupiter.api.Test;
//
// public class UserTaskFrequencyByUserTaskWorkDurationByUserTaskIT
//     extends AbstractUserTaskFrequencyByUserTaskDurationByUserTaskIT {
//
//   @Override
//   protected ProcessInstanceEngineDto startProcessInstanceCompleteTaskAndModifyDuration(
//       final String definitionId, final Number durationInMillis) {
//     final ProcessInstanceEngineDto processInstance =
//         engineIntegrationExtension.startProcessInstance(definitionId);
//     engineIntegrationExtension.finishAllRunningUserTasks(processInstance.getId());
//     changeUserTaskWorkDuration(processInstance, durationInMillis);
//     return processInstance;
//   }
//
//   @Override
//   protected void changeRunningInstanceReferenceDate(
//       final ProcessInstanceEngineDto runningProcessInstance, final OffsetDateTime startTime) {
//     engineIntegrationExtension.claimAllRunningUserTasks(runningProcessInstance.getId());
//     changeUserTaskClaimDate(runningProcessInstance, startTime, USER_TASK_1, 0);
//     changeUserTaskStartDate(runningProcessInstance, startTime, USER_TASK_1, 0);
//   }
//
//   @Override
//   protected UserTaskDurationTime getUserTaskDurationTime() {
//     return UserTaskDurationTime.WORK;
//   }
//
//   @Test
//   public void multipleProcessInstances_testInstanceWithoutWorkTimeDoesNotCauseTrouble() {
//     // given
//     final int completedUserTaskDuration = 1000;
//     final ProcessDefinitionEngineDto definition = deploySimpleOneUserTasksDefinition();
//     startProcessInstanceCompleteTaskAndModifyDuration(
//         definition.getId(), completedUserTaskDuration);
//     // there is a running user task instance without a claim which would yield a `null` work
//     // duration script result
//     engineIntegrationExtension.startProcessInstance(definition.getId());
//     importAllEngineEntitiesFromScratch();
//
//     // when
//     final ProcessReportDataDto reportData =
//         createReport(definition.getKey(), definition.getVersionAsString());
//     AuthorizedProcessReportEvaluationResponseDto<List<HyperMapResultEntryDto>> evaluationResponse
// =
//         reportClient.evaluateHyperMapReport(reportData);
//
//     // then we expect two instances in a complete result, however as for one no work time could
// be
//     // calculated there
//     // is just one duration bucket with one user task instance present
//     final ReportResultResponseDto<List<HyperMapResultEntryDto>> resultDto =
//         evaluationResponse.getResult();
//     // @formatter:off
//     HyperMapAsserter.asserter()
//         .processInstanceCount(2L)
//         .processInstanceCountWithoutFilters(2L)
//         .measure(ViewProperty.FREQUENCY)
//         .groupByContains(createDurationBucketKey(completedUserTaskDuration))
//         .distributedByContains(USER_TASK_1, 1., USER_TASK_1)
//         .doAssert(resultDto);
//     // @formatter:on
//   }
// }
