/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process.single.flownode.duration.groupby.date.distributedby.none;

import com.google.common.collect.ImmutableList;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.MembershipFilterOperator;
import org.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto;
import org.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import org.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
import org.camunda.optimize.dto.optimize.rest.report.measure.MeasureResponseDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.report.process.single.ModelElementDurationByModelElementDateReportEvaluationIT;
import org.camunda.optimize.service.es.report.util.MapResultUtil;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

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
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.MembershipFilterOperator.IN;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.MembershipFilterOperator.NOT_IN;
import static org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto.SORT_BY_KEY;
import static org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto.SORT_BY_VALUE;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_PASSWORD;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;
import static org.camunda.optimize.test.util.DateCreationFreezer.dateFreezer;
import static org.camunda.optimize.test.util.DateModificationHelper.truncateToStartOfUnit;
import static org.camunda.optimize.test.util.DurationAggregationUtil.calculateExpectedValueGivenDurations;
import static org.camunda.optimize.test.util.DurationAggregationUtil.getSupportedAggregationTypes;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION;

public abstract class FlowNodeDurationByFlowNodeDateReportEvaluationIT
  extends ModelElementDurationByModelElementDateReportEvaluationIT {

  @Test
  public void evaluateReportForMultipleEventsWithAllAggregationTypes() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployStartEndDefinition();
    final OffsetDateTime today = OffsetDateTime.now();

    final ProcessInstanceEngineDto processInstanceDto1 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    changeModelElementDate(processInstanceDto1, START_EVENT, today);
    changeModelElementDate(processInstanceDto1, END_EVENT, today.minusDays(1));
    changeDuration(processInstanceDto1, START_EVENT, 10.);
    changeDuration(processInstanceDto1, END_EVENT, 10.);

    final ProcessInstanceEngineDto processInstanceDto2 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    changeModelElementDate(processInstanceDto2, START_EVENT, today);
    changeModelElementDate(processInstanceDto2, END_EVENT, today.minusDays(1));
    changeDuration(processInstanceDto2, START_EVENT, 20.);
    changeDuration(processInstanceDto2, END_EVENT, 20.);

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
      final List<MapResultEntryDto> resultData = measureResult.getData();
      assertThat(MapResultUtil.getEntryForKey(resultData, groupedByDayDateAsString(today)))
        .get()
        .extracting(MapResultEntryDto::getValue)
        .isEqualTo(calculateExpectedValueGivenDurations(10., 20.).get(measureResult.getAggregationType()));
    });
  }

  @Test
  public void reportEvaluationForSeveralProcessesWithAllAggregationTypes() {
    // given
    final ProcessDefinitionEngineDto processDefinition1 = deployStartEndDefinition();
    final OffsetDateTime today = OffsetDateTime.now();

    final ProcessInstanceEngineDto processInstanceDto1 =
      engineIntegrationExtension.startProcessInstance(processDefinition1.getId());
    changeModelElementDate(processInstanceDto1, START_EVENT, today);
    changeModelElementDate(processInstanceDto1, END_EVENT, today.minusDays(1));
    changeDuration(processInstanceDto1, START_EVENT, 10.);
    changeDuration(processInstanceDto1, END_EVENT, 10.);

    final ProcessDefinitionEngineDto processDefinition2 = deployStartEndDefinition();
    final ProcessInstanceEngineDto processInstanceDto2 =
      engineIntegrationExtension.startProcessInstance(processDefinition2.getId());
    changeModelElementDate(processInstanceDto2, START_EVENT, today);
    changeModelElementDate(processInstanceDto2, END_EVENT, today.minusDays(1));
    changeDuration(processInstanceDto2, START_EVENT, 20.);
    changeDuration(processInstanceDto2, END_EVENT, 20.);

    importAllEngineEntitiesFromScratch();

    final ProcessReportDataDto reportData =
      createReportData(processDefinition1.getKey(), ALL_VERSIONS, AggregateByDateUnit.DAY);
    reportData.getConfiguration().setAggregationTypes(getSupportedAggregationTypes());

    // when
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
    ProcessDefinitionEngineDto processDefinition = deployOneUserTaskDefinition();
    ProcessInstanceEngineDto processInstance1 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();
    changeModelElementDate(processInstance1, START_EVENT, referenceDate.minusDays(1));
    changeModelElementDate(processInstance1, USER_TASK_1, referenceDate.minusDays(3));
    changeModelElementDate(processInstance1, END_EVENT, referenceDate.minusDays(5));

    ProcessInstanceEngineDto processInstance2 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();
    changeModelElementDate(processInstance2, START_EVENT, referenceDate.minusDays(2));
    changeModelElementDate(processInstance2, USER_TASK_1, referenceDate.minusDays(4));
    changeModelElementDate(processInstance2, END_EVENT, referenceDate.minusDays(6));

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createGroupedByDayReport(processDefinition);
    final ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData)
      .getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(2L);
    assertThat(result.getFirstMeasureData())
      .hasSize(6)
      .extracting(MapResultEntryDto::getKey)
      .isSortedAccordingTo(Comparator.naturalOrder());
  }

  @Test
  public void testCustomOrderOnResultKeyIsApplied() {
    // given
    final OffsetDateTime referenceDate = OffsetDateTime.now();
    ProcessDefinitionEngineDto processDefinition = deployOneUserTaskDefinition();
    ProcessInstanceEngineDto processInstance1 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();
    changeModelElementDate(processInstance1, START_EVENT, referenceDate.minusDays(1));
    changeModelElementDate(processInstance1, USER_TASK_1, referenceDate.minusDays(3));
    changeModelElementDate(processInstance1, END_EVENT, referenceDate.minusDays(5));

    ProcessInstanceEngineDto processInstance2 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();
    changeModelElementDate(processInstance2, START_EVENT, referenceDate.minusDays(2));
    changeModelElementDate(processInstance2, USER_TASK_1, referenceDate.minusDays(4));
    changeModelElementDate(processInstance2, END_EVENT, referenceDate.minusDays(6));

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createGroupedByDayReport(processDefinition);
    reportData.getConfiguration().setSorting(new ReportSortingDto(SORT_BY_KEY, SortOrder.DESC));
    final ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData)
      .getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(2L);
    assertThat(result.getFirstMeasureData())
      .hasSize(6)
      .extracting(MapResultEntryDto::getKey)
      .isSortedAccordingTo(Comparator.comparing(String::toString).reversed());
  }

  @Test
  public void testCustomOrderOnResultValueIsApplied() {
    // given
    final OffsetDateTime referenceDate = OffsetDateTime.now();
    ProcessDefinitionEngineDto processDefinition = deployOneUserTaskDefinition();
    ProcessInstanceEngineDto processInstance1 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();
    changeModelElementDate(processInstance1, START_EVENT, referenceDate.minusDays(1));
    changeModelElementDate(processInstance1, USER_TASK_1, referenceDate.minusDays(1));
    changeModelElementDate(processInstance1, END_EVENT, referenceDate.minusDays(1));

    ProcessInstanceEngineDto processInstance2 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();
    changeModelElementDate(processInstance2, START_EVENT, referenceDate.minusDays(2));
    changeModelElementDate(processInstance2, USER_TASK_1, referenceDate.minusDays(2));
    changeModelElementDate(processInstance2, END_EVENT, referenceDate.minusDays(2));

    ProcessInstanceEngineDto processInstance3 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();
    changeModelElementDate(processInstance3, START_EVENT, referenceDate.minusDays(3));
    changeModelElementDate(processInstance3, USER_TASK_1, referenceDate.minusDays(3));
    changeModelElementDate(processInstance3, END_EVENT, referenceDate.minusDays(3));

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
  public void flowNodesStartedAtSameIntervalAreGroupedTogether() {
    // given
    final OffsetDateTime referenceDate = OffsetDateTime.now();
    ProcessDefinitionEngineDto processDefinition = deployStartEndDefinition();
    ProcessInstanceEngineDto processInstance1 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    changeModelElementDate(processInstance1, START_EVENT, referenceDate.minusDays(1));
    changeModelElementDate(processInstance1, END_EVENT, referenceDate.minusDays(2));
    changeDuration(processInstance1, START_EVENT, 10.);
    changeDuration(processInstance1, END_EVENT, 20.);

    ProcessInstanceEngineDto processInstance2 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    changeModelElementDate(processInstance2, START_EVENT, referenceDate.minusDays(1));
    changeModelElementDate(processInstance2, END_EVENT, referenceDate.minusDays(2));
    changeDuration(processInstance2, START_EVENT, 10.);
    changeDuration(processInstance2, END_EVENT, 20.);

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
  public void emptyIntervalBetweenTwoFlowNodeDates() {
    // given
    final OffsetDateTime referenceDate = OffsetDateTime.now();
    ProcessDefinitionEngineDto processDefinition = deployOneUserTaskDefinition();
    ProcessInstanceEngineDto processInstance =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();
    changeModelElementDate(processInstance, START_EVENT, referenceDate.minusDays(1));
    changeModelElementDate(processInstance, USER_TASK_1, referenceDate.minusDays(3));
    changeModelElementDate(processInstance, END_EVENT, referenceDate.minusDays(4));
    changeDuration(processInstance, START_EVENT, 10.);
    changeDuration(processInstance, USER_TASK_1, 30.);
    changeDuration(processInstance, END_EVENT, 50.);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createGroupedByDayReport(processDefinition);
    final ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData)
      .getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(1L);
    final List<MapResultEntryDto> resultData = result.getFirstMeasureData();
    assertThat(resultData).hasSize(4);
    ZonedDateTime startOfToday = truncateToStartOfUnit(referenceDate, ChronoUnit.DAYS);

    final String oneDayAgo = localDateTimeToString(startOfToday.minusDays(1));
    assertThat(resultData).contains(new MapResultEntryDto(oneDayAgo, 10.));
    final String twoDaysAgo = localDateTimeToString(startOfToday.minusDays(2));
    assertThat(resultData).contains(new MapResultEntryDto(twoDaysAgo, null));
    final String threeDaysAgo = localDateTimeToString(startOfToday.minusDays(3));
    assertThat(resultData).contains(new MapResultEntryDto(threeDaysAgo, 30.));
    final String fourDaysAgo = localDateTimeToString(startOfToday.minusDays(4));
    assertThat(resultData).contains(new MapResultEntryDto(fourDaysAgo, 50.));
  }

  @Test
  public void otherProcessDefinitionsDoNotAffectResult() {
    // given
    final OffsetDateTime referenceDate = OffsetDateTime.now();
    ProcessDefinitionEngineDto processDefinition1 = deployOneUserTaskDefinition();
    ProcessInstanceEngineDto processInstance1 =
      engineIntegrationExtension.startProcessInstance(processDefinition1.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();
    changeModelElementDate(processInstance1, START_EVENT, referenceDate.minusDays(1));
    changeModelElementDate(processInstance1, USER_TASK_1, referenceDate.minusDays(1));
    changeModelElementDate(processInstance1, END_EVENT, referenceDate.minusDays(1));
    changeDuration(processInstance1, 10.);

    ProcessDefinitionEngineDto processDefinition2 = deployOneUserTaskDefinition();
    ProcessInstanceEngineDto processInstance2 =
      engineIntegrationExtension.startProcessInstance(processDefinition2.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();
    changeDuration(processInstance2, 100.);

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

  @Test
  public void automaticIntervalSelection_simpleSetup() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployStartEndDefinition();
    ProcessInstanceEngineDto processInstanceDto1 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    ProcessInstanceEngineDto processInstanceDto2 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    ProcessInstanceEngineDto processInstanceDto3 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    Map<String, OffsetDateTime> updates = new HashMap<>();
    OffsetDateTime startOfToday = OffsetDateTime.now().truncatedTo(ChronoUnit.DAYS);
    updates.put(processInstanceDto1.getId(), startOfToday);
    updates.put(processInstanceDto2.getId(), startOfToday);
    updates.put(processInstanceDto3.getId(), startOfToday.minusDays(1));
    changeModelElementDates(updates);
    changeDuration(processInstanceDto1, START_EVENT, 100.);
    changeDuration(processInstanceDto1, END_EVENT, 100.);
    changeDuration(processInstanceDto2, START_EVENT, 100.);
    changeDuration(processInstanceDto2, END_EVENT, 100.);
    changeDuration(processInstanceDto3, START_EVENT, 200.);
    changeDuration(processInstanceDto3, END_EVENT, 200.);

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
  public void automaticIntervalSelection_takesAllFlowNodesIntoAccount() {
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
    changeDuration(processInstanceDto1, START_EVENT, 100.);
    changeDuration(processInstanceDto1, USER_TASK_1, 100.);
    changeDuration(processInstanceDto1, END_EVENT, 100.);
    changeDuration(processInstanceDto2, START_EVENT, 200.);
    changeDuration(processInstanceDto2, USER_TASK_1, 200.);
    changeDuration(processInstanceDto2, END_EVENT, 200.);
    changeDuration(processInstanceDto3, START_EVENT, 500.);
    changeDuration(processInstanceDto3, USER_TASK_1, 500.);
    changeDuration(processInstanceDto3, END_EVENT, 500.);

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

  private static Stream<Arguments> viewLevelAssigneeFilterScenarios() {
    return Stream.of(
      Arguments.of(
        IN,
        new String[]{SECOND_USER},
        new Double[]{3000.}
      ),
      Arguments.of(
        IN,
        new String[]{DEFAULT_USERNAME, SECOND_USER, null},
        new Double[]{2000., 3000., 4000.}
      ),
      Arguments.of(
        NOT_IN,
        new String[]{SECOND_USER},
        new Double[]{2000., null, 4000.}
      ),
      Arguments.of(
        NOT_IN,
        new String[]{DEFAULT_USERNAME, SECOND_USER},
        new Double[]{4000.}
      )
    );
  }

  @ParameterizedTest
  @MethodSource("viewLevelAssigneeFilterScenarios")
  public void viewLevelAssigneeFilterOnlyIncludesFlowNodesMatchingFilter(final MembershipFilterOperator filterOperator,
                                                                         final String[] filterValues,
                                                                         final Double[] expectedResults) {
    // given
    final OffsetDateTime now = dateFreezer().freezeDateAndReturn();
    engineIntegrationExtension.addUser(SECOND_USER, SECOND_USER_FIRST_NAME, SECOND_USER_LAST_NAME);
    engineIntegrationExtension.grantAllAuthorizations(SECOND_USER);
    final ProcessDefinitionEngineDto processDefinition = deployThreeUserTasksDefinition();
    final ProcessInstanceEngineDto processInstanceDto = engineIntegrationExtension
      .startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(
      DEFAULT_USERNAME, DEFAULT_PASSWORD, processInstanceDto.getId()
    );
    engineIntegrationExtension.finishAllRunningUserTasks(
      SECOND_USER, SECOND_USERS_PASSWORD, processInstanceDto.getId()
    );
    engineIntegrationExtension.completeUserTaskWithoutClaim(processInstanceDto.getId());

    changeModelElementDate(processInstanceDto, START_EVENT, now);
    changeModelElementDate(processInstanceDto, USER_TASK_1, now.plusDays(1));
    changeModelElementDate(processInstanceDto, USER_TASK_2, now.plusDays(2));
    changeModelElementDate(processInstanceDto, USER_TASK_3, now.plusDays(3));
    changeModelElementDate(processInstanceDto, END_EVENT, now.plusDays(4));
    changeDuration(processInstanceDto, START_EVENT, 1000.);
    changeDuration(processInstanceDto, USER_TASK_1, 2000.);
    changeDuration(processInstanceDto, USER_TASK_2, 3000.);
    changeDuration(processInstanceDto, USER_TASK_3, 4000.);
    changeDuration(processInstanceDto, END_EVENT, 5000.);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReportData(processDefinition, AggregateByDateUnit.DAY);
    reportData.setFilter(
      ProcessFilterBuilder.filter()
        .assignee()
        .ids(filterValues)
        .operator(filterOperator)
        .filterLevel(FilterApplicationLevel.VIEW)
        .add()
        .buildList());
    final ReportResultResponseDto<List<MapResultEntryDto>> result =
      reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getFirstMeasureData())
      .extracting(MapResultEntryDto::getValue)
      .containsExactly(expectedResults);
  }

  private static Stream<Arguments> viewLevelCandidateGroupFilterScenarios() {
    return Stream.of(
      Arguments.of(
        IN,
        new String[]{SECOND_CANDIDATE_GROUP_ID},
        new Double[]{3000.}
      ),
      Arguments.of(
        IN,
        new String[]{FIRST_CANDIDATE_GROUP_ID, SECOND_CANDIDATE_GROUP_ID, null},
        new Double[]{2000., 3000., 4000.}
      ),
      Arguments.of(
        NOT_IN,
        new String[]{SECOND_CANDIDATE_GROUP_ID},
        new Double[]{2000., null, 4000.}
      ),
      Arguments.of(
        NOT_IN,
        new String[]{FIRST_CANDIDATE_GROUP_ID, SECOND_CANDIDATE_GROUP_ID},
        new Double[]{4000.}
      )
    );
  }

  @ParameterizedTest
  @MethodSource("viewLevelCandidateGroupFilterScenarios")
  public void viewLevelCandidateGroupFilterOnlyIncludesFlowNodesMatchingFilter(final MembershipFilterOperator filterOperator,
                                                                               final String[] filterValues,
                                                                               final Double[] expectedResults) {
    // given
    final OffsetDateTime now = dateFreezer().freezeDateAndReturn();
    engineIntegrationExtension.createGroup(FIRST_CANDIDATE_GROUP_ID, FIRST_CANDIDATE_GROUP_NAME);
    engineIntegrationExtension.createGroup(SECOND_CANDIDATE_GROUP_ID, SECOND_CANDIDATE_GROUP_NAME);
    final ProcessDefinitionEngineDto processDefinition = deployThreeUserTasksDefinition();
    final ProcessInstanceEngineDto processInstanceDto = engineIntegrationExtension
      .startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP_ID);
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(SECOND_CANDIDATE_GROUP_ID);
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineIntegrationExtension.finishAllRunningUserTasks();

    changeModelElementDate(processInstanceDto, START_EVENT, now);
    changeModelElementDate(processInstanceDto, USER_TASK_1, now.plusDays(1));
    changeModelElementDate(processInstanceDto, USER_TASK_2, now.plusDays(2));
    changeModelElementDate(processInstanceDto, USER_TASK_3, now.plusDays(3));
    changeModelElementDate(processInstanceDto, END_EVENT, now.plusDays(4));
    changeDuration(processInstanceDto, START_EVENT, 1000.);
    changeDuration(processInstanceDto, USER_TASK_1, 2000.);
    changeDuration(processInstanceDto, USER_TASK_2, 3000.);
    changeDuration(processInstanceDto, USER_TASK_3, 4000.);
    changeDuration(processInstanceDto, END_EVENT, 5000.);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReportData(processDefinition, AggregateByDateUnit.DAY);
    reportData.setFilter(
      ProcessFilterBuilder.filter()
        .candidateGroups()
        .ids(filterValues)
        .operator(filterOperator)
        .filterLevel(FilterApplicationLevel.VIEW)
        .add()
        .buildList());
    final ReportResultResponseDto<List<MapResultEntryDto>> result =
      reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getFirstMeasureData())
      .extracting(MapResultEntryDto::getValue)
      .containsExactly(expectedResults);
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

  protected void changeDuration(final ProcessInstanceEngineDto processInstanceDto,
                                final String modelElementId,
                                final Double durationInMs) {
    engineDatabaseExtension.changeFlowNodeTotalDuration(
      processInstanceDto.getId(),
      modelElementId,
      durationInMs.longValue()
    );
    changeUserTaskTotalDuration(processInstanceDto, modelElementId, durationInMs.longValue());
  }

  protected void changeDuration(final ProcessInstanceEngineDto processInstanceDto,
                                final Double durationInMs) {
    engineDatabaseExtension.changeAllFlowNodeTotalDurations(
      processInstanceDto.getId(),
      durationInMs.longValue()
    );
  }

  protected abstract void changeModelElementDates(final Map<String, OffsetDateTime> updates);

  protected abstract void changeModelElementDate(final ProcessInstanceEngineDto processInstance,
                                                 final String modelElementId,
                                                 final OffsetDateTime dateToChangeTo);

  @Override
  protected ProcessViewEntity getViewEntity() {
    return ProcessViewEntity.FLOW_NODE;
  }

  @Override
  protected void startProcessInstancesWithModelElementDateInDayRange(ProcessDefinitionEngineDto processDefinition,
                                                                     ZonedDateTime min,
                                                                     ZonedDateTime max) {
    ProcessInstanceEngineDto procInstMin = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    ProcessInstanceEngineDto procInstMax = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    changeModelElementDate(procInstMin, START_EVENT, min.toOffsetDateTime());
    changeModelElementDate(procInstMin, END_EVENT, min.toOffsetDateTime());
    changeModelElementDate(procInstMax, START_EVENT, max.toOffsetDateTime());
    changeModelElementDate(procInstMax, END_EVENT, max.toOffsetDateTime());
  }

  @Override
  protected ProcessDefinitionEngineDto deploySimpleDefinition() {
    return deployStartEndDefinition();
  }

}
