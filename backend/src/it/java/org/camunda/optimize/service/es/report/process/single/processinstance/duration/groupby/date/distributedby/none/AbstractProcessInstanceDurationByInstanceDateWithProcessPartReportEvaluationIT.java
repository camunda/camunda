/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.process.single.processinstance.duration.groupby.date.distributedby.none;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.ReportConstants;
import org.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationDto;
import org.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto;
import org.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResponseDto;
import org.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
import org.camunda.optimize.dto.optimize.rest.report.measure.MeasureResponseDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.report.process.AbstractProcessDefinitionIT;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

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
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnitMapper.mapToChronoUnit;
import static org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto.SORT_BY_KEY;
import static org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto.SORT_BY_VALUE;
import static org.camunda.optimize.test.util.DateModificationHelper.truncateToStartOfUnit;
import static org.camunda.optimize.test.util.DurationAggregationUtil.calculateExpectedValueGivenDurations;
import static org.camunda.optimize.test.util.DurationAggregationUtil.calculateExpectedValueGivenDurationsDefaultAggr;
import static org.camunda.optimize.test.util.DurationAggregationUtil.getAggregationTypesAsListForProcessParts;
import static org.camunda.optimize.util.BpmnModels.END_LOOP;
import static org.camunda.optimize.util.BpmnModels.START_LOOP;
import static org.camunda.optimize.util.BpmnModels.getSingleServiceTaskProcess;

