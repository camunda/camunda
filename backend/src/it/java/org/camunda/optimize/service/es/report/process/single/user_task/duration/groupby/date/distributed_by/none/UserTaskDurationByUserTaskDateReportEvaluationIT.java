/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process.single.user_task.duration.groupby.date.distributed_by.none;

import com.google.common.collect.ImmutableList;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.UserTaskDurationTime;
import org.camunda.optimize.dto.optimize.query.report.single.group.GroupByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.value.DateGroupByValueDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.result.ReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import org.camunda.optimize.dto.optimize.query.sorting.SortingDto;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResultDto;
import org.camunda.optimize.dto.optimize.rest.report.CombinedProcessReportResultDataDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.report.process.AbstractProcessDefinitionIT;
import org.camunda.optimize.test.util.ProcessReportDataType;
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
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.google.common.collect.Lists.newArrayList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.dto.optimize.query.report.FilterOperatorConstants.IN;
import static org.camunda.optimize.dto.optimize.query.report.FilterOperatorConstants.NOT_IN;
import static org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType.getAggregationTypesAsListWithoutSum;
import static org.camunda.optimize.dto.optimize.query.sorting.SortingDto.SORT_BY_KEY;
import static org.camunda.optimize.dto.optimize.query.sorting.SortingDto.SORT_BY_VALUE;
import static org.camunda.optimize.service.es.filter.DateHistogramBucketLimiterUtil.mapToChronoUnit;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_PASSWORD;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;
import static org.camunda.optimize.test.util.DateModificationHelper.truncateToStartOfUnit;
import static org.camunda.optimize.test.util.DurationAggregationUtil.calculateExpectedValueGivenDurations;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createCombinedReportData;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION;

public abstract class UserTaskDurationByUserTaskDateReportEvaluationIT extends AbstractProcessDefinitionIT {

  protected static final String START_EVENT = "startEvent";
  protected static final String END_EVENT = "endEvent";
  protected static final String USER_TASK_1 = "userTask1";
  protected static final String USER_TASK_2 = "userTask2";

  @Test
  public void reportEvaluationForOneProcess() {
    // given
    ProcessDefinitionEngineDto processDefinition = deployOneUserTaskDefinition();
    ProcessInstanceEngineDto processInstanceDto =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    finishAllUserTasks(processInstanceDto);

    final Double expectedDuration = 20.;
    changeDuration(processInstanceDto, expectedDuration);

    importAllEngineEntitiesFromScratch();

    final ProcessReportDataDto reportData = createGroupedByDayReport(processDefinition);

    // when
    final AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto> evaluationResponse =
      reportClient.evaluateMapReport(reportData);

    // then
    final ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey()).isEqualTo(processDefinition.getKey());
    assertThat(resultReportDataDto.getDefinitionVersions()).containsExactly(processDefinition.getVersionAsString());
    assertThat(resultReportDataDto.getView()).isNotNull();
    assertThat(resultReportDataDto.getView().getEntity()).isEqualTo(ProcessViewEntity.USER_TASK);
    assertThat(resultReportDataDto.getView().getProperty()).isEqualTo(ProcessViewProperty.DURATION);
    assertThat(resultReportDataDto.getGroupBy()).isNotNull();
    assertThat(resultReportDataDto.getGroupBy().getType()).isEqualTo(getGroupByType());
    assertThat(resultReportDataDto.getGroupBy().getValue())
      .extracting(DateGroupByValueDto.class::cast)
      .extracting(DateGroupByValueDto::getUnit)
      .isEqualTo(GroupByDateUnit.DAY);

