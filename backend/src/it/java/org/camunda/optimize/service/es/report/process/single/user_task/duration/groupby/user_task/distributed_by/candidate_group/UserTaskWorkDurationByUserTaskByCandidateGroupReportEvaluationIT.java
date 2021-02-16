/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process.single.user_task.duration.groupby.user_task.distributed_by.candidate_group;

import org.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.UserTaskDurationTime;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.CanceledFlowNodesOnlyFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.CompletedOrCanceledFlowNodesOnlyFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.RunningFlowNodesOnlyFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.ReportHyperMapResultDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.report.util.HyperMapAsserter;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;

import java.util.List;
import java.util.Map;

import static org.camunda.optimize.service.es.report.command.modules.distributed_by.process.identity.ProcessDistributedByIdentity.DISTRIBUTE_BY_IDENTITY_MISSING_KEY;
import static org.camunda.optimize.test.util.DurationAggregationUtil.calculateExpectedValueGivenDurations;
import static org.camunda.optimize.test.util.DurationAggregationUtil.calculateExpectedValueGivenDurationsDefaultAggr;
import static org.camunda.optimize.test.util.ProcessReportDataType.USER_TASK_DURATION_GROUP_BY_USER_TASK_BY_CANDIDATE_GROUP;
import static org.junit.jupiter.api.Assertions.fail;