public abstract class AbstractProcessInstanceDurationByInstanceDateWithProcessPartReportEvaluationIT
  extends AbstractProcessDefinitionIT {

  protected abstract ProcessReportDataType getTestReportDataType();

  protected abstract ProcessGroupByType getGroupByType();

  @Test
  public void reportEvaluationForOneProcess() {
    // given
    OffsetDateTime procInstReferenceDate = OffsetDateTime.now();
    OffsetDateTime activityStartDate = OffsetDateTime.now();
    OffsetDateTime endDate = activityStartDate.plusSeconds(1);
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    adjustProcessInstanceDates(processInstanceDto.getId(), procInstReferenceDate, 0L);
    engineDatabaseExtension.changeFlowNodeStartDatesForProcessDefinition(
      processInstanceDto.getDefinitionId(),
      activityStartDate
    );
    engineDatabaseExtension.changeFlowNodeEndDatesForProcessDefinition(
      processInstanceDto.getDefinitionId(),
      endDate
    );
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
      .setStartFlowNodeId(START_EVENT)
      .setEndFlowNodeId(END_EVENT)
      .setGroupByDateInterval(AggregateByDateUnit.DAY)
      .setReportDataType(getTestReportDataType())
      .build();

    AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResponse =
      reportClient.evaluateMapReport(reportData);

    // then
    final ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey()).isEqualTo(processInstanceDto.getProcessDefinitionKey());
    assertThat(resultReportDataDto.getDefinitionVersions()).containsExactly(processInstanceDto.getProcessDefinitionVersion());
    assertThat(resultReportDataDto.getView()).isNotNull();
    assertThat(resultReportDataDto.getView().getEntity()).isEqualTo(ProcessViewEntity.PROCESS_INSTANCE);
    assertThat(resultReportDataDto.getView().getFirstProperty()).isEqualTo(ViewProperty.DURATION);
    assertThat(resultReportDataDto.getGroupBy().getType()).isEqualTo(getGroupByType());
    assertThat(resultReportDataDto.getConfiguration().getProcessPart()).isPresent();

    assertThat(evaluationResponse.getResult().getInstanceCount()).isEqualTo(1L);
    final List<MapResultEntryDto> resultData = evaluationResponse.getResult().getFirstMeasureData();
    ZonedDateTime startOfToday = truncateToStartOfUnit(OffsetDateTime.now(), ChronoUnit.DAYS);
    assertThat(resultData.get(0).getKey()).isEqualTo(localDateTimeToString(startOfToday));
    assertThat(resultData.get(0).getValue()).isEqualTo(1000.);
  }

  @Test
  public void reportEvaluationById() {
    // given
    OffsetDateTime procInstRefDate = OffsetDateTime.now();
    OffsetDateTime activityStartDate = OffsetDateTime.now();
    OffsetDateTime endDate = activityStartDate.plusSeconds(1);
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    adjustProcessInstanceDates(processInstanceDto.getId(), procInstRefDate, 0L);
    engineDatabaseExtension.changeFlowNodeStartDatesForProcessDefinition(
      processInstanceDto.getDefinitionId(),
      activityStartDate
    );
    engineDatabaseExtension.changeFlowNodeEndDatesForProcessDefinition(
      processInstanceDto.getDefinitionId(),
      endDate
    );
    importAllEngineEntitiesFromScratch();
    ProcessReportDataDto reportDataDto = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
      .setStartFlowNodeId(START_EVENT)
      .setEndFlowNodeId(END_EVENT)
      .setReportDataType(getTestReportDataType())
      .setGroupByDateInterval(AggregateByDateUnit.DAY)
      .build();

    String reportId = createNewReport(reportDataDto);

    // when
    AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResponse =
      reportClient.evaluateMapReportById(reportId);

    // then
    final ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey()).isEqualTo(processInstanceDto.getProcessDefinitionKey());
    assertThat(resultReportDataDto.getDefinitionVersions()).containsExactly(processInstanceDto.getProcessDefinitionVersion());
    assertThat(resultReportDataDto.getView()).isNotNull();
    assertThat(resultReportDataDto.getView().getEntity()).isEqualTo(ProcessViewEntity.PROCESS_INSTANCE);
    assertThat(resultReportDataDto.getView().getFirstProperty()).isEqualTo(ViewProperty.DURATION);
    assertThat(resultReportDataDto.getGroupBy().getType()).isEqualTo(getGroupByType());
    assertThat(resultReportDataDto.getConfiguration().getProcessPart()).isPresent();
    assertThat(evaluationResponse.getResult().getFirstMeasureData()).isNotNull();
    final List<MapResultEntryDto> resultData = evaluationResponse.getResult().getFirstMeasureData();
    ZonedDateTime startOfToday = truncateToStartOfUnit(OffsetDateTime.now(), ChronoUnit.DAYS);
    assertThat(resultData.get(0).getKey()).isEqualTo(localDateTimeToString(startOfToday));
    assertThat(resultData.get(0).getValue()).isEqualTo(1000.);
  }

  @Test
  public void evaluateReportForMultipleEvents() {
    // given
    OffsetDateTime procInstRefDate = OffsetDateTime.now();
    ProcessDefinitionEngineDto procDefDto = deploySimpleServiceTaskProcess();
    startThreeProcessInstances(procInstRefDate, 0, procDefDto, Arrays.asList(1, 2, 9));
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(procDefDto.getKey())
      .setProcessDefinitionVersion(procDefDto.getVersionAsString())
      .setStartFlowNodeId(START_EVENT)
      .setEndFlowNodeId(END_EVENT)
      .setGroupByDateInterval(AggregateByDateUnit.DAY)
      .setReportDataType(getTestReportDataType())
      .build();
    ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    final List<MapResultEntryDto> resultData = result.getFirstMeasureData();
    assertThat(resultData).isNotNull();
    ZonedDateTime startOfToday = truncateToStartOfUnit(OffsetDateTime.now(), ChronoUnit.DAYS);
    assertThat(resultData.get(0).getKey()).isEqualTo(localDateTimeToString(startOfToday));
    assertThat(resultData.get(0).getValue()).isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(
      1000.,
      2000.,
      9000.
    ));
  }

  @Test
  public void evaluateReportForMultipleEventsWithAllAggregationTypes() {
    // given
    OffsetDateTime procInstRefDate = OffsetDateTime.now();
    ProcessDefinitionEngineDto procDefDto = deploySimpleServiceTaskProcess();
    startThreeProcessInstances(procInstRefDate, 0, procDefDto, Arrays.asList(1, 2, 9));
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(procDefDto.getKey())
      .setProcessDefinitionVersion(procDefDto.getVersionAsString())
      .setStartFlowNodeId(START_EVENT)
      .setEndFlowNodeId(END_EVENT)
      .setGroupByDateInterval(AggregateByDateUnit.DAY)
      .setReportDataType(getTestReportDataType())
      .build();
    reportData.getConfiguration().setAggregationTypes(getAggregationTypesAsListForProcessParts());

    final AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResponse =
      reportClient.evaluateMapReport(reportData);

    // then
    assertDurationMapReportResults(evaluationResponse, new Double[]{1000., 2000., 9000.});
  }

  @Test
  public void multipleEventsInEachDateRange() {
    // given
    OffsetDateTime procInstRefDate = OffsetDateTime.now();
    ProcessDefinitionEngineDto procDefDto = deploySimpleServiceTaskProcess();
    startThreeProcessInstances(procInstRefDate, 0, procDefDto, Arrays.asList(1, 2, 9));
    startThreeProcessInstances(procInstRefDate, -1, procDefDto, Arrays.asList(2, 4, 12));


    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(procDefDto.getKey())
      .setProcessDefinitionVersion(procDefDto.getVersionAsString())
      .setStartFlowNodeId(START_EVENT)
      .setEndFlowNodeId(END_EVENT)
      .setReportDataType(getTestReportDataType())
      .setGroupByDateInterval(AggregateByDateUnit.DAY)
      .build();
    ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    final List<MapResultEntryDto> resultData = result.getFirstMeasureData();
    assertThat(resultData)
      .isNotNull()
      .hasSize(2);
    ZonedDateTime startOfToday = truncateToStartOfUnit(procInstRefDate, ChronoUnit.DAYS);
    assertThat(resultData.get(0).getKey()).isEqualTo(localDateTimeToString(startOfToday.minusDays(1)));
    assertThat(resultData.get(0).getValue())
      .isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(2000., 4000., 12000.));
    assertThat(resultData.get(1).getKey()).isEqualTo(localDateTimeToString(startOfToday));
    assertThat(resultData.get(1).getValue())
      .isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(1000., 2000., 9000.));
  }

  @Test
  public void takeCorrectActivityOccurrences() {
    // given
    OffsetDateTime procInstRefDate = OffsetDateTime.now();
    OffsetDateTime activityStartDate = OffsetDateTime.now().minusHours(1);
    ProcessInstanceEngineDto processInstanceDto = deployAndStartLoopingProcess();
    adjustProcessInstanceDates(processInstanceDto.getId(), procInstRefDate, 0L);
    engineDatabaseExtension.changeFirstFlowNodeInstanceStartDate(START_LOOP, activityStartDate);
    engineDatabaseExtension.changeFirstFlowNodeInstanceEndDate(END_LOOP, activityStartDate.plusSeconds(2));
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
      .setStartFlowNodeId(START_LOOP)
      .setEndFlowNodeId(END_LOOP)
      .setReportDataType(getTestReportDataType())
      .setGroupByDateInterval(AggregateByDateUnit.DAY)
      .build();

    ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getFirstMeasureData()).isNotNull();
    final List<MapResultEntryDto> resultData = result.getFirstMeasureData();
    ZonedDateTime startOfToday = truncateToStartOfUnit(OffsetDateTime.now(), ChronoUnit.DAYS);
    assertThat(resultData.get(0).getKey()).isEqualTo(localDateTimeToString(startOfToday));
    assertThat(resultData.get(0).getValue()).isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(2000.));
  }

  @Test
  public void unknownStartReturnsEmptyResult() {
    // given
    OffsetDateTime procInstRefDate = OffsetDateTime.now();
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    adjustProcessInstanceDates(processInstanceDto.getId(), procInstRefDate, 0L);
    engineDatabaseExtension.changeFlowNodeEndDatesForProcessDefinition(
      processInstanceDto.getDefinitionId(),
      OffsetDateTime.now().plusHours(1)
    );
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
      .setStartFlowNodeId("foo")
      .setEndFlowNodeId(END_EVENT)
      .setReportDataType(getTestReportDataType())
      .setGroupByDateInterval(AggregateByDateUnit.DAY)
      .build();

    ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getFirstMeasureData()).isNotNull();
    assertThat(result.getFirstMeasureData()).isEmpty();
  }

  @Test
  public void unknownEndReturnsEmptyResult() {
    // given
    OffsetDateTime procInstRefDate = OffsetDateTime.now();
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    adjustProcessInstanceDates(processInstanceDto.getId(), procInstRefDate, 0L);
    engineDatabaseExtension.changeFlowNodeStartDatesForProcessDefinition(
      processInstanceDto.getDefinitionId(),
      OffsetDateTime.now().minusHours(1)
    );
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
      .setStartFlowNodeId(START_EVENT)
      .setEndFlowNodeId("foo")
      .setReportDataType(getTestReportDataType())
      .setGroupByDateInterval(AggregateByDateUnit.DAY)
      .build();

    ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getFirstMeasureData()).isNotNull();
    assertThat(result.getFirstMeasureData()).isEmpty();
  }

  @Test
  public void noAvailableProcessInstancesReturnsEmptyResult() {
    // when
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey("fooProcDef")
      .setProcessDefinitionVersion("1")
      .setStartFlowNodeId(START_EVENT)
      .setEndFlowNodeId(END_EVENT)
      .setReportDataType(getTestReportDataType())
      .setGroupByDateInterval(AggregateByDateUnit.DAY)
      .build();
    ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getFirstMeasureData()).isNotNull();
    assertThat(result.getFirstMeasureData()).isEmpty();
  }

  @Test
  public void otherProcessDefinitionsDoNoAffectResult() {
    // given
    OffsetDateTime procInstRefDate = OffsetDateTime.now();
    OffsetDateTime activityStartdate = OffsetDateTime.now();
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();

    String processDefinitionKey = processInstanceDto.getProcessDefinitionKey();
    String processDefinitionVersion = processInstanceDto.getProcessDefinitionVersion();

    adjustProcessInstanceDates(processInstanceDto.getId(), procInstRefDate, 0L);
    engineDatabaseExtension.changeAllFlowNodeStartDates(processInstanceDto.getId(), activityStartdate);
    engineDatabaseExtension.changeAllFlowNodeEndDates(processInstanceDto.getId(), activityStartdate.plusSeconds(1));
    processInstanceDto = engineIntegrationExtension.startProcessInstance(processInstanceDto.getDefinitionId());
    adjustProcessInstanceDates(processInstanceDto.getId(), procInstRefDate, 0L);
    engineDatabaseExtension.changeAllFlowNodeStartDates(processInstanceDto.getId(), activityStartdate);
    engineDatabaseExtension.changeAllFlowNodeEndDates(processInstanceDto.getId(), activityStartdate.plusSeconds(9));
    processInstanceDto = engineIntegrationExtension.startProcessInstance(processInstanceDto.getDefinitionId());
    adjustProcessInstanceDates(processInstanceDto.getId(), procInstRefDate, 0L);
    engineDatabaseExtension.changeAllFlowNodeStartDates(processInstanceDto.getId(), activityStartdate);
    engineDatabaseExtension.changeAllFlowNodeEndDates(processInstanceDto.getId(), activityStartdate.plusSeconds(2));
    deployAndStartSimpleServiceTaskProcess();
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersion(processDefinitionVersion)
      .setStartFlowNodeId(START_EVENT)
      .setEndFlowNodeId(END_EVENT)
      .setReportDataType(getTestReportDataType())
      .setGroupByDateInterval(AggregateByDateUnit.DAY)
      .build();
    ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getFirstMeasureData()).isNotNull();
    final List<MapResultEntryDto> resultData = result.getFirstMeasureData();
    ZonedDateTime startOfToday = truncateToStartOfUnit(OffsetDateTime.now(), ChronoUnit.DAYS);
    assertThat(resultData.get(0).getKey()).isEqualTo(localDateTimeToString(startOfToday));
    assertThat(resultData.get(0).getValue()).isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(
      1000.,
      9000.,
      2000.
    ));
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
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processKey)
      .setProcessDefinitionVersion(ReportConstants.ALL_VERSIONS)
      .setStartFlowNodeId(START_EVENT)
      .setEndFlowNodeId(END_EVENT)
      .setReportDataType(getTestReportDataType())
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
    OffsetDateTime procInstRefDate = OffsetDateTime.now();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", true);
    OffsetDateTime activityStartDate = OffsetDateTime.now();
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcessWithVariables(variables);
    adjustProcessInstanceDates(processInstanceDto.getId(), procInstRefDate, 0L);
    engineDatabaseExtension.changeAllFlowNodeStartDates(processInstanceDto.getId(), activityStartDate);
    engineDatabaseExtension.changeAllFlowNodeEndDates(processInstanceDto.getId(), activityStartDate.plusSeconds(1));
    String processDefinitionId = processInstanceDto.getDefinitionId();
    engineIntegrationExtension.startProcessInstance(processDefinitionId);
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(getTestReportDataType())
      .setStartFlowNodeId(START_EVENT)
      .setEndFlowNodeId(END_EVENT)
      .setGroupByDateInterval(AggregateByDateUnit.DAY)
      .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
      .setFilter(createVariableFilter("true"))
      .build();

    ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    List<MapResultEntryDto> resultData = result.getFirstMeasureData();
    ZonedDateTime startOfToday = truncateToStartOfUnit(OffsetDateTime.now(), ChronoUnit.DAYS);
    assertThat(resultData.get(0).getKey()).isEqualTo(localDateTimeToString(startOfToday));
    assertThat(resultData.get(0).getValue()).isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(1000.));

    // when
    reportData.setFilter(createVariableFilter("false"));
    result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    resultData = result.getFirstMeasureData();
    assertThat(result.getFirstMeasureData()).isEmpty();
  }

  @Test
  public void processInstancesAtSameIntervalAreGroupedTogether() {
    // given
    OffsetDateTime procInstRefDate = OffsetDateTime.now();
    OffsetDateTime activityStartDate = OffsetDateTime.now();
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    String processDefinitionKey = processInstanceDto.getProcessDefinitionKey();
    String processDefinitionVersion = processInstanceDto.getProcessDefinitionVersion();

    adjustProcessInstanceDates(processInstanceDto.getId(), procInstRefDate, 0L);
    engineDatabaseExtension.changeAllFlowNodeStartDates(processInstanceDto.getId(), activityStartDate);
    engineDatabaseExtension.changeAllFlowNodeEndDates(processInstanceDto.getId(), activityStartDate.plusSeconds(1));
    processInstanceDto = engineIntegrationExtension.startProcessInstance(processInstanceDto.getDefinitionId());
    engineDatabaseExtension.changeAllFlowNodeStartDates(processInstanceDto.getId(), activityStartDate);
    engineDatabaseExtension.changeAllFlowNodeEndDates(processInstanceDto.getId(), activityStartDate.plusSeconds(2));
    adjustProcessInstanceDates(processInstanceDto.getId(), procInstRefDate, 0L);
    processInstanceDto = engineIntegrationExtension.startProcessInstance(processInstanceDto.getDefinitionId());
    engineDatabaseExtension.changeAllFlowNodeStartDates(processInstanceDto.getId(), activityStartDate);
    engineDatabaseExtension.changeAllFlowNodeEndDates(processInstanceDto.getId(), activityStartDate.plusSeconds(9));
    adjustProcessInstanceDates(processInstanceDto.getId(), procInstRefDate, 0L);
    ProcessInstanceEngineDto processInstanceDto3 =
      engineIntegrationExtension.startProcessInstance(processInstanceDto.getDefinitionId());
    engineDatabaseExtension.changeAllFlowNodeStartDates(processInstanceDto3.getId(), activityStartDate);
    engineDatabaseExtension.changeAllFlowNodeEndDates(
      processInstanceDto3.getId(),
      activityStartDate.plusSeconds(1)
    );
    adjustProcessInstanceDates(processInstanceDto3.getId(), procInstRefDate, -1L);

    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setGroupByDateInterval(AggregateByDateUnit.DAY)
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersion(processDefinitionVersion)
      .setStartFlowNodeId(START_EVENT)
      .setEndFlowNodeId(END_EVENT)
      .setReportDataType(getTestReportDataType())
      .build();
    reportData.getConfiguration().setSorting(new ReportSortingDto(ReportSortingDto.SORT_BY_KEY, SortOrder.DESC));
    ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    final List<MapResultEntryDto> resultData = result.getFirstMeasureData();
    assertThat(resultData).hasSize(2);
    ZonedDateTime startOfToday = truncateToStartOfUnit(procInstRefDate, ChronoUnit.DAYS);
    assertThat(resultData.get(0).getKey()).isEqualTo(localDateTimeToString(startOfToday));
    assertThat(resultData.get(0).getValue()).isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(
      1000.,
      9000.,
      2000.
    ));
    assertThat(resultData.get(1).getKey()).isEqualTo(localDateTimeToString(startOfToday.minusDays(1)));
    assertThat(resultData.get(1).getValue()).isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(1000.));
  }

  @Test
  public void resultIsSortedInAscendingOrder() {
    // given
    OffsetDateTime procInstRefDate = OffsetDateTime.now();
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    String processDefinitionKey = processInstanceDto.getProcessDefinitionKey();
    String processDefinitionVersion = processInstanceDto.getProcessDefinitionVersion();

    adjustProcessInstanceDates(processInstanceDto.getId(), procInstRefDate, 0L);
    processInstanceDto = engineIntegrationExtension.startProcessInstance(processInstanceDto.getDefinitionId());
    adjustProcessInstanceDates(processInstanceDto.getId(), procInstRefDate, -2L);
    ProcessInstanceEngineDto processInstanceDto3 =
      engineIntegrationExtension.startProcessInstance(processInstanceDto.getDefinitionId());
    adjustProcessInstanceDates(processInstanceDto3.getId(), procInstRefDate, -1L);


    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setGroupByDateInterval(AggregateByDateUnit.DAY)
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersion(processDefinitionVersion)
      .setStartFlowNodeId(START_EVENT)
      .setEndFlowNodeId(END_EVENT)
      .setReportDataType(getTestReportDataType())
      .build();

    ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    final List<MapResultEntryDto> resultData = result.getFirstMeasureData();
    assertThat(resultData).hasSize(3);
    final List<String> resultKeys = resultData.stream().map(MapResultEntryDto::getKey).collect(Collectors.toList());
    assertThat(resultKeys).isSortedAccordingTo(Comparator.naturalOrder());
  }

  @Test
  public void testCustomOrderOnResultKeyIsApplied() {
    // given
    final OffsetDateTime referenceDate = OffsetDateTime.now();
    final ProcessInstanceEngineDto instance1 = deployAndStartSimpleServiceTaskProcess();
    final String processDefinitionId = instance1.getDefinitionId();
    final String processDefinitionKey = instance1.getProcessDefinitionKey();
    final String processDefinitionVersion = instance1.getProcessDefinitionVersion();
    adjustProcessInstanceDates(instance1.getId(), referenceDate, 0L, 1L);

    final ProcessInstanceEngineDto instance2 = engineIntegrationExtension.startProcessInstance(processDefinitionId);
    adjustProcessInstanceDates(instance2.getId(), referenceDate, -2L, 3L);

    final ProcessInstanceEngineDto instance3 = engineIntegrationExtension.startProcessInstance(processDefinitionId);
    adjustProcessInstanceDates(instance3.getId(), referenceDate, -1L, 1L);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder.createReportData()
      .setGroupByDateInterval(AggregateByDateUnit.DAY)
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersion(processDefinitionVersion)
      .setStartFlowNodeId(START_EVENT)
      .setEndFlowNodeId(END_EVENT)
      .setReportDataType(getTestReportDataType())
      .build();
    reportData.getConfiguration().setSorting(new ReportSortingDto(SORT_BY_KEY, SortOrder.ASC));
    final ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData)
      .getResult();

    // then
    final List<MapResultEntryDto> resultData = result.getFirstMeasureData();
    assertThat(resultData).hasSize(3);
    final List<String> resultKeys = resultData.stream().map(MapResultEntryDto::getKey).collect(Collectors.toList());
    assertThat(resultKeys)
      // expect ascending order
      .isSortedAccordingTo(Comparator.naturalOrder());
  }

  @Test
  public void testCustomOrderOnResultValueIsApplied() {
    // given
    final OffsetDateTime referenceDate = OffsetDateTime.now();
    final ProcessInstanceEngineDto processInstanceDto1 = deployAndStartSimpleServiceTaskProcess();
    final String processDefinitionId = processInstanceDto1.getDefinitionId();
    final String processDefinitionKey = processInstanceDto1.getProcessDefinitionKey();
    final String processDefinitionVersion = processInstanceDto1.getProcessDefinitionVersion();
    adjustProcessInstanceDates(processInstanceDto1.getId(), referenceDate, 0L, 1L);

    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtension.startProcessInstance(
      processDefinitionId);
    adjustProcessInstanceDates(processInstanceDto2.getId(), referenceDate, -1L, 2L);
    final ProcessInstanceEngineDto processInstanceDto3 = engineIntegrationExtension.startProcessInstance(
      processDefinitionId);
    adjustProcessInstanceDates(processInstanceDto3.getId(), referenceDate, -1L, 100L);

    final ProcessInstanceEngineDto processInstanceDto4 = engineIntegrationExtension.startProcessInstance(
      processDefinitionId);
    adjustProcessInstanceDates(processInstanceDto4.getId(), referenceDate, -2L, 1L);
    final ProcessInstanceEngineDto processInstanceDto5 = engineIntegrationExtension.startProcessInstance(
      processDefinitionId);
    adjustProcessInstanceDates(processInstanceDto5.getId(), referenceDate, -2L, 2L);
    final ProcessInstanceEngineDto processInstanceDto6 = engineIntegrationExtension.startProcessInstance(
      processDefinitionId);
    adjustProcessInstanceDates(processInstanceDto6.getId(), referenceDate, -2L, 3L);
    final ProcessInstanceEngineDto processInstanceDto7 = engineIntegrationExtension.startProcessInstance(
      processDefinitionId);
    adjustProcessInstanceDates(processInstanceDto7.getId(), referenceDate, -2L, 4L);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder.createReportData()
      .setGroupByDateInterval(AggregateByDateUnit.DAY)
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersion(processDefinitionVersion)
      .setStartFlowNodeId(START_EVENT)
      .setEndFlowNodeId(END_EVENT)
      .setReportDataType(getTestReportDataType())
      .build();
    reportData.getConfiguration().setSorting(new ReportSortingDto(SORT_BY_VALUE, SortOrder.ASC));
    reportData.getConfiguration().setAggregationTypes(getAggregationTypesAsListForProcessParts());
    final ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData)
      .getResult();

    // then
    assertThat(result.getMeasures())
      .extracting(MeasureResponseDto::getAggregationType)
      .containsExactlyInAnyOrderElementsOf(Arrays.asList(getAggregationTypesAsListForProcessParts()));
    result.getMeasures().forEach(measureResult -> {
      final List<MapResultEntryDto> resultData = measureResult.getData();
      assertThat(resultData)
        .hasSize(3)
        .extracting(MapResultEntryDto::getValue)
        .isSortedAccordingTo(Comparator.naturalOrder());
    });
  }

  @Test
  public void emptyIntervalBetweenTwoProcessInstances() {
    // given
    OffsetDateTime procInstRefDate = OffsetDateTime.now();
    ProcessDefinitionEngineDto procDefDto = deploySimpleServiceTaskProcess();
    startThreeProcessInstances(procInstRefDate, 0, procDefDto, Arrays.asList(1, 2, 9));
    OffsetDateTime activityStartDate = OffsetDateTime.now();
    ProcessInstanceEngineDto processInstanceDto3 =
      engineIntegrationExtension.startProcessInstance(procDefDto.getId());
    adjustProcessInstanceDates(processInstanceDto3.getId(), procInstRefDate, -2L);
    engineDatabaseExtension.changeAllFlowNodeStartDates(processInstanceDto3.getId(), activityStartDate);
    engineDatabaseExtension.changeAllFlowNodeEndDates(
      processInstanceDto3.getId(),
      activityStartDate.plusSeconds(2)
    );

    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setGroupByDateInterval(AggregateByDateUnit.DAY)
      .setProcessDefinitionKey(procDefDto.getKey())
      .setProcessDefinitionVersion(procDefDto.getVersionAsString())
      .setStartFlowNodeId(START_EVENT)
      .setEndFlowNodeId(END_EVENT)
      .setReportDataType(getTestReportDataType())
      .build();
    reportData.getConfiguration().setSorting(new ReportSortingDto(ReportSortingDto.SORT_BY_KEY, SortOrder.DESC));
    ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    final List<MapResultEntryDto> resultData = result.getFirstMeasureData();
    assertThat(resultData).hasSize(3);
    ZonedDateTime startOfToday = truncateToStartOfUnit(OffsetDateTime.now(), ChronoUnit.DAYS);
    assertThat(resultData.get(0).getKey()).isEqualTo(localDateTimeToString(startOfToday));
    assertThat(resultData.get(0).getValue()).isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(
      1000.,
      2000.,
      9000.
    ));
    assertThat(resultData.get(1).getKey()).isEqualTo(localDateTimeToString(startOfToday.minusDays(1)));
    assertThat(resultData.get(1).getValue()).isNull();
    assertThat(resultData.get(2).getKey()).isEqualTo(localDateTimeToString(startOfToday.minusDays(2)));
    assertThat(resultData.get(2).getValue()).isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(2000.));
  }

  @ParameterizedTest
  @MethodSource("staticAggregateByDateUnits")
  public void groupedByStaticDateUnit(final AggregateByDateUnit unit) {
    // given
    List<ProcessInstanceEngineDto> processInstanceDtos = deployAndStartSimpleProcesses(5);
    OffsetDateTime now = OffsetDateTime.now();
    updateProcessInstancesDates(processInstanceDtos, now, mapToChronoUnit(unit));
    importAllEngineEntitiesFromScratch();

    // when
    ProcessInstanceEngineDto dto = processInstanceDtos.get(0);
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setGroupByDateInterval(unit)
      .setProcessDefinitionKey(dto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(dto.getProcessDefinitionVersion())
      .setStartFlowNodeId(START_EVENT)
      .setEndFlowNodeId(END_EVENT)
      .setReportDataType(getTestReportDataType())
      .build();
    reportData.getConfiguration().setSorting(new ReportSortingDto(ReportSortingDto.SORT_BY_KEY, SortOrder.DESC));
    ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getFirstMeasureData()).isNotNull();
    assertDateResultMap(result.getFirstMeasureData(), 5, now, mapToChronoUnit(unit));
  }

  private void assertDateResultMap(List<MapResultEntryDto> resultData,
                                   int size,
                                   OffsetDateTime now,
                                   ChronoUnit unit) {
    assertThat(resultData.size()).isEqualTo(size);
    final ZonedDateTime finalStartOfUnit = truncateToStartOfUnit(now, unit);
    IntStream.range(0, size)
      .forEach(i -> {
        String expectedDateString = localDateTimeToString(finalStartOfUnit.minus(i, unit));
        assertThat(resultData.get(i).getKey()).isEqualTo(expectedDateString);
        assertThat(resultData.get(i).getValue()).isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(1000.));
      });
  }

  private void updateProcessInstancesDates(List<ProcessInstanceEngineDto> procInsts,
                                           OffsetDateTime now,
                                           ChronoUnit unit) {
    Map<String, OffsetDateTime> idToNewStartDate = new HashMap<>();
    Map<String, OffsetDateTime> idToNewEndDate = new HashMap<>();
    for (int i = 0; i < procInsts.size(); i++) {
      String id = procInsts.get(i).getId();
      OffsetDateTime newDate = now.minus(i, unit);
      idToNewStartDate.put(id, newDate);
      idToNewEndDate.put(id, newDate);
      engineDatabaseExtension.changeAllFlowNodeStartDates(id, now);
      engineDatabaseExtension.changeAllFlowNodeEndDates(id, now.plusSeconds(1));
    }
    engineDatabaseExtension.changeProcessInstanceStartDates(idToNewStartDate);
    engineDatabaseExtension.changeProcessInstanceEndDates(idToNewEndDate);

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

  protected ProcessDefinitionEngineDto deploySimpleServiceTaskProcess() {
    return engineIntegrationExtension.deployProcessAndGetProcessDefinition(getSingleServiceTaskProcess());
  }

  private ProcessInstanceEngineDto deployAndStartSimpleServiceTaskProcessWithVariables(Map<String, Object> variables) {
    // @formatter:off
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .name("aProcessName")
      .startEvent(START_EVENT)
      .serviceTask(TEST_ACTIVITY)
        .camundaExpression("${true}")
      .endEvent(END_EVENT)
      .done();
    // @formatter:on
    return engineIntegrationExtension.deployAndStartProcessWithVariables(processModel, variables);
  }

  protected void adjustProcessInstanceDates(String processInstanceId,
                                            OffsetDateTime referenceDate,
                                            long daysToShift) {
    adjustProcessInstanceDates(processInstanceId, referenceDate, daysToShift, null);
  }

  protected abstract void adjustProcessInstanceDates(String processInstanceId,
                                                     OffsetDateTime referenceDate,
                                                     long daysToShift,
                                                     Long durationInSec);

  protected void startThreeProcessInstances(OffsetDateTime procInstRefDate,
                                            int daysToShiftProcessInstance,
                                            ProcessDefinitionEngineDto procDefDto,
                                            List<Integer> activityDurationsInSec) {
    OffsetDateTime activityStartDate = OffsetDateTime.now();
    ProcessInstanceEngineDto processInstanceDto = engineIntegrationExtension.startProcessInstance(procDefDto.getId());
    adjustProcessInstanceDates(processInstanceDto.getId(), procInstRefDate, daysToShiftProcessInstance);

    ProcessInstanceEngineDto processInstanceDto2 =
      engineIntegrationExtension.startProcessInstance(procDefDto.getId());
    adjustProcessInstanceDates(processInstanceDto2.getId(), procInstRefDate, daysToShiftProcessInstance);
    ProcessInstanceEngineDto processInstanceDto3 =
      engineIntegrationExtension.startProcessInstance(procDefDto.getId());
    adjustProcessInstanceDates(processInstanceDto3.getId(), procInstRefDate, daysToShiftProcessInstance);

    Map<String, OffsetDateTime> activityStartDatesToUpdate = new HashMap<>();
    Map<String, OffsetDateTime> endDatesToUpdate = new HashMap<>();
    activityStartDatesToUpdate.put(processInstanceDto.getId(), activityStartDate);
    activityStartDatesToUpdate.put(processInstanceDto2.getId(), activityStartDate);
    activityStartDatesToUpdate.put(processInstanceDto3.getId(), activityStartDate);
    endDatesToUpdate.put(processInstanceDto.getId(), activityStartDate.plusSeconds(activityDurationsInSec.get(0)));
    endDatesToUpdate.put(processInstanceDto2.getId(), activityStartDate.plusSeconds(activityDurationsInSec.get(1)));
    endDatesToUpdate.put(processInstanceDto3.getId(), activityStartDate.plusSeconds(activityDurationsInSec.get(2)));

    engineDatabaseExtension.changeAllFlowNodeStartDates(activityStartDatesToUpdate);
    engineDatabaseExtension.changeAllFlowNodeEndDates(endDatesToUpdate);
  }

  private void assertDurationMapReportResults(AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResponse,
                                              Double[] expectedDurations) {
    assertThat(evaluationResponse.getResult().getMeasures())
      .extracting(MeasureResponseDto::getAggregationType)
      .containsExactly(getAggregationTypesAsListForProcessParts());

    final Map<AggregationDto, List<MapResultEntryDto>> resultByAggregationType = evaluationResponse.getResult()
      .getMeasures()
      .stream()
      .collect(Collectors.toMap(MeasureResponseDto::getAggregationType, MeasureResponseDto::getData));

    Arrays.stream(getAggregationTypesAsListForProcessParts()).forEach(aggregationType -> {
      assertThat(resultByAggregationType.get(aggregationType).get(0).getValue())
        .isEqualTo(calculateExpectedValueGivenDurations(expectedDurations).get(aggregationType));
    });
  }
}