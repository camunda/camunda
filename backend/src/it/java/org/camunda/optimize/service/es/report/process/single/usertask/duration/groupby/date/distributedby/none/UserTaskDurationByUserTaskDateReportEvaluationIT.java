/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process.single.usertask.duration.groupby.date.distributedby.none;

import com.google.common.collect.ImmutableList;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.UserTaskDurationTime;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DurationFilterUnit;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.MembershipFilterOperator;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.ComparisonOperator;
import org.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.data.DurationFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto;
import org.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import org.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
import org.camunda.optimize.dto.optimize.rest.report.measure.MeasureResponseDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.report.process.single.ModelElementDurationByModelElementDateReportEvaluationIT;
import org.camunda.optimize.service.es.report.util.MapResultUtil;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.ComparisonOperator.GREATER_THAN;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.ComparisonOperator.GREATER_THAN_EQUALS;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.ComparisonOperator.LESS_THAN;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.ComparisonOperator.LESS_THAN_EQUALS;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.MembershipFilterOperator.IN;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.MembershipFilterOperator.NOT_IN;
import static org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto.SORT_BY_KEY;
import static org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto.SORT_BY_VALUE;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_PASSWORD;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;
import static org.camunda.optimize.test.util.DateModificationHelper.truncateToStartOfUnit;
import static org.camunda.optimize.test.util.DurationAggregationUtil.calculateExpectedValueGivenDurations;
import static org.camunda.optimize.test.util.DurationAggregationUtil.getSupportedAggregationTypes;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION;
import static org.camunda.optimize.util.SuppressionConstants.SAME_PARAM_VALUE;

