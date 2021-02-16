/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process.single.user_task.duration.groupby.assignee.distributed_by.none;

import com.google.common.collect.ImmutableMap;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.UserTaskDurationTime;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.ReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType.MIN;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;
import static org.camunda.optimize.test.util.DurationAggregationUtil.calculateExpectedValueGivenDurationsDefaultAggr;
import static org.camunda.optimize.test.util.ProcessReportDataType.USER_TASK_DURATION_GROUP_BY_ASSIGNEE;

public class UserTaskWorkDurationByAssigneeReportEvaluationIT
  extends AbstractUserTaskDurationByAssigneeReportEvaluationIT {

  @Override
  protected UserTaskDurationTime getUserTaskDurationTime() {
    return UserTaskDurationTime.WORK;
  }

  @Override
  protected void changeDuration(final ProcessInstanceEngineDto processInstanceDto, final Number durationInMs) {
    changeUserTaskWorkDuration(processInstanceDto, durationInMs);
  }

  @Override
  protected void changeDuration(final ProcessInstanceEngineDto processInstanceDto,
                                final String userTaskKey,
                                final Number durationInMs) {
    changeUserTaskWorkDuration(processInstanceDto, userTaskKey, durationInMs);
  }

  @Override
  protected ProcessReportDataDto createReport(final String processDefinitionKey, final List<String> versions) {
    return TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersions(versions)
      .setUserTaskDurationTime(UserTaskDurationTime.WORK)
      .setReportDataType(USER_TASK_DURATION_GROUP_BY_ASSIGNEE)
      .build();
  }

  @Override
  protected void assertEvaluateReportWithFlowNodeStatusFilter(final ReportMapResultDto result,
                                                              final FlowNodeStateTestValues flowNodeStatusValues) {
    assertThat(result.getEntryForKey(DEFAULT_USERNAME).map(MapResultEntryDto::getValue).orElse(null))
      .isEqualTo(flowNodeStatusValues.getExpectedWorkDurationValues().get(DEFAULT_USERNAME));
    assertThat(result.getEntryForKey(SECOND_USER).map(MapResultEntryDto::getValue).orElse(null))
      .isEqualTo(flowNodeStatusValues.getExpectedWorkDurationValues().get(SECOND_USER));
  }

  @Override
  protected void assertMap_ForOneProcessWithUnassignedTasks(final double setDuration, final ReportMapResultDto result) {
    assertThat(result.getFirstMeasureData()).isNotNull();
    assertThat(result.getFirstMeasureData()).hasSize(1);
    assertThat(result.getEntryForKey(DEFAULT_USERNAME)).isPresent().get()
      .satisfies(mapResultEntryDto -> assertThat(mapResultEntryDto.getValue())
        .withFailMessage(getIncorrectValueForKeyAssertionMsg(DEFAULT_USERNAME))
        .isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(setDuration))
      );
    assertThat(result.getInstanceCount()).isEqualTo(1L);
  }

  @Override
  protected void assertMap_ForSeveralProcesses(final ReportMapResultDto result) {
    assertThat(result.getFirstMeasureData()).hasSize(2);
    assertThat(result.getEntryForKey(DEFAULT_USERNAME)).isPresent().get()
      .satisfies(mapResultEntryDto -> assertThat(mapResultEntryDto.getValue())
        .withFailMessage(getIncorrectValueForKeyAssertionMsg(DEFAULT_USERNAME))
        .isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(SET_DURATIONS))
      );
    assertThat(result.getEntryForKey(SECOND_USER)).isPresent().get()
      .satisfies(mapResultEntryDto -> assertThat(mapResultEntryDto.getValue())
        .withFailMessage(getIncorrectValueForKeyAssertionMsg(SECOND_USER))
        .isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(SET_DURATIONS[0]))
      );

    assertThat(result.getInstanceCount()).isEqualTo(2L);
  }

  @Override
  protected void assertMap_ForSeveralProcessesWithAllAggregationTypes(final Map<AggregationType, ReportMapResultDto> results) {
    assertDurationMapReportResults(
      results,
      ImmutableMap.of(
        DEFAULT_USERNAME, SET_DURATIONS,
        SECOND_USER, new Double[]{SET_DURATIONS[0]}
      )
    );
    assertThat(results.get(MIN).getInstanceCount()).isEqualTo(2L);
  }

  @Override
  protected void assertMap_ForMultipleEvents(final ReportMapResultDto result) {
    assertThat(result.getFirstMeasureData()).hasSize(2);
    assertThat(result.getEntryForKey(DEFAULT_USERNAME)).isPresent().get()
      .satisfies(mapResultEntryDto -> assertThat(mapResultEntryDto.getValue())
        .withFailMessage(getIncorrectValueForKeyAssertionMsg(DEFAULT_USERNAME))
        .isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(SET_DURATIONS[0]))
      );
    assertThat(result.getEntryForKey(SECOND_USER)).isPresent().get()
      .satisfies(mapResultEntryDto -> assertThat(mapResultEntryDto.getValue())
        .withFailMessage(getIncorrectValueForKeyAssertionMsg(SECOND_USER))
        .isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(SET_DURATIONS[1]))
      );
  }

  @Override
  protected void assertMap_ForMultipleEventsWithAllAggregationTypes(final Map<AggregationType, ReportMapResultDto> results) {
    assertDurationMapReportResults(
      results,
      ImmutableMap.of(
        DEFAULT_USERNAME, new Double[]{SET_DURATIONS[0]},
        SECOND_USER, new Double[]{SET_DURATIONS[1]}
      )
    );
  }

  @Override
  protected void assertMap_otherProcessDefinitionsDoNotInfluenceResult(final ReportMapResultDto result1,
                                                                       final ReportMapResultDto result2) {
    assertThat(result1.getFirstMeasureData()).hasSize(1);
    assertThat(result1.getEntryForKey(DEFAULT_USERNAME)).isPresent().get()
      .satisfies(mapResultEntryDto -> assertThat(mapResultEntryDto.getValue())
        .withFailMessage(getIncorrectValueForKeyAssertionMsg(DEFAULT_USERNAME) + " for result 1")
        .isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(SET_DURATIONS[0]))
      );

    assertThat(result2.getFirstMeasureData()).hasSize(1);
    assertThat(result2.getEntryForKey(DEFAULT_USERNAME)).isPresent().get()
      .satisfies(mapResultEntryDto -> assertThat(mapResultEntryDto.getValue())
        .withFailMessage(getIncorrectValueForKeyAssertionMsg(DEFAULT_USERNAME) + " for result 2")
        .isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(SET_DURATIONS[1]))
      );
  }

  @Override
  protected void assertCustomOrderOnResultValueIsApplied(ReportMapResultDto result) {
    assertThat(result.getFirstMeasureData()).hasSize(2);
    assertCorrectValueOrdering(result);
  }

}
