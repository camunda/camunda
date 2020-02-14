/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process.user_task.duration.groupby.user_task.distributed_by.candidate_group;

import org.camunda.optimize.dto.engine.HistoricUserTaskInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.FlowNodeExecutionState;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.UserTaskDurationTime;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.ReportHyperMapResultDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.report.util.HyperMapAsserter;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;

import java.sql.SQLException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.camunda.optimize.test.util.DurationAggregationUtil.calculateExpectedValueGivenDurations;
import static org.camunda.optimize.test.util.DurationAggregationUtil.calculateExpectedValueGivenDurationsDefaultAggr;
import static org.camunda.optimize.test.util.ProcessReportDataType.USER_TASK_DURATION_GROUP_BY_FLOW_NODE_BY_CANDIDATE_GROUP;

public class UserTaskWorkDurationByUserTaskByCandidateGroupReportEvaluationIT
  extends AbstractUserTaskDurationByUserTaskByCandidateGroupReportEvaluationIT {
  @Override
  protected UserTaskDurationTime getUserTaskDurationTime() {
    return UserTaskDurationTime.WORK;
  }

  @Override
  protected void changeDuration(final ProcessInstanceEngineDto processInstanceDto, final long setDuration) {
    engineIntegrationExtension.getHistoricTaskInstances(processInstanceDto.getId())
      .forEach(
        historicUserTaskInstanceDto -> {
          if (historicUserTaskInstanceDto.getEndTime() != null) {
            changeUserClaimTimestamp(
              setDuration,
              historicUserTaskInstanceDto
            );
          }
        }
      );
  }

  @Override
  protected void changeDuration(final ProcessInstanceEngineDto processInstanceDto,
                                final String userTaskKey,
                                final long duration) {
    engineIntegrationExtension.getHistoricTaskInstances(processInstanceDto.getId(), userTaskKey)
      .forEach(
        historicUserTaskInstanceDto -> {
          if (historicUserTaskInstanceDto.getEndTime() != null) {
            changeUserClaimTimestamp(
              duration,
              historicUserTaskInstanceDto
            );
          }
        }
      );
  }

  @Override
  protected ProcessReportDataDto createReport(final String processDefinitionKey, final List<String> versions) {
    return TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersions(versions)
      .setUserTaskDurationTime(UserTaskDurationTime.WORK)
      .setReportDataType(USER_TASK_DURATION_GROUP_BY_FLOW_NODE_BY_CANDIDATE_GROUP)
      .build();
  }

  private void changeUserClaimTimestamp(final long millis,
                                        final HistoricUserTaskInstanceDto historicUserTaskInstanceDto) {
    try {
      engineDatabaseExtension.changeUserTaskAssigneeOperationTimestamp(
        historicUserTaskInstanceDto.getId(),
        historicUserTaskInstanceDto.getEndTime().minus(millis, ChronoUnit.MILLIS)
      );
    } catch (SQLException e) {
      throw new OptimizeIntegrationTestException(e);
    }
  }

  @Override
  protected void assertEvaluateReportWithExecutionState(final ReportHyperMapResultDto result,
                                                        final FlowNodeExecutionState executionState) {
    switch (executionState) {
      case RUNNING:
        // @formatter:off
        HyperMapAsserter.asserter()
          .processInstanceCount(2L)
          .isComplete(true)
          .groupByContains(USER_TASK_1)
          .distributedByContains(FIRST_CANDIDATE_GROUP, 500L)
          .groupByContains(USER_TASK_2)
          .distributedByContains(FIRST_CANDIDATE_GROUP, 500L)
          .doAssert(result);
        // @formatter:on
        break;
      case COMPLETED:
        // @formatter:off
        HyperMapAsserter.asserter()
          .processInstanceCount(2L)
          .isComplete(true)
          .groupByContains(USER_TASK_1)
          .distributedByContains(FIRST_CANDIDATE_GROUP, 100L)
          .groupByContains(USER_TASK_2)
          .distributedByContains(FIRST_CANDIDATE_GROUP, null)
          .doAssert(result);
        // @formatter:on
        break;
      case ALL:
        // @formatter:off
        HyperMapAsserter.asserter()
          .processInstanceCount(2L)
          .isComplete(true)
          .groupByContains(USER_TASK_1)
          .distributedByContains(FIRST_CANDIDATE_GROUP, calculateExpectedValueGivenDurationsDefaultAggr(100L, 500L))
          .groupByContains(USER_TASK_2)
          .distributedByContains(FIRST_CANDIDATE_GROUP, 500L)
          .doAssert(result);
        // @formatter:on
        break;
    }
  }

  @Override
  protected void assertHyperMap_ForOneProcessWithUnassignedTasks(final ReportHyperMapResultDto actualResult) {
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .groupByContains(USER_TASK_1)
        .distributedByContains(FIRST_CANDIDATE_GROUP, calculateExpectedValueGivenDurationsDefaultAggr(SET_DURATIONS[1]))
        .distributedByContains(getLocalisedUnassignedLabel(), null)
      .groupByContains(USER_TASK_2)
        .distributedByContains(FIRST_CANDIDATE_GROUP, null)
        .distributedByContains(getLocalisedUnassignedLabel(), null)
      .groupByContains(USER_TASK_A)
        .distributedByContains(FIRST_CANDIDATE_GROUP, calculateExpectedValueGivenDurationsDefaultAggr(SET_DURATIONS[1]))
        .distributedByContains(getLocalisedUnassignedLabel(), null)
      .groupByContains(USER_TASK_B)
        .distributedByContains(FIRST_CANDIDATE_GROUP, null)
        .distributedByContains(getLocalisedUnassignedLabel(), null)
      .doAssert(actualResult);
    // @formatter:on
  }

  @Override
  protected void assertHyperMap_ForSeveralProcesses(final ReportHyperMapResultDto actualResult) {
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .groupByContains(USER_TASK_1)
        .distributedByContains(FIRST_CANDIDATE_GROUP, calculateExpectedValueGivenDurationsDefaultAggr(SET_DURATIONS))
        .distributedByContains(SECOND_CANDIDATE_GROUP, null)
        .distributedByContains(getLocalisedUnassignedLabel(), null)
      .groupByContains(USER_TASK_2)
        .distributedByContains(FIRST_CANDIDATE_GROUP, null)
        .distributedByContains(SECOND_CANDIDATE_GROUP, calculateExpectedValueGivenDurationsDefaultAggr(SET_DURATIONS[0]))
        .distributedByContains(getLocalisedUnassignedLabel(), null)
      .groupByContains(USER_TASK_A)
        .distributedByContains(FIRST_CANDIDATE_GROUP, calculateExpectedValueGivenDurationsDefaultAggr(SET_DURATIONS))
        .distributedByContains(SECOND_CANDIDATE_GROUP, null)
        .distributedByContains(getLocalisedUnassignedLabel(), null)
      .groupByContains(USER_TASK_B)
        .distributedByContains(FIRST_CANDIDATE_GROUP, null)
        .distributedByContains(SECOND_CANDIDATE_GROUP, calculateExpectedValueGivenDurationsDefaultAggr(SET_DURATIONS[0]))
        .distributedByContains(getLocalisedUnassignedLabel(), null)
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
      .groupByContains(USER_TASK_1)
        .distributedByContains(FIRST_CANDIDATE_GROUP, calculateExpectedValueGivenDurations(SET_DURATIONS).get(aggType))
        .distributedByContains(SECOND_CANDIDATE_GROUP, null)
        .distributedByContains(getLocalisedUnassignedLabel(), null)
      .groupByContains(USER_TASK_2)
        .distributedByContains(FIRST_CANDIDATE_GROUP, null)
        .distributedByContains(SECOND_CANDIDATE_GROUP, calculateExpectedValueGivenDurations(SET_DURATIONS[0]).get(aggType))
        .distributedByContains(getLocalisedUnassignedLabel(), null)
      .groupByContains(USER_TASK_A)
        .distributedByContains(FIRST_CANDIDATE_GROUP, calculateExpectedValueGivenDurations(SET_DURATIONS).get(aggType))
        .distributedByContains(SECOND_CANDIDATE_GROUP, null)
        .distributedByContains(getLocalisedUnassignedLabel(), null)
      .groupByContains(USER_TASK_B)
        .distributedByContains(FIRST_CANDIDATE_GROUP, null)
        .distributedByContains(SECOND_CANDIDATE_GROUP, calculateExpectedValueGivenDurations(SET_DURATIONS[0]).get(aggType))
        .distributedByContains(getLocalisedUnassignedLabel(), null)
      .doAssert(actualResults.get(aggType));
    // @formatter:on
  }

  @Override
  protected void assertHyperMap_ForMultipleEventsWithAllAggregationTypes(
    final Map<AggregationType, ReportHyperMapResultDto> results, final AggregationType aggType) {
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .groupByContains(USER_TASK_1)
        .distributedByContains(FIRST_CANDIDATE_GROUP, calculateExpectedValueGivenDurations(SET_DURATIONS).get(aggType))
        .distributedByContains(SECOND_CANDIDATE_GROUP, null)
        .distributedByContains(getLocalisedUnassignedLabel(), null)
      .groupByContains(USER_TASK_2)
        .distributedByContains(FIRST_CANDIDATE_GROUP, null)
        .distributedByContains(SECOND_CANDIDATE_GROUP, calculateExpectedValueGivenDurations(SET_DURATIONS[0]).get(aggType))
        .distributedByContains(getLocalisedUnassignedLabel(), null)
      .doAssert(results.get(aggType));
    // @formatter:on
  }

  @Override
  protected void assertHyperMap_CustomOrderOnResultValueIsApplied(
    final Map<AggregationType, ReportHyperMapResultDto> results, final AggregationType aggType) {
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .groupByContains(USER_TASK_1)
        .distributedByContains(FIRST_CANDIDATE_GROUP, calculateExpectedValueGivenDurations(SET_DURATIONS).get(aggType))
        .distributedByContains(SECOND_CANDIDATE_GROUP, null)
        .distributedByContains(getLocalisedUnassignedLabel(), null)
      .groupByContains(USER_TASK_2)
        .distributedByContains(SECOND_CANDIDATE_GROUP, calculateExpectedValueGivenDurations(SET_DURATIONS[0]).get(aggType))
        .distributedByContains(getLocalisedUnassignedLabel(), null)
        .distributedByContains(FIRST_CANDIDATE_GROUP,null)
      .doAssert(results.get(aggType));
    // @formatter:on
  }

  @Override
  protected void assertHyperMap_otherProcessDefinitionsDoNotInfluenceResult(final Long[] setDurations1,
                                                                            final Long[] setDurations2,
                                                                            final ReportHyperMapResultDto result1,
                                                                            final ReportHyperMapResultDto result2) {
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .groupByContains(USER_TASK_1)
        .distributedByContains(FIRST_CANDIDATE_GROUP, calculateExpectedValueGivenDurationsDefaultAggr(setDurations1))
      .doAssert(result1);

    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .groupByContains(USER_TASK_1)
        .distributedByContains(FIRST_CANDIDATE_GROUP, calculateExpectedValueGivenDurationsDefaultAggr(setDurations2[0]))
        .distributedByContains(getLocalisedUnassignedLabel(), null)
      .doAssert(result2);
    // @formatter:on
  }
}
