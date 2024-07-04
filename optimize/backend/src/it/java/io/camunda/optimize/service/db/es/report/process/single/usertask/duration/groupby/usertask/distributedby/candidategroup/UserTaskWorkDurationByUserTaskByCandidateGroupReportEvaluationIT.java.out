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
// io.camunda.optimize.service.db.es.report.command.modules.distributed_by.process.identity.ProcessDistributedByIdentity.DISTRIBUTE_BY_IDENTITY_MISSING_KEY;
// import static
// io.camunda.optimize.service.util.ProcessReportDataType.USER_TASK_DUR_GROUP_BY_USER_TASK_BY_CANDIDATE_GROUP;
// import static
// io.camunda.optimize.test.util.DurationAggregationUtil.calculateExpectedValueGivenDurationsDefaultAggr;
// import static io.camunda.optimize.test.util.DurationAggregationUtil.getSupportedAggregationTypes;
// import static org.assertj.core.api.Assertions.assertThat;
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
// import io.camunda.optimize.dto.optimize.rest.report.measure.MeasureResponseDto;
// import io.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
// import io.camunda.optimize.service.db.es.report.util.HyperMapAsserter;
// import io.camunda.optimize.service.util.TemplatedProcessReportDataBuilder;
// import java.util.Arrays;
// import java.util.List;
//
// public class UserTaskWorkDurationByUserTaskByCandidateGroupReportEvaluationIT
//     extends AbstractUserTaskDurationByUserTaskByCandidateGroupReportEvaluationIT {
//
//   @Override
//   protected UserTaskDurationTime getUserTaskDurationTime() {
//     return UserTaskDurationTime.WORK;
//   }
//
//   @Override
//   protected void changeDuration(
//       final ProcessInstanceEngineDto processInstanceDto, final Double durationInMs) {
//     changeUserTaskWorkDuration(processInstanceDto, durationInMs);
//   }
//
//   @Override
//   protected void changeDuration(
//       final ProcessInstanceEngineDto processInstanceDto,
//       final String userTaskKey,
//       final Double durationInMs) {
//     changeUserTaskWorkDuration(processInstanceDto, userTaskKey, durationInMs);
//   }
//
//   @Override
//   protected ProcessReportDataDto createReport(
//       final String processDefinitionKey, final List<String> versions) {
//     return TemplatedProcessReportDataBuilder.createReportData()
//         .setProcessDefinitionKey(processDefinitionKey)
//         .setProcessDefinitionVersions(versions)
//         .setUserTaskDurationTime(UserTaskDurationTime.WORK)
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
//           .distributedByContains(FIRST_CANDIDATE_GROUP_ID, 500., FIRST_CANDIDATE_GROUP_NAME)
//           .groupByContains(USER_TASK_2)
//           .distributedByContains(FIRST_CANDIDATE_GROUP_ID, 500., FIRST_CANDIDATE_GROUP_NAME)
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
//
//   @Override
//   protected void assertHyperMap_ForOneProcessInstanceWithUnassignedTasks(
//       final ReportResultResponseDto<List<HyperMapResultEntryDto>> actualResult) {
//     // @formatter:off
//     HyperMapAsserter.asserter()
//         .processInstanceCount(1L)
//         .processInstanceCountWithoutFilters(1L)
//         .measure(ViewProperty.DURATION, AggregationType.AVERAGE, getUserTaskDurationTime())
//         .groupByContains(USER_TASK_1)
//         .distributedByContains(
//             FIRST_CANDIDATE_GROUP_ID,
//             calculateExpectedValueGivenDurationsDefaultAggr(SET_DURATIONS[1]),
//             FIRST_CANDIDATE_GROUP_NAME)
//         .distributedByContains(
//             DISTRIBUTE_BY_IDENTITY_MISSING_KEY, null, getLocalizedUnassignedLabel())
//         .groupByContains(USER_TASK_2)
//         .distributedByContains(FIRST_CANDIDATE_GROUP_ID, null, FIRST_CANDIDATE_GROUP_NAME)
//         .distributedByContains(
//             DISTRIBUTE_BY_IDENTITY_MISSING_KEY, null, getLocalizedUnassignedLabel())
//         .groupByContains(USER_TASK_A)
//         .distributedByContains(
//             FIRST_CANDIDATE_GROUP_ID,
//             calculateExpectedValueGivenDurationsDefaultAggr(SET_DURATIONS[1]),
//             FIRST_CANDIDATE_GROUP_NAME)
//         .distributedByContains(
//             DISTRIBUTE_BY_IDENTITY_MISSING_KEY, null, getLocalizedUnassignedLabel())
//         .groupByContains(USER_TASK_B)
//         .distributedByContains(FIRST_CANDIDATE_GROUP_ID, null, FIRST_CANDIDATE_GROUP_NAME)
//         .distributedByContains(
//             DISTRIBUTE_BY_IDENTITY_MISSING_KEY, null, getLocalizedUnassignedLabel())
//         .doAssert(actualResult);
//     // @formatter:on
//   }
//
//   @Override
//   protected void assertHyperMap_ForSeveralProcessInstances(
//       final ReportResultResponseDto<List<HyperMapResultEntryDto>> actualResult) {
//     // @formatter:off
//     HyperMapAsserter.asserter()
//         .processInstanceCount(2L)
//         .processInstanceCountWithoutFilters(2L)
//         .measure(ViewProperty.DURATION, AggregationType.AVERAGE, getUserTaskDurationTime())
//         .groupByContains(USER_TASK_1)
//         .distributedByContains(
//             FIRST_CANDIDATE_GROUP_ID,
//             calculateExpectedValueGivenDurationsDefaultAggr(SET_DURATIONS),
//             FIRST_CANDIDATE_GROUP_NAME)
//         .distributedByContains(SECOND_CANDIDATE_GROUP_ID, null, SECOND_CANDIDATE_GROUP_NAME)
//         .distributedByContains(
//             DISTRIBUTE_BY_IDENTITY_MISSING_KEY, null, getLocalizedUnassignedLabel())
//         .groupByContains(USER_TASK_2)
//         .distributedByContains(FIRST_CANDIDATE_GROUP_ID, null, FIRST_CANDIDATE_GROUP_NAME)
//         .distributedByContains(
//             SECOND_CANDIDATE_GROUP_ID,
//             calculateExpectedValueGivenDurationsDefaultAggr(SET_DURATIONS[0]),
//             SECOND_CANDIDATE_GROUP_NAME)
//         .distributedByContains(
//             DISTRIBUTE_BY_IDENTITY_MISSING_KEY, null, getLocalizedUnassignedLabel())
//         .groupByContains(USER_TASK_A)
//         .distributedByContains(
//             FIRST_CANDIDATE_GROUP_ID,
//             calculateExpectedValueGivenDurationsDefaultAggr(SET_DURATIONS),
//             FIRST_CANDIDATE_GROUP_NAME)
//         .distributedByContains(SECOND_CANDIDATE_GROUP_ID, null, SECOND_CANDIDATE_GROUP_NAME)
//         .distributedByContains(
//             DISTRIBUTE_BY_IDENTITY_MISSING_KEY, null, getLocalizedUnassignedLabel())
//         .groupByContains(USER_TASK_B)
//         .distributedByContains(FIRST_CANDIDATE_GROUP_ID, null, FIRST_CANDIDATE_GROUP_NAME)
//         .distributedByContains(
//             SECOND_CANDIDATE_GROUP_ID,
//             calculateExpectedValueGivenDurationsDefaultAggr(SET_DURATIONS[0]),
//             SECOND_CANDIDATE_GROUP_NAME)
//         .distributedByContains(
//             DISTRIBUTE_BY_IDENTITY_MISSING_KEY, null, getLocalizedUnassignedLabel())
//         .doAssert(actualResult);
//     // @formatter:on
//   }
//
//   @Override
//   protected void assertHyperMap_ForSeveralProcessInstancesWithAllAggregationTypes(
//       final ReportResultResponseDto<List<HyperMapResultEntryDto>> result) {
//     assertThat(result.getMeasures())
//         .extracting(MeasureResponseDto::getAggregationType)
//         .containsExactly(getSupportedAggregationTypes());
//     final HyperMapAsserter hyperMapAsserter =
//
// HyperMapAsserter.asserter().processInstanceCount(2L).processInstanceCountWithoutFilters(2L);
//     Arrays.stream(getSupportedAggregationTypes())
//         .forEach(
//             aggType -> {
//               // @formatter:off
//               hyperMapAsserter
//                   .measure(ViewProperty.DURATION, aggType, getUserTaskDurationTime())
//                   .groupByContains(USER_TASK_1)
//                   .distributedByContains(
//                       FIRST_CANDIDATE_GROUP_ID,
//                       databaseIntegrationTestExtension
//                           .calculateExpectedValueGivenDurations(SET_DURATIONS)
//                           .get(aggType),
//                       FIRST_CANDIDATE_GROUP_NAME)
//                   .distributedByContains(
//                       SECOND_CANDIDATE_GROUP_ID, null, SECOND_CANDIDATE_GROUP_NAME)
//                   .distributedByContains(
//                       DISTRIBUTE_BY_IDENTITY_MISSING_KEY, null, getLocalizedUnassignedLabel())
//                   .groupByContains(USER_TASK_2)
//                   .distributedByContains(FIRST_CANDIDATE_GROUP_ID, null,
// FIRST_CANDIDATE_GROUP_NAME)
//                   .distributedByContains(
//                       SECOND_CANDIDATE_GROUP_ID,
//                       databaseIntegrationTestExtension
//                           .calculateExpectedValueGivenDurations(SET_DURATIONS[0])
//                           .get(aggType),
//                       SECOND_CANDIDATE_GROUP_NAME)
//                   .distributedByContains(
//                       DISTRIBUTE_BY_IDENTITY_MISSING_KEY, null, getLocalizedUnassignedLabel())
//                   .groupByContains(USER_TASK_A)
//                   .distributedByContains(
//                       FIRST_CANDIDATE_GROUP_ID,
//                       databaseIntegrationTestExtension
//                           .calculateExpectedValueGivenDurations(SET_DURATIONS)
//                           .get(aggType),
//                       FIRST_CANDIDATE_GROUP_NAME)
//                   .distributedByContains(
//                       SECOND_CANDIDATE_GROUP_ID, null, SECOND_CANDIDATE_GROUP_NAME)
//                   .distributedByContains(
//                       DISTRIBUTE_BY_IDENTITY_MISSING_KEY, null, getLocalizedUnassignedLabel())
//                   .groupByContains(USER_TASK_B)
//                   .distributedByContains(FIRST_CANDIDATE_GROUP_ID, null,
// FIRST_CANDIDATE_GROUP_NAME)
//                   .distributedByContains(
//                       SECOND_CANDIDATE_GROUP_ID,
//                       databaseIntegrationTestExtension
//                           .calculateExpectedValueGivenDurations(SET_DURATIONS[0])
//                           .get(aggType),
//                       SECOND_CANDIDATE_GROUP_NAME)
//                   .distributedByContains(
//                       DISTRIBUTE_BY_IDENTITY_MISSING_KEY, null, getLocalizedUnassignedLabel())
//                   .add()
//                   .add();
//               // @formatter:on
//             });
//     hyperMapAsserter.doAssert(result);
//   }
//
//   @Override
//   protected void assertHyperMap_ForMultipleEventsWithAllAggregationTypes(
//       final ReportResultResponseDto<List<HyperMapResultEntryDto>> result) {
//     assertThat(result.getMeasures())
//         .extracting(MeasureResponseDto::getAggregationType)
//         .containsExactly(getSupportedAggregationTypes());
//     final HyperMapAsserter hyperMapAsserter =
//
// HyperMapAsserter.asserter().processInstanceCount(2L).processInstanceCountWithoutFilters(2L);
//     Arrays.stream(getSupportedAggregationTypes())
//         .forEach(
//             aggType -> {
//               // @formatter:off
//               hyperMapAsserter
//                   .measure(ViewProperty.DURATION, aggType, getUserTaskDurationTime())
//                   .groupByContains(USER_TASK_1)
//                   .distributedByContains(
//                       FIRST_CANDIDATE_GROUP_ID,
//                       databaseIntegrationTestExtension
//                           .calculateExpectedValueGivenDurations(SET_DURATIONS)
//                           .get(aggType),
//                       FIRST_CANDIDATE_GROUP_NAME)
//                   .distributedByContains(
//                       SECOND_CANDIDATE_GROUP_ID, null, SECOND_CANDIDATE_GROUP_NAME)
//                   .distributedByContains(
//                       DISTRIBUTE_BY_IDENTITY_MISSING_KEY, null, getLocalizedUnassignedLabel())
//                   .groupByContains(USER_TASK_2)
//                   .distributedByContains(FIRST_CANDIDATE_GROUP_ID, null,
// FIRST_CANDIDATE_GROUP_NAME)
//                   .distributedByContains(
//                       SECOND_CANDIDATE_GROUP_ID,
//                       databaseIntegrationTestExtension
//                           .calculateExpectedValueGivenDurations(SET_DURATIONS[0])
//                           .get(aggType),
//                       SECOND_CANDIDATE_GROUP_NAME)
//                   .distributedByContains(
//                       DISTRIBUTE_BY_IDENTITY_MISSING_KEY, null, getLocalizedUnassignedLabel())
//                   .add()
//                   .add();
//               // @formatter:on
//             });
//     hyperMapAsserter.doAssert(result);
//   }
//
//   @Override
//   protected void assertHyperMap_otherProcessDefinitionsDoNotInfluenceResult(
//       final Double[] setDurations1,
//       final Double[] setDurations2,
//       final ReportResultResponseDto<List<HyperMapResultEntryDto>> result1,
//       final ReportResultResponseDto<List<HyperMapResultEntryDto>> result2) {
//     // @formatter:off
//     HyperMapAsserter.asserter()
//         .processInstanceCount(2L)
//         .processInstanceCountWithoutFilters(2L)
//         .measure(ViewProperty.DURATION, AggregationType.AVERAGE, getUserTaskDurationTime())
//         .groupByContains(USER_TASK_1)
//         .distributedByContains(
//             FIRST_CANDIDATE_GROUP_ID,
//             calculateExpectedValueGivenDurationsDefaultAggr(setDurations1),
//             FIRST_CANDIDATE_GROUP_NAME)
//         .doAssert(result1);
//
//     HyperMapAsserter.asserter()
//         .processInstanceCount(2L)
//         .processInstanceCountWithoutFilters(2L)
//         .measure(ViewProperty.DURATION, AggregationType.AVERAGE, getUserTaskDurationTime())
//         .groupByContains(USER_TASK_1)
//         .distributedByContains(
//             FIRST_CANDIDATE_GROUP_ID,
//             calculateExpectedValueGivenDurationsDefaultAggr(setDurations2[0]),
//             FIRST_CANDIDATE_GROUP_NAME)
//         .distributedByContains(
//             DISTRIBUTE_BY_IDENTITY_MISSING_KEY, null, getLocalizedUnassignedLabel())
//         .doAssert(result2);
//     // @formatter:on
//   }
// }
