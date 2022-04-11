/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.process.single.usertask.duration.groupby.candidategroup.distributedby.none;

import com.google.common.collect.ImmutableMap;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.UserTaskDurationTime;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.report.util.MapResultUtil;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;
import static org.camunda.optimize.test.util.DurationAggregationUtil.calculateExpectedValueGivenDurationsDefaultAggr;
import static org.camunda.optimize.test.util.ProcessReportDataType.USER_TASK_DUR_GROUP_BY_CANDIDATE;

public class UserTaskWorkDurationByCandidateGroupReportEvaluationIT
  extends AbstractUserTaskDurationByCandidateGroupReportEvaluationIT {

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
      .setReportDataType(USER_TASK_DUR_GROUP_BY_CANDIDATE)
      .build();
  }

  @Override
  protected void assertEvaluateReportWithFlowNodeStatusFilter(final ReportResultResponseDto<List<MapResultEntryDto>> result,
                                                              final FlowNodeStatusTestValues expectedValues) {
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), FIRST_CANDIDATE_GROUP_ID).map(MapResultEntryDto::getValue).orElse(null))
      .isEqualTo(expectedValues.getExpectedWorkDurationValues().get(FIRST_CANDIDATE_GROUP_ID));
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), SECOND_CANDIDATE_GROUP_ID).map(MapResultEntryDto::getValue).orElse(null))
      .isEqualTo(expectedValues.getExpectedWorkDurationValues().get(SECOND_CANDIDATE_GROUP_ID));
  }

  @Override
  protected void assertMap_ForOneProcessInstanceWithUnassignedTasks(final Double setDuration, final ReportResultResponseDto<List<MapResultEntryDto>> result) {
    assertThat(result.getFirstMeasureData()).hasSize(1);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), FIRST_CANDIDATE_GROUP_ID)).isPresent().get()
      .satisfies(mapResultEntryDto -> assertThat(mapResultEntryDto.getValue())
        .withFailMessage(getIncorrectValueForKeyAssertionMsg(FIRST_CANDIDATE_GROUP_ID))
        .isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(setDuration))
      );
    assertThat(result.getInstanceCount()).isEqualTo(1);
  }

  @Override
  protected void assertMap_ForSeveralProcessesInstances(final ReportResultResponseDto<List<MapResultEntryDto>> result) {
    assertThat(result.getFirstMeasureData()).hasSize(2);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), FIRST_CANDIDATE_GROUP_ID)).isPresent().get()
      .satisfies(mapResultEntryDto -> assertThat(mapResultEntryDto.getValue())
        .withFailMessage(getIncorrectValueForKeyAssertionMsg(FIRST_CANDIDATE_GROUP_ID))
        .isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(SET_DURATIONS))
      );
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), SECOND_CANDIDATE_GROUP_ID)).isPresent().get()
      .satisfies(mapResultEntryDto -> assertThat(mapResultEntryDto.getValue())
        .withFailMessage(getIncorrectValueForKeyAssertionMsg(SECOND_CANDIDATE_GROUP_ID))
        .isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(SET_DURATIONS[0]))
      );
    assertThat(result.getInstanceCount()).isEqualTo(2);
  }

  @Override
  protected void assertMap_ForSeveralProcessesWithAllAggregationTypes(
    final ReportResultResponseDto<List<MapResultEntryDto>> result) {
    assertThat(result.getInstanceCount()).isEqualTo(2);
    assertDurationMapReportResults(
      result,
      ImmutableMap.of(
        FIRST_CANDIDATE_GROUP_ID, SET_DURATIONS,
        SECOND_CANDIDATE_GROUP_ID, new Double[]{SET_DURATIONS[0]}
      )
    );
  }

  @Override
  protected void assertMap_ForMultipleEvents(final ReportResultResponseDto<List<MapResultEntryDto>> result) {
    assertThat(result.getFirstMeasureData()).hasSize(2);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), FIRST_CANDIDATE_GROUP_ID)).isPresent().get()
      .satisfies(mapResultEntryDto -> assertThat(mapResultEntryDto.getValue())
        .withFailMessage(getIncorrectValueForKeyAssertionMsg(FIRST_CANDIDATE_GROUP_ID))
        .isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(SET_DURATIONS[0]))
      );
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), SECOND_CANDIDATE_GROUP_ID)).isPresent().get()
      .satisfies(mapResultEntryDto -> assertThat(mapResultEntryDto.getValue())
        .withFailMessage(getIncorrectValueForKeyAssertionMsg(SECOND_CANDIDATE_GROUP_ID))
        .isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(SET_DURATIONS[1]))
      );
  }

  @Override
  protected void assertMap_ForMultipleEventsWithAllAggregationTypes(
    final ReportResultResponseDto<List<MapResultEntryDto>> result) {
    assertDurationMapReportResults(
      result,
      ImmutableMap.of(
        FIRST_CANDIDATE_GROUP_ID, new Double[]{SET_DURATIONS[0], SET_DURATIONS[0]},
        SECOND_CANDIDATE_GROUP_ID, new Double[]{SET_DURATIONS[1]}
      )
    );
  }

  @Override
  protected void assertMap_otherProcessDefinitionsDoNotInfluenceResult(final ReportResultResponseDto<List<MapResultEntryDto>> result1,
                                                                       final ReportResultResponseDto<List<MapResultEntryDto>> result2) {
    assertThat(result1.getFirstMeasureData()).hasSize(1);
    assertThat(MapResultUtil.getEntryForKey(result1.getFirstMeasureData(), FIRST_CANDIDATE_GROUP_ID)).isPresent().get()
      .satisfies(mapResultEntryDto -> assertThat(mapResultEntryDto.getValue())
        .withFailMessage(getIncorrectValueForKeyAssertionMsg(DEFAULT_USERNAME) + " in result 1")
        .isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(SET_DURATIONS[0]))
      );

    assertThat(result2.getFirstMeasureData()).hasSize(1);
    assertThat(MapResultUtil.getEntryForKey(result2.getFirstMeasureData(), FIRST_CANDIDATE_GROUP_ID)).isPresent().get()
      .satisfies(mapResultEntryDto -> assertThat(mapResultEntryDto.getValue())
        .withFailMessage(getIncorrectValueForKeyAssertionMsg(FIRST_CANDIDATE_GROUP_ID) + " in result 2")
        .isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(SET_DURATIONS[1]))
      );
  }

  @Override
  protected void assertCustomOrderOnResultValueIsApplied(ReportResultResponseDto<List<MapResultEntryDto>> result) {
    assertThat(result.getFirstMeasureData()).hasSize(2);
    assertCorrectValueOrdering(result);
  }
}
