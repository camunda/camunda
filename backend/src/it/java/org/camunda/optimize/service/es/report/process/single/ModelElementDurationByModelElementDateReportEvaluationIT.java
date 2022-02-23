/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process.single;

import com.google.common.collect.ImmutableList;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.value.DateGroupByValueDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResponseDto;
import org.camunda.optimize.dto.optimize.rest.report.CombinedProcessReportResultDataDto;
import org.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.report.process.AbstractProcessDefinitionIT;
import org.camunda.optimize.service.es.report.util.MapResultUtil;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.google.common.collect.Lists.newArrayList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnitMapper.mapToChronoUnit;
import static org.camunda.optimize.test.util.DateModificationHelper.truncateToStartOfUnit;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createCombinedReportData;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION;
import static org.camunda.optimize.util.BpmnModels.getDoubleUserTaskDiagram;
import static org.camunda.optimize.util.BpmnModels.getSimpleBpmnDiagram;
import static org.camunda.optimize.util.BpmnModels.getSingleUserTaskDiagram;
import static org.camunda.optimize.util.BpmnModels.getTripleUserTaskDiagram;

public abstract class ModelElementDurationByModelElementDateReportEvaluationIT
  extends AbstractProcessDefinitionIT {

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
    final AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResponse =
      reportClient.evaluateMapReport(reportData);

    // then
    final ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey()).isEqualTo(processDefinition.getKey());
    assertThat(resultReportDataDto.getDefinitionVersions()).containsExactly(processDefinition.getVersionAsString());
    assertThat(resultReportDataDto.getView()).isNotNull();
    assertThat(resultReportDataDto.getView().getEntity()).isEqualTo(getViewEntity());
    assertThat(resultReportDataDto.getView().getFirstProperty()).isEqualTo(ViewProperty.DURATION);
    assertThat(resultReportDataDto.getGroupBy()).isNotNull();
    assertThat(resultReportDataDto.getGroupBy().getType()).isEqualTo(getGroupByType());
    assertThat(resultReportDataDto.getGroupBy().getValue())
      .extracting(DateGroupByValueDto.class::cast)
      .extracting(DateGroupByValueDto::getUnit)
      .isEqualTo(AggregateByDateUnit.DAY);

    final ReportResultResponseDto<List<MapResultEntryDto>> result = evaluationResponse.getResult();
    assertThat(result.getInstanceCount()).isEqualTo(1L);
    assertThat(result.getFirstMeasureData()).isNotNull();
    assertThat(result.getFirstMeasureData()).hasSize(1);
    assertThat(getExecutedFlowNodeCount(result)).isEqualTo(1L);
    ZonedDateTime startOfToday = truncateToStartOfUnit(OffsetDateTime.now(), ChronoUnit.DAYS);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), localDateTimeToString(startOfToday)))
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
    SingleProcessReportDefinitionRequestDto singleProcessReportDefinitionDto =
      new SingleProcessReportDefinitionRequestDto();
    singleProcessReportDefinitionDto.setData(reportData);
    final String reportId = reportClient.createSingleProcessReport(singleProcessReportDefinitionDto);

    // when
    AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResponse =
      reportClient.evaluateMapReportById(reportId);

    // then
    final ReportResultResponseDto<List<MapResultEntryDto>> result = evaluationResponse.getResult();
    assertThat(result.getInstanceCount()).isEqualTo(1L);
    assertThat(result.getFirstMeasureData()).isNotNull();
    assertThat(result.getFirstMeasureData()).hasSize(1);
    assertThat(getExecutedFlowNodeCount(result)).isEqualTo(1L);
    ZonedDateTime startOfToday = truncateToStartOfUnit(OffsetDateTime.now(), ChronoUnit.DAYS);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), localDateTimeToString(startOfToday)))
      .get()
      .extracting(MapResultEntryDto::getValue)
      .isEqualTo(expectedDuration);
  }

  @ParameterizedTest
  @MethodSource("staticAggregateByDateUnits")
  public void groupedByDateUnit(final AggregateByDateUnit groupByDateUnit) {
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
    final ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData)
      .getResult();

    // then
    final List<MapResultEntryDto> resultData = result.getFirstMeasureData();
    assertThat(resultData).hasSize(groupingCount);
    // startDate is groupingCount - 1 in the past from now
    OffsetDateTime startDate = now.minus(groupingCount - 1, groupByUnitAsChrono);
    IntStream.range(0, groupingCount)
      .forEach(i -> {
        final String expectedDateString =
          groupedByDateAsString(startDate.plus(i, groupByUnitAsChrono), groupByUnitAsChrono);
        assertThat(resultData.get(i).getKey()).isEqualTo(expectedDateString);
        assertThat(resultData.get(i).getValue()).isEqualTo((long) 10);
      });
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
    final ProcessReportDataDto reportData = createReportData(processKey, "1", AggregateByDateUnit.DAY);
    reportData.setTenantIds(selectedTenants);
    final ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData)
      .getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo((long) selectedTenants.size());
  }

  @Test
  public void flowNodeStatusFilterWorks() {
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
    final ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData)
      .getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(1L);
    assertThat(result.getFirstMeasureData()).hasSize(1);
    assertThat(result.getFirstMeasureData())
      .extracting(MapResultEntryDto::getValue)
      .containsExactly(10.);
  }

  @Test
  public void automaticIntervalSelection_forNoData() {
    // given
    ProcessDefinitionEngineDto processDefinition = deployOneUserTaskDefinition();
    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReportData(processDefinition, AggregateByDateUnit.AUTOMATIC);
    final ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData)
      .getResult();

    // then
    final List<MapResultEntryDto> resultData = result.getFirstMeasureData();
    assertThat(resultData).isEmpty();
  }

  @Test
  public void automaticIntervalSelection_combinedReportsWithDistinctRanges() {
    // given
    ZonedDateTime now = ZonedDateTime.now();
    ProcessDefinitionEngineDto processDefinition1 = deploySimpleDefinition();
    startProcessInstancesWithModelElementDateInDayRange(processDefinition1, now.plusDays(1), now.plusDays(3));
    ProcessDefinitionEngineDto processDefinition2 = deploySimpleDefinition();
    startProcessInstancesWithModelElementDateInDayRange(processDefinition2, now.plusDays(4), now.plusDays(6));
    importAllEngineEntitiesFromScratch();

    SingleProcessReportDefinitionRequestDto singleProcessReportDefinitionDto =
      new SingleProcessReportDefinitionRequestDto();
    ProcessReportDataDto reportData = createReportData(processDefinition1, AggregateByDateUnit.AUTOMATIC);
    singleProcessReportDefinitionDto.setData(reportData);
    final String singleReportId1 = reportClient.createSingleProcessReport(singleProcessReportDefinitionDto);
    reportData = createReportData(processDefinition2, AggregateByDateUnit.AUTOMATIC);
    singleProcessReportDefinitionDto.setData(reportData);
    final String singleReportId2 = reportClient.createSingleProcessReport(singleProcessReportDefinitionDto);

    // when
    CombinedProcessReportResultDataDto<List<MapResultEntryDto>> result =
      reportClient.evaluateUnsavedCombined(createCombinedReportData(singleReportId1, singleReportId2));

    // then
    Map<String, AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>>> resultMap = result.getData();
    assertResultIsInCorrectRanges(now.plusDays(1), now.plusDays(6), resultMap, 2);
  }

  @Test
  public void automaticIntervalSelection_combinedReportsWithOneIncludingRange() {
    // given
    ZonedDateTime now = ZonedDateTime.now();
    ProcessDefinitionEngineDto processDefinition1 = deploySimpleDefinition();
    startProcessInstancesWithModelElementDateInDayRange(processDefinition1, now.plusDays(1), now.plusDays(6));
    ProcessDefinitionEngineDto processDefinition2 = deploySimpleDefinition();
    startProcessInstancesWithModelElementDateInDayRange(processDefinition2, now.plusDays(3), now.plusDays(5));
    importAllEngineEntitiesFromScratch();

    SingleProcessReportDefinitionRequestDto singleProcessReportDefinitionDto =
      new SingleProcessReportDefinitionRequestDto();
    ProcessReportDataDto reportData = createReportData(processDefinition1, AggregateByDateUnit.AUTOMATIC);
    singleProcessReportDefinitionDto.setData(reportData);
    final String singleReportId1 = reportClient.createSingleProcessReport(singleProcessReportDefinitionDto);
    reportData = createReportData(processDefinition2, AggregateByDateUnit.AUTOMATIC);
    singleProcessReportDefinitionDto.setData(reportData);
    final String singleReportId2 = reportClient.createSingleProcessReport(singleProcessReportDefinitionDto);

    // when
    CombinedProcessReportResultDataDto<List<MapResultEntryDto>> result =
      reportClient.evaluateUnsavedCombined(createCombinedReportData(singleReportId1, singleReportId2));

    // then
    Map<String, AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>>> resultMap = result.getData();
    assertResultIsInCorrectRanges(now.plusDays(1), now.plusDays(6), resultMap, 2);
  }

  @Test
  public void automaticIntervalSelection_combinedReportsWithIntersectingRange() {
    // given
    ZonedDateTime now = ZonedDateTime.now();
    ProcessDefinitionEngineDto processDefinition1 = deploySimpleDefinition();
    startProcessInstancesWithModelElementDateInDayRange(processDefinition1, now.plusDays(1), now.plusDays(4));
    ProcessDefinitionEngineDto processDefinition2 = deploySimpleDefinition();
    startProcessInstancesWithModelElementDateInDayRange(processDefinition2, now.plusDays(4), now.plusDays(6));
    importAllEngineEntitiesFromScratch();

    SingleProcessReportDefinitionRequestDto singleProcessReportDefinitionDto =
      new SingleProcessReportDefinitionRequestDto();
    ProcessReportDataDto reportData = createReportData(processDefinition1, AggregateByDateUnit.AUTOMATIC);
    singleProcessReportDefinitionDto.setData(reportData);
    final String singleReportId1 = reportClient.createSingleProcessReport(singleProcessReportDefinitionDto);
    reportData = createReportData(processDefinition2, AggregateByDateUnit.AUTOMATIC);
    singleProcessReportDefinitionDto.setData(reportData);
    final String singleReportId2 = reportClient.createSingleProcessReport(singleProcessReportDefinitionDto);

    // when
    CombinedProcessReportResultDataDto<List<MapResultEntryDto>> result =
      reportClient.evaluateUnsavedCombined(createCombinedReportData(singleReportId1, singleReportId2));

    // then
    Map<String, AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>>> resultMap = result.getData();
    assertResultIsInCorrectRanges(now.plusDays(1), now.plusDays(6), resultMap, 2);
  }

  protected void assertResultIsInCorrectRanges(
    ZonedDateTime startRange,
    ZonedDateTime endRange,
    Map<String, AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>>> resultMap,
    int resultSize) {
    assertThat(resultMap).hasSize(resultSize);
    for (AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> result : resultMap.values()) {
      final List<MapResultEntryDto> resultData = result.getResult().getFirstMeasureData();
      assertThat(resultData).hasSize(NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION);
      assertThat(resultData).first().extracting(MapResultEntryDto::getKey).isEqualTo(localDateTimeToString(startRange));
      assertIsInRangeOfLastInterval(resultData.get(resultData.size() - 1).getKey(), startRange, endRange);
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

  protected void updateUserTaskTime(List<ProcessInstanceEngineDto> procInsts,
                                    OffsetDateTime now,
                                    ChronoUnit unit) {
    Map<String, OffsetDateTime> idToNewStartDate = new HashMap<>();
    IntStream.range(0, procInsts.size())
      .forEach(i -> {
        String id = procInsts.get(i).getId();
        OffsetDateTime newStartDate = now.minus(i, unit);
        idToNewStartDate.put(id, newStartDate);
      });
    changeModelElementDates(idToNewStartDate);
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

  protected ProcessReportDataDto createGroupedByDayReport(final ProcessDefinitionEngineDto processDefinition) {
    return createReportData(processDefinition, AggregateByDateUnit.DAY);
  }

  protected ProcessReportDataDto createReportData(final ProcessDefinitionEngineDto processDefinition,
                                                  final AggregateByDateUnit groupByDateUnit) {
    return createReportData(
      processDefinition.getKey(),
      String.valueOf(processDefinition.getVersion()),
      groupByDateUnit
    );
  }

  protected void finishAllUserTasks(final ProcessInstanceEngineDto processInstanceDto1) {
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

  protected ProcessDefinitionEngineDto deployStartEndDefinition() {
    return engineIntegrationExtension.deployProcessAndGetProcessDefinition(getSimpleBpmnDiagram());
  }

  protected ProcessDefinitionEngineDto deployOneUserTaskDefinition() {
    return deployOneUserTaskDefinition("aProcess", null);
  }

  protected ProcessDefinitionEngineDto deployOneUserTaskDefinition(String key, String tenantId) {
    return engineIntegrationExtension.deployProcessAndGetProcessDefinition(getSingleUserTaskDiagram(key), tenantId);
  }

  protected ProcessDefinitionEngineDto deployTwoUserTasksDefinition() {
    return engineIntegrationExtension.deployProcessAndGetProcessDefinition(getDoubleUserTaskDiagram());
  }

  protected ProcessDefinitionEngineDto deployThreeUserTasksDefinition() {
    return engineIntegrationExtension.deployProcessAndGetProcessDefinition(getTripleUserTaskDiagram());
  }

  private long getExecutedFlowNodeCount(ReportResultResponseDto<List<MapResultEntryDto>> resultList) {
    return resultList.getFirstMeasureData()
      .stream()
      .map(MapResultEntryDto::getValue)
      .filter(Objects::nonNull)
      .count();
  }

  protected abstract void changeDuration(final ProcessInstanceEngineDto processInstanceDto,
                                         final String modelElementId,
                                         final Double durationInMs);

  protected abstract void changeDuration(final ProcessInstanceEngineDto processInstanceDto, final Double durationInMs);

  protected String groupedByDayDateAsString(final OffsetDateTime referenceDate) {
    return groupedByDateAsString(referenceDate, ChronoUnit.DAYS);
  }

  protected String groupedByDateAsString(final OffsetDateTime referenceDate, final ChronoUnit chronoUnit) {
    return localDateTimeToString(truncateToStartOfUnit(referenceDate, chronoUnit));
  }

  protected abstract ProcessGroupByType getGroupByType();

  protected abstract ProcessReportDataType getReportDataType();

  protected abstract void changeModelElementDates(final Map<String, OffsetDateTime> updates);

  protected abstract void changeModelElementDate(final ProcessInstanceEngineDto processInstance,
                                                 final String modelElementId,
                                                 final OffsetDateTime dateToChangeTo);

  protected abstract ProcessViewEntity getViewEntity();

  protected abstract void startProcessInstancesWithModelElementDateInDayRange(ProcessDefinitionEngineDto processDefinition,
                                                                              ZonedDateTime min,
                                                                              ZonedDateTime max);

  protected abstract ProcessDefinitionEngineDto deploySimpleDefinition();

}
