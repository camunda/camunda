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
// io.camunda.optimize.service.db.es.report.command.modules.distributed_by.process.identity.ProcessDistributedByIdentity.DISTRIBUTE_BY_IDENTITY_MISSING_KEY;
// import static
// io.camunda.optimize.service.util.ProcessReportDataType.USER_TASK_DUR_GROUP_BY_USER_TASK_BY_ASSIGNEE;
// import static io.camunda.optimize.test.it.extension.EngineIntegrationExtension.DEFAULT_FULLNAME;
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
// io.camunda.optimize.dto.optimize.query.report.single.process.filter.CompletedFlowNodesOnlyFilterDto;
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
// public class UserTaskWorkDurationByUserTaskByAssigneeReportEvaluationIT
//     extends AbstractUserTaskDurationByUserTaskByAssigneeReportEvaluationIT {
//   //  protected static final Double UNASSIGNED_TASK_DURATION = null;
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
//           .distributedByContains(DEFAULT_USERNAME, 500., DEFAULT_FULLNAME)
//           .groupByContains(USER_TASK_2)
//           .distributedByContains(DEFAULT_USERNAME, 500., DEFAULT_FULLNAME)
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
//
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
//
//   @Override
//   protected void assertHyperMap_forOneProcessInstanceWithUnassignedTasks(
//       final ReportResultResponseDto<List<HyperMapResultEntryDto>> result) {
//     // @formatter:off
//     HyperMapAsserter.asserter()
//         .processInstanceCount(1L)
//         .processInstanceCountWithoutFilters(1L)
//         .measure(ViewProperty.DURATION, AggregationType.AVERAGE, getUserTaskDurationTime())
//         .groupByContains(USER_TASK_1)
//         .distributedByContains(
//             DEFAULT_USERNAME,
//             calculateExpectedValueGivenDurationsDefaultAggr(SET_DURATIONS[1]),
//             DEFAULT_FULLNAME)
//         .distributedByContains(
//             DISTRIBUTE_BY_IDENTITY_MISSING_KEY, null, getLocalizedUnassignedLabel())
//         .groupByContains(USER_TASK_2)
//         .distributedByContains(DEFAULT_USERNAME, null, DEFAULT_FULLNAME)
//         .distributedByContains(
//             DISTRIBUTE_BY_IDENTITY_MISSING_KEY, null, getLocalizedUnassignedLabel())
//         .groupByContains(USER_TASK_A)
//         .distributedByContains(
//             DEFAULT_USERNAME,
//             calculateExpectedValueGivenDurationsDefaultAggr(SET_DURATIONS[1]),
//             DEFAULT_FULLNAME)
//         .distributedByContains(
//             DISTRIBUTE_BY_IDENTITY_MISSING_KEY, null, getLocalizedUnassignedLabel())
//         .groupByContains(USER_TASK_B)
//         .distributedByContains(DEFAULT_USERNAME, null, DEFAULT_FULLNAME)
//         .distributedByContains(
//             DISTRIBUTE_BY_IDENTITY_MISSING_KEY, null, getLocalizedUnassignedLabel())
//         .doAssert(result);
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
//                       DEFAULT_USERNAME,
//                       databaseIntegrationTestExtension
//                           .calculateExpectedValueGivenDurations(SET_DURATIONS)
//                           .get(aggType),
//                       DEFAULT_FULLNAME)
//                   .distributedByContains(SECOND_USER, null, SECOND_USER_FULL_NAME)
//                   .distributedByContains(
//                       DISTRIBUTE_BY_IDENTITY_MISSING_KEY, null, getLocalizedUnassignedLabel())
//                   .groupByContains(USER_TASK_2)
//                   .distributedByContains(DEFAULT_USERNAME, null, DEFAULT_FULLNAME)
//                   .distributedByContains(
//                       SECOND_USER,
//                       databaseIntegrationTestExtension
//                           .calculateExpectedValueGivenDurations(SET_DURATIONS[0])
//                           .get(aggType),
//                       SECOND_USER_FULL_NAME)
//                   .distributedByContains(
//                       DISTRIBUTE_BY_IDENTITY_MISSING_KEY, null, getLocalizedUnassignedLabel())
//                   .groupByContains(USER_TASK_A)
//                   .distributedByContains(
//                       DEFAULT_USERNAME,
//                       databaseIntegrationTestExtension
//                           .calculateExpectedValueGivenDurations(SET_DURATIONS)
//                           .get(aggType),
//                       DEFAULT_FULLNAME)
//                   .distributedByContains(SECOND_USER, null, SECOND_USER_FULL_NAME)
//                   .distributedByContains(
//                       DISTRIBUTE_BY_IDENTITY_MISSING_KEY, null, getLocalizedUnassignedLabel())
//                   .groupByContains(USER_TASK_B)
//                   .distributedByContains(DEFAULT_USERNAME, null, DEFAULT_FULLNAME)
//                   .distributedByContains(
//                       SECOND_USER,
//                       databaseIntegrationTestExtension
//                           .calculateExpectedValueGivenDurations(SET_DURATIONS[0])
//                           .get(aggType),
//                       SECOND_USER_FULL_NAME)
//                   .distributedByContains(
//                       DISTRIBUTE_BY_IDENTITY_MISSING_KEY, null, getLocalizedUnassignedLabel())
//                   .add()
//                   .add();
//               // @formatter:on
//             });
//     hyperMapAsserter.doAssert(result);
//   }
//
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
//             DEFAULT_USERNAME,
//             calculateExpectedValueGivenDurationsDefaultAggr(setDurations1),
//             DEFAULT_FULLNAME)
//         .doAssert(result1);
//
//     HyperMapAsserter.asserter()
//         .processInstanceCount(2L)
//         .processInstanceCountWithoutFilters(2L)
//         .measure(ViewProperty.DURATION, AggregationType.AVERAGE, getUserTaskDurationTime())
//         .groupByContains(USER_TASK_1)
//         .distributedByContains(
//             DEFAULT_USERNAME,
//             calculateExpectedValueGivenDurationsDefaultAggr(setDurations2[0]),
//             DEFAULT_FULLNAME)
//         .distributedByContains(
//             DISTRIBUTE_BY_IDENTITY_MISSING_KEY, null, getLocalizedUnassignedLabel())
//         .doAssert(result2);
//     // @formatter:on
//   }
// }
