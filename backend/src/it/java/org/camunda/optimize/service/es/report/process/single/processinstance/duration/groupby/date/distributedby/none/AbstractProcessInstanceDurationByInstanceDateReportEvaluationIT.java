/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process.single.processinstance.duration.groupby.date.distributedby.none;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.ReportConstants;
import org.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DurationFilterUnit;
import org.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.value.DateGroupByValueDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto;
import org.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResponseDto;
import org.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
import org.camunda.optimize.dto.optimize.rest.report.measure.MeasureResponseDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.report.process.AbstractProcessDefinitionIT;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.google.common.collect.Lists.newArrayList;
import static java.time.temporal.ChronoUnit.MILLIS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.ComparisonOperator.LESS_THAN;
import static org.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnitMapper.mapToChronoUnit;
import static org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto.SORT_BY_KEY;
import static org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto.SORT_BY_VALUE;
import static org.camunda.optimize.test.util.DateCreationFreezer.dateFreezer;
import static org.camunda.optimize.test.util.DateModificationHelper.truncateToStartOfUnit;
import static org.camunda.optimize.test.util.DurationAggregationUtil.calculateExpectedValueGivenDurations;
import static org.camunda.optimize.test.util.DurationAggregationUtil.calculateExpectedValueGivenDurationsDefaultAggr;
import static org.camunda.optimize.test.util.DurationAggregationUtil.getSupportedAggregationTypes;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION;

