/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.process.single.usertask.duration.groupby.usertask.distributedby.assignee;

import org.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.UserTaskDurationTime;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.CompletedFlowNodesOnlyFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.CompletedOrCanceledFlowNodesOnlyFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.RunningFlowNodesOnlyFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.HyperMapResultEntryDto;
import org.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
import org.camunda.optimize.dto.optimize.rest.report.measure.MeasureResponseDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.report.util.HyperMapAsserter;
import org.camunda.optimize.service.util.TemplatedProcessReportDataBuilder;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.rest.RestTestConstants.DEFAULT_USERNAME;
import static org.camunda.optimize.service.es.report.command.modules.distributed_by.process.identity.ProcessDistributedByIdentity.DISTRIBUTE_BY_IDENTITY_MISSING_KEY;
import static org.camunda.optimize.test.it.extension.EngineIntegrationExtension.DEFAULT_FULLNAME;
import static org.camunda.optimize.test.util.DurationAggregationUtil.calculateExpectedValueGivenDurations;
import static org.camunda.optimize.test.util.DurationAggregationUtil.calculateExpectedValueGivenDurationsDefaultAggr;
import static org.camunda.optimize.test.util.DurationAggregationUtil.getSupportedAggregationTypes;
import static org.camunda.optimize.service.util.ProcessReportDataType.USER_TASK_DUR_GROUP_BY_USER_TASK_BY_ASSIGNEE;
import static org.junit.jupiter.api.Assertions.fail;