public abstract class UserTaskDurationByUserTaskDateReportEvaluationIT
  extends ModelElementDurationByModelElementDateReportEvaluationIT {

  @Test
  public void evaluateReportForMultipleEventsWithAllAggregationTypes() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();
    final OffsetDateTime today = OffsetDateTime.now();

    final ProcessInstanceEngineDto processInstanceDto1 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    finishAllUserTasks(processInstanceDto1);
    finishAllUserTasks(processInstanceDto1);
    changeModelElementDate(processInstanceDto1, USER_TASK_1, today);
    changeModelElementDate(processInstanceDto1, USER_TASK_2, today.minusDays(1));
    changeDuration(processInstanceDto1, USER_TASK_1, 10.);
    changeDuration(processInstanceDto1, USER_TASK_2, 10.);

    final ProcessInstanceEngineDto processInstanceDto2 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    finishAllUserTasks(processInstanceDto2);
    finishAllUserTasks(processInstanceDto2);
    changeModelElementDate(processInstanceDto2, USER_TASK_1, today);
    changeModelElementDate(processInstanceDto2, USER_TASK_2, today.minusDays(1));
    changeDuration(processInstanceDto2, USER_TASK_1, 20.);
    changeDuration(processInstanceDto2, USER_TASK_2, 20.);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createGroupedByDayReport(processDefinition);
    reportData.getConfiguration().setAggregationTypes(getSupportedAggregationTypes());
    final ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData)
      .getResult();

    // then
    assertThat(result.getMeasures())
      .extracting(MeasureResponseDto::getAggregationType)
      .containsExactly(getSupportedAggregationTypes());
    result.getMeasures().forEach(measureResult -> {
      assertThat(MapResultUtil.getEntryForKey(measureResult.getData(), groupedByDayDateAsString(today)))
        .get()
        .extracting(MapResultEntryDto::getValue)
        .isEqualTo(calculateExpectedValueGivenDurations(10., 20.).get(measureResult.getAggregationType()));
    });
  }

  @Test
  public void reportEvaluationForSeveralProcessesWithAllAggregationTypes() {
    // given
    final ProcessDefinitionEngineDto processDefinition1 = deployTwoUserTasksDefinition();
    final OffsetDateTime today = OffsetDateTime.now();

    final ProcessInstanceEngineDto processInstanceDto1 =
      engineIntegrationExtension.startProcessInstance(processDefinition1.getId());
    finishAllUserTasks(processInstanceDto1);
    finishAllUserTasks(processInstanceDto1);
    changeModelElementDate(processInstanceDto1, USER_TASK_1, today);
    changeModelElementDate(processInstanceDto1, USER_TASK_2, today.minusDays(1));
    changeDuration(processInstanceDto1, USER_TASK_1, 10.);
    changeDuration(processInstanceDto1, USER_TASK_2, 10.);

    final ProcessDefinitionEngineDto processDefinition2 = deployTwoUserTasksDefinition();
    final ProcessInstanceEngineDto processInstanceDto2 =
      engineIntegrationExtension.startProcessInstance(processDefinition2.getId());
    finishAllUserTasks(processInstanceDto2);
    finishAllUserTasks(processInstanceDto2);
    changeModelElementDate(processInstanceDto2, USER_TASK_1, today);
    changeModelElementDate(processInstanceDto2, USER_TASK_2, today.minusDays(1));
    changeDuration(processInstanceDto2, USER_TASK_1, 20.);
    changeDuration(processInstanceDto2, USER_TASK_2, 20.);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData =
      createReportData(processDefinition1.getKey(), ALL_VERSIONS, AggregateByDateUnit.DAY);
    reportData.getConfiguration().setAggregationTypes(getSupportedAggregationTypes());
    final ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData)
      .getResult();

    // then
    assertThat(result.getMeasures())
      .extracting(MeasureResponseDto::getAggregationType)
      .containsExactly(getSupportedAggregationTypes());
    result.getMeasures().forEach(measureResult -> {
      assertThat(MapResultUtil.getEntryForKey(measureResult.getData(), groupedByDayDateAsString(today)))
        .get()
        .extracting(MapResultEntryDto::getValue)
        .isEqualTo(calculateExpectedValueGivenDurations(10., 20.).get(measureResult.getAggregationType()));
    });
  }

  @Test
  public void resultIsSortedInAscendingOrder() {
    // given
    final OffsetDateTime referenceDate = OffsetDateTime.now();
    ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();
    ProcessInstanceEngineDto processInstance1 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineIntegrationExtension.finishAllRunningUserTasks();
    changeModelElementDate(processInstance1, USER_TASK_1, referenceDate.minusDays(3));
    changeModelElementDate(processInstance1, USER_TASK_2, referenceDate.minusDays(1));

    ProcessInstanceEngineDto processInstance2 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineIntegrationExtension.finishAllRunningUserTasks();
    changeModelElementDate(processInstance2, USER_TASK_1, referenceDate.minusDays(2));
    changeModelElementDate(processInstance2, USER_TASK_2, referenceDate.minusDays(4));

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createGroupedByDayReport(processDefinition);
    final ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData)
      .getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(2L);
    assertThat(result.getFirstMeasureData())
      .hasSize(4)
      .extracting(MapResultEntryDto::getKey)
      .isSortedAccordingTo(Comparator.naturalOrder());
  }

  @Test
  public void testCustomOrderOnResultKeyIsApplied() {
    // given
    final OffsetDateTime referenceDate = OffsetDateTime.now();
    ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();
    ProcessInstanceEngineDto processInstance1 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineIntegrationExtension.finishAllRunningUserTasks();
    changeModelElementDate(processInstance1, USER_TASK_1, referenceDate.minusDays(3));
    changeModelElementDate(processInstance1, USER_TASK_2, referenceDate.minusDays(1));

    ProcessInstanceEngineDto processInstance2 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineIntegrationExtension.finishAllRunningUserTasks();
    changeModelElementDate(processInstance2, USER_TASK_1, referenceDate.minusDays(2));
    changeModelElementDate(processInstance2, USER_TASK_2, referenceDate.minusDays(4));

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createGroupedByDayReport(processDefinition);
    reportData.getConfiguration().setSorting(new ReportSortingDto(SORT_BY_KEY, SortOrder.DESC));
    final ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData)
      .getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(2L);
    assertThat(result.getFirstMeasureData())
      .hasSize(4)
      .extracting(MapResultEntryDto::getKey)
      .isSortedAccordingTo(Comparator.comparing(String::toString).reversed());
  }

  @Test
  public void testCustomOrderOnResultValueIsApplied() {
    // given
    final OffsetDateTime referenceDate = OffsetDateTime.now();
    ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();
    ProcessInstanceEngineDto processInstance1 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineIntegrationExtension.finishAllRunningUserTasks();
    changeModelElementDate(processInstance1, USER_TASK_1, referenceDate.minusDays(3));
    changeModelElementDate(processInstance1, USER_TASK_2, referenceDate.minusDays(1));

    ProcessInstanceEngineDto processInstance2 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineIntegrationExtension.finishAllRunningUserTasks();
    changeModelElementDate(processInstance2, USER_TASK_1, referenceDate.minusDays(2));
    changeModelElementDate(processInstance2, USER_TASK_2, referenceDate.minusDays(2));

    ProcessInstanceEngineDto processInstance3 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineIntegrationExtension.finishAllRunningUserTasks();
    changeModelElementDate(processInstance3, USER_TASK_1, referenceDate.minusDays(3));
    changeModelElementDate(processInstance3, USER_TASK_2, referenceDate.minusDays(3));

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createGroupedByDayReport(processDefinition);
    reportData.getConfiguration().setSorting(new ReportSortingDto(SORT_BY_VALUE, SortOrder.DESC));
    final ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData)
      .getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(3L);
    assertThat(result.getFirstMeasureData())
      .hasSize(3)
      .isSortedAccordingTo(Comparator.comparing(MapResultEntryDto::getValue).reversed());
  }

  @Test
  public void userTasksStartedAtSameIntervalAreGroupedTogether() {
    // given
    final OffsetDateTime referenceDate = OffsetDateTime.now();
    ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();
    ProcessInstanceEngineDto processInstance1 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineIntegrationExtension.finishAllRunningUserTasks();
    changeModelElementDate(processInstance1, USER_TASK_1, referenceDate.minusDays(1));
    changeModelElementDate(processInstance1, USER_TASK_2, referenceDate.minusDays(2));
    changeDuration(processInstance1, USER_TASK_1, 10.);
    changeDuration(processInstance1, USER_TASK_2, 20.);

    ProcessInstanceEngineDto processInstance2 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineIntegrationExtension.finishAllRunningUserTasks();
    changeModelElementDate(processInstance2, USER_TASK_1, referenceDate.minusDays(1));
    changeModelElementDate(processInstance2, USER_TASK_2, referenceDate.minusDays(2));
    changeDuration(processInstance2, USER_TASK_1, 10.);
    changeDuration(processInstance2, USER_TASK_2, 20.);


    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createGroupedByDayReport(processDefinition);
    final ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData)
      .getResult();

    // then
    final List<MapResultEntryDto> resultData = result.getFirstMeasureData();
    assertThat(resultData).hasSize(2);
    ZonedDateTime startOfToday = truncateToStartOfUnit(referenceDate, ChronoUnit.DAYS);

    final String expectedStringYesterday = localDateTimeToString(startOfToday.minusDays(1));
    assertThat(resultData).contains(new MapResultEntryDto(expectedStringYesterday, 10.));
    final String expectedStringDayBeforeYesterday = localDateTimeToString(startOfToday.minusDays(2));
    assertThat(resultData).contains(new MapResultEntryDto(expectedStringDayBeforeYesterday, 20.));
  }

  @Test
  public void emptyIntervalBetweenTwoUserTaskDates() {
    // given
    final OffsetDateTime referenceDate = OffsetDateTime.now();
    ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();
    ProcessInstanceEngineDto processInstance =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineIntegrationExtension.finishAllRunningUserTasks();
    changeModelElementDate(processInstance, USER_TASK_1, referenceDate.minusDays(1));
    changeModelElementDate(processInstance, USER_TASK_2, referenceDate.minusDays(3));
    changeDuration(processInstance, USER_TASK_1, 10.);
    changeDuration(processInstance, USER_TASK_2, 30.);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createGroupedByDayReport(processDefinition);
    final ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData)
      .getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(1L);
    final List<MapResultEntryDto> resultData = result.getFirstMeasureData();
    assertThat(resultData).hasSize(3);
    ZonedDateTime startOfToday = truncateToStartOfUnit(referenceDate, ChronoUnit.DAYS);

    final String expectedStringYesterday = localDateTimeToString(startOfToday.minusDays(1));
    assertThat(resultData).contains(new MapResultEntryDto(expectedStringYesterday, 10.));
    final String expectedStringDayBeforeYesterday = localDateTimeToString(startOfToday.minusDays(2));
    assertThat(resultData).contains(new MapResultEntryDto(expectedStringDayBeforeYesterday, null));
    final String threeDaysAgo = localDateTimeToString(startOfToday.minusDays(3));
    assertThat(resultData).contains(new MapResultEntryDto(threeDaysAgo, 30.));
  }

  @Test
  public void otherProcessDefinitionsDoNotAffectResult() {
    // given
    final OffsetDateTime referenceDate = OffsetDateTime.now();
    ProcessDefinitionEngineDto processDefinition1 = deployOneUserTaskDefinition();
    ProcessInstanceEngineDto processInstance1 =
      engineIntegrationExtension.startProcessInstance(processDefinition1.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();
    changeModelElementDate(processInstance1, USER_TASK_1, referenceDate.minusDays(1));
    changeDuration(processInstance1, USER_TASK_1, 10.);

    ProcessDefinitionEngineDto processDefinition2 = deployOneUserTaskDefinition();
    ProcessInstanceEngineDto processInstance2 =
      engineIntegrationExtension.startProcessInstance(processDefinition2.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();
    changeModelElementDate(processInstance2, USER_TASK_1, referenceDate.minusDays(1));
    changeDuration(processInstance2, USER_TASK_1, 100.);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createGroupedByDayReport(processDefinition1);
    final ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData)
      .getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(1L);
    final List<MapResultEntryDto> resultData = result.getFirstMeasureData();
    assertThat(resultData).hasSize(1);
    ZonedDateTime startOfToday = truncateToStartOfUnit(referenceDate, ChronoUnit.DAYS);

    final String expectedStringYesterday = localDateTimeToString(startOfToday.minusDays(1));
    assertThat(resultData).contains(new MapResultEntryDto(expectedStringYesterday, 10.));
  }

  public static Stream<Arguments> assigneeFilterScenarios() {
    return Stream.of(
      Arguments.of(IN, new String[]{SECOND_USER}, 10.),
      Arguments.of(IN, new String[]{DEFAULT_USERNAME, SECOND_USER}, 10.),
      Arguments.of(NOT_IN, new String[]{SECOND_USER}, 10.),
      Arguments.of(NOT_IN, new String[]{DEFAULT_USERNAME, SECOND_USER}, null)
    );
  }

  @ParameterizedTest
  @MethodSource("assigneeFilterScenarios")
  public void filterByAssigneeOnlyCountsUserTasksWithThatAssignee(final MembershipFilterOperator filterOperator,
                                                                  final String[] filterValues,
                                                                  final Double expectedUserTaskCount) {
    // given
    engineIntegrationExtension.addUser(SECOND_USER, SECOND_USERS_PASSWORD);
    engineIntegrationExtension.grantAllAuthorizations(SECOND_USER);

    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();
    final ProcessInstanceEngineDto processInstanceDto = engineIntegrationExtension
      .startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(
      DEFAULT_USERNAME, DEFAULT_PASSWORD, processInstanceDto.getId()
    );
    engineIntegrationExtension.finishAllRunningUserTasks(
      SECOND_USER, SECOND_USERS_PASSWORD, processInstanceDto.getId()
    );
    changeDuration(processInstanceDto, USER_TASK_1, 10.);
    changeDuration(processInstanceDto, USER_TASK_2, 10.);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createGroupedByDayReport(processDefinition);
    final List<ProcessFilterDto<?>> assigneeFilter = ProcessFilterBuilder
      .filter().assignee().ids(filterValues).operator(filterOperator).add().buildList();
    reportData.setFilter(assigneeFilter);
    final ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData)
      .getResult();

    // then
    if (expectedUserTaskCount != null) {
      assertThat(result.getFirstMeasureData())
        .extracting(MapResultEntryDto::getValue)
        .containsExactly(expectedUserTaskCount);
    } else {
      assertThat(result.getFirstMeasureData()).hasSize(0);
    }
  }

  public static Stream<Arguments> candidateGroupFilterScenarios() {
    return Stream.of(
      Arguments.of(IN, new String[]{SECOND_CANDIDATE_GROUP_ID}, 10.),
      Arguments.of(
        IN,
        new String[]{FIRST_CANDIDATE_GROUP_ID, SECOND_CANDIDATE_GROUP_ID},
        10.
      ),
      Arguments.of(NOT_IN, new String[]{SECOND_CANDIDATE_GROUP_ID}, 10.),
      Arguments.of(
        NOT_IN,
        new String[]{FIRST_CANDIDATE_GROUP_ID, SECOND_CANDIDATE_GROUP_ID},
        null
      )
    );
  }

  @ParameterizedTest
  @MethodSource("candidateGroupFilterScenarios")
  public void filterByCandidateGroupOnlyCountsUserTasksWithThatCandidateGroup(final MembershipFilterOperator filterOperator,
                                                                              final String[] filterValues,
                                                                              final Double expectedUserTaskCount) {
    // given
    engineIntegrationExtension.createGroup(FIRST_CANDIDATE_GROUP_ID);
    engineIntegrationExtension.createGroup(SECOND_CANDIDATE_GROUP_ID);

    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();
    final ProcessInstanceEngineDto processInstanceDto = engineIntegrationExtension
      .startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP_ID);
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(SECOND_CANDIDATE_GROUP_ID);
    engineIntegrationExtension.finishAllRunningUserTasks();
    changeDuration(processInstanceDto, USER_TASK_1, 10.);
    changeDuration(processInstanceDto, USER_TASK_2, 10.);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createGroupedByDayReport(processDefinition);
    final List<ProcessFilterDto<?>> candidateGroupFilter = ProcessFilterBuilder
      .filter().candidateGroups().ids(filterValues).operator(filterOperator).add().buildList();
    reportData.setFilter(candidateGroupFilter);
    final ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData)
      .getResult();

    // then
    if (expectedUserTaskCount != null) {
      assertThat(result.getFirstMeasureData())
        .extracting(MapResultEntryDto::getValue)
        .containsExactly(expectedUserTaskCount);
    } else {
      assertThat(result.getFirstMeasureData()).isEmpty();
    }
  }

  public static Stream<Arguments> viewLevelFlowNodeDurationFilterScenarios() {
    return Stream.of(
      Arguments.of(USER_TASK_1, GREATER_THAN, 1000L, 1L, 2000.),
      Arguments.of(USER_TASK_2, GREATER_THAN, 500L, 1L, 1000.),
      Arguments.of(USER_TASK_1, GREATER_THAN_EQUALS, 2000L, 1L, 2000.),
      Arguments.of(USER_TASK_1, LESS_THAN_EQUALS, 2000L, 1L, 2000.),
      Arguments.of(USER_TASK_2, LESS_THAN_EQUALS, 1000L, 1L, 1000.),
      Arguments.of(USER_TASK_1, LESS_THAN, 2000L, 0L, null)
    );
  }

  @ParameterizedTest
  @MethodSource("viewLevelFlowNodeDurationFilterScenarios")
  public void viewLevelFilterByFlowNodeDurationOnlyCountsUserTasksMatchingFilter(final String userTaskId,
                                                                                 final ComparisonOperator filterOperator,
                                                                                 final Long filterValueInMs,
                                                                                 final Long expectedInstanceCount,
                                                                                 final Double expectedUserTaskCount) {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();
    final ProcessInstanceEngineDto processInstanceDto = engineIntegrationExtension
      .startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineIntegrationExtension.finishAllRunningUserTasks();
    // We have to change the specific duration property for the test scenario
    changeDuration(processInstanceDto, USER_TASK_1, 2000.);
    changeDuration(processInstanceDto, USER_TASK_2, 1000.);
    // We also have to change both total durations as the instance level filtering applies to the activities and the
    // view level filtering applies to the user tasks
    engineDatabaseExtension.changeFlowNodeTotalDuration(processInstanceDto.getId(), USER_TASK_1, 2000.);
    engineDatabaseExtension.changeFlowNodeTotalDuration(processInstanceDto.getId(), USER_TASK_2, 1000.);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createGroupedByDayReport(processDefinition);
    final List<ProcessFilterDto<?>> flowNodeDurationFilter = ProcessFilterBuilder.filter()
      .flowNodeDuration()
      .flowNode(userTaskId, DurationFilterDataDto.builder().unit(DurationFilterUnit.MILLIS)
        .value(filterValueInMs).operator(filterOperator).build())
      .filterLevel(FilterApplicationLevel.VIEW)
      .add()
      .buildList();
    reportData.setFilter(flowNodeDurationFilter);
    final ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData)
      .getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(expectedInstanceCount);
    if (expectedUserTaskCount != null) {
      assertThat(result.getFirstMeasureData())
        .extracting(MapResultEntryDto::getValue)
        .containsExactly(expectedUserTaskCount);
    } else {
      assertThat(result.getFirstMeasureData()).isEmpty();
    }
  }

  @Test
  public void automaticIntervalSelection_simpleSetup() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployOneUserTaskDefinition();
    ProcessInstanceEngineDto processInstanceDto1 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    ProcessInstanceEngineDto processInstanceDto2 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    ProcessInstanceEngineDto processInstanceDto3 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();
    Map<String, OffsetDateTime> updates = new HashMap<>();
    OffsetDateTime startOfToday = OffsetDateTime.now().truncatedTo(ChronoUnit.DAYS);
    updates.put(processInstanceDto1.getId(), startOfToday);
    updates.put(processInstanceDto2.getId(), startOfToday);
    updates.put(processInstanceDto3.getId(), startOfToday.minusDays(1));
    changeModelElementDates(updates);
    changeDuration(processInstanceDto1, USER_TASK_1, 100.);
    changeDuration(processInstanceDto2, USER_TASK_1, 100.);
    changeDuration(processInstanceDto3, USER_TASK_1, 200.);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReportData(processDefinition, AggregateByDateUnit.AUTOMATIC);
    final ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData)
      .getResult();

    // then
    final List<MapResultEntryDto> resultData = result.getFirstMeasureData();
    assertThat(resultData).hasSize(NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION);
    assertThat(resultData).first().extracting(MapResultEntryDto::getValue).isEqualTo(200.);
    assertThat(resultData).last().extracting(MapResultEntryDto::getValue).isEqualTo(100.);
  }

  @Test
  public void automaticIntervalSelection_takesAllUserTasksIntoAccount() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployOneUserTaskDefinition();
    ProcessInstanceEngineDto processInstanceDto1 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    ProcessInstanceEngineDto processInstanceDto2 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    ProcessInstanceEngineDto processInstanceDto3 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();
    Map<String, OffsetDateTime> updates = new HashMap<>();
    OffsetDateTime startOfToday = OffsetDateTime.now().truncatedTo(ChronoUnit.DAYS);
    updates.put(processInstanceDto1.getId(), startOfToday);
    updates.put(processInstanceDto2.getId(), startOfToday.plusDays(2));
    updates.put(processInstanceDto3.getId(), startOfToday.plusDays(5));
    changeModelElementDates(updates);
    changeDuration(processInstanceDto1, USER_TASK_1, 100.);
    changeDuration(processInstanceDto2, USER_TASK_1, 200.);
    changeDuration(processInstanceDto3, USER_TASK_1, 500.);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReportData(processDefinition, AggregateByDateUnit.AUTOMATIC);
    final ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData)
      .getResult();

    // then
    final List<MapResultEntryDto> resultData = result.getFirstMeasureData();
    assertThat(resultData).hasSize(NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION);
    assertThat(resultData.stream()
                 .map(MapResultEntryDto::getValue)
                 .filter(Objects::nonNull)
                 .mapToInt(Double::intValue)
                 .sum()).isEqualTo(800);
    assertThat(resultData).first().extracting(MapResultEntryDto::getValue).isEqualTo(100.);
    assertThat(resultData).last().extracting(MapResultEntryDto::getValue).isEqualTo(500.);
  }

  @Test
  public void automaticIntervalSelection_forOneDataPoint() {
    // given there is only one data point
    final ProcessDefinitionEngineDto processDefinition = deployOneUserTaskDefinition();
    final ProcessInstanceEngineDto processInstance =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();
    changeDuration(processInstance, 100.);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReportData(processDefinition, AggregateByDateUnit.AUTOMATIC);
    final ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData)
      .getResult();

    // then the single data point should be grouped by month
    final List<MapResultEntryDto> resultData = result.getFirstMeasureData();
    assertThat(resultData).hasSize(1);
    ZonedDateTime nowStrippedToMonth = truncateToStartOfUnit(OffsetDateTime.now(), ChronoUnit.MONTHS);
    String nowStrippedToMonthAsString = localDateTimeToString(nowStrippedToMonth);
    assertThat(resultData).first().extracting(MapResultEntryDto::getKey).isEqualTo(nowStrippedToMonthAsString);
    assertThat(resultData).first().extracting(MapResultEntryDto::getValue).isEqualTo(100.);
  }

  protected ProcessReportDataDto createReportData(final String processDefinitionKey, final String version,
                                                  final AggregateByDateUnit groupByDateUnit) {
    return createReportData(processDefinitionKey, ImmutableList.of(version), groupByDateUnit);
  }

  protected ProcessReportDataDto createReportData(final String processDefinitionKey,
                                                  final List<String> versions,
                                                  final AggregateByDateUnit groupByDateUnit) {
    return TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersions(versions)
      .setUserTaskDurationTime(getUserTaskDurationTime())
      .setReportDataType(getReportDataType())
      .setGroupByDateInterval(groupByDateUnit)
      .build();
  }

  protected ProcessReportDataDto createReportData(final ProcessDefinitionEngineDto processDefinition,
                                                  final AggregateByDateUnit groupByDateUnit) {
    return createReportData(
      processDefinition.getKey(),
      String.valueOf(processDefinition.getVersion()),
      groupByDateUnit
    );
  }

  @SuppressWarnings(SAME_PARAM_VALUE)
  protected void changeUserTaskStartDate(final ProcessInstanceEngineDto processInstanceDto,
                                         final OffsetDateTime now,
                                         final String userTaskId,
                                         final long offsetDuration) {
    engineDatabaseExtension.changeFlowNodeStartDate(
      processInstanceDto.getId(),
      userTaskId,
      now.minus(offsetDuration, ChronoUnit.MILLIS)
    );
  }

  @SuppressWarnings(SAME_PARAM_VALUE)
  protected void changeUserTaskClaimDate(final ProcessInstanceEngineDto processInstanceDto,
                                         final OffsetDateTime now,
                                         final String userTaskKey,
                                         final long offsetDurationInMs) {

    engineIntegrationExtension.getHistoricTaskInstances(processInstanceDto.getId(), userTaskKey)
      .forEach(
        historicUserTaskInstanceDto ->
        {
          try {
            engineDatabaseExtension.changeUserTaskAssigneeClaimOperationTimestamp(
              historicUserTaskInstanceDto.getId(),
              now.minus(offsetDurationInMs, ChronoUnit.MILLIS)
            );
          } catch (SQLException e) {
            throw new OptimizeIntegrationTestException(e);
          }
        }
      );
  }

  protected abstract UserTaskDurationTime getUserTaskDurationTime();

  @Override
  protected ProcessViewEntity getViewEntity() {
    return ProcessViewEntity.USER_TASK;
  }

  @Override
  protected void startProcessInstancesWithModelElementDateInDayRange(ProcessDefinitionEngineDto processDefinition,
                                                                     ZonedDateTime min,
                                                                     ZonedDateTime max) {
    ProcessInstanceEngineDto procInstMin = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    ProcessInstanceEngineDto procInstMax = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    changeModelElementDate(procInstMin, USER_TASK_1, min.toOffsetDateTime());
    changeModelElementDate(procInstMax, USER_TASK_1, max.toOffsetDateTime());
  }

  @Override
  protected ProcessDefinitionEngineDto deploySimpleDefinition() {
    return deployOneUserTaskDefinition();
  }

  @Override
  protected void changeModelElementDates(final Map<String, OffsetDateTime> updates) {
    engineDatabaseExtension.changeAllFlowNodeStartDates(updates);
    engineDatabaseExtension.changeAllFlowNodeEndDates(updates);
  }

  @Override
  protected void changeModelElementDate(final ProcessInstanceEngineDto processInstance,
                                        final String modelElementId,
                                        final OffsetDateTime dateToChangeTo) {
    engineDatabaseExtension.changeFlowNodeStartDate(processInstance.getId(), modelElementId, dateToChangeTo);
    engineDatabaseExtension.changeFlowNodeEndDate(processInstance.getId(), modelElementId, dateToChangeTo);
  }
}