public class UserTaskWorkDurationByUserTaskByCandidateGroupReportEvaluationIT
  extends AbstractUserTaskDurationByUserTaskByCandidateGroupReportEvaluationIT {

  @Override
  protected UserTaskDurationTime getUserTaskDurationTime() {
    return UserTaskDurationTime.WORK;
  }

  @Override
  protected void changeDuration(final ProcessInstanceEngineDto processInstanceDto, final Double durationInMs) {
    changeUserTaskWorkDuration(processInstanceDto, durationInMs);
  }

  @Override
  protected void changeDuration(final ProcessInstanceEngineDto processInstanceDto,
                                final String userTaskKey,
                                final Double durationInMs) {
    changeUserTaskWorkDuration(processInstanceDto, userTaskKey, durationInMs);
  }

  @Override
  protected ProcessReportDataDto createReport(final String processDefinitionKey, final List<String> versions) {
    return TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersions(versions)
      .setUserTaskDurationTime(UserTaskDurationTime.WORK)
      .setReportDataType(USER_TASK_DURATION_GROUP_BY_USER_TASK_BY_CANDIDATE_GROUP)
      .build();
  }

  @Override
  protected void assertEvaluateReportWithFlowNodeStatusFilter(final ReportHyperMapResultDto result,
                                                              final List<ProcessFilterDto<?>> processFilter) {
    if (isSingleFilterOfType(processFilter, RunningFlowNodesOnlyFilterDto.class)) {
      // @formatter:off
      HyperMapAsserter.asserter()
        .processInstanceCount(2L)
        .processInstanceCountWithoutFilters(2L)
        .measure(ViewProperty.DURATION, AggregationType.AVERAGE, getUserTaskDurationTime())
          .groupByContains(USER_TASK_1)
            .distributedByContains(FIRST_CANDIDATE_GROUP_ID, 500., FIRST_CANDIDATE_GROUP_NAME)
          .groupByContains(USER_TASK_2)
            .distributedByContains(FIRST_CANDIDATE_GROUP_ID, 500., FIRST_CANDIDATE_GROUP_NAME)
        .doAssert(result);
      // @formatter:on
    } else if (isSingleFilterOfType(processFilter, CompletedOrCanceledFlowNodesOnlyFilterDto.class)) {
      // @formatter:off
      HyperMapAsserter.asserter()
        .processInstanceCount(2L)
        .processInstanceCountWithoutFilters(2L)
        .measure(ViewProperty.DURATION, AggregationType.AVERAGE, getUserTaskDurationTime())
          .groupByContains(USER_TASK_1)
            .distributedByContains(FIRST_CANDIDATE_GROUP_ID, 100., FIRST_CANDIDATE_GROUP_NAME)
        .doAssert(result);
      // @formatter:on
    } else if (isSingleFilterOfType(processFilter, CanceledFlowNodesOnlyFilterDto.class)) {
      // @formatter:off
      HyperMapAsserter.asserter()
        .processInstanceCount(2L)
        .processInstanceCountWithoutFilters(2L)
        .measure(ViewProperty.DURATION, AggregationType.AVERAGE, getUserTaskDurationTime())
          .groupByContains(USER_TASK_1)
            .distributedByContains(FIRST_CANDIDATE_GROUP_ID, 700., FIRST_CANDIDATE_GROUP_NAME)
          .groupByContains(USER_TASK_2)
            .distributedByContains(FIRST_CANDIDATE_GROUP_ID, 700., FIRST_CANDIDATE_GROUP_NAME)
        .doAssert(result);
      // @formatter:on
    } else {
      fail("No assertions for execution state: " + processFilter);
    }
  }

  @Override
  protected void assertHyperMap_ForOneProcessWithUnassignedTasks(final ReportHyperMapResultDto actualResult) {
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .processInstanceCountWithoutFilters(1L)
      .measure(ViewProperty.DURATION, AggregationType.AVERAGE, getUserTaskDurationTime())
        .groupByContains(USER_TASK_1)
          .distributedByContains(
            FIRST_CANDIDATE_GROUP_ID,
            calculateExpectedValueGivenDurationsDefaultAggr(SET_DURATIONS[1]),
            FIRST_CANDIDATE_GROUP_NAME
          )
          .distributedByContains(DISTRIBUTE_BY_IDENTITY_MISSING_KEY, null, getLocalisedUnassignedLabel())
        .groupByContains(USER_TASK_2)
          .distributedByContains(FIRST_CANDIDATE_GROUP_ID, null, FIRST_CANDIDATE_GROUP_NAME)
          .distributedByContains(DISTRIBUTE_BY_IDENTITY_MISSING_KEY, null, getLocalisedUnassignedLabel())
        .groupByContains(USER_TASK_A)
          .distributedByContains(
            FIRST_CANDIDATE_GROUP_ID,
            calculateExpectedValueGivenDurationsDefaultAggr(SET_DURATIONS[1]),
            FIRST_CANDIDATE_GROUP_NAME
          )
          .distributedByContains(DISTRIBUTE_BY_IDENTITY_MISSING_KEY, null, getLocalisedUnassignedLabel())
        .groupByContains(USER_TASK_B)
          .distributedByContains(FIRST_CANDIDATE_GROUP_ID, null, FIRST_CANDIDATE_GROUP_NAME)
          .distributedByContains(DISTRIBUTE_BY_IDENTITY_MISSING_KEY, null, getLocalisedUnassignedLabel())
      .doAssert(actualResult);
    // @formatter:on
  }

  @Override
  protected void assertHyperMap_ForSeveralProcesses(final ReportHyperMapResultDto actualResult) {
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .processInstanceCountWithoutFilters(2L)
      .measure(ViewProperty.DURATION, AggregationType.AVERAGE, getUserTaskDurationTime())
        .groupByContains(USER_TASK_1)
          .distributedByContains(
            FIRST_CANDIDATE_GROUP_ID,
            calculateExpectedValueGivenDurationsDefaultAggr(SET_DURATIONS),
            FIRST_CANDIDATE_GROUP_NAME
          )
          .distributedByContains(SECOND_CANDIDATE_GROUP_ID, null, SECOND_CANDIDATE_GROUP_NAME)
          .distributedByContains(DISTRIBUTE_BY_IDENTITY_MISSING_KEY, null, getLocalisedUnassignedLabel())
        .groupByContains(USER_TASK_2)
          .distributedByContains(FIRST_CANDIDATE_GROUP_ID, null, FIRST_CANDIDATE_GROUP_NAME)
          .distributedByContains(
            SECOND_CANDIDATE_GROUP_ID,
            calculateExpectedValueGivenDurationsDefaultAggr(SET_DURATIONS[0]),
            SECOND_CANDIDATE_GROUP_NAME
          )
          .distributedByContains(DISTRIBUTE_BY_IDENTITY_MISSING_KEY, null, getLocalisedUnassignedLabel())
        .groupByContains(USER_TASK_A)
          .distributedByContains(
            FIRST_CANDIDATE_GROUP_ID,
            calculateExpectedValueGivenDurationsDefaultAggr(SET_DURATIONS),
            FIRST_CANDIDATE_GROUP_NAME
          )
          .distributedByContains(SECOND_CANDIDATE_GROUP_ID, null, SECOND_CANDIDATE_GROUP_NAME)
          .distributedByContains(DISTRIBUTE_BY_IDENTITY_MISSING_KEY, null, getLocalisedUnassignedLabel())
        .groupByContains(USER_TASK_B)
          .distributedByContains(FIRST_CANDIDATE_GROUP_ID, null, FIRST_CANDIDATE_GROUP_NAME)
          .distributedByContains(
            SECOND_CANDIDATE_GROUP_ID,
            calculateExpectedValueGivenDurationsDefaultAggr(SET_DURATIONS[0]),
            SECOND_CANDIDATE_GROUP_NAME
          )
          .distributedByContains(DISTRIBUTE_BY_IDENTITY_MISSING_KEY, null, getLocalisedUnassignedLabel())
      .doAssert(actualResult);
    // @formatter:on
  }

  @Override
  protected void assertHyperMap_ForSeveralProcessesWithAllAggregationTypes(
    final Map<AggregationType, ReportHyperMapResultDto> actualResults,
    final AggregationType aggType) {
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .processInstanceCountWithoutFilters(2L)
      .measure(ViewProperty.DURATION, aggType, getUserTaskDurationTime())
        .groupByContains(USER_TASK_1)
          .distributedByContains(
            FIRST_CANDIDATE_GROUP_ID,
            calculateExpectedValueGivenDurations(SET_DURATIONS).get(aggType),
            FIRST_CANDIDATE_GROUP_NAME
          )
          .distributedByContains(SECOND_CANDIDATE_GROUP_ID, null, SECOND_CANDIDATE_GROUP_NAME)
          .distributedByContains(DISTRIBUTE_BY_IDENTITY_MISSING_KEY, null, getLocalisedUnassignedLabel())
        .groupByContains(USER_TASK_2)
          .distributedByContains(FIRST_CANDIDATE_GROUP_ID, null, FIRST_CANDIDATE_GROUP_NAME)
          .distributedByContains(
            SECOND_CANDIDATE_GROUP_ID,
            calculateExpectedValueGivenDurations(SET_DURATIONS[0]).get(aggType),
            SECOND_CANDIDATE_GROUP_NAME
          )
          .distributedByContains(DISTRIBUTE_BY_IDENTITY_MISSING_KEY, null, getLocalisedUnassignedLabel())
        .groupByContains(USER_TASK_A)
          .distributedByContains(
            FIRST_CANDIDATE_GROUP_ID,
            calculateExpectedValueGivenDurations(SET_DURATIONS).get(aggType),
            FIRST_CANDIDATE_GROUP_NAME
          )
          .distributedByContains(SECOND_CANDIDATE_GROUP_ID, null, SECOND_CANDIDATE_GROUP_NAME)
          .distributedByContains(DISTRIBUTE_BY_IDENTITY_MISSING_KEY, null, getLocalisedUnassignedLabel())
        .groupByContains(USER_TASK_B)
          .distributedByContains(FIRST_CANDIDATE_GROUP_ID, null, FIRST_CANDIDATE_GROUP_NAME)
          .distributedByContains(
            SECOND_CANDIDATE_GROUP_ID,
            calculateExpectedValueGivenDurations(SET_DURATIONS[0]).get(aggType),
            SECOND_CANDIDATE_GROUP_NAME
          )
          .distributedByContains(DISTRIBUTE_BY_IDENTITY_MISSING_KEY, null, getLocalisedUnassignedLabel())
      .doAssert(actualResults.get(aggType));
    // @formatter:on
  }

  @Override
  protected void assertHyperMap_ForMultipleEventsWithAllAggregationTypes(
    final Map<AggregationType, ReportHyperMapResultDto> results, final AggregationType aggType) {
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .processInstanceCountWithoutFilters(2L)
      .measure(ViewProperty.DURATION, aggType, getUserTaskDurationTime())
        .groupByContains(USER_TASK_1)
          .distributedByContains(
            FIRST_CANDIDATE_GROUP_ID,
            calculateExpectedValueGivenDurations(SET_DURATIONS).get(aggType),
            FIRST_CANDIDATE_GROUP_NAME
          )
          .distributedByContains(SECOND_CANDIDATE_GROUP_ID, null, SECOND_CANDIDATE_GROUP_NAME)
          .distributedByContains(DISTRIBUTE_BY_IDENTITY_MISSING_KEY, null, getLocalisedUnassignedLabel())
        .groupByContains(USER_TASK_2)
          .distributedByContains(FIRST_CANDIDATE_GROUP_ID, null, FIRST_CANDIDATE_GROUP_NAME)
          .distributedByContains(
            SECOND_CANDIDATE_GROUP_ID,
            calculateExpectedValueGivenDurations(SET_DURATIONS[0]).get(aggType),
            SECOND_CANDIDATE_GROUP_NAME
          )
          .distributedByContains(DISTRIBUTE_BY_IDENTITY_MISSING_KEY, null, getLocalisedUnassignedLabel())
      .doAssert(results.get(aggType));
    // @formatter:on
  }

  @Override
  protected void assertHyperMap_otherProcessDefinitionsDoNotInfluenceResult(final Double[] setDurations1,
                                                                            final Double[] setDurations2,
                                                                            final ReportHyperMapResultDto result1,
                                                                            final ReportHyperMapResultDto result2) {
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .processInstanceCountWithoutFilters(2L)
      .measure(ViewProperty.DURATION, AggregationType.AVERAGE, getUserTaskDurationTime())
        .groupByContains(USER_TASK_1)
          .distributedByContains(
            FIRST_CANDIDATE_GROUP_ID,
            calculateExpectedValueGivenDurationsDefaultAggr(setDurations1),
            FIRST_CANDIDATE_GROUP_NAME
          )
      .doAssert(result1);

    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .processInstanceCountWithoutFilters(2L)
      .measure(ViewProperty.DURATION, AggregationType.AVERAGE, getUserTaskDurationTime())
        .groupByContains(USER_TASK_1)
          .distributedByContains(
            FIRST_CANDIDATE_GROUP_ID,
            calculateExpectedValueGivenDurationsDefaultAggr(setDurations2[0]),
            FIRST_CANDIDATE_GROUP_NAME
          )
          .distributedByContains(DISTRIBUTE_BY_IDENTITY_MISSING_KEY, null, getLocalisedUnassignedLabel())
      .doAssert(result2);
    // @formatter:on
  }
}
