/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process.single.usertask.duration.groupby.date.distributedby.usertask;

import org.assertj.core.groups.Tuple;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.DistributedByType;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.UserTaskDurationTime;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DurationUnit;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.ComparisonOperator;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.MembershipFilterOperator;
import org.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.data.DurationFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.value.DateGroupByValueDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.HyperMapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto;
import org.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResponseDto;
import org.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
import org.camunda.optimize.dto.optimize.rest.report.measure.MeasureResponseDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.report.process.single.ModelElementDurationByModelElementDateByModelElementReportEvaluationIT;
import org.camunda.optimize.service.es.report.util.HyperMapAsserter;
import org.camunda.optimize.test.util.DateCreationFreezer;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.camunda.optimize.util.BpmnModels;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.ComparisonOperator.GREATER_THAN;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.ComparisonOperator.GREATER_THAN_EQUALS;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.ComparisonOperator.LESS_THAN;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.ComparisonOperator.LESS_THAN_EQUALS;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.MembershipFilterOperator.IN;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.MembershipFilterOperator.NOT_IN;
import static org.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnitMapper.mapToChronoUnit;
import static org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto.SORT_BY_KEY;
import static org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto.SORT_BY_VALUE;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_PASSWORD;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;
import static org.camunda.optimize.test.util.DateModificationHelper.truncateToStartOfUnit;
import static org.camunda.optimize.test.util.DurationAggregationUtil.calculateExpectedValueGivenDurations;
import static org.camunda.optimize.test.util.DurationAggregationUtil.getSupportedAggregationTypes;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION;
import static org.camunda.optimize.util.SuppressionConstants.SAME_PARAM_VALUE;
import static org.camunda.optimize.util.SuppressionConstants.UNCHECKED_CAST;

