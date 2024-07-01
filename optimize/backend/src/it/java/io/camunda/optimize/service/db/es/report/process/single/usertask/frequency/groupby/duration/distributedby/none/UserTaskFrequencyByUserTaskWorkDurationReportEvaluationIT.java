/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
// TODO recreate C8 IT equivalent of this with #13337
// package
// io.camunda.optimize.service.db.es.report.process.single.usertask.frequency.groupby.duration.distributedby.none;
//
// import static
// io.camunda.optimize.service.util.ProcessReportDataType.USER_TASK_FREQ_GROUP_BY_USER_TASK_DURATION;
// import static org.assertj.core.api.Assertions.assertThat;
//
// import io.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
// import io.camunda.optimize.dto.optimize.query.report.single.configuration.UserTaskDurationTime;
// import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
// import io.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
// import io.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
// import io.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResponseDto;
// import io.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
// import io.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
// import
// io.camunda.optimize.service.db.es.report.process.single.ModelElementFrequencyByModelElementDurationIT;
// import io.camunda.optimize.service.util.TemplatedProcessReportDataBuilder;
// import java.time.OffsetDateTime;
// import java.util.List;
// import org.assertj.core.groups.Tuple;
// import org.junit.jupiter.api.Test;
//
// public class UserTaskFrequencyByUserTaskWorkDurationReportEvaluationIT
//     extends ModelElementFrequencyByModelElementDurationIT {
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
//   }
//
//   @Override
//   protected ProcessViewEntity getModelElementView() {
//     return ProcessViewEntity.USER_TASK;
//   }
//
//   @Override
//   protected int getNumberOfModelElementsPerInstance() {
//     return 1;
//   }
//
//   @Override
//   protected ProcessReportDataDto createReport(
//       final String processKey, final String definitionVersion) {
//     return TemplatedProcessReportDataBuilder.createReportData()
//         .setProcessDefinitionKey(processKey)
//         .setProcessDefinitionVersion(definitionVersion)
//         .setReportDataType(USER_TASK_FREQ_GROUP_BY_USER_TASK_DURATION)
//         .setUserTaskDurationTime(UserTaskDurationTime.WORK)
//         .build();
//   }
//
//   @Test
//   public void multipleProcessInstances_testInstanceWithoutWorkTimeDoesNotCauseTrouble() {
//     // given
//     final int completedActivityInstanceDuration = 1000;
//     final ProcessDefinitionEngineDto definition = deploySimpleOneUserTasksDefinition();
//     startProcessInstanceCompleteTaskAndModifyDuration(
//         definition.getId(), completedActivityInstanceDuration);
//     // there is a running user task instance without a claim which would yield a `null` work
//     // duration script result
//     engineIntegrationExtension.startProcessInstance(definition.getId());
//     importAllEngineEntitiesFromScratch();
//
//     // when
//     final ProcessReportDataDto reportData =
//         createReport(definition.getKey(), definition.getVersionAsString());
//     AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResponse =
//         reportClient.evaluateMapReport(reportData);
//
//     // then we expect two instances in a complete result, however as for one no work time could
// be
//     // calculated there
//     // is just one duration bucket with one user task instance present
//     final ReportResultResponseDto<List<MapResultEntryDto>> resultDto =
//         evaluationResponse.getResult();
//     assertThat(resultDto.getInstanceCount()).isEqualTo(2L);
//     assertThat(resultDto.getFirstMeasureData())
//         .hasSize(1)
//         .extracting(MapResultEntryDto::getKey, MapResultEntryDto::getValue)
//         .contains(
//             Tuple.tuple(
//                 createDurationBucketKey(completedActivityInstanceDuration),
//                 getExpectedNumberOfModelElements()));
//   }
// }