public abstract class AbstractProcessInstanceDurationByInstanceDateReportEvaluationIT
  extends AbstractProcessDefinitionIT {

  protected abstract ProcessReportDataType getTestReportDataType();

  protected abstract ProcessGroupByType getGroupByType();

  protected void adjustProcessInstanceDates(String processInstanceId,
                                            OffsetDateTime refDate,
                                            long daysToShift,
                                            Long durationInSec) {
    OffsetDateTime shiftedEndDate = refDate.plusDays(daysToShift);
    if (durationInSec != null) {
      engineDatabaseExtension.changeProcessInstanceStartDate(
        processInstanceId,
        shiftedEndDate.minusSeconds(durationInSec)
      );
    }
    engineDatabaseExtension.changeProcessInstanceEndDate(processInstanceId, shiftedEndDate);
  }

  protected ProcessDefinitionEngineDto deployTwoRunningAndOneCompletedUserTaskProcesses(
    final OffsetDateTime now) {
    final ProcessDefinitionEngineDto processDefinition = deploySimpleOneUserTasksDefinition();

    final ProcessInstanceEngineDto processInstance1 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(processInstance1.getId());

    engineDatabaseExtension.changeProcessInstanceStartDate(processInstance1.getId(), now.minusSeconds(1));
    engineDatabaseExtension.changeProcessInstanceEndDate(processInstance1.getId(), now);

    final ProcessInstanceEngineDto processInstance2 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineDatabaseExtension.changeProcessInstanceStartDate(processInstance2.getId(), now.minusDays(1));
    final ProcessInstanceEngineDto processInstance3 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineDatabaseExtension.changeProcessInstanceStartDate(processInstance3.getId(), now.minusDays(2));
    return processDefinition;
  }

  @Test
  public void simpleReportEvaluation() {
    // given
    OffsetDateTime referenceDate = OffsetDateTime.now();
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    adjustProcessInstanceDates(processInstanceDto.getId(), referenceDate, 0L, 1L);
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder.createReportData()
      .setGroupByDateInterval(AggregateByDateUnit.DAY)
      .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
      .setReportDataType(getTestReportDataType())
      .build();
    AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResponse =
      reportClient.evaluateMapReport(reportData);

    // then
    final ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey()).isEqualTo(processInstanceDto.getProcessDefinitionKey());
    assertThat(resultReportDataDto.getDefinitionVersions()).contains(processInstanceDto.getProcessDefinitionVersion());
    assertThat(resultReportDataDto.getView()).isNotNull();
    assertThat(resultReportDataDto.getView().getEntity()).isEqualTo(ProcessViewEntity.PROCESS_INSTANCE);
    assertThat(resultReportDataDto.getView().getFirstProperty()).isEqualTo(ViewProperty.DURATION);
    assertThat(resultReportDataDto.getGroupBy().getType()).isEqualTo(getGroupByType());
    assertThat(((DateGroupByValueDto) resultReportDataDto.getGroupBy()
      .getValue()).getUnit()).isEqualTo(AggregateByDateUnit.DAY);
    assertThat(evaluationResponse.getResult().getInstanceCount()).isEqualTo(1L);
    assertThat(evaluationResponse.getResult().getFirstMeasureData()).isNotNull();
    assertThat(evaluationResponse.getResult().getFirstMeasureData()).hasSize(1);

    final List<MapResultEntryDto> resultData = evaluationResponse.getResult().getFirstMeasureData();
    ZonedDateTime startOfToday = truncateToStartOfUnit(referenceDate, ChronoUnit.DAYS);
    assertThat(resultData.get(0).getKey()).isEqualTo(localDateTimeToString(startOfToday));
    assertThat(resultData.get(0).getValue()).isEqualTo(1000.);

  }

  @Test
  public void simpleReportEvaluationById() {
    // given
    OffsetDateTime referenceDate = OffsetDateTime.now();
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    adjustProcessInstanceDates(processInstanceDto.getId(), referenceDate, 0L, 1L);

    importAllEngineEntitiesFromScratch();

    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
      .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
      .setReportDataType(getTestReportDataType())
      .setGroupByDateInterval(AggregateByDateUnit.DAY)
      .build();

    // when
    String reportId = createNewReport(reportData);
    AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResponse =
      reportClient.evaluateMapReportById(reportId);

    // then
    final ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey()).isEqualTo(processInstanceDto.getProcessDefinitionKey());
    assertThat(resultReportDataDto.getDefinitionVersions()).contains(processInstanceDto.getProcessDefinitionVersion());
    assertThat(resultReportDataDto.getView()).isNotNull();
    assertThat(resultReportDataDto.getView().getEntity()).isEqualTo(ProcessViewEntity.PROCESS_INSTANCE);
    assertThat(resultReportDataDto.getView().getFirstProperty()).isEqualTo(ViewProperty.DURATION);
    assertThat(resultReportDataDto.getGroupBy().getType()).isEqualTo(getGroupByType());
    assertThat(((DateGroupByValueDto) resultReportDataDto.getGroupBy()
      .getValue()).getUnit()).isEqualTo(AggregateByDateUnit.DAY);
    assertThat(evaluationResponse.getResult().getFirstMeasureData()).isNotNull();
    assertThat(evaluationResponse.getResult().getFirstMeasureData()).hasSize(1);

    final List<MapResultEntryDto> resultData = evaluationResponse.getResult().getFirstMeasureData();
    ZonedDateTime startOfToday = truncateToStartOfUnit(referenceDate, ChronoUnit.DAYS);
    assertThat(resultData.get(0).getKey()).isEqualTo(localDateTimeToString(startOfToday));
    assertThat(resultData.get(0).getValue()).isEqualTo(1000.);
  }

  @Test
  public void evaluateReportForMultipleEventsWithAllAggregationTypes() {
    // given
    OffsetDateTime referenceDate = OffsetDateTime.now();
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    String processDefinitionKey = processInstanceDto.getProcessDefinitionKey();
    String processDefinitionVersion = processInstanceDto.getProcessDefinitionVersion();

    adjustProcessInstanceDates(processInstanceDto.getId(), referenceDate, 0L, 1L);
    processInstanceDto = engineIntegrationExtension.startProcessInstance(processInstanceDto.getDefinitionId());
    adjustProcessInstanceDates(processInstanceDto.getId(), referenceDate, 0L, 9L);
    processInstanceDto = engineIntegrationExtension.startProcessInstance(processInstanceDto.getDefinitionId());
    adjustProcessInstanceDates(processInstanceDto.getId(), referenceDate, 0L, 2L);
    ProcessInstanceEngineDto processInstanceDto3 =
      engineIntegrationExtension.startProcessInstance(processInstanceDto.getDefinitionId());
    adjustProcessInstanceDates(processInstanceDto3.getId(), referenceDate, -1L, 1L);

    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReportDataSortedDesc(
      processDefinitionKey,
      processDefinitionVersion,
      getTestReportDataType(),
      AggregateByDateUnit.DAY
    );
    reportData.getConfiguration().setAggregationTypes(getSupportedAggregationTypes());

    final AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResponse =
      reportClient.evaluateMapReport(reportData);

    // then
    assertDurationMapReportResults(evaluationResponse, new Double[]{1000., 9000., 2000.});
  }

  @Test
  public void resultIsSortedInAscendingOrder() {
    // given
    OffsetDateTime referenceDate = OffsetDateTime.now();
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    String processDefinitionKey = processInstanceDto.getProcessDefinitionKey();
    String processDefinitionVersion = processInstanceDto.getProcessDefinitionVersion();

    adjustProcessInstanceDates(processInstanceDto.getId(), referenceDate, 0L, 1L);
    processInstanceDto = engineIntegrationExtension.startProcessInstance(processInstanceDto.getDefinitionId());
    adjustProcessInstanceDates(processInstanceDto.getId(), referenceDate, -2L, 3L);
    ProcessInstanceEngineDto processInstanceDto3 =
      engineIntegrationExtension.startProcessInstance(processInstanceDto.getDefinitionId());
    adjustProcessInstanceDates(processInstanceDto3.getId(), referenceDate, -1L, 1L);


    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder.createReportData()
      .setGroupByDateInterval(AggregateByDateUnit.DAY)
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersion(processDefinitionVersion)
      .setReportDataType(getTestReportDataType())
      .build();
    ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    final List<MapResultEntryDto> resultData = result.getFirstMeasureData();
    assertThat(resultData).hasSize(3);
    final List<String> resultKeys = resultData.stream().map(MapResultEntryDto::getKey).collect(Collectors.toList());
    // expect descending order
    assertThat(resultKeys).isSortedAccordingTo(Comparator.naturalOrder());
  }

  @Test
  public void customOrderOnResultKeyIsApplied() {
    // given
    final OffsetDateTime referenceDate = OffsetDateTime.now();
    final ProcessInstanceEngineDto processInstanceDto1 = deployAndStartSimpleServiceTaskProcess();
    final String processDefinitionId = processInstanceDto1.getDefinitionId();
    final String processDefinitionKey = processInstanceDto1.getProcessDefinitionKey();
    final String processDefinitionVersion = processInstanceDto1.getProcessDefinitionVersion();
    adjustProcessInstanceDates(processInstanceDto1.getId(), referenceDate, 0L, 1L);

    final ProcessInstanceEngineDto processInstanceDto2 =
      engineIntegrationExtension.startProcessInstance(processDefinitionId);
    adjustProcessInstanceDates(processInstanceDto2.getId(), referenceDate, -2L, 3L);

    final ProcessInstanceEngineDto processInstanceDto3 =
      engineIntegrationExtension.startProcessInstance(processDefinitionId);
    adjustProcessInstanceDates(processInstanceDto3.getId(), referenceDate, -1L, 1L);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder.createReportData()
      .setGroupByDateInterval(AggregateByDateUnit.DAY)
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersion(processDefinitionVersion)
      .setReportDataType(getTestReportDataType())
      .build();
    reportData.getConfiguration().setSorting(new ReportSortingDto(SORT_BY_KEY, SortOrder.ASC));
    final ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData)
      .getResult();

    // then
    final List<MapResultEntryDto> resultData = result.getFirstMeasureData();
    assertThat(resultData).hasSize(3);
    final List<String> resultKeys = resultData.stream().map(MapResultEntryDto::getKey).collect(Collectors.toList());
    // expect ascending order
    assertThat(resultKeys).isSortedAccordingTo(Comparator.naturalOrder());
  }

  @Test
  public void customOrderOnResultValueIsApplied() {
    // given
    final OffsetDateTime referenceDate = OffsetDateTime.now();
    final ProcessInstanceEngineDto instance1 = deployAndStartSimpleServiceTaskProcess();
    final String processDefinitionId = instance1.getDefinitionId();
    final String processDefinitionKey = instance1.getProcessDefinitionKey();
    final String processDefinitionVersion = instance1.getProcessDefinitionVersion();
    adjustProcessInstanceDates(instance1.getId(), referenceDate, 0L, 1L);

    final ProcessInstanceEngineDto instance2 = engineIntegrationExtension.startProcessInstance(processDefinitionId);
    adjustProcessInstanceDates(instance2.getId(), referenceDate, -1L, 2L);
    final ProcessInstanceEngineDto instance3 = engineIntegrationExtension.startProcessInstance(processDefinitionId);
    adjustProcessInstanceDates(instance3.getId(), referenceDate, -1L, 100L);
    final ProcessInstanceEngineDto instance4 = engineIntegrationExtension.startProcessInstance(processDefinitionId);
    adjustProcessInstanceDates(instance4.getId(), referenceDate, -2L, 1L);
    final ProcessInstanceEngineDto instance5 = engineIntegrationExtension.startProcessInstance(processDefinitionId);
    adjustProcessInstanceDates(instance5.getId(), referenceDate, -2L, 2L);
    final ProcessInstanceEngineDto instance6 = engineIntegrationExtension.startProcessInstance(processDefinitionId);
    adjustProcessInstanceDates(instance6.getId(), referenceDate, -2L, 3L);
    final ProcessInstanceEngineDto instance7 = engineIntegrationExtension.startProcessInstance(processDefinitionId);
    adjustProcessInstanceDates(instance7.getId(), referenceDate, -2L, 4L);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder.createReportData()
      .setGroupByDateInterval(AggregateByDateUnit.DAY)
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersion(processDefinitionVersion)
      .setReportDataType(getTestReportDataType())
      .build();
    reportData.getConfiguration().setSorting(new ReportSortingDto(SORT_BY_VALUE, SortOrder.ASC));
    reportData.getConfiguration().setAggregationTypes(getSupportedAggregationTypes());
    final ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData)
      .getResult();

    // then
    assertThat(result.getMeasures())
      .extracting(MeasureResponseDto::getAggregationType)
      .containsExactly(getSupportedAggregationTypes());
    result.getMeasures().forEach(measureResult -> {
      final List<MapResultEntryDto> measureData = measureResult.getData();
      assertThat(measureData)
        .hasSize(3)
        .extracting(MapResultEntryDto::getValue)
        .isSortedAccordingTo(Comparator.naturalOrder());
    });
  }

  @Test
  public void emptyIntervalBetweenTwoProcessInstances() {
    // given
    OffsetDateTime referenceDate = OffsetDateTime.now();
    ProcessInstanceEngineDto instance = deployAndStartSimpleServiceTaskProcess();
    String processDefinitionKey = instance.getProcessDefinitionKey();
    String processDefinitionVersion = instance.getProcessDefinitionVersion();

    adjustProcessInstanceDates(instance.getId(), referenceDate, 0L, 1L);
    instance = engineIntegrationExtension.startProcessInstance(instance.getDefinitionId());
    adjustProcessInstanceDates(instance.getId(), referenceDate, 0L, 9L);
    instance = engineIntegrationExtension.startProcessInstance(instance.getDefinitionId());
    adjustProcessInstanceDates(instance.getId(), referenceDate, 0L, 2L);
    ProcessInstanceEngineDto instance3 = engineIntegrationExtension.startProcessInstance(instance.getDefinitionId());
    adjustProcessInstanceDates(instance3.getId(), referenceDate, -2L, 1L);

    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReportDataSortedDesc(
      processDefinitionKey,
      processDefinitionVersion,
      getTestReportDataType(),
      AggregateByDateUnit.DAY
    );
    ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    final List<MapResultEntryDto> resultData = result.getFirstMeasureData();
    assertThat(resultData).hasSize(3);
    ZonedDateTime startOfToday = truncateToStartOfUnit(referenceDate, ChronoUnit.DAYS);
    assertThat(resultData.get(0).getKey()).isEqualTo(localDateTimeToString(startOfToday));
    assertThat(resultData.get(0).getValue())
      .isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(1000., 9000., 2000.));
    assertThat(resultData.get(1).getKey()).isEqualTo(localDateTimeToString(startOfToday.minusDays(1)));
    assertThat(resultData.get(1).getValue()).isNull();
    assertThat(resultData.get(2).getKey()).isEqualTo(localDateTimeToString(startOfToday.minusDays(2)));
    assertThat(resultData.get(2).getValue()).isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(1000.));
  }

  @Test
  public void automaticIntervalSelectionWorks() {
    // given
    OffsetDateTime referenceDate = OffsetDateTime.now();
    ProcessInstanceEngineDto instance = deployAndStartSimpleServiceTaskProcess();
    String processDefinitionKey = instance.getProcessDefinitionKey();
    String processDefinitionVersion = instance.getProcessDefinitionVersion();

    adjustProcessInstanceDates(instance.getId(), referenceDate, 0L, 1L);
    instance = engineIntegrationExtension.startProcessInstance(instance.getDefinitionId());
    adjustProcessInstanceDates(instance.getId(), referenceDate, 0L, 9L);
    instance = engineIntegrationExtension.startProcessInstance(instance.getDefinitionId());
    adjustProcessInstanceDates(instance.getId(), referenceDate, 0L, 2L);
    ProcessInstanceEngineDto instance3 = engineIntegrationExtension.startProcessInstance(instance.getDefinitionId());
    adjustProcessInstanceDates(instance3.getId(), referenceDate, -2L, 1L);

    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReportDataSortedDesc(
      processDefinitionKey,
      processDefinitionVersion,
      getTestReportDataType(),
      AggregateByDateUnit.AUTOMATIC
    );
    ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    final List<MapResultEntryDto> resultData = result.getFirstMeasureData();
    assertThat(resultData).hasSize(NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION);
    assertThat(resultData.get(0).getValue())
      .isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(1000., 9000., 2000.));
    assertThat(resultData.stream().map(MapResultEntryDto::getValue).filter(v -> v != null && v > 0L).count())
      .isEqualTo(2L);
    assertThat(resultData.get(resultData.size() - 1).getValue())
      .isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(1000.));
  }

  @Test
  public void calculateDurationForCompletedProcessInstances() {
    // given
    OffsetDateTime now = OffsetDateTime.now();
    LocalDateUtil.setCurrentTime(now);

    // 1 completed proc inst
    ProcessInstanceEngineDto completeProcessInstanceDto = deployAndStartSimpleUserTaskProcess();
    engineIntegrationExtension.finishAllRunningUserTasks(completeProcessInstanceDto.getId());

    OffsetDateTime completedProcInstStartDate = now.minusDays(2);
    OffsetDateTime completedProcInstEndDate = completedProcInstStartDate.plus(1000, MILLIS);
    engineDatabaseExtension.changeProcessInstanceStartDate(
      completeProcessInstanceDto.getId(),
      completedProcInstStartDate
    );
    engineDatabaseExtension.changeProcessInstanceEndDate(completeProcessInstanceDto.getId(), completedProcInstEndDate);

    // 1 running proc inst
    final ProcessInstanceEngineDto newRunningProcessInstance =
      engineIntegrationExtension.startProcessInstance(completeProcessInstanceDto.getDefinitionId());
    OffsetDateTime runningProcInstStartDate = now.minusDays(1);
    engineDatabaseExtension.changeProcessInstanceStartDate(newRunningProcessInstance.getId(), runningProcInstStartDate);

    importAllEngineEntitiesFromScratch();

    // when
    final List<ProcessFilterDto<?>> completedInstanceFilter = ProcessFilterBuilder.filter()
      .completedInstancesOnly()
      .add()
      .buildList();

    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder.createReportData()
      .setReportDataType(getTestReportDataType())
      .setProcessDefinitionVersion(completeProcessInstanceDto.getProcessDefinitionVersion())
      .setProcessDefinitionKey(completeProcessInstanceDto.getProcessDefinitionKey())
      .setGroupByDateInterval(AggregateByDateUnit.DAY)
      .setFilter(completedInstanceFilter)
      .build();
    ReportResultResponseDto<List<MapResultEntryDto>> resultDto = reportClient.evaluateMapReport(reportData).getResult();

    // then
    final List<MapResultEntryDto> resultData = resultDto.getFirstMeasureData();
    assertThat(resultData).isNotNull().hasSize(1);
    assertThat(resultData.get(0).getValue()).isEqualTo(1000.);
  }

  @Test
  public void durationFilterWorksForCompletedProcessInstances() {
    // given
    OffsetDateTime now = OffsetDateTime.now();
    LocalDateUtil.setCurrentTime(now);

    ProcessInstanceEngineDto completeProcessInstanceDto = deployAndStartSimpleUserTaskProcess();
    engineIntegrationExtension.finishAllRunningUserTasks(completeProcessInstanceDto.getId());

    OffsetDateTime completedProcInstStartDate = now.minusDays(2);
    OffsetDateTime completedProcInstEndDate = completedProcInstStartDate.plus(1000, MILLIS);
    engineDatabaseExtension.changeProcessInstanceStartDate(
      completeProcessInstanceDto.getId(),
      completedProcInstStartDate
    );
    engineDatabaseExtension.changeProcessInstanceEndDate(completeProcessInstanceDto.getId(), completedProcInstEndDate);

    final ProcessInstanceEngineDto completedProcessInstance2 =
      engineIntegrationExtension.startProcessInstance(completeProcessInstanceDto.getDefinitionId());
    engineIntegrationExtension.finishAllRunningUserTasks(completedProcessInstance2.getId());
    engineDatabaseExtension.changeProcessInstanceStartDate(completedProcessInstance2.getId(), now.minusDays(1));
    engineDatabaseExtension.changeProcessInstanceEndDate(completedProcessInstance2.getId(), now);

    importAllEngineEntitiesFromScratch();

    // when
    final List<ProcessFilterDto<?>> durationFilter = ProcessFilterBuilder.filter()
      .duration()
      .operator(LESS_THAN)
      .unit(DurationFilterUnit.HOURS)
      .value(2L)
      .add()
      .buildList();

    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder.createReportData()
      .setReportDataType(getTestReportDataType())
      .setProcessDefinitionVersion(completeProcessInstanceDto.getProcessDefinitionVersion())
      .setProcessDefinitionKey(completeProcessInstanceDto.getProcessDefinitionKey())
      .setGroupByDateInterval(AggregateByDateUnit.DAY)
      .setFilter(durationFilter)
      .build();
    ReportResultResponseDto<List<MapResultEntryDto>> resultDto = reportClient.evaluateMapReport(reportData).getResult();

    // then
    final List<MapResultEntryDto> resultData = resultDto.getFirstMeasureData();
    assertThat(resultData).isNotNull();
    assertThat(resultData).hasSize(1);
    assertThat(resultData.get(0).getValue()).isEqualTo(1000.);
  }

  @ParameterizedTest
  @MethodSource("staticAggregateByDateUnits")
  public void groupedByStaticDateUnit(final AggregateByDateUnit unit) {
    // given
    List<ProcessInstanceEngineDto> processInstanceDtos = deployAndStartSimpleProcesses(8);
    OffsetDateTime now = dateFreezer().truncateToUnit(ChronoUnit.SECONDS).freezeDateAndReturn();
    updateProcessInstancesDates(processInstanceDtos, now, mapToChronoUnit(unit));
    importAllEngineEntitiesFromScratch();

    // when
    ProcessInstanceEngineDto dto = processInstanceDtos.get(0);
    ProcessReportDataDto reportData = createReportDataSortedDesc(
      dto.getProcessDefinitionKey(),
      dto.getProcessDefinitionVersion(),
      getTestReportDataType(),
      unit
    );

    ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getFirstMeasureData()).isNotNull();
    assertDateResultMap(result.getFirstMeasureData(), 8, now, mapToChronoUnit(unit));
  }

  @Test
  public void otherProcessDefinitionsDoNoAffectResult() {
    // given
    OffsetDateTime referenceDate = OffsetDateTime.now().minusDays(2);
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    adjustProcessInstanceDates(processInstanceDto.getId(), referenceDate, 0L, 1L);
    deployAndStartSimpleServiceTaskProcess();
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder.createReportData()
      .setReportDataType(getTestReportDataType())
      .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
      .setGroupByDateInterval(AggregateByDateUnit.DAY)
      .build();

    ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    final List<MapResultEntryDto> resultMap = result.getFirstMeasureData();
    ZonedDateTime startOfToday = truncateToStartOfUnit(referenceDate, ChronoUnit.DAYS);
    assertThat(resultMap).hasSize(1);
    assertThat(resultMap.get(0).getKey()).isEqualTo(localDateTimeToString(startOfToday));
    assertThat(resultMap.get(0).getValue()).isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(1000.));
  }

  @Test
  public void reportEvaluationSingleBucketFilteredBySingleTenant() {
    // given
    final String tenantId1 = "tenantId1";
    final String tenantId2 = "tenantId2";
    final List<String> selectedTenants = newArrayList(tenantId1);
    final String processKey = deployAndStartMultiTenantSimpleServiceTaskProcess(
      newArrayList(null, tenantId1, tenantId2)
    );

    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder.createReportData()
      .setReportDataType(getTestReportDataType())
      .setProcessDefinitionKey(processKey)
      .setProcessDefinitionVersion(ReportConstants.ALL_VERSIONS)
      .setGroupByDateInterval(AggregateByDateUnit.HOUR)
      .build();

    reportData.setTenantIds(selectedTenants);
    ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo((long) selectedTenants.size());
  }

  @Test
  public void filterInReportWorks() {
    // given
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", true);
    OffsetDateTime referenceDate = OffsetDateTime.now();
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcessWithVariables(variables);
    adjustProcessInstanceDates(processInstanceDto.getId(), referenceDate, 0L, 1L);
    String processDefinitionId = processInstanceDto.getDefinitionId();
    engineIntegrationExtension.startProcessInstance(processDefinitionId);
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setGroupByDateInterval(AggregateByDateUnit.DAY)
      .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
      .setReportDataType(getTestReportDataType())
      .setFilter(createVariableFilter("true"))
      .build();

    ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getFirstMeasureData()).isNotNull();
    assertThat(result.getFirstMeasureData()).hasSize(1);

    // when
    reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setGroupByDateInterval(AggregateByDateUnit.DAY)
      .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
      .setReportDataType(getTestReportDataType())
      .setFilter(createVariableFilter("false"))
      .build();

    result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getFirstMeasureData()).isNotNull();
    assertThat(result.getFirstMeasureData()).isEmpty();
  }

  private List<ProcessFilterDto<?>> createVariableFilter(String value) {
    return ProcessFilterBuilder
      .filter()
      .variable()
      .booleanType()
      .values(Collections.singletonList(value))
      .name("var")
      .add()
      .buildList();
  }

  @Test
  public void optimizeExceptionOnGroupByTypeIsNull() {
    // given
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    importAllEngineEntitiesFromScratch();

    ProcessReportDataDto dataDto = TemplatedProcessReportDataBuilder.createReportData()
      .setReportDataType(getTestReportDataType())
      .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
      .setGroupByDateInterval(AggregateByDateUnit.DAY)
      .build();
    dataDto.getGroupBy().setType(null);

    // when
    Response response = reportClient.evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void optimizeExceptionOnGroupByValueIsNull() {
    // given
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    importAllEngineEntitiesFromScratch();

    ProcessReportDataDto dataDto = TemplatedProcessReportDataBuilder.createReportData()
      .setReportDataType(getTestReportDataType())
      .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
      .setGroupByDateInterval(AggregateByDateUnit.DAY)
      .build();
    DateGroupByValueDto groupByValueDto = (DateGroupByValueDto) dataDto.getGroupBy().getValue();
    groupByValueDto.setUnit(null);

    // when
    Response response = reportClient.evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
  }

  @Test
  public void optimizeExceptionOnGroupByUnitIsNull() {
    // given
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    importAllEngineEntitiesFromScratch();

    ProcessReportDataDto dataDto = TemplatedProcessReportDataBuilder.createReportData()
      .setReportDataType(getTestReportDataType())
      .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
      .setGroupByDateInterval(AggregateByDateUnit.DAY)
      .build();
    ProcessGroupByDto<?> groupByDate = dataDto.getGroupBy();
    groupByDate.setValue(null);

    // when
    Response response = reportClient.evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
  }

  private ProcessInstanceEngineDto deployAndStartSimpleServiceTaskProcessWithVariables(Map<String, Object> variables) {
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .name("aProcessName")
      .startEvent()
      .serviceTask(TEST_ACTIVITY)
      .camundaExpression("${true}")
      .endEvent()
      .done();
    return engineIntegrationExtension.deployAndStartProcessWithVariables(processModel, variables);
  }

  private void assertDurationMapReportResults(AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResponse,
                                              Double[] expectedDurations) {
    assertThat(evaluationResponse.getResult().getMeasures())
      .extracting(MeasureResponseDto::getAggregationType)
      .containsExactly(getSupportedAggregationTypes());

    final Map<AggregationDto, List<MapResultEntryDto>> resultByAggregationType =
      evaluationResponse.getResult().getMeasures().stream()
        .collect(Collectors.toMap(MeasureResponseDto::getAggregationType, MeasureResponseDto::getData));

    Arrays.stream(getSupportedAggregationTypes()).forEach(aggregationType -> {
      assertThat(resultByAggregationType.get(aggregationType).get(0).getValue())
        .isEqualTo(calculateExpectedValueGivenDurations(expectedDurations).get(aggregationType));
    });
  }

  private void assertDateResultMap(List<MapResultEntryDto> resultData,
                                   int size,
                                   OffsetDateTime now,
                                   ChronoUnit unit) {
    assertThat(resultData).hasSize(size);
    final ZonedDateTime finalStartOfUnit = truncateToStartOfUnit(now, unit);
    IntStream.range(0, size)
      .forEach(i -> {
        final String expectedDateString = localDateTimeToString(finalStartOfUnit.minus(i, unit));
        assertThat(resultData.get(i).getKey()).isEqualTo(expectedDateString);
        assertThat(resultData.get(i).getValue()).isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(1000.));
      });
  }

  private void updateProcessInstancesDates(List<ProcessInstanceEngineDto> procInsts,
                                           OffsetDateTime now,
                                           ChronoUnit unit) {
    Map<String, OffsetDateTime> idToNewStartDate = new HashMap<>();
    Map<String, OffsetDateTime> idToNewEndDate = new HashMap<>();
    IntStream.range(0, procInsts.size())
      .forEach(i -> {
        String id = procInsts.get(i).getId();
        OffsetDateTime newStartDate = now.minus(i, unit);
        idToNewStartDate.put(id, newStartDate);
        idToNewEndDate.put(id, newStartDate.plusSeconds(1L));
      });

    engineDatabaseExtension.changeProcessInstanceStartDates(idToNewStartDate);
    engineDatabaseExtension.changeProcessInstanceEndDates(idToNewEndDate);
  }

}