public abstract class UserTaskDurationByUserTaskDateByUserTaskReportEvaluationIT
  extends ModelElementDurationByModelElementDateByModelElementReportEvaluationIT {

  @Test
  public void reportEvaluationForOneProcessInstance() {
    // given
    ProcessDefinitionEngineDto processDefinition = deployOneUserTaskDefinition();
    final ProcessInstanceEngineDto processInstance =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(processInstance.getId());

    final Double expectedDuration = 20.;
    changeDuration(processInstance, expectedDuration);

    importAllEngineEntitiesFromScratch();

    final ProcessReportDataDto reportData = createGroupedByDayReport(processDefinition);

    // when
    final AuthorizedProcessReportEvaluationResponseDto<List<HyperMapResultEntryDto>> evaluationResponse =
      reportClient.evaluateHyperMapReport(reportData);

    // then
    final ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey()).isEqualTo(processDefinition.getKey());
    assertThat(resultReportDataDto.getDefinitionVersions()).containsExactly(processDefinition.getVersionAsString());
    assertThat(resultReportDataDto.getView()).isNotNull();
    assertThat(resultReportDataDto.getView().getEntity()).isEqualTo(ProcessViewEntity.USER_TASK);
    assertThat(resultReportDataDto.getView().getFirstProperty()).isEqualTo(ViewProperty.DURATION);
    assertThat(resultReportDataDto.getGroupBy()).isNotNull();
    assertThat(resultReportDataDto.getGroupBy().getType()).isEqualTo(getGroupByType());
    assertThat(resultReportDataDto.getGroupBy().getValue())
      .extracting(DateGroupByValueDto.class::cast)
      .extracting(DateGroupByValueDto::getUnit)
      .isEqualTo(AggregateByDateUnit.DAY);
    assertThat(resultReportDataDto.getDistributedBy().getType()).isEqualTo(DistributedByType.USER_TASK);

    final ReportResultResponseDto<List<HyperMapResultEntryDto>> result = evaluationResponse.getResult();
    ZonedDateTime startOfToday = truncateToStartOfUnit(OffsetDateTime.now(), ChronoUnit.DAYS);
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .processInstanceCountWithoutFilters(1L)
      .measure(ViewProperty.DURATION, AggregationType.AVERAGE, getUserTaskDurationTime())
        .groupByContains(localDateTimeToString(startOfToday))
          .distributedByContains(USER_TASK_1, expectedDuration, USER_TASK_1_NAME)
      .doAssert(result);
    // @formatter:on
  }

  @Test
  public void reportEvaluationForSeveralProcessDefinitions() {
    // given
    final String key1 = "key1";
    final String key2 = "key2";
    // freeze date to avoid instability when test runs on the edge of the day
    final OffsetDateTime now = DateCreationFreezer.dateFreezer().freezeDateAndReturn();
    final Double expectedDuration = 20.;
    final ProcessDefinitionEngineDto processDefinition1 = engineIntegrationExtension
      .deployProcessAndGetProcessDefinition(BpmnModels.getSingleUserTaskDiagram(key1, USER_TASK_1));
    final ProcessDefinitionEngineDto processDefinition2 = engineIntegrationExtension
      .deployProcessAndGetProcessDefinition(BpmnModels.getSingleUserTaskDiagram(key2, USER_TASK_2));

    final ProcessInstanceEngineDto processInstanceDto1 =
      engineIntegrationExtension.startProcessInstance(processDefinition1.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(processInstanceDto1.getId());
    changeDuration(processInstanceDto1, expectedDuration);
    final ProcessInstanceEngineDto processInstanceDto2 =
      engineIntegrationExtension.startProcessInstance(processDefinition2.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(processInstanceDto2.getId());
    changeDuration(processInstanceDto2, expectedDuration);
    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createGroupedByDayReport(processDefinition1);
    reportData.getDefinitions().add(createReportDataDefinitionDto(key2));
    AuthorizedProcessReportEvaluationResponseDto<List<HyperMapResultEntryDto>> evaluationResponse =
      reportClient.evaluateHyperMapReport(reportData);

    // then
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> result = evaluationResponse.getResult();
    ZonedDateTime startOfToday = truncateToStartOfUnit(now, ChronoUnit.DAYS);
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .processInstanceCountWithoutFilters(2L)
      .measure(ViewProperty.DURATION, AggregationType.AVERAGE, getUserTaskDurationTime())
        .groupByContains(localDateTimeToString(startOfToday))
          .distributedByContains(USER_TASK_1, expectedDuration, USER_TASK_1)
          .distributedByContains(USER_TASK_2, expectedDuration, USER_TASK_2)
      .doAssert(result);
    // @formatter:on
  }

  @Test
  public void simpleReportEvaluationById() {
    // given
    ProcessDefinitionEngineDto processDefinition = deployOneUserTaskDefinition();
    final ProcessInstanceEngineDto processInstance =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(processInstance.getId());

    final Double expectedDuration = 20.;
    changeDuration(processInstance, expectedDuration);

    importAllEngineEntitiesFromScratch();

    final ProcessReportDataDto reportData = createGroupedByDayReport(processDefinition);
    SingleProcessReportDefinitionRequestDto singleProcessReportDefinitionDto =
      new SingleProcessReportDefinitionRequestDto();
    singleProcessReportDefinitionDto.setData(reportData);
    final String reportId = reportClient.createSingleProcessReport(singleProcessReportDefinitionDto);

    // when
    final AuthorizedProcessReportEvaluationResponseDto<List<HyperMapResultEntryDto>> evaluationResponse =
      reportClient.evaluateHyperMapReportById(reportId);

    // then
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> result = evaluationResponse.getResult();
    ZonedDateTime startOfToday = truncateToStartOfUnit(OffsetDateTime.now(), ChronoUnit.DAYS);
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .processInstanceCountWithoutFilters(1L)
      .measure(ViewProperty.DURATION, AggregationType.AVERAGE, getUserTaskDurationTime())
        .groupByContains(localDateTimeToString(startOfToday))
          .distributedByContains(USER_TASK_1, expectedDuration, USER_TASK_1_NAME)
      .doAssert(result);
    // @formatter:on
  }

  @Test
  public void evaluateReportForMultipleEventsWithAllAggregationTypes() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();
    final OffsetDateTime today = OffsetDateTime.now();

    final ProcessInstanceEngineDto processInstance1 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(processInstance1.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(processInstance1.getId());
    changeModelElementDate(processInstance1, USER_TASK_1, today);
    changeModelElementDate(processInstance1, USER_TASK_2, today.minusDays(1));
    changeDuration(processInstance1, USER_TASK_1, 10.);
    changeDuration(processInstance1, USER_TASK_2, 10.);

    final ProcessInstanceEngineDto processInstance2 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(processInstance2.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(processInstance2.getId());
    changeModelElementDate(processInstance2, USER_TASK_1, today);
    changeModelElementDate(processInstance2, USER_TASK_2, today.minusDays(1));
    changeDuration(processInstance2, USER_TASK_1, 20.);
    changeDuration(processInstance2, USER_TASK_2, 20.);

    importAllEngineEntitiesFromScratch();

    final ProcessReportDataDto reportData = createGroupedByDayReport(processDefinition);
    reportData.getConfiguration().setAggregationTypes(getSupportedAggregationTypes());

    // when
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> result = reportClient.evaluateHyperMapReport(reportData)
      .getResult();

    // then
    assertThat(result.getMeasures())
      .extracting(MeasureResponseDto::getAggregationType)
      .containsExactly(getSupportedAggregationTypes());
    final HyperMapAsserter hyperMapAsserter = HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .processInstanceCountWithoutFilters(2L);
    result.getMeasures().forEach(measureResult -> {
      final AggregationDto aggType = measureResult.getAggregationType();
      // @formatter:off
      hyperMapAsserter
        .measure(ViewProperty.DURATION, aggType, getUserTaskDurationTime())
          .groupByContains(groupedByDayDateAsString(today.minusDays(1)))
            .distributedByContains(USER_TASK_1, null, USER_TASK_1_NAME)
            .distributedByContains(USER_TASK_2, calculateExpectedValueGivenDurations(10., 20.).get(aggType), USER_TASK_2_NAME)
          .groupByContains(groupedByDayDateAsString(today))
            .distributedByContains(USER_TASK_1, calculateExpectedValueGivenDurations(10., 20.).get(aggType), USER_TASK_1_NAME)
            .distributedByContains(USER_TASK_2, null, USER_TASK_2_NAME)
          .add()
        .add();
      // @formatter:on
    });
    hyperMapAsserter.doAssert(result);
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
    changeDuration(processInstance1, USER_TASK_1, 10.);
    changeDuration(processInstance1, USER_TASK_2, 10.);

    ProcessInstanceEngineDto processInstance2 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineIntegrationExtension.finishAllRunningUserTasks();
    changeModelElementDate(processInstance2, USER_TASK_1, referenceDate.minusDays(2));
    changeModelElementDate(processInstance2, USER_TASK_2, referenceDate.minusDays(4));
    changeDuration(processInstance2, USER_TASK_1, 10.);
    changeDuration(processInstance2, USER_TASK_2, 10.);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createGroupedByDayReport(processDefinition);
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> result = reportClient.evaluateHyperMapReport(reportData)
      .getResult();

    // then
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .processInstanceCountWithoutFilters(2L)
      .measure(ViewProperty.DURATION, AggregationType.AVERAGE, getUserTaskDurationTime())
        .groupByContains(groupedByDayDateAsString(referenceDate.minusDays(4)))
          .distributedByContains(USER_TASK_1, null, USER_TASK_1_NAME)
          .distributedByContains(USER_TASK_2, 10., USER_TASK_2_NAME)
        .groupByContains(groupedByDayDateAsString(referenceDate.minusDays(3)))
          .distributedByContains(USER_TASK_1, 10., USER_TASK_1_NAME)
          .distributedByContains(USER_TASK_2, null, USER_TASK_2_NAME)
        .groupByContains(groupedByDayDateAsString(referenceDate.minusDays(2)))
          .distributedByContains(USER_TASK_1, 10., USER_TASK_1_NAME)
          .distributedByContains(USER_TASK_2, null, USER_TASK_2_NAME)
        .groupByContains(groupedByDayDateAsString(referenceDate.minusDays(1)))
          .distributedByContains(USER_TASK_1, null, USER_TASK_1_NAME)
          .distributedByContains(USER_TASK_2, 10., USER_TASK_2_NAME)
      .doAssert(result);
    // @formatter:on
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
    changeDuration(processInstance1, USER_TASK_1, 10.);
    changeDuration(processInstance1, USER_TASK_2, 10.);

    ProcessInstanceEngineDto processInstance2 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineIntegrationExtension.finishAllRunningUserTasks();
    changeModelElementDate(processInstance2, USER_TASK_1, referenceDate.minusDays(2));
    changeModelElementDate(processInstance2, USER_TASK_2, referenceDate.minusDays(4));
    changeDuration(processInstance2, USER_TASK_1, 10.);
    changeDuration(processInstance2, USER_TASK_2, 10.);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createGroupedByDayReport(processDefinition);
    reportData.getConfiguration().setSorting(new ReportSortingDto(SORT_BY_KEY, SortOrder.DESC));
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> result = reportClient.evaluateHyperMapReport(reportData)
      .getResult();

    // then
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .processInstanceCountWithoutFilters(2L)
      .measure(ViewProperty.DURATION, AggregationType.AVERAGE, getUserTaskDurationTime())
        .groupByContains(groupedByDayDateAsString(referenceDate.minusDays(1)))
          .distributedByContains(USER_TASK_1, null, USER_TASK_1_NAME)
          .distributedByContains(USER_TASK_2, 10., USER_TASK_2_NAME)
        .groupByContains(groupedByDayDateAsString(referenceDate.minusDays(2)))
          .distributedByContains(USER_TASK_1, 10., USER_TASK_1_NAME)
          .distributedByContains(USER_TASK_2, null, USER_TASK_2_NAME)
        .groupByContains(groupedByDayDateAsString(referenceDate.minusDays(3)))
          .distributedByContains(USER_TASK_1, 10., USER_TASK_1_NAME)
          .distributedByContains(USER_TASK_2, null, USER_TASK_2_NAME)
        .groupByContains(groupedByDayDateAsString(referenceDate.minusDays(4)))
          .distributedByContains(USER_TASK_1, null, USER_TASK_1_NAME)
          .distributedByContains(USER_TASK_2, 10., USER_TASK_2_NAME)
      .doAssert(result);
    // @formatter:on
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
    changeModelElementDate(processInstance1, USER_TASK_1, referenceDate.minusDays(1));
    changeModelElementDate(processInstance1, USER_TASK_2, referenceDate.minusDays(1));
    changeDuration(processInstance1, USER_TASK_1, 20.);
    changeDuration(processInstance1, USER_TASK_2, 10.);

    ProcessInstanceEngineDto processInstance2 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineIntegrationExtension.finishAllRunningUserTasks();
    changeModelElementDate(processInstance2, USER_TASK_1, referenceDate.minusDays(2));
    changeModelElementDate(processInstance2, USER_TASK_2, referenceDate.minusDays(2));
    changeDuration(processInstance2, USER_TASK_1, 10.);
    changeDuration(processInstance2, USER_TASK_2, 20.);

    ProcessInstanceEngineDto processInstance3 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineIntegrationExtension.finishAllRunningUserTasks();
    changeModelElementDate(processInstance3, USER_TASK_1, referenceDate.minusDays(3));
    changeModelElementDate(processInstance3, USER_TASK_2, referenceDate.minusDays(3));
    changeDuration(processInstance3, USER_TASK_1, 30.);
    changeDuration(processInstance3, USER_TASK_2, 20.);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createGroupedByDayReport(processDefinition);
    reportData.getConfiguration().setSorting(new ReportSortingDto(SORT_BY_VALUE, SortOrder.DESC));
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> result = reportClient.evaluateHyperMapReport(reportData)
      .getResult();

    // then
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(3L)
      .processInstanceCountWithoutFilters(3L)
      .measure(ViewProperty.DURATION, AggregationType.AVERAGE, getUserTaskDurationTime())
        .groupByContains(groupedByDayDateAsString(referenceDate.minusDays(1)))
          .distributedByContains(USER_TASK_1, 20., USER_TASK_1_NAME)
          .distributedByContains(USER_TASK_2, 10., USER_TASK_2_NAME)
        .groupByContains(groupedByDayDateAsString(referenceDate.minusDays(2)))
          .distributedByContains(USER_TASK_1, 10., USER_TASK_1_NAME)
          .distributedByContains(USER_TASK_2, 20., USER_TASK_2_NAME)
        .groupByContains(groupedByDayDateAsString(referenceDate.minusDays(3)))
          .distributedByContains(USER_TASK_1, 30., USER_TASK_1_NAME)
          .distributedByContains(USER_TASK_2, 20., USER_TASK_2_NAME)
      .doAssert(result);
    // @formatter:on
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
    changeDuration(processInstance1, USER_TASK_2, 10.);

    ProcessInstanceEngineDto processInstance2 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineIntegrationExtension.finishAllRunningUserTasks();
    changeModelElementDate(processInstance2, USER_TASK_1, referenceDate.minusDays(1));
    changeModelElementDate(processInstance2, USER_TASK_2, referenceDate.minusDays(2));
    changeDuration(processInstance2, USER_TASK_1, 20.);
    changeDuration(processInstance2, USER_TASK_2, 20.);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createGroupedByDayReport(processDefinition);
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> result = reportClient.evaluateHyperMapReport(reportData)
      .getResult();

    // then
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .processInstanceCountWithoutFilters(2L)
      .measure(ViewProperty.DURATION, AggregationType.AVERAGE, getUserTaskDurationTime())
        .groupByContains(groupedByDayDateAsString(referenceDate.minusDays(2)))
          .distributedByContains(USER_TASK_1, null, USER_TASK_1_NAME)
          .distributedByContains(USER_TASK_2, 15., USER_TASK_2_NAME)
        .groupByContains(groupedByDayDateAsString(referenceDate.minusDays(1)))
          .distributedByContains(USER_TASK_1, 15., USER_TASK_1_NAME)
          .distributedByContains(USER_TASK_2, null, USER_TASK_2_NAME)
      .doAssert(result);
    // @formatter:on
  }

  @Test
  public void emptyIntervalBetweenTwoUserTaskDates() {
    // given
    final OffsetDateTime referenceDate = OffsetDateTime.now();
    ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();
    ProcessInstanceEngineDto processInstance1 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineIntegrationExtension.finishAllRunningUserTasks();
    changeModelElementDate(processInstance1, USER_TASK_1, referenceDate.minusDays(1));
    changeModelElementDate(processInstance1, USER_TASK_2, referenceDate.minusDays(3));
    changeDuration(processInstance1, USER_TASK_1, 10.);
    changeDuration(processInstance1, USER_TASK_2, 10.);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createGroupedByDayReport(processDefinition);
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> result = reportClient.evaluateHyperMapReport(reportData)
      .getResult();

    // then
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .processInstanceCountWithoutFilters(1L)
      .measure(ViewProperty.DURATION, AggregationType.AVERAGE, getUserTaskDurationTime())
        .groupByContains(groupedByDayDateAsString(referenceDate.minusDays(3)))
          .distributedByContains(USER_TASK_1, null, USER_TASK_1_NAME)
          .distributedByContains(USER_TASK_2, 10., USER_TASK_2_NAME)
        .groupByContains(groupedByDayDateAsString(referenceDate.minusDays(2)))
          .distributedByContains(USER_TASK_1, null, USER_TASK_1_NAME)
          .distributedByContains(USER_TASK_2, null, USER_TASK_2_NAME)
        .groupByContains(groupedByDayDateAsString(referenceDate.minusDays(1)))
          .distributedByContains(USER_TASK_1, 10., USER_TASK_1_NAME)
          .distributedByContains(USER_TASK_2, null, USER_TASK_2_NAME)
      .doAssert(result);
    // @formatter:on
  }

  @ParameterizedTest
  @MethodSource("staticAggregateByDateUnits")
  public void countGroupByDateUnit(final AggregateByDateUnit groupByDateUnit) {
    // given
    final ChronoUnit groupByUnitAsChrono = mapToChronoUnit(groupByDateUnit);
    final int groupingCount = 5;
    final OffsetDateTime referenceDate = OffsetDateTime.parse("2019-06-15T12:00:00+02:00");

    ProcessDefinitionEngineDto processDefinition = deployOneUserTaskDefinition();
    List<ProcessInstanceEngineDto> processInstanceDtos = IntStream.range(0, groupingCount)
      .mapToObj(i -> {
        ProcessInstanceEngineDto processInstanceEngineDto =
          engineIntegrationExtension.startProcessInstance(processDefinition.getId());
        engineIntegrationExtension.finishAllRunningUserTasks(processInstanceEngineDto.getId());
        processInstanceEngineDto.setProcessDefinitionKey(processDefinition.getKey());
        processInstanceEngineDto.setProcessDefinitionVersion(String.valueOf(processDefinition.getVersion()));
        return processInstanceEngineDto;
      })
      .collect(Collectors.toList());
    updateModelElementTimes(processInstanceDtos, referenceDate, groupByUnitAsChrono);
    processInstanceDtos.forEach(procInst -> changeDuration(procInst, 10.));

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReportData(processDefinition, groupByDateUnit);
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> result = reportClient.evaluateHyperMapReport(reportData)
      .getResult();

    // then
    HyperMapAsserter.GroupByAdder groupByAdder = HyperMapAsserter.asserter()
      .processInstanceCount(groupingCount)
      .processInstanceCountWithoutFilters(groupingCount)
      .measure(ViewProperty.DURATION, AggregationType.AVERAGE, getUserTaskDurationTime())
      .groupByContains(groupedByDateAsString(referenceDate.plus(0, groupByUnitAsChrono), groupByUnitAsChrono))
      .distributedByContains(USER_TASK_1, 10., USER_TASK_1_NAME);

    for (int i = 1; i < groupingCount; i++) {
      groupByAdder = groupByAdder
        .groupByContains(groupedByDateAsString(referenceDate.plus(i, groupByUnitAsChrono), groupByUnitAsChrono))
        .distributedByContains(USER_TASK_1, 10., USER_TASK_1_NAME);
    }
    groupByAdder.doAssert(result);
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
    changeDuration(processInstance2, USER_TASK_1, 50.);


    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createGroupedByDayReport(processDefinition1);
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> result = reportClient.evaluateHyperMapReport(reportData)
      .getResult();

    // then
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .processInstanceCountWithoutFilters(1L)
      .measure(ViewProperty.DURATION, AggregationType.AVERAGE, getUserTaskDurationTime())
      .groupByContains(groupedByDayDateAsString(referenceDate.minusDays(1)))
        .distributedByContains(USER_TASK_1, 10., USER_TASK_1_NAME)
      .doAssert(result);
    // @formatter:on
  }

  @Test
  public void filterWorks() {
    // given
    final OffsetDateTime referenceDate = OffsetDateTime.now();
    ProcessDefinitionEngineDto processDefinition = deployOneUserTaskDefinition();
    final ProcessInstanceEngineDto processInstance1 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();
    changeDuration(processInstance1, USER_TASK_1, 10.);
    engineIntegrationExtension.startProcessInstance(processDefinition.getId());

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createGroupedByDayReport(processDefinition);
    final List<ProcessFilterDto<?>> processFilterDtoList = ProcessFilterBuilder.filter()
      .completedInstancesOnly().add().buildList();
    reportData.setFilter(processFilterDtoList);
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> result = reportClient.evaluateHyperMapReport(reportData)
      .getResult();

    // then
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .processInstanceCountWithoutFilters(2L)
      .measure(ViewProperty.DURATION, AggregationType.AVERAGE, getUserTaskDurationTime())
      .groupByContains(groupedByDayDateAsString(referenceDate))
      .distributedByContains(USER_TASK_1, 10., USER_TASK_1_NAME)
      .doAssert(result);
    // @formatter:on
  }

  public static Stream<Arguments> viewLevelAssigneeFilterScenarios() {
    return Stream.of(
      Arguments.of(
        IN, new String[]{SECOND_USER}, 1L,
        Arrays.asList(Tuple.tuple(USER_TASK_1, null), Tuple.tuple(USER_TASK_2, 10.))
      ),
      Arguments.of(
        IN, new String[]{DEFAULT_USERNAME, SECOND_USER}, 1L,
        Arrays.asList(Tuple.tuple(USER_TASK_1, 10.), Tuple.tuple(USER_TASK_2, 10.))
      ),
      Arguments.of(
        NOT_IN,
        new String[]{SECOND_USER}, 1L,
        Arrays.asList(Tuple.tuple(USER_TASK_1, 10.), Tuple.tuple(USER_TASK_2, null))
      ),
      Arguments.of(
        NOT_IN, new String[]{DEFAULT_USERNAME, SECOND_USER}, 0L, Collections.emptyList())
    );
  }

  @ParameterizedTest
  @MethodSource("viewLevelAssigneeFilterScenarios")
  @SuppressWarnings(UNCHECKED_CAST)
  public void viewLevelFilterByAssigneeOnlyCountsUserTasksWithThatAssignee(final MembershipFilterOperator filterOperator,
                                                                           final String[] filterValues,
                                                                           final Long expectedInstanceCount,
                                                                           final List<Tuple> expectedResult) {
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
      .filter().assignee().ids(filterValues).operator(filterOperator)
      .filterLevel(FilterApplicationLevel.VIEW).add().buildList();
    reportData.setFilter(assigneeFilter);
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> result = reportClient.evaluateHyperMapReport(reportData)
      .getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(expectedInstanceCount);
    assertThat(result.getFirstMeasureData())
      .flatExtracting(HyperMapResultEntryDto::getValue)
      .extracting(MapResultEntryDto::getKey, MapResultEntryDto::getValue)
      .containsExactlyInAnyOrderElementsOf(expectedResult);
  }

  public static Stream<Arguments> instanceLevelAssigneeFilterScenarios() {
    return Stream.of(
      Arguments.of(
        IN, new String[]{SECOND_USER}, 1L, Arrays.asList(Tuple.tuple(USER_TASK_1, 10.), Tuple.tuple(USER_TASK_2, 10.))),
      Arguments.of(
        IN, new String[]{DEFAULT_USERNAME, SECOND_USER}, 2L,
        Arrays.asList(Tuple.tuple(USER_TASK_1, 15.), Tuple.tuple(USER_TASK_2, 20.))
      ),
      Arguments.of(
        NOT_IN, new String[]{SECOND_USER}, 2L,
        Arrays.asList(Tuple.tuple(USER_TASK_1, 15.), Tuple.tuple(USER_TASK_2, 20.))
      ),
      Arguments.of(NOT_IN, new String[]{DEFAULT_USERNAME, SECOND_USER}, 0L, Collections.emptyList())
    );
  }

  @ParameterizedTest
  @MethodSource("instanceLevelAssigneeFilterScenarios")
  @SuppressWarnings(UNCHECKED_CAST)
  public void instanceLevelFilterByAssigneeOnlyCountsUserTasksFromInstancesWithThatAssignee(final MembershipFilterOperator filterOperator,
                                                                                            final String[] filterValues,
                                                                                            final Long expectedInstanceCount,
                                                                                            final List<Tuple> expectedResult) {
    // given
    engineIntegrationExtension.addUser(SECOND_USER, SECOND_USERS_PASSWORD);
    engineIntegrationExtension.grantAllAuthorizations(SECOND_USER);

    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();
    final ProcessInstanceEngineDto firstInstance =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(DEFAULT_USERNAME, DEFAULT_PASSWORD, firstInstance.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(SECOND_USER, SECOND_USERS_PASSWORD, firstInstance.getId());
    final ProcessInstanceEngineDto secondInstance =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(DEFAULT_USERNAME, DEFAULT_PASSWORD, secondInstance.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(DEFAULT_USERNAME, DEFAULT_PASSWORD, secondInstance.getId());

    changeDuration(firstInstance, USER_TASK_1, 10.);
    changeDuration(firstInstance, USER_TASK_2, 10.);
    changeDuration(secondInstance, USER_TASK_1, 20.);
    changeDuration(secondInstance, USER_TASK_2, 30.);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createGroupedByDayReport(processDefinition);
    final List<ProcessFilterDto<?>> assigneeFilter = ProcessFilterBuilder
      .filter().assignee().ids(filterValues).operator(filterOperator)
      .filterLevel(FilterApplicationLevel.INSTANCE).add().buildList();
    reportData.setFilter(assigneeFilter);
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> result =
      reportClient.evaluateHyperMapReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(expectedInstanceCount);
    assertThat(result.getFirstMeasureData())
      .flatExtracting(HyperMapResultEntryDto::getValue)
      .extracting(MapResultEntryDto::getKey, MapResultEntryDto::getValue)
      .containsExactlyInAnyOrderElementsOf(expectedResult);
  }

  public static Stream<Arguments> viewLevelCandidateGroupFilterScenarios() {
    return Stream.of(
      Arguments.of(
        IN, new String[]{SECOND_CANDIDATE_GROUP_ID}, 1L,
        Arrays.asList(Tuple.tuple(USER_TASK_1, null), Tuple.tuple(USER_TASK_2, 10.))
      ),
      Arguments.of(
        IN, new String[]{FIRST_CANDIDATE_GROUP_ID, SECOND_CANDIDATE_GROUP_ID}, 1L,
        Arrays.asList(Tuple.tuple(USER_TASK_1, 10.), Tuple.tuple(USER_TASK_2, 10.))
      ),
      Arguments.of(
        NOT_IN, new String[]{SECOND_CANDIDATE_GROUP_ID}, 1L,
        Arrays.asList(Tuple.tuple(USER_TASK_1, 10.), Tuple.tuple(USER_TASK_2, null))
      ),
      Arguments.of(
        NOT_IN,
        new String[]{FIRST_CANDIDATE_GROUP_ID, SECOND_CANDIDATE_GROUP_ID},
        0L,
        Collections.emptyList()
      )
    );
  }

  @ParameterizedTest
  @MethodSource("viewLevelCandidateGroupFilterScenarios")
  @SuppressWarnings(UNCHECKED_CAST)
  public void viewLevelFilterByCandidateGroupOnlyCountsUserTasksWithThatCandidateGroup(final MembershipFilterOperator filterOperator,
                                                                                       final String[] filterValues,
                                                                                       final Long expectedInstanceCount,
                                                                                       final List<Tuple> expectedResult) {
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
      .filter().candidateGroups().ids(filterValues).operator(filterOperator)
      .filterLevel(FilterApplicationLevel.VIEW).add().buildList();
    reportData.setFilter(candidateGroupFilter);
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> result = reportClient.evaluateHyperMapReport(reportData)
      .getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(expectedInstanceCount);
    assertThat(result.getFirstMeasureData())
      .flatExtracting(HyperMapResultEntryDto::getValue)
      .extracting(MapResultEntryDto::getKey, MapResultEntryDto::getValue)
      .containsExactlyInAnyOrderElementsOf(expectedResult);
  }

  public static Stream<Arguments> viewLevelFlowNodeDurationFilterScenarios() {
    return Stream.of(
      Arguments.of(USER_TASK_1, GREATER_THAN, 1000L, 1L, Arrays.asList(
        Tuple.tuple(USER_TASK_1, 2000.), Tuple.tuple(USER_TASK_2, null))),
      Arguments.of(USER_TASK_2, GREATER_THAN, 500L, 1L, Arrays.asList(
        Tuple.tuple(USER_TASK_1, null), Tuple.tuple(USER_TASK_2, 1000.))),
      Arguments.of(USER_TASK_1, GREATER_THAN_EQUALS, 2000L, 1L, Arrays.asList(
        Tuple.tuple(USER_TASK_1, 2000.), Tuple.tuple(USER_TASK_2, null))),
      Arguments.of(USER_TASK_1, LESS_THAN_EQUALS, 2000L, 1L, Arrays.asList(
        Tuple.tuple(USER_TASK_1, 2000.), Tuple.tuple(USER_TASK_2, null))),
      Arguments.of(USER_TASK_2, LESS_THAN_EQUALS, 1000L, 1L, Arrays.asList(
        Tuple.tuple(USER_TASK_1, null), Tuple.tuple(USER_TASK_2, 1000.))),
      Arguments.of(USER_TASK_1, LESS_THAN, 2000L, 0L, Collections.emptyList())
    );
  }

  @ParameterizedTest
  @MethodSource("viewLevelFlowNodeDurationFilterScenarios")
  @SuppressWarnings(UNCHECKED_CAST)
  public void viewLevelFilterByFlowNodeDurationOnlyCountsUserTasksMatchingFilter(final String userTaskId,
                                                                                 final ComparisonOperator filterOperator,
                                                                                 final Long filterValueInMs,
                                                                                 final Long expectedInstanceCount,
                                                                                 final List<Tuple> expectedResult) {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();
    final ProcessInstanceEngineDto processInstanceDto = engineIntegrationExtension
      .startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineIntegrationExtension.finishAllRunningUserTasks();
    // We have to change the specific duration property for the test scenario
    changeDuration(processInstanceDto, USER_TASK_1, 2000.);
    changeDuration(processInstanceDto, USER_TASK_2, 1000.);
    // We also have to change both durations as the instance level filtering applies to the activities and the
    // view level filtering applies to the user tasks
    engineDatabaseExtension.changeFlowNodeTotalDuration(processInstanceDto.getId(), USER_TASK_1, 2000.);
    engineDatabaseExtension.changeFlowNodeTotalDuration(processInstanceDto.getId(), USER_TASK_2, 1000.);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createGroupedByDayReport(processDefinition);
    final List<ProcessFilterDto<?>> flowNodeDurationFilter = ProcessFilterBuilder.filter()
      .flowNodeDuration()
      .flowNode(userTaskId, DurationFilterDataDto.builder().unit(DurationUnit.MILLIS)
        .value(filterValueInMs).operator(filterOperator).build())
      .filterLevel(FilterApplicationLevel.VIEW)
      .add()
      .buildList();
    reportData.setFilter(flowNodeDurationFilter);
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> result = reportClient.evaluateHyperMapReport(reportData)
      .getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(expectedInstanceCount);
    assertThat(result.getFirstMeasureData())
      .flatExtracting(HyperMapResultEntryDto::getValue)
      .extracting(MapResultEntryDto::getKey, MapResultEntryDto::getValue)
      .containsExactlyInAnyOrderElementsOf(expectedResult);
  }

  public static Stream<Arguments> instanceLevelCandidateGroupFilterScenarios() {
    return Stream.of(
      Arguments.of(
        IN, new String[]{SECOND_CANDIDATE_GROUP_ID}, 1L,
        Arrays.asList(Tuple.tuple(USER_TASK_1, 10.), Tuple.tuple(USER_TASK_2, 10.))
      ),
      Arguments.of(
        IN, new String[]{FIRST_CANDIDATE_GROUP_ID, SECOND_CANDIDATE_GROUP_ID}, 2L,
        Arrays.asList(Tuple.tuple(USER_TASK_1, 15.), Tuple.tuple(USER_TASK_2, 20.))
      ),
      Arguments.of(
        NOT_IN, new String[]{SECOND_CANDIDATE_GROUP_ID}, 2L,
        Arrays.asList(Tuple.tuple(USER_TASK_1, 15.), Tuple.tuple(USER_TASK_2, 20.))
      ),
      Arguments.of(
        NOT_IN,
        new String[]{FIRST_CANDIDATE_GROUP_ID, SECOND_CANDIDATE_GROUP_ID},
        0L,
        Collections.emptyList()
      )
    );
  }

  @ParameterizedTest
  @MethodSource("instanceLevelCandidateGroupFilterScenarios")
  @SuppressWarnings(UNCHECKED_CAST)
  public void instanceLevelFilterByCandidateGroupOnlyCountsUserTasksForInstancesWithThatCandidateGroup(final MembershipFilterOperator filterOperator,
                                                                                                       final String[] filterValues,
                                                                                                       final Long expectedInstanceCount,
                                                                                                       final List<Tuple> expectedResult) {
    // given
    engineIntegrationExtension.createGroup(FIRST_CANDIDATE_GROUP_ID);
    engineIntegrationExtension.createGroup(SECOND_CANDIDATE_GROUP_ID);

    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();
    final ProcessInstanceEngineDto firstInstance = engineIntegrationExtension
      .startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP_ID);
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(SECOND_CANDIDATE_GROUP_ID);
    engineIntegrationExtension.finishAllRunningUserTasks();
    final ProcessInstanceEngineDto secondInstance = engineIntegrationExtension
      .startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP_ID);
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP_ID);
    engineIntegrationExtension.finishAllRunningUserTasks();

    changeDuration(firstInstance, USER_TASK_1, 10.);
    changeDuration(firstInstance, USER_TASK_2, 10.);
    changeDuration(secondInstance, USER_TASK_1, 20.);
    changeDuration(secondInstance, USER_TASK_2, 30.);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createGroupedByDayReport(processDefinition);
    final List<ProcessFilterDto<?>> candidateGroupFilter = ProcessFilterBuilder
      .filter().candidateGroups().ids(filterValues).operator(filterOperator)
      .filterLevel(FilterApplicationLevel.INSTANCE).add().buildList();
    reportData.setFilter(candidateGroupFilter);
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> result = reportClient.evaluateHyperMapReport(reportData)
      .getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(expectedInstanceCount);
    assertThat(result.getFirstMeasureData())
      .flatExtracting(HyperMapResultEntryDto::getValue)
      .extracting(MapResultEntryDto::getKey, MapResultEntryDto::getValue)
      .containsExactlyInAnyOrderElementsOf(expectedResult);
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
    Map<String, OffsetDateTime> updates = new HashMap<>();
    OffsetDateTime startOfToday = OffsetDateTime.now().truncatedTo(ChronoUnit.DAYS);
    updates.put(processInstanceDto1.getId(), startOfToday);
    updates.put(processInstanceDto2.getId(), startOfToday);
    updates.put(processInstanceDto3.getId(), startOfToday.minusDays(1));
    engineIntegrationExtension.finishAllRunningUserTasks();
    changeModelElementDates(updates);
    changeDuration(processInstanceDto1, 10.);
    changeDuration(processInstanceDto2, 10.);
    changeDuration(processInstanceDto3, 20.);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReportData(processDefinition, AggregateByDateUnit.AUTOMATIC);
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> result = reportClient.evaluateHyperMapReport(reportData)
      .getResult();

    // then
    final List<HyperMapResultEntryDto> resultData = result.getFirstMeasureData();
    assertThat(resultData).hasSize(NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION);
    assertFirstValueEquals(resultData, 20.);
    assertLastValueEquals(resultData, 10.);
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
    Map<String, OffsetDateTime> updates = new HashMap<>();
    OffsetDateTime startOfToday = OffsetDateTime.now().truncatedTo(ChronoUnit.DAYS);
    updates.put(processInstanceDto1.getId(), startOfToday);
    updates.put(processInstanceDto2.getId(), startOfToday.plusDays(2));
    updates.put(processInstanceDto3.getId(), startOfToday.plusDays(5));
    engineIntegrationExtension.finishAllRunningUserTasks();
    changeModelElementDates(updates);
    changeDuration(processInstanceDto1, 10.);
    changeDuration(processInstanceDto2, 20.);
    changeDuration(processInstanceDto3, 50.);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReportData(processDefinition, AggregateByDateUnit.AUTOMATIC);
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> result = reportClient.evaluateHyperMapReport(reportData)
      .getResult();

    // then
    final List<HyperMapResultEntryDto> resultData = result.getFirstMeasureData();
    assertThat(resultData).hasSize(NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION);
    assertFirstValueEquals(resultData, 10.);
    assertLastValueEquals(resultData, 50.);
    final int sumOfAllValues = resultData.stream()
      .map(HyperMapResultEntryDto::getValue)
      .flatMap(List::stream)
      .filter(Objects::nonNull)
      .map(MapResultEntryDto::getValue)
      .filter(Objects::nonNull)
      .mapToInt(Double::intValue).sum();
    assertThat(sumOfAllValues).isEqualTo(80);
  }

  @Test
  public void automaticIntervalSelection_forOneDataPoint() {
    // given there is only one data point
    final ProcessDefinitionEngineDto processDefinition = deployOneUserTaskDefinition();
    engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();
    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReportData(processDefinition, AggregateByDateUnit.AUTOMATIC);
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> result = reportClient.evaluateHyperMapReport(reportData)
      .getResult();

    // then the single data point should be grouped by month
    final List<HyperMapResultEntryDto> resultData = result.getFirstMeasureData();
    ZonedDateTime nowStrippedToMonth = truncateToStartOfUnit(OffsetDateTime.now(), ChronoUnit.MONTHS);
    String nowStrippedToMonthAsString = localDateTimeToString(nowStrippedToMonth);
    assertThat(resultData).hasSize(1);
    assertThat(resultData).first().extracting(HyperMapResultEntryDto::getKey).isEqualTo(nowStrippedToMonthAsString);
  }

  @Test
  public void allVersionsRespectLatestNodesOnlyWhereLatestHasMoreNodes() {
    // given
    ProcessDefinitionEngineDto processDefinition1 = deployOneUserTaskDefinition();
    final ProcessInstanceEngineDto processInstance1 =
      engineIntegrationExtension.startProcessInstance(processDefinition1.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();
    changeDuration(processInstance1, 10.);

    ProcessDefinitionEngineDto latestDefinition = deployTwoUserTasksDefinition();
    final ProcessInstanceEngineDto processInstance2 =
      engineIntegrationExtension.startProcessInstance(latestDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineIntegrationExtension.finishAllRunningUserTasks();
    changeDuration(processInstance2, 20.);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createGroupedByDayReport(latestDefinition);
    reportData.setProcessDefinitionVersion(ALL_VERSIONS);
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> result = reportClient.evaluateHyperMapReport(reportData)
      .getResult();

    // then
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .processInstanceCountWithoutFilters(2L)
      .measure(ViewProperty.DURATION, AggregationType.AVERAGE, getUserTaskDurationTime())
        .groupByContains(groupedByDayDateAsString(OffsetDateTime.now()))
          .distributedByContains(USER_TASK_1, 15., USER_TASK_1_NAME)
          .distributedByContains(USER_TASK_2, 20., USER_TASK_2_NAME)
      .doAssert(result);
    // @formatter:on
  }

  @Test
  public void multipleVersionsRespectLatestNodesOnlyWhereLatestHasMoreNodes() {
    // given
    ProcessDefinitionEngineDto firstDefinition = deployOneUserTaskDefinition();
    final ProcessInstanceEngineDto processInstance1 =
      engineIntegrationExtension.startProcessInstance(firstDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();
    changeDuration(processInstance1, 10.);

    ProcessDefinitionEngineDto latestDefinition = deployTwoUserTasksDefinition();
    final ProcessInstanceEngineDto processInstance2 =
      engineIntegrationExtension.startProcessInstance(latestDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineIntegrationExtension.finishAllRunningUserTasks();
    changeDuration(processInstance2, 20.);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createGroupedByDayReport(latestDefinition);
    reportData.setProcessDefinitionVersions(Arrays.asList(
      firstDefinition.getVersionAsString(),
      latestDefinition.getVersionAsString()
    ));
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> result = reportClient.evaluateHyperMapReport(reportData)
      .getResult();

    // then
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .processInstanceCountWithoutFilters(2L)
      .measure(ViewProperty.DURATION, AggregationType.AVERAGE, getUserTaskDurationTime())
        .groupByContains(groupedByDayDateAsString(OffsetDateTime.now()))
          .distributedByContains(USER_TASK_1, 15., USER_TASK_1_NAME)
          .distributedByContains(USER_TASK_2, 20., USER_TASK_2_NAME)
      .doAssert(result);
    // @formatter:on
  }

  @Test
  public void allVersionsRespectLatestNodesOnlyWhereLatestHasFewerNodes() {
    // given
    ProcessDefinitionEngineDto processDefinition1 = deployTwoUserTasksDefinition();
    final ProcessInstanceEngineDto processInstance1 =
      engineIntegrationExtension.startProcessInstance(processDefinition1.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineIntegrationExtension.finishAllRunningUserTasks();
    changeDuration(processInstance1, 10.);

    ProcessDefinitionEngineDto latestDefinition = deployOneUserTaskDefinition();
    final ProcessInstanceEngineDto processInstance2 =
      engineIntegrationExtension.startProcessInstance(latestDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();
    changeDuration(processInstance2, 20.);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createGroupedByDayReport(latestDefinition);
    reportData.setProcessDefinitionVersion(ALL_VERSIONS);
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> result = reportClient.evaluateHyperMapReport(reportData)
      .getResult();

    // then
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .processInstanceCountWithoutFilters(2L)
      .measure(ViewProperty.DURATION, AggregationType.AVERAGE, getUserTaskDurationTime())
        .groupByContains(groupedByDayDateAsString(OffsetDateTime.now()))
          .distributedByContains(USER_TASK_1, 15., USER_TASK_1_NAME)
      .doAssert(result);
    // @formatter:on
  }

  @Test
  public void multipleVersionsRespectLatestNodesOnlyWhereLatestHasFewerNodes() {
    // given
    ProcessDefinitionEngineDto firstDefinition = deployTwoUserTasksDefinition();
    final ProcessInstanceEngineDto processInstance1 =
      engineIntegrationExtension.startProcessInstance(firstDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineIntegrationExtension.finishAllRunningUserTasks();
    changeDuration(processInstance1, 10.);

    ProcessDefinitionEngineDto latestDefinition = deployOneUserTaskDefinition();
    final ProcessInstanceEngineDto processInstance2 =
      engineIntegrationExtension.startProcessInstance(latestDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();
    changeDuration(processInstance2, 20.);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createGroupedByDayReport(latestDefinition);
    reportData.setProcessDefinitionVersions(Arrays.asList(
      firstDefinition.getVersionAsString(),
      latestDefinition.getVersionAsString()
    ));
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> result = reportClient.evaluateHyperMapReport(reportData)
      .getResult();

    // then
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .processInstanceCountWithoutFilters(2L)
      .measure(ViewProperty.DURATION, AggregationType.AVERAGE, getUserTaskDurationTime())
        .groupByContains(groupedByDayDateAsString(OffsetDateTime.now()))
          .distributedByContains(USER_TASK_1, 15., USER_TASK_1_NAME)
      .doAssert(result);
    // @formatter:on
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

  @Override
  protected ProcessReportDataDto createReportData(final String processDefinitionKey, final List<String> versions,
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

  protected abstract UserTaskDurationTime getUserTaskDurationTime();

  @Override
  protected void changeModelElementDates(final Map<String, OffsetDateTime> updates) {
    engineDatabaseExtension.changeAllFlowNodeStartDates(updates);
    engineDatabaseExtension.changeAllFlowNodeEndDates(updates);
  }

  @Override
  protected void changeModelElementDate(final ProcessInstanceEngineDto processInstance,
                                        final String userTaskKey,
                                        final OffsetDateTime dateToChangeTo) {
    engineDatabaseExtension.changeFlowNodeStartDate(processInstance.getId(), userTaskKey, dateToChangeTo);
    engineDatabaseExtension.changeFlowNodeEndDate(processInstance.getId(), userTaskKey, dateToChangeTo);
  }

}
