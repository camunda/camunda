/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.process.single.usertask.duration.groupby.assignee.distributedby.none;

import com.google.common.collect.ImmutableMap;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.UserTaskDurationTime;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.report.util.MapResultUtil;
import org.camunda.optimize.service.util.TemplatedProcessReportDataBuilder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.rest.RestTestConstants.DEFAULT_USERNAME;
import static org.camunda.optimize.test.util.DurationAggregationUtil.calculateExpectedValueGivenDurationsDefaultAggr;
import static org.camunda.optimize.service.util.ProcessReportDataType.USER_TASK_DUR_GROUP_BY_ASSIGNEE;

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
      .setReportDataType(USER_TASK_DUR_GROUP_BY_ASSIGNEE)
      .build();
  }

  @Override
  protected void assertEvaluateReportWithFlowNodeStatusFilter(final ReportResultResponseDto<List<MapResultEntryDto>> result,
                                                              final FlowNodeStateTestValues flowNodeStatusValues) {
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), DEFAULT_USERNAME)
                 .map(MapResultEntryDto::getValue)
                 .orElse(null))
      .isEqualTo(flowNodeStatusValues.getExpectedWorkDurationValues().get(DEFAULT_USERNAME));
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), SECOND_USER)
                 .map(MapResultEntryDto::getValue)
                 .orElse(null))
      .isEqualTo(flowNodeStatusValues.getExpectedWorkDurationValues().get(SECOND_USER));
  }

  @Override
  protected void assertMap_ForOneProcessInstanceWithUnassignedTasks(final double setDuration,
                                                            final ReportResultResponseDto<List<MapResultEntryDto>> result) {
    assertThat(result.getFirstMeasureData()).isNotNull();
    assertThat(result.getFirstMeasureData()).hasSize(1);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), DEFAULT_USERNAME)).isPresent().get()
      .satisfies(mapResultEntryDto -> assertThat(mapResultEntryDto.getValue())
        .withFailMessage(getIncorrectValueForKeyAssertionMsg(DEFAULT_USERNAME))
        .isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(setDuration))
      );
    assertThat(result.getInstanceCount()).isEqualTo(1L);
  }

  @Override
  protected void assertMap_forSeveralProcessInstances(final ReportResultResponseDto<List<MapResultEntryDto>> result) {
    assertThat(result.getFirstMeasureData()).hasSize(2);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), DEFAULT_USERNAME)).isPresent().get()
      .satisfies(mapResultEntryDto -> assertThat(mapResultEntryDto.getValue())
        .withFailMessage(getIncorrectValueForKeyAssertionMsg(DEFAULT_USERNAME))
        .isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(SET_DURATIONS))
      );
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), SECOND_USER)).isPresent().get()
      .satisfies(mapResultEntryDto -> assertThat(mapResultEntryDto.getValue())
        .withFailMessage(getIncorrectValueForKeyAssertionMsg(SECOND_USER))
        .isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(SET_DURATIONS[0]))
      );

    assertThat(result.getInstanceCount()).isEqualTo(2L);
  }

  @Override
  protected void assertMap_ForSeveralProcessesWithAllAggregationTypes(
    final ReportResultResponseDto<List<MapResultEntryDto>> result) {
    assertThat(result.getInstanceCount()).isEqualTo(2L);
    assertDurationMapReportResultsForAllAggregationTypes(
      result,
      ImmutableMap.of(
        DEFAULT_USERNAME, SET_DURATIONS,
        SECOND_USER, new Double[]{SET_DURATIONS[0]}
      )
    );
  }

  @Override
  protected void assertMap_ForMultipleEvents(final ReportResultResponseDto<List<MapResultEntryDto>> result) {
    assertThat(result.getFirstMeasureData()).hasSize(2);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), DEFAULT_USERNAME)).isPresent().get()
      .satisfies(mapResultEntryDto -> assertThat(mapResultEntryDto.getValue())
        .withFailMessage(getIncorrectValueForKeyAssertionMsg(DEFAULT_USERNAME))
        .isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(SET_DURATIONS[0]))
      );
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), SECOND_USER)).isPresent().get()
      .satisfies(mapResultEntryDto -> assertThat(mapResultEntryDto.getValue())
        .withFailMessage(getIncorrectValueForKeyAssertionMsg(SECOND_USER))
        .isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(SET_DURATIONS[1]))
      );
  }

  @Override
  protected void assertMap_ForMultipleEventsWithAllAggregationTypes(
    final ReportResultResponseDto<List<MapResultEntryDto>> result) {
    assertDurationMapReportResultsForAllAggregationTypes(
      result,
      ImmutableMap.of(
        DEFAULT_USERNAME, new Double[]{SET_DURATIONS[0], SET_DURATIONS[0]},
        SECOND_USER, new Double[]{SET_DURATIONS[1]}
      )
    );
  }

  @Override
  protected void assertMap_otherProcessDefinitionsDoNotInfluenceResult(final ReportResultResponseDto<List<MapResultEntryDto>> result1,
                                                                       final ReportResultResponseDto<List<MapResultEntryDto>> result2) {
    assertThat(result1.getFirstMeasureData()).hasSize(1);
    assertThat(MapResultUtil.getEntryForKey(result1.getFirstMeasureData(), DEFAULT_USERNAME)).isPresent().get()
      .satisfies(mapResultEntryDto -> assertThat(mapResultEntryDto.getValue())
        .withFailMessage(getIncorrectValueForKeyAssertionMsg(DEFAULT_USERNAME) + " for result 1")
        .isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(SET_DURATIONS[0]))
      );

    assertThat(result2.getFirstMeasureData()).hasSize(1);
    assertThat(MapResultUtil.getEntryForKey(result2.getFirstMeasureData(), DEFAULT_USERNAME)).isPresent().get()
      .satisfies(mapResultEntryDto -> assertThat(mapResultEntryDto.getValue())
        .withFailMessage(getIncorrectValueForKeyAssertionMsg(DEFAULT_USERNAME) + " for result 2")
        .isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(SET_DURATIONS[1]))
      );
  }

}