public class UserTaskWorkDurationByUserTaskByAssigneeReportEvaluationIT
  extends AbstractUserTaskDurationByUserTaskByAssigneeReportEvaluationIT {
//  protected static final Double UNASSIGNED_TASK_DURATION = null;

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
      .setReportDataType(USER_TASK_DUR_GROUP_BY_USER_TASK_BY_ASSIGNEE)
      .build();
  }

  @Override
  protected void assertEvaluateReportWithFlowNodeStatusFilter(final ReportResultResponseDto<List<HyperMapResultEntryDto>> result,
                                                              final List<ProcessFilterDto<?>> filter,
                                                              final long expectedInstanceCount) {
    if (isSingleFilterOfType(filter, RunningFlowNodesOnlyFilterDto.class)) {
      // @formatter:off
      HyperMapAsserter.asserter()
        .processInstanceCount(expectedInstanceCount)
        .processInstanceCountWithoutFilters(2L)
        .measure(ViewProperty.DURATION, AggregationType.AVERAGE, getUserTaskDurationTime())
          .groupByContains(USER_TASK_1)
            .distributedByContains(DEFAULT_USERNAME, 500., DEFAULT_FULLNAME)
          .groupByContains(USER_TASK_2)
            .distributedByContains(DEFAULT_USERNAME, 500., DEFAULT_FULLNAME)
        .doAssert(result);
      // @formatter:on
    } else if (isSingleFilterOfType(filter, CompletedFlowNodesOnlyFilterDto.class)) {
      // @formatter:off
      HyperMapAsserter.asserter()
        .processInstanceCount(expectedInstanceCount)
        .processInstanceCountWithoutFilters(2L)
        .measure(ViewProperty.DURATION, AggregationType.AVERAGE, getUserTaskDurationTime())
          .groupByContains(USER_TASK_1)
            .distributedByContains(DEFAULT_USERNAME, 100., DEFAULT_FULLNAME)
        .doAssert(result);

      // @formatter:on
    } else if (isSingleFilterOfType(filter, CompletedOrCanceledFlowNodesOnlyFilterDto.class)) {
      // @formatter:off
      HyperMapAsserter.asserter()
        .processInstanceCount(expectedInstanceCount)
        .processInstanceCountWithoutFilters(2L)
        .measure(ViewProperty.DURATION, AggregationType.AVERAGE, getUserTaskDurationTime())
          .groupByContains(USER_TASK_1)
            .distributedByContains(DEFAULT_USERNAME, 100., DEFAULT_FULLNAME)
        .doAssert(result);
      // @formatter:on
    } else {
      fail("Not a valid flow node status filter for test");
    }
  }

  @Override
  protected void assertHyperMap_forOneProcessInstanceWithUnassignedTasks(final ReportResultResponseDto<List<HyperMapResultEntryDto>>result) {
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .processInstanceCountWithoutFilters(1L)
      .measure(ViewProperty.DURATION, AggregationType.AVERAGE, getUserTaskDurationTime())
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
  protected void assertHyperMap_ForSeveralProcessInstancesWithAllAggregationTypes(
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> result) {
    assertThat(result.getMeasures())
      .extracting(MeasureResponseDto::getAggregationType)
      .containsExactly(getSupportedAggregationTypes());
    final HyperMapAsserter hyperMapAsserter = HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .processInstanceCountWithoutFilters(2L);
    Arrays.stream(getSupportedAggregationTypes()).forEach(aggType -> {
      // @formatter:off
      hyperMapAsserter
        .measure(ViewProperty.DURATION, aggType, getUserTaskDurationTime())
          .groupByContains(USER_TASK_1)
            .distributedByContains(DEFAULT_USERNAME, calculateExpectedValueGivenDurations(SET_DURATIONS).get(aggType), DEFAULT_FULLNAME)
            .distributedByContains(SECOND_USER, null, SECOND_USER_FULL_NAME)
            .distributedByContains(DISTRIBUTE_BY_IDENTITY_MISSING_KEY, null, getLocalisedUnassignedLabel())
          .groupByContains(USER_TASK_2)
            .distributedByContains(DEFAULT_USERNAME, null, DEFAULT_FULLNAME)
            .distributedByContains(SECOND_USER, calculateExpectedValueGivenDurations(SET_DURATIONS[0]).get(aggType), SECOND_USER_FULL_NAME)
            .distributedByContains(DISTRIBUTE_BY_IDENTITY_MISSING_KEY, null, getLocalisedUnassignedLabel())
          .groupByContains(USER_TASK_A)
            .distributedByContains(DEFAULT_USERNAME, calculateExpectedValueGivenDurations(SET_DURATIONS).get(aggType), DEFAULT_FULLNAME)
            .distributedByContains(SECOND_USER, null, SECOND_USER_FULL_NAME)
            .distributedByContains(DISTRIBUTE_BY_IDENTITY_MISSING_KEY, null, getLocalisedUnassignedLabel())
          .groupByContains(USER_TASK_B)
            .distributedByContains(DEFAULT_USERNAME, null, DEFAULT_FULLNAME)
            .distributedByContains(SECOND_USER, calculateExpectedValueGivenDurations(SET_DURATIONS[0]).get(aggType), SECOND_USER_FULL_NAME)
            .distributedByContains(DISTRIBUTE_BY_IDENTITY_MISSING_KEY, null, getLocalisedUnassignedLabel())
          .add()
        .add();
      // @formatter:on
    });
    hyperMapAsserter.doAssert(result);
  }

  protected void assertHyperMap_otherProcessDefinitionsDoNotInfluenceResult(final Double[] setDurations1,
                                                                            final Double[] setDurations2,
                                                                            final ReportResultResponseDto<List<HyperMapResultEntryDto>>result1,
                                                                            final ReportResultResponseDto<List<HyperMapResultEntryDto>>result2) {
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .processInstanceCountWithoutFilters(2L)
      .measure(ViewProperty.DURATION, AggregationType.AVERAGE, getUserTaskDurationTime())
        .groupByContains(USER_TASK_1)
          .distributedByContains(DEFAULT_USERNAME, calculateExpectedValueGivenDurationsDefaultAggr(setDurations1), DEFAULT_FULLNAME)
      .doAssert(result1);

    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .processInstanceCountWithoutFilters(2L)
      .measure(ViewProperty.DURATION, AggregationType.AVERAGE, getUserTaskDurationTime())
        .groupByContains(USER_TASK_1)
          .distributedByContains(DEFAULT_USERNAME, calculateExpectedValueGivenDurationsDefaultAggr(setDurations2[0]), DEFAULT_FULLNAME)
          .distributedByContains(DISTRIBUTE_BY_IDENTITY_MISSING_KEY, null, getLocalisedUnassignedLabel())
      .doAssert(result2);
    // @formatter:on
  }
}
