/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process.single.user_task.duration.groupby.user_task.distributed_by.assignee;

import org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.UserTaskDurationTime;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.CompletedFlowNodesOnlyFilterDto;
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
import static org.camunda.optimize.test.it.extension.EngineIntegrationExtension.DEFAULT_FULLNAME;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;
import static org.camunda.optimize.test.util.DurationAggregationUtil.calculateExpectedValueGivenDurations;
import static org.camunda.optimize.test.util.DurationAggregationUtil.calculateExpectedValueGivenDurationsDefaultAggr;
import static org.camunda.optimize.test.util.ProcessReportDataType.USER_TASK_DURATION_GROUP_BY_USER_TASK_BY_ASSIGNEE;
import static org.junit.jupiter.api.Assertions.fail;

public class UserTaskWorkDurationByUserTaskByAssigneeReportEvaluationIT
  extends AbstractUserTaskDurationByUserTaskByAssigneeReportEvaluationIT {
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
      .setReportDataType(USER_TASK_DURATION_GROUP_BY_USER_TASK_BY_ASSIGNEE)
      .build();
  }

  @Override
  protected void assertEvaluateReportWithFlowNodeStatusFilter(final ReportHyperMapResultDto result,
                                                              final List<ProcessFilterDto<?>> filter) {
    if (isSingleFilterOfType(filter, RunningFlowNodesOnlyFilterDto.class)) {
      // @formatter:off
      HyperMapAsserter.asserter()
        .processInstanceCount(2L)
        .processInstanceCountWithoutFilters(2L)
          .isComplete(true)
          .groupByContains(USER_TASK_1)
            .distributedByContains(DEFAULT_USERNAME, 500., DEFAULT_FULLNAME)
          .groupByContains(USER_TASK_2)
            .distributedByContains(DEFAULT_USERNAME, 500., DEFAULT_FULLNAME)
        .doAssert(result);
      // @formatter:on
    } else if (isSingleFilterOfType(filter, CompletedFlowNodesOnlyFilterDto.class)) {
      // @formatter:off
      HyperMapAsserter.asserter()
        .processInstanceCount(2L)
        .processInstanceCountWithoutFilters(2L)
        .isComplete(true)
          .groupByContains(USER_TASK_1)
            .distributedByContains(DEFAULT_USERNAME, 100., DEFAULT_FULLNAME)
        .doAssert(result);

      // @formatter:on
    } else if (isSingleFilterOfType(filter, CompletedOrCanceledFlowNodesOnlyFilterDto.class)) {
      // @formatter:off
      HyperMapAsserter.asserter()
        .processInstanceCount(2L)
        .processInstanceCountWithoutFilters(2L)
        .isComplete(true)
          .groupByContains(USER_TASK_1)
            .distributedByContains(DEFAULT_USERNAME, 100., DEFAULT_FULLNAME)
        .doAssert(result);
      // @formatter:on
    } else {
      fail("Not a valid flow node status filter for test");
    }
  }

  @Override
  protected void assertHyperMap_ForOneProcessWithUnassignedTasks(final ReportHyperMapResultDto result) {
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .processInstanceCountWithoutFilters(1L)
      .groupByContains(USER_TASK_1)
        .distributedByContains(DEFAULT_USERNAME, calculateExpectedValueGivenDurationsDefaultAggr(SET_DURATIONS[1]), DEFAULT_FULLNAME)
        .distributedByContains(DISTRIBUTE_BY_IDENTITY_MISSING_KEY, null, getLocalisedUnassignedLabel())
      .groupByContains(USER_TASK_2)
        .distributedByContains(DEFAULT_USERNAME, null, DEFAULT_FULLNAME)
        .distributedByContains(DISTRIBUTE_BY_IDENTITY_MISSING_KEY, null, getLocalisedUnassignedLabel())
      .groupByContains(USER_TASK_A)
        .distributedByContains(DEFAULT_USERNAME, calculateExpectedValueGivenDurationsDefaultAggr(SET_DURATIONS[1]), DEFAULT_FULLNAME)
        .distributedByContains(DISTRIBUTE_BY_IDENTITY_MISSING_KEY, null, getLocalisedUnassignedLabel())
      .groupByContains(USER_TASK_B)
        .distributedByContains(DEFAULT_USERNAME, null, DEFAULT_FULLNAME)
        .distributedByContains(DISTRIBUTE_BY_IDENTITY_MISSING_KEY, null, getLocalisedUnassignedLabel())
      .doAssert(result);
    // @formatter:on
  }

  @Override
  protected void assertHyperMap_ForSeveralProcessesWithAllAggregationTypes(
    final Map<AggregationType, ReportHyperMapResultDto> results, final AggregationType aggType) {
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .processInstanceCountWithoutFilters(2L)
      .groupByContains(USER_TASK_1)
        .distributedByContains(DEFAULT_USERNAME, calculateExpectedValueGivenDurations(SET_DURATIONS).get(aggType), DEFAULT_FULLNAME)
        .distributedByContains(SECOND_USER, null, SECOND_USER_FULLNAME)
        .distributedByContains(DISTRIBUTE_BY_IDENTITY_MISSING_KEY, null, getLocalisedUnassignedLabel())
      .groupByContains(USER_TASK_2)
        .distributedByContains(DEFAULT_USERNAME, null, DEFAULT_FULLNAME)
        .distributedByContains(SECOND_USER, calculateExpectedValueGivenDurations(SET_DURATIONS[0]).get(aggType), SECOND_USER_FULLNAME)
        .distributedByContains(DISTRIBUTE_BY_IDENTITY_MISSING_KEY, null, getLocalisedUnassignedLabel())
      .groupByContains(USER_TASK_A)
        .distributedByContains(DEFAULT_USERNAME, calculateExpectedValueGivenDurations(SET_DURATIONS).get(aggType), DEFAULT_FULLNAME)
        .distributedByContains(SECOND_USER, null, SECOND_USER_FULLNAME)
        .distributedByContains(DISTRIBUTE_BY_IDENTITY_MISSING_KEY, null, getLocalisedUnassignedLabel())
      .groupByContains(USER_TASK_B)
        .distributedByContains(DEFAULT_USERNAME, null, DEFAULT_FULLNAME)
        .distributedByContains(SECOND_USER, calculateExpectedValueGivenDurations(SET_DURATIONS[0]).get(aggType), SECOND_USER_FULLNAME)
        .distributedByContains(DISTRIBUTE_BY_IDENTITY_MISSING_KEY, null, getLocalisedUnassignedLabel())
      .doAssert(results.get(aggType));
    // @formatter:on
  }

  @Override
  protected void assertHyperMap_CustomOrderOnResultValueIsApplied(
    final Map<AggregationType, ReportHyperMapResultDto> results, final AggregationType aggType) {
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .processInstanceCountWithoutFilters(2L)
      .groupByContains(USER_TASK_1)
        .distributedByContains(DEFAULT_USERNAME, calculateExpectedValueGivenDurations(SET_DURATIONS).get(aggType), DEFAULT_FULLNAME)
        .distributedByContains(DISTRIBUTE_BY_IDENTITY_MISSING_KEY, null, getLocalisedUnassignedLabel())
        .distributedByContains(SECOND_USER, null, SECOND_USER_FULLNAME)
      .groupByContains(USER_TASK_2)
        .distributedByContains(SECOND_USER, calculateExpectedValueGivenDurations(SET_DURATIONS[0]).get(aggType), SECOND_USER_FULLNAME)
        .distributedByContains(DISTRIBUTE_BY_IDENTITY_MISSING_KEY, null, getLocalisedUnassignedLabel())
        .distributedByContains(DEFAULT_USERNAME, null, DEFAULT_FULLNAME)
      .doAssert(results.get(aggType));
    // @formatter:on
  }

  protected void assertHyperMap_otherProcessDefinitionsDoNotInfluenceResult(final Double[] setDurations1,
                                                                            final Double[] setDurations2,
                                                                            final ReportHyperMapResultDto result1,
                                                                            final ReportHyperMapResultDto result2) {
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .processInstanceCountWithoutFilters(2L)
      .groupByContains(USER_TASK_1)
        .distributedByContains(DEFAULT_USERNAME, calculateExpectedValueGivenDurationsDefaultAggr(setDurations1), DEFAULT_FULLNAME)
      .doAssert(result1);

    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .processInstanceCountWithoutFilters(2L)
      .groupByContains(USER_TASK_1)
        .distributedByContains(DEFAULT_USERNAME, calculateExpectedValueGivenDurationsDefaultAggr(setDurations2[0]), DEFAULT_FULLNAME)
        .distributedByContains(DISTRIBUTE_BY_IDENTITY_MISSING_KEY, null, getLocalisedUnassignedLabel())
      .doAssert(result2);
    // @formatter:on
  }
}