    final ReportMapResultDto result = evaluationResponse.getResult();
    assertThat(result.getInstanceCount()).isEqualTo(1L);
    assertThat(result.getIsComplete()).isTrue();
    assertThat(result.getData()).isNotNull();
    assertThat(result.getData()).hasSize(1);
    assertThat(getExecutedFlowNodeCount(result)).isEqualTo(1L);
    ZonedDateTime startOfToday = truncateToStartOfUnit(OffsetDateTime.now(), ChronoUnit.DAYS);
    assertThat(result.getEntryForKey(localDateTimeToString(startOfToday)))
      .get()
      .extracting(MapResultEntryDto::getValue)
      .isEqualTo(expectedDuration);
  }

  @Test
  public void simpleReportEvaluationById() {
    // given
    ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();
    ProcessInstanceEngineDto processInstanceDto =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    finishAllUserTasks(processInstanceDto);

    final Double expectedDuration = 20.;
    changeDuration(processInstanceDto, expectedDuration);

    importAllEngineEntitiesFromScratch();

    final ProcessReportDataDto reportData = createGroupedByDayReport(processDefinition);
    SingleProcessReportDefinitionDto singleProcessReportDefinitionDto = new SingleProcessReportDefinitionDto();
    singleProcessReportDefinitionDto.setData(reportData);
    final String reportId = reportClient.createSingleProcessReport(singleProcessReportDefinitionDto);

    // when
    AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto> evaluationResponse = reportClient.evaluateMapReportById(
      reportId
    );

    // then
    final ReportMapResultDto result = evaluationResponse.getResult();
    assertThat(result.getInstanceCount()).isEqualTo(1L);
    assertThat(result.getIsComplete()).isTrue();
    assertThat(result.getData()).isNotNull();
    assertThat(result.getData()).hasSize(1);
    assertThat(getExecutedFlowNodeCount(result)).isEqualTo(1L);
    ZonedDateTime startOfToday = truncateToStartOfUnit(OffsetDateTime.now(), ChronoUnit.DAYS);
    assertThat(result.getEntryForKey(localDateTimeToString(startOfToday)))
      .get()
      .extracting(MapResultEntryDto::getValue)
      .isEqualTo(expectedDuration);
  }

  @Test
  public void evaluateReportForMultipleEventsWithAllAggregationTypes() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();
    final OffsetDateTime today = OffsetDateTime.now();

    final ProcessInstanceEngineDto processInstanceDto1 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    finishAllUserTasks(processInstanceDto1);
    finishAllUserTasks(processInstanceDto1);
    changeUserTaskDate(processInstanceDto1, USER_TASK_1, today);
    changeUserTaskDate(processInstanceDto1, USER_TASK_2, today.minusDays(1));
    changeDuration(processInstanceDto1, USER_TASK_1, 10.);
    changeDuration(processInstanceDto1, USER_TASK_2, 10.);

    final ProcessInstanceEngineDto processInstanceDto2 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    finishAllUserTasks(processInstanceDto2);
    finishAllUserTasks(processInstanceDto2);
    changeUserTaskDate(processInstanceDto2, USER_TASK_1, today);
    changeUserTaskDate(processInstanceDto2, USER_TASK_2, today.minusDays(1));
    changeDuration(processInstanceDto2, USER_TASK_1, 20.);
    changeDuration(processInstanceDto2, USER_TASK_2, 20.);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createGroupedByDayReport(processDefinition);
    final Map<AggregationType, ReportMapResultDto> results =
      evaluateMapReportForAllAggTypes(reportData);

    // then
    getAggregationTypesAsListWithoutSum().forEach((AggregationType aggType) -> {
      ReportMapResultDto result = results.get(aggType);
      assertThat(result.getData()).isNotNull();

      assertThat(result.getEntryForKey(groupedByDayDateAsString(today)))
        .get()
        .extracting(MapResultEntryDto::getValue)
        .isEqualTo(calculateExpectedValueGivenDurations(10., 20.).get(aggType));
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
    changeUserTaskDate(processInstanceDto1, USER_TASK_1, today);
    changeUserTaskDate(processInstanceDto1, USER_TASK_2, today.minusDays(1));
    changeDuration(processInstanceDto1, USER_TASK_1, 10.);
    changeDuration(processInstanceDto1, USER_TASK_2, 10.);

    final ProcessDefinitionEngineDto processDefinition2 = deployTwoUserTasksDefinition();
    final ProcessInstanceEngineDto processInstanceDto2 =
      engineIntegrationExtension.startProcessInstance(processDefinition2.getId());
    finishAllUserTasks(processInstanceDto2);
    finishAllUserTasks(processInstanceDto2);
    changeUserTaskDate(processInstanceDto2, USER_TASK_1, today);
    changeUserTaskDate(processInstanceDto2, USER_TASK_2, today.minusDays(1));
    changeDuration(processInstanceDto2, USER_TASK_1, 20.);
    changeDuration(processInstanceDto2, USER_TASK_2, 20.);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData =
      createReportData(processDefinition1.getKey(), ALL_VERSIONS, GroupByDateUnit.DAY);
    final Map<AggregationType, ReportMapResultDto> results = evaluateMapReportForAllAggTypes(reportData);

    // then
    getAggregationTypesAsListWithoutSum().forEach((AggregationType aggType) -> {
      ReportMapResultDto result = results.get(aggType);
      assertThat(result.getData()).isNotNull();

      assertThat(result.getEntryForKey(groupedByDayDateAsString(today)))
        .get()
        .extracting(MapResultEntryDto::getValue)
        .isEqualTo(calculateExpectedValueGivenDurations(10., 20.).get(aggType));
    });
  }

  @Test
  public void resultIsSortedInDescendingOrder() {
    // given
    final OffsetDateTime referenceDate = OffsetDateTime.now();
    ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();
    ProcessInstanceEngineDto processInstance1 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineIntegrationExtension.finishAllRunningUserTasks();
    changeUserTaskDate(processInstance1, USER_TASK_1, referenceDate.minusDays(3));
    changeUserTaskDate(processInstance1, USER_TASK_2, referenceDate.minusDays(1));

    ProcessInstanceEngineDto processInstance2 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineIntegrationExtension.finishAllRunningUserTasks();
    changeUserTaskDate(processInstance2, USER_TASK_1, referenceDate.minusDays(2));
    changeUserTaskDate(processInstance2, USER_TASK_2, referenceDate.minusDays(4));

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createGroupedByDayReport(processDefinition);
    final ReportMapResultDto result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(2L);
    assertThat(result.getIsComplete()).isTrue();
    assertThat(result.getData())
      .hasSize(4)
      .extracting(MapResultEntryDto::getKey)
      .isSortedAccordingTo(Comparator.comparing(String::toString).reversed());
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
    changeUserTaskDate(processInstance1, USER_TASK_1, referenceDate.minusDays(3));
    changeUserTaskDate(processInstance1, USER_TASK_2, referenceDate.minusDays(1));

    ProcessInstanceEngineDto processInstance2 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineIntegrationExtension.finishAllRunningUserTasks();
    changeUserTaskDate(processInstance2, USER_TASK_1, referenceDate.minusDays(2));
    changeUserTaskDate(processInstance2, USER_TASK_2, referenceDate.minusDays(4));

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createGroupedByDayReport(processDefinition);
    reportData.getConfiguration().setSorting(new SortingDto(SORT_BY_KEY, SortOrder.ASC));
    final ReportMapResultDto result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(2L);
    assertThat(result.getIsComplete()).isTrue();
    assertThat(result.getData())
      .hasSize(4)
      .extracting(MapResultEntryDto::getKey)
      .isSortedAccordingTo(Comparator.comparing(String::toString));
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
    changeUserTaskDate(processInstance1, USER_TASK_1, referenceDate.minusDays(3));
    changeUserTaskDate(processInstance1, USER_TASK_2, referenceDate.minusDays(1));

    ProcessInstanceEngineDto processInstance2 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineIntegrationExtension.finishAllRunningUserTasks();
    changeUserTaskDate(processInstance2, USER_TASK_1, referenceDate.minusDays(2));
    changeUserTaskDate(processInstance2, USER_TASK_2, referenceDate.minusDays(2));

    ProcessInstanceEngineDto processInstance3 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineIntegrationExtension.finishAllRunningUserTasks();
    changeUserTaskDate(processInstance3, USER_TASK_1, referenceDate.minusDays(3));
    changeUserTaskDate(processInstance3, USER_TASK_2, referenceDate.minusDays(3));

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createGroupedByDayReport(processDefinition);
    reportData.getConfiguration().setSorting(new SortingDto(SORT_BY_VALUE, SortOrder.DESC));
    final ReportMapResultDto result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(3L);
    assertThat(result.getIsComplete()).isTrue();
    assertThat(result.getData())
      .hasSize(3)
      .isSortedAccordingTo(Comparator.comparing(MapResultEntryDto::getValue).reversed());
  }

  @Test
  public void multipleBuckets_noFilter_resultLimitedByConfig() {
    // given
    final OffsetDateTime referenceDate = OffsetDateTime.now();
    ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();
    ProcessInstanceEngineDto processInstance1 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineIntegrationExtension.finishAllRunningUserTasks();
    changeUserTaskDate(processInstance1, USER_TASK_1, referenceDate.minusDays(3));
    changeUserTaskDate(processInstance1, USER_TASK_2, referenceDate.minusDays(1));

    ProcessInstanceEngineDto processInstance2 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineIntegrationExtension.finishAllRunningUserTasks();
    changeUserTaskDate(processInstance2, USER_TASK_1, referenceDate.minusDays(2));
    changeUserTaskDate(processInstance2, USER_TASK_2, referenceDate.minusDays(4));

    importAllEngineEntitiesFromScratch();

    embeddedOptimizeExtension.getConfigurationService().setEsAggregationBucketLimit(2);

    // when
    final ProcessReportDataDto reportData = createGroupedByDayReport(processDefinition);
    final ReportMapResultDto result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getData()).hasSize(2);
    assertThat(result.getIsComplete()).isFalse();
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
    changeUserTaskDate(processInstance1, USER_TASK_1, referenceDate.minusDays(1));
    changeUserTaskDate(processInstance1, USER_TASK_2, referenceDate.minusDays(2));
    changeDuration(processInstance1, USER_TASK_1, 10.);
    changeDuration(processInstance1, USER_TASK_2, 20.);

    ProcessInstanceEngineDto processInstance2 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineIntegrationExtension.finishAllRunningUserTasks();
    changeUserTaskDate(processInstance2, USER_TASK_1, referenceDate.minusDays(1));
    changeUserTaskDate(processInstance2, USER_TASK_2, referenceDate.minusDays(2));
    changeDuration(processInstance2, USER_TASK_1, 10.);
    changeDuration(processInstance2, USER_TASK_2, 20.);


    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createGroupedByDayReport(processDefinition);
    final ReportMapResultDto result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    final List<MapResultEntryDto> resultData = result.getData();
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
    changeUserTaskDate(processInstance, USER_TASK_1, referenceDate.minusDays(1));
    changeUserTaskDate(processInstance, USER_TASK_2, referenceDate.minusDays(3));
    changeDuration(processInstance, USER_TASK_1, 10.);
    changeDuration(processInstance, USER_TASK_2, 30.);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createGroupedByDayReport(processDefinition);
    final ReportMapResultDto result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getIsComplete()).isTrue();
    assertThat(result.getInstanceCount()).isEqualTo(1L);
    final List<MapResultEntryDto> resultData = result.getData();
    assertThat(resultData).hasSize(3);
    ZonedDateTime startOfToday = truncateToStartOfUnit(referenceDate, ChronoUnit.DAYS);

    final String expectedStringYesterday = localDateTimeToString(startOfToday.minusDays(1));
    assertThat(resultData).contains(new MapResultEntryDto(expectedStringYesterday, 10.));
    final String expectedStringDayBeforeYesterday = localDateTimeToString(startOfToday.minusDays(2));
    assertThat(resultData).contains(new MapResultEntryDto(expectedStringDayBeforeYesterday, null));
    final String threeDaysAgo = localDateTimeToString(startOfToday.minusDays(3));
    assertThat(resultData).contains(new MapResultEntryDto(threeDaysAgo, 30.));
  }

  @ParameterizedTest
  @MethodSource("staticGroupByDateUnits")
  public void groupedByDateUnit(final GroupByDateUnit groupByDateUnit) {
    // given
    final ChronoUnit groupByUnitAsChrono = mapToChronoUnit(groupByDateUnit);
    final int groupingCount = 5;
    OffsetDateTime now = OffsetDateTime.now();

    ProcessDefinitionEngineDto processDefinition = deployOneUserTaskDefinition();
    List<ProcessInstanceEngineDto> processInstanceDtos = IntStream.range(0, groupingCount)
      .mapToObj(i -> {
        ProcessInstanceEngineDto processInstanceEngineDto =
          engineIntegrationExtension.startProcessInstance(processDefinition.getId());
        processInstanceEngineDto.setProcessDefinitionKey(processDefinition.getKey());
        processInstanceEngineDto.setProcessDefinitionVersion(String.valueOf(processDefinition.getVersion()));
        return processInstanceEngineDto;
      })
      .collect(Collectors.toList());
    engineIntegrationExtension.finishAllRunningUserTasks();
    updateUserTaskTime(processInstanceDtos, now, groupByUnitAsChrono);
    processInstanceDtos.forEach(procInst -> changeDuration(procInst, 10.));

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReportData(processDefinition, groupByDateUnit);
    final ReportMapResultDto result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    final List<MapResultEntryDto> resultData = result.getData();
    assertThat(resultData).hasSize(groupingCount);
    IntStream.range(0, groupingCount)
      .forEach(i -> {
        final String expectedDateString =
          groupedByDateAsString(now.minus((i), groupByUnitAsChrono), groupByUnitAsChrono);
        assertThat(resultData.get(i).getKey()).isEqualTo(expectedDateString);
        assertThat(resultData.get(i).getValue()).isEqualTo((long) 10);
      });
  }

  @Test
  public void otherProcessDefinitionsDoNotAffectResult() {
    // given
    final OffsetDateTime referenceDate = OffsetDateTime.now();
    ProcessDefinitionEngineDto processDefinition1 = deployOneUserTaskDefinition();
    ProcessInstanceEngineDto processInstance1 =
      engineIntegrationExtension.startProcessInstance(processDefinition1.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();
    changeUserTaskDate(processInstance1, USER_TASK_1, referenceDate.minusDays(1));
    changeDuration(processInstance1, USER_TASK_1, 10.);

    ProcessDefinitionEngineDto processDefinition2 = deployOneUserTaskDefinition();
    ProcessInstanceEngineDto processInstance2 =
      engineIntegrationExtension.startProcessInstance(processDefinition2.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();
    changeUserTaskDate(processInstance2, USER_TASK_1, referenceDate.minusDays(1));
    changeDuration(processInstance2, USER_TASK_1, 100.);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createGroupedByDayReport(processDefinition1);
    final ReportMapResultDto result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getIsComplete()).isTrue();
    assertThat(result.getInstanceCount()).isEqualTo(1L);
    final List<MapResultEntryDto> resultData = result.getData();
    assertThat(resultData).hasSize(1);
    ZonedDateTime startOfToday = truncateToStartOfUnit(referenceDate, ChronoUnit.DAYS);

    final String expectedStringYesterday = localDateTimeToString(startOfToday.minusDays(1));
    assertThat(resultData).contains(new MapResultEntryDto(expectedStringYesterday, 10.));
  }

  @Test
  public void reportEvaluationSingleBucketFilteredBySingleTenant() {
    // given
    final String tenantId1 = "tenantId1";
    final String tenantId2 = "tenantId2";
    final List<String> selectedTenants = newArrayList(tenantId1);
    final String processKey = deployAndStartMultiTenantUserTaskProcess(
      newArrayList(null, tenantId1, tenantId2)
    );

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReportData(processKey, "1", GroupByDateUnit.DAY);
    reportData.setTenantIds(selectedTenants);
    final ReportMapResultDto result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo((long) selectedTenants.size());
  }

  @Test
  public void filterWorks() {
    // given
    ProcessDefinitionEngineDto processDefinition = deployOneUserTaskDefinition();
    final ProcessInstanceEngineDto processInstance =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();
    changeDuration(processInstance, 10.);
    engineIntegrationExtension.startProcessInstance(processDefinition.getId());

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createGroupedByDayReport(processDefinition);
    final List<ProcessFilterDto<?>> processFilterDtoList = ProcessFilterBuilder.filter()
      .completedInstancesOnly().add().buildList();
    reportData.setFilter(processFilterDtoList);
    final ReportMapResultDto result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(1L);
    assertThat(result.getData()).hasSize(1);
    assertThat(result.getData())
      .extracting(MapResultEntryDto::getValue)
      .containsExactly(10.);
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
  public void filterByAssigneeOnlyCountsUserTasksWithThatAssignee(final String filterOperator,
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
    final List<ProcessFilterDto<?>> inclusiveAssigneeFilter = ProcessFilterBuilder
      .filter().assignee().ids(filterValues).operator(filterOperator).add().buildList();
    reportData.setFilter(inclusiveAssigneeFilter);
    final ReportMapResultDto result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    if (expectedUserTaskCount != null) {
      assertThat(result.getData())
        .extracting(MapResultEntryDto::getValue)
        .containsExactly(expectedUserTaskCount);
    } else {
      assertThat(result.getData()).hasSize(0);
    }
  }

  public static Stream<Arguments> candidateGroupFilterScenarios() {
    return Stream.of(
      Arguments.of(IN, new String[]{SECOND_CANDIDATE_GROUP}, 10.),
      Arguments.of(IN, new String[]{FIRST_CANDIDATE_GROUP, SECOND_CANDIDATE_GROUP}, 10.),
      Arguments.of(NOT_IN, new String[]{SECOND_CANDIDATE_GROUP}, 10.),
      Arguments.of(NOT_IN, new String[]{FIRST_CANDIDATE_GROUP, SECOND_CANDIDATE_GROUP}, null)
    );
  }

  @ParameterizedTest
  @MethodSource("candidateGroupFilterScenarios")
  public void filterByCandidateGroupOnlyCountsUserTasksWithThatCandidateGroup(final String filterOperator,
                                                                              final String[] filterValues,
                                                                              final Double expectedUserTaskCount) {
    // given
    engineIntegrationExtension.createGroup(FIRST_CANDIDATE_GROUP);
    engineIntegrationExtension.createGroup(SECOND_CANDIDATE_GROUP);

    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();
    final ProcessInstanceEngineDto processInstanceDto = engineIntegrationExtension
      .startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP);
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(SECOND_CANDIDATE_GROUP);
    engineIntegrationExtension.finishAllRunningUserTasks();
    changeDuration(processInstanceDto, USER_TASK_1, 10.);
    changeDuration(processInstanceDto, USER_TASK_2, 10.);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createGroupedByDayReport(processDefinition);
    final List<ProcessFilterDto<?>> inclusiveAssigneeFilter = ProcessFilterBuilder
      .filter().candidateGroups().ids(filterValues).operator(filterOperator).add().buildList();
    reportData.setFilter(inclusiveAssigneeFilter);
    final ReportMapResultDto result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    if (expectedUserTaskCount != null) {
      assertThat(result.getData())
        .extracting(MapResultEntryDto::getValue)
        .containsExactly(expectedUserTaskCount);
    } else {
      assertThat(result.getData()).hasSize(0);
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
    changeUserTaskDates(updates);
    changeDuration(processInstanceDto1, USER_TASK_1, 100.);
    changeDuration(processInstanceDto2, USER_TASK_1, 100.);
    changeDuration(processInstanceDto3, USER_TASK_1, 200.);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReportData(processDefinition, GroupByDateUnit.AUTOMATIC);
    final ReportMapResultDto result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    final List<MapResultEntryDto> resultData = result.getData();
    assertThat(resultData).hasSize(NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION);
    assertThat(resultData).first().extracting(MapResultEntryDto::getValue).isEqualTo(100.);
    assertThat(resultData).last().extracting(MapResultEntryDto::getValue).isEqualTo(200.);
  }

  @Test
  public void automaticIntervalSelection_takesAllUserTasksIntoAccount() {
    //given
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
    changeUserTaskDates(updates);
    changeDuration(processInstanceDto1, USER_TASK_1, 100.);
    changeDuration(processInstanceDto2, USER_TASK_1, 200.);
    changeDuration(processInstanceDto3, USER_TASK_1, 500.);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReportData(processDefinition, GroupByDateUnit.AUTOMATIC);
    final ReportMapResultDto result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    final List<MapResultEntryDto> resultData = result.getData();
    assertThat(resultData).hasSize(NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION);
    assertThat(resultData.stream()
                 .map(MapResultEntryDto::getValue)
                 .filter(Objects::nonNull)
                 .mapToInt(Double::intValue)
                 .sum()).isEqualTo(800);
    assertThat(resultData).first().extracting(MapResultEntryDto::getValue).isEqualTo(500.);
    assertThat(resultData).last().extracting(MapResultEntryDto::getValue).isEqualTo(100.);
  }

  @Test
  public void automaticIntervalSelection_forNoData() {
    // given
    ProcessDefinitionEngineDto processDefinition = deployOneUserTaskDefinition();
    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReportData(processDefinition, GroupByDateUnit.AUTOMATIC);
    final ReportMapResultDto result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    final List<MapResultEntryDto> resultData = result.getData();
    assertThat(resultData).isEmpty();
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
    final ProcessReportDataDto reportData = createReportData(processDefinition, GroupByDateUnit.AUTOMATIC);
    final ReportMapResultDto result = reportClient.evaluateMapReport(reportData).getResult();

    // then the single data point should be grouped by month
    final List<MapResultEntryDto> resultData = result.getData();
    assertThat(resultData).hasSize(1);
    ZonedDateTime nowStrippedToMonth = truncateToStartOfUnit(OffsetDateTime.now(), ChronoUnit.MONTHS);
    String nowStrippedToMonthAsString = localDateTimeToString(nowStrippedToMonth);
    assertThat(resultData).first().extracting(MapResultEntryDto::getKey).isEqualTo(nowStrippedToMonthAsString);
    assertThat(resultData).first().extracting(MapResultEntryDto::getValue).isEqualTo(100.);
  }

  @Test
  public void automaticIntervalSelection_combinedReportsWithDistinctRanges() {
    // given
    ZonedDateTime now = ZonedDateTime.now();
    ProcessDefinitionEngineDto processDefinition1 = deployOneUserTaskDefinition();
    startProcessInstancesWithUserTaskDateInDayRange(processDefinition1, now.plusDays(1), now.plusDays(3));
    ProcessDefinitionEngineDto processDefinition2 = deployOneUserTaskDefinition();
    startProcessInstancesWithUserTaskDateInDayRange(processDefinition2, now.plusDays(4), now.plusDays(6));
    importAllEngineEntitiesFromScratch();

    SingleProcessReportDefinitionDto singleProcessReportDefinitionDto = new SingleProcessReportDefinitionDto();
    ProcessReportDataDto reportData = createReportData(processDefinition1, GroupByDateUnit.AUTOMATIC);
    singleProcessReportDefinitionDto.setData(reportData);
    final String singleReportId1 = reportClient.createSingleProcessReport(singleProcessReportDefinitionDto);
    reportData = createReportData(processDefinition2, GroupByDateUnit.AUTOMATIC);
    singleProcessReportDefinitionDto.setData(reportData);
    final String singleReportId2 = reportClient.createSingleProcessReport(singleProcessReportDefinitionDto);

    // when
    CombinedProcessReportResultDataDto<ReportMapResultDto> result =
      reportClient.evaluateUnsavedCombined(createCombinedReportData(singleReportId1, singleReportId2));

    // then
    Map<String, AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto>> resultMap = result.getData();
    assertResultIsInCorrectRanges(now.plusDays(1), now.plusDays(6), resultMap, 2);
  }

  @Test
  public void automaticIntervalSelection_combinedReportsWithOneIncludingRange() {
    // given
    ZonedDateTime now = ZonedDateTime.now();
    ProcessDefinitionEngineDto processDefinition1 = deployOneUserTaskDefinition();
    startProcessInstancesWithUserTaskDateInDayRange(processDefinition1, now.plusDays(1), now.plusDays(6));
    ProcessDefinitionEngineDto processDefinition2 = deployOneUserTaskDefinition();
    startProcessInstancesWithUserTaskDateInDayRange(processDefinition2, now.plusDays(3), now.plusDays(5));
    importAllEngineEntitiesFromScratch();

    SingleProcessReportDefinitionDto singleProcessReportDefinitionDto = new SingleProcessReportDefinitionDto();
    ProcessReportDataDto reportData = createReportData(processDefinition1, GroupByDateUnit.AUTOMATIC);
    singleProcessReportDefinitionDto.setData(reportData);
    final String singleReportId1 = reportClient.createSingleProcessReport(singleProcessReportDefinitionDto);
    reportData = createReportData(processDefinition2, GroupByDateUnit.AUTOMATIC);
    singleProcessReportDefinitionDto.setData(reportData);
    final String singleReportId2 = reportClient.createSingleProcessReport(singleProcessReportDefinitionDto);

    // when
    CombinedProcessReportResultDataDto<ReportMapResultDto> result =
      reportClient.evaluateUnsavedCombined(createCombinedReportData(singleReportId1, singleReportId2));

    // then
    Map<String, AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto>> resultMap = result.getData();
    assertResultIsInCorrectRanges(now.plusDays(1), now.plusDays(6), resultMap, 2);
  }

  @Test
  public void automaticIntervalSelection_combinedReportsWithIntersectingRange() {
    // given
    ZonedDateTime now = ZonedDateTime.now();
    ProcessDefinitionEngineDto processDefinition1 = deployOneUserTaskDefinition();
    startProcessInstancesWithUserTaskDateInDayRange(processDefinition1, now.plusDays(1), now.plusDays(4));
    ProcessDefinitionEngineDto processDefinition2 = deployOneUserTaskDefinition();
    startProcessInstancesWithUserTaskDateInDayRange(processDefinition2, now.plusDays(4), now.plusDays(6));
    importAllEngineEntitiesFromScratch();

    SingleProcessReportDefinitionDto singleProcessReportDefinitionDto = new SingleProcessReportDefinitionDto();
    ProcessReportDataDto reportData = createReportData(processDefinition1, GroupByDateUnit.AUTOMATIC);
    singleProcessReportDefinitionDto.setData(reportData);
    final String singleReportId1 = reportClient.createSingleProcessReport(singleProcessReportDefinitionDto);
    reportData = createReportData(processDefinition2, GroupByDateUnit.AUTOMATIC);
    singleProcessReportDefinitionDto.setData(reportData);
    final String singleReportId2 = reportClient.createSingleProcessReport(singleProcessReportDefinitionDto);

    // when
    CombinedProcessReportResultDataDto<ReportMapResultDto> result =
      reportClient.evaluateUnsavedCombined(createCombinedReportData(singleReportId1, singleReportId2));

    // then
    Map<String, AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto>> resultMap = result.getData();
    assertResultIsInCorrectRanges(now.plusDays(1), now.plusDays(6), resultMap, 2);
  }

  @SuppressWarnings("SameParameterValue")
  private void assertResultIsInCorrectRanges(
    ZonedDateTime startRange,
    ZonedDateTime endRange,
    Map<String, AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto>> resultMap,
    int resultSize) {
    assertThat(resultMap).hasSize(resultSize);
    for (AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto> result : resultMap.values()) {
      final List<MapResultEntryDto> resultData = result.getResult().getData();
      assertThat(resultData).hasSize(NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION);
      assertThat(resultData).last().extracting(MapResultEntryDto::getKey).isEqualTo(localDateTimeToString(startRange));
      assertIsInRangeOfLastInterval(resultData.get(0).getKey(), startRange, endRange);
    }
  }

  private void assertIsInRangeOfLastInterval(String lastIntervalAsString,
                                             ZonedDateTime startTotal,
                                             ZonedDateTime endTotal) {
    long totalDuration = endTotal.toInstant().toEpochMilli() - startTotal.toInstant().toEpochMilli();
    long interval = totalDuration / NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION;
    assertThat(lastIntervalAsString)
      .isGreaterThanOrEqualTo(localDateTimeToString(endTotal.minus(interval, ChronoUnit.MILLIS)))
      .isLessThan(localDateTimeToString(endTotal));
  }

  private void startProcessInstancesWithUserTaskDateInDayRange(ProcessDefinitionEngineDto processDefinition,
                                                               ZonedDateTime min,
                                                               ZonedDateTime max) {
    ProcessInstanceEngineDto procInstMin = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    ProcessInstanceEngineDto procInstMax = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    changeUserTaskDate(procInstMin, USER_TASK_1, min.toOffsetDateTime());
    changeUserTaskDate(procInstMax, USER_TASK_1, max.toOffsetDateTime());
  }

  private void updateUserTaskTime(List<ProcessInstanceEngineDto> procInsts,
                                  OffsetDateTime now,
                                  ChronoUnit unit) {
    Map<String, OffsetDateTime> idToNewStartDate = new HashMap<>();
    IntStream.range(0, procInsts.size())
      .forEach(i -> {
        String id = procInsts.get(i).getId();
        OffsetDateTime newStartDate = now.minus(i, unit);
        idToNewStartDate.put(id, newStartDate);
      });
    changeUserTaskDates(idToNewStartDate);
  }

  protected ProcessReportDataDto createReportData(final String processDefinitionKey, final String version,
                                                  final GroupByDateUnit groupByDateUnit) {
    return createReportData(processDefinitionKey, ImmutableList.of(version), groupByDateUnit);
  }

  protected ProcessReportDataDto createReportData(final String processDefinitionKey,
                                                  final List<String> versions,
                                                  final GroupByDateUnit groupByDateUnit) {
    return TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersions(versions)
      .setUserTaskDurationTime(getUserTaskDurationTime())
      .setReportDataType(getReportDataType())
      .setDateInterval(groupByDateUnit)
      .build();
  }

  private ProcessReportDataDto createGroupedByDayReport(final ProcessDefinitionEngineDto processDefinition) {
    return createReportData(processDefinition, GroupByDateUnit.DAY);
  }

  protected ProcessReportDataDto createReportData(final ProcessDefinitionEngineDto processDefinition,
                                                  final GroupByDateUnit groupByDateUnit) {
    return createReportData(
      processDefinition.getKey(),
      String.valueOf(processDefinition.getVersion()),
      groupByDateUnit
    );
  }

  private void finishAllUserTasks(final ProcessInstanceEngineDto processInstanceDto1) {
    // finish first task
    engineIntegrationExtension.finishAllRunningUserTasks(processInstanceDto1.getId());
    // finish second task
    engineIntegrationExtension.finishAllRunningUserTasks(processInstanceDto1.getId());
  }

  private String deployAndStartMultiTenantUserTaskProcess(final List<String> deployedTenants) {
    final String processKey = "multiTenantProcess";
    deployedTenants.stream()
      .filter(Objects::nonNull)
      .forEach(tenantId -> engineIntegrationExtension.createTenant(tenantId));
    deployedTenants
      .forEach(tenant -> {
        final ProcessDefinitionEngineDto processDefinitionEngineDto = deployOneUserTaskDefinition(processKey, tenant);
        engineIntegrationExtension.startProcessInstance(processDefinitionEngineDto.getId());
      });

    return processKey;
  }

  private ProcessDefinitionEngineDto deployOneUserTaskDefinition() {
    return deployOneUserTaskDefinition("aProcess", null);
  }

  private ProcessDefinitionEngineDto deployOneUserTaskDefinition(String key, String tenantId) {
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess(key)
      .startEvent(START_EVENT)
      .userTask(USER_TASK_1)
      .endEvent(END_EVENT)
      .done();
    return engineIntegrationExtension.deployProcessAndGetProcessDefinition(modelInstance, tenantId);
  }

  protected ProcessDefinitionEngineDto deployTwoUserTasksDefinition() {
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess("aProcess")
      .startEvent(START_EVENT)
      .userTask(USER_TASK_1)
      .userTask(USER_TASK_2)
      .endEvent(END_EVENT)
      .done();
    return engineIntegrationExtension.deployProcessAndGetProcessDefinition(modelInstance);
  }

  private Map<AggregationType, ReportMapResultDto> evaluateMapReportForAllAggTypes(final ProcessReportDataDto reportData) {

    Map<AggregationType, ReportMapResultDto> resultsMap = new HashMap<>();
    getAggregationTypesAsListWithoutSum().forEach((AggregationType aggType) -> {
      reportData.getConfiguration().setAggregationType(aggType);
      final ReportMapResultDto result = reportClient.evaluateMapReport(reportData).getResult();
      resultsMap.put(aggType, result);
    });
    return resultsMap;
  }

  private long getExecutedFlowNodeCount(ReportMapResultDto resultList) {
    return resultList.getData()
      .stream()
      .map(MapResultEntryDto::getValue)
      .filter(Objects::nonNull)
      .count();
  }

  @SuppressWarnings("SameParameterValue")
  protected void changeUserTaskStartDate(final ProcessInstanceEngineDto processInstanceDto,
                                         final OffsetDateTime now,
                                         final String userTaskId,
                                         final long offsetDuration) {
    engineDatabaseExtension.changeUserTaskStartDate(
      processInstanceDto.getId(),
      userTaskId,
      now.minus(offsetDuration, ChronoUnit.MILLIS)
    );
  }

  @SuppressWarnings("SameParameterValue")
  protected void changeUserTaskClaimDate(final ProcessInstanceEngineDto processInstanceDto,
                                         final OffsetDateTime now,
                                         final String userTaskKey,
                                         final long offsetDuration) {

    engineIntegrationExtension.getHistoricTaskInstances(processInstanceDto.getId(), userTaskKey)
      .forEach(
        historicUserTaskInstanceDto ->
        {
          try {
            engineDatabaseExtension.changeUserTaskAssigneeOperationTimestamp(
              historicUserTaskInstanceDto.getId(),
              now.minus(offsetDuration, ChronoUnit.MILLIS)
            );
          } catch (SQLException e) {
            throw new OptimizeIntegrationTestException(e);
          }
        }
      );
  }

  protected String localDateTimeToString(ZonedDateTime time) {
    return embeddedOptimizeExtension.getDateTimeFormatter().format(time);
  }

  private String groupedByDayDateAsString(final OffsetDateTime referenceDate) {
    return groupedByDateAsString(referenceDate, ChronoUnit.DAYS);
  }

  private String groupedByDateAsString(final OffsetDateTime referenceDate, final ChronoUnit chronoUnit) {
    return localDateTimeToString(truncateToStartOfUnit(referenceDate, chronoUnit));
  }

  protected abstract void changeDuration(final ProcessInstanceEngineDto processInstanceDto,
                                         final String userTaskKey,
                                         final Double durationInMs);

  protected abstract void changeDuration(final ProcessInstanceEngineDto processInstanceDto, final Double durationInMs);

  protected abstract UserTaskDurationTime getUserTaskDurationTime();

  protected abstract ProcessGroupByType getGroupByType();

  protected abstract ProcessReportDataType getReportDataType();

  protected abstract void changeUserTaskDates(final Map<String, OffsetDateTime> updates);

  protected abstract void changeUserTaskDate(final ProcessInstanceEngineDto processInstance,
                                             final String userTaskKey,
                                             final OffsetDateTime dateToChangeTo);
}
