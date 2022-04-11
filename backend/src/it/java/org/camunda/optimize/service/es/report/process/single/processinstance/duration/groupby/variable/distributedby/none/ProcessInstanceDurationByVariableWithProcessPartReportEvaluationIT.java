/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.process.single.processinstance.duration.groupby.variable.distributedby.none;

import lombok.SneakyThrows;
import org.assertj.core.util.Maps;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.IdResponseDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportItemDto;
import org.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.RunningInstancesOnlyFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.VariableGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto;
import org.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResponseDto;
import org.camunda.optimize.dto.optimize.rest.report.CombinedProcessReportResultDataDto;
import org.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
import org.camunda.optimize.dto.optimize.rest.report.measure.MeasureResponseDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.report.process.AbstractProcessDefinitionIT;
import org.camunda.optimize.service.es.report.util.MapResultUtil;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.google.common.collect.Lists.newArrayList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.dto.optimize.ReportConstants.MISSING_VARIABLE_KEY;
import static org.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnitMapper.mapToChronoUnit;
import static org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto.SORT_BY_KEY;
import static org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto.SORT_BY_VALUE;
import static org.camunda.optimize.test.util.DateModificationHelper.truncateToStartOfUnit;
import static org.camunda.optimize.test.util.DurationAggregationUtil.calculateExpectedValueGivenDurations;
import static org.camunda.optimize.test.util.DurationAggregationUtil.calculateExpectedValueGivenDurationsDefaultAggr;
import static org.camunda.optimize.test.util.DurationAggregationUtil.getAggregationTypesAsListForProcessParts;
import static org.camunda.optimize.test.util.ProcessReportDataType.PROC_INST_DUR_GROUP_BY_VARIABLE;
import static org.camunda.optimize.test.util.ProcessReportDataType.PROC_INST_DUR_GROUP_BY_VARIABLE_WITH_PART;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION;
import static org.camunda.optimize.util.BpmnModels.END_LOOP;
import static org.camunda.optimize.util.BpmnModels.START_LOOP;

public class ProcessInstanceDurationByVariableWithProcessPartReportEvaluationIT extends AbstractProcessDefinitionIT {

  private static final String TEST_ACTIVITY = "testActivity";

  @Test
  public void reportEvaluationForOneProcess() {
    // given
    OffsetDateTime startDate = OffsetDateTime.now();
    OffsetDateTime endDate = startDate.plusSeconds(1);
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    engineDatabaseExtension.changeFlowNodeStartDatesForProcessDefinition(
      processInstanceDto.getDefinitionId(),
      startDate
    );
    engineDatabaseExtension.changeFlowNodeEndDatesForProcessDefinition(
      processInstanceDto.getDefinitionId(),
      endDate
    );
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(PROC_INST_DUR_GROUP_BY_VARIABLE_WITH_PART)
      .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
      .setVariableName(DEFAULT_VARIABLE_NAME)
      .setVariableType(DEFAULT_VARIABLE_TYPE)
      .setStartFlowNodeId(START_EVENT)
      .setEndFlowNodeId(END_EVENT)
      .build();

    AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResponse =
      reportClient.evaluateMapReport(reportData);

    // then
    ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey()).isEqualTo(processInstanceDto.getProcessDefinitionKey());
    assertThat(resultReportDataDto.getDefinitionVersions()).contains(processInstanceDto.getProcessDefinitionVersion());
    assertThat(resultReportDataDto.getView()).isNotNull();
    assertThat(resultReportDataDto.getView().getEntity()).isEqualTo(ProcessViewEntity.PROCESS_INSTANCE);
    assertThat(resultReportDataDto.getView().getFirstProperty()).isEqualTo(ViewProperty.DURATION);
    assertThat(resultReportDataDto.getGroupBy().getType()).isEqualTo(ProcessGroupByType.VARIABLE);
    VariableGroupByDto variableGroupByDto = (VariableGroupByDto) resultReportDataDto.getGroupBy();
    assertThat(variableGroupByDto.getValue().getName()).isEqualTo(DEFAULT_VARIABLE_NAME);
    assertThat(variableGroupByDto.getValue().getType()).isEqualTo(DEFAULT_VARIABLE_TYPE);

    final ReportResultResponseDto<List<MapResultEntryDto>> result = evaluationResponse.getResult();
    assertThat(result.getInstanceCount()).isEqualTo(1L);
    assertThat(result.getFirstMeasureData()).hasSize(1);
    final Double calculatedResult = MapResultUtil.getEntryForKey(result.getFirstMeasureData(), DEFAULT_VARIABLE_VALUE)
      .get()
      .getValue();
    assertThat(calculatedResult).isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(1000.));
  }

  @Test
  public void reportEvaluationById() {
    // given
    OffsetDateTime startDate = OffsetDateTime.now();
    OffsetDateTime endDate = startDate.plusSeconds(1);
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    engineDatabaseExtension.changeFlowNodeStartDatesForProcessDefinition(
      processInstanceDto.getDefinitionId(),
      startDate
    );
    engineDatabaseExtension.changeFlowNodeEndDatesForProcessDefinition(
      processInstanceDto.getDefinitionId(),
      endDate
    );
    importAllEngineEntitiesFromScratch();
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(PROC_INST_DUR_GROUP_BY_VARIABLE_WITH_PART)
      .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
      .setVariableName(DEFAULT_VARIABLE_NAME)
      .setVariableType(DEFAULT_VARIABLE_TYPE)
      .setStartFlowNodeId(START_EVENT)
      .setEndFlowNodeId(END_EVENT)
      .build();

    String reportId = createNewReport(reportData);

    // when
    AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResponse =
      reportClient.evaluateMapReportById(reportId);

    // then
    ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey()).isEqualTo(processInstanceDto.getProcessDefinitionKey());
    assertThat(resultReportDataDto.getDefinitionVersions()).contains(processInstanceDto.getProcessDefinitionVersion());
    assertThat(resultReportDataDto.getView()).isNotNull();
    assertThat(resultReportDataDto.getView().getEntity()).isEqualTo(ProcessViewEntity.PROCESS_INSTANCE);
    assertThat(resultReportDataDto.getView().getFirstProperty()).isEqualTo(ViewProperty.DURATION);
    assertThat(resultReportDataDto.getGroupBy().getType()).isEqualTo(ProcessGroupByType.VARIABLE);
    VariableGroupByDto variableGroupByDto = (VariableGroupByDto) resultReportDataDto.getGroupBy();
    assertThat(variableGroupByDto.getValue().getName()).isEqualTo(DEFAULT_VARIABLE_NAME);
    assertThat(variableGroupByDto.getValue().getType()).isEqualTo(DEFAULT_VARIABLE_TYPE);

    final ReportResultResponseDto<List<MapResultEntryDto>> result = evaluationResponse.getResult();
    assertThat(result.getFirstMeasureData()).hasSize(1);
    final Double calculatedResult = MapResultUtil.getEntryForKey(result.getFirstMeasureData(), DEFAULT_VARIABLE_VALUE)
      .get()
      .getValue();
    assertThat(calculatedResult).isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(1000.));
  }

  @Test
  public void evaluateReportForMultipleEvents() {
    // given
    OffsetDateTime startDate = OffsetDateTime.now();
    ProcessDefinitionEngineDto processEngineDto = deploySimpleServiceTaskProcess();
    startThreeProcessInstances(startDate, processEngineDto, Arrays.asList(1, 2, 9));
    Map<String, Object> variables = new HashMap<>();
    variables.put(DEFAULT_VARIABLE_NAME, DEFAULT_VARIABLE_VALUE + 2);
    ProcessInstanceEngineDto processInstanceDto = engineIntegrationExtension.startProcessInstance(
      processEngineDto.getId(),
      variables
    );
    engineDatabaseExtension.changeAllFlowNodeStartDates(processInstanceDto.getId(), startDate);
    engineDatabaseExtension.changeAllFlowNodeEndDates(processInstanceDto.getId(), startDate.plusSeconds(1));
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(PROC_INST_DUR_GROUP_BY_VARIABLE_WITH_PART)
      .setProcessDefinitionKey(processEngineDto.getKey())
      .setProcessDefinitionVersion(processEngineDto.getVersionAsString())
      .setVariableName(DEFAULT_VARIABLE_NAME)
      .setVariableType(DEFAULT_VARIABLE_TYPE)
      .setStartFlowNodeId(START_EVENT)
      .setEndFlowNodeId(END_EVENT)
      .build();
    ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getFirstMeasureData()).isNotNull();
    assertThat(result.getFirstMeasureData()).hasSize(2);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), DEFAULT_VARIABLE_VALUE).get().getValue())
      .isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(1000., 2000., 9000.));
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), DEFAULT_VARIABLE_VALUE + 2).get().getValue())
      .isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(1000.));
  }

  @Test
  public void testCustomOrderOnResultKeyIsApplied() {
    // given
    OffsetDateTime startDate = OffsetDateTime.now();
    ProcessDefinitionEngineDto processEngineDto = deploySimpleServiceTaskProcess();
    startThreeProcessInstances(startDate, processEngineDto, Arrays.asList(1, 2, 9));

    Map<String, Object> variables = new HashMap<>();
    variables.put(DEFAULT_VARIABLE_NAME, DEFAULT_VARIABLE_VALUE + 2);
    ProcessInstanceEngineDto processInstanceDto4 = engineIntegrationExtension.startProcessInstance(
      processEngineDto.getId(),
      variables
    );
    engineDatabaseExtension.changeAllFlowNodeStartDates(processInstanceDto4.getId(), startDate);
    engineDatabaseExtension.changeAllFlowNodeEndDates(processInstanceDto4.getId(), startDate.plusSeconds(1));

    variables.put(DEFAULT_VARIABLE_NAME, DEFAULT_VARIABLE_VALUE + 3);
    ProcessInstanceEngineDto processInstanceDto5 = engineIntegrationExtension.startProcessInstance(
      processEngineDto.getId(),
      variables
    );
    engineDatabaseExtension.changeAllFlowNodeStartDates(processInstanceDto5.getId(), startDate);
    engineDatabaseExtension.changeAllFlowNodeEndDates(processInstanceDto5.getId(), startDate.plusSeconds(1));

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(PROC_INST_DUR_GROUP_BY_VARIABLE_WITH_PART)
      .setProcessDefinitionKey(processEngineDto.getKey())
      .setProcessDefinitionVersion(processEngineDto.getVersionAsString())
      .setVariableName(DEFAULT_VARIABLE_NAME)
      .setVariableType(DEFAULT_VARIABLE_TYPE)
      .setStartFlowNodeId(START_EVENT)
      .setEndFlowNodeId(END_EVENT)
      .build();
    reportData.getConfiguration().setSorting(new ReportSortingDto(SORT_BY_KEY, SortOrder.DESC));
    final ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData)
      .getResult();

    // then
    final List<MapResultEntryDto> resultData = result.getFirstMeasureData();
    assertThat(resultData).hasSize(3);
    final List<String> resultKeys = resultData.stream().map(MapResultEntryDto::getKey).collect(Collectors.toList());
    assertThat(resultKeys).isSortedAccordingTo(Comparator.reverseOrder());
  }

  @Test
  public void testEvaluationResultForAllAggregationTypes() {
    // given
    OffsetDateTime startDate = OffsetDateTime.now();
    ProcessDefinitionEngineDto processEngineDto = deploySimpleServiceTaskProcess();
    startThreeProcessInstances(startDate, processEngineDto, Arrays.asList(1, 2, 9));

    Map<String, Object> variables = new HashMap<>();
    variables.put(DEFAULT_VARIABLE_NAME, DEFAULT_VARIABLE_VALUE + 2);
    ProcessInstanceEngineDto processInstanceDto4 = engineIntegrationExtension.startProcessInstance(
      processEngineDto.getId(),
      variables
    );
    engineDatabaseExtension.changeAllFlowNodeStartDates(processInstanceDto4.getId(), startDate);
    engineDatabaseExtension.changeAllFlowNodeEndDates(processInstanceDto4.getId(), startDate.plusSeconds(1));

    variables.put(DEFAULT_VARIABLE_NAME, DEFAULT_VARIABLE_VALUE + 3);
    ProcessInstanceEngineDto processInstanceDto5 = engineIntegrationExtension.startProcessInstance(
      processEngineDto.getId(),
      variables
    );
    engineDatabaseExtension.changeAllFlowNodeStartDates(processInstanceDto5.getId(), startDate);
    engineDatabaseExtension.changeAllFlowNodeEndDates(processInstanceDto5.getId(), startDate.plusSeconds(1));

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(PROC_INST_DUR_GROUP_BY_VARIABLE_WITH_PART)
      .setProcessDefinitionKey(processEngineDto.getKey())
      .setProcessDefinitionVersion(processEngineDto.getVersionAsString())
      .setVariableName(DEFAULT_VARIABLE_NAME)
      .setVariableType(DEFAULT_VARIABLE_TYPE)
      .setStartFlowNodeId(START_EVENT)
      .setEndFlowNodeId(END_EVENT)
      .build();
    reportData.getConfiguration().setSorting(new ReportSortingDto(SORT_BY_KEY, SortOrder.DESC));
    reportData.getConfiguration().setAggregationTypes(getAggregationTypesAsListForProcessParts());

    final AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResponse =
      reportClient.evaluateMapReport(reportData);

    // then
    assertThat(evaluationResponse.getResult().getMeasures())
      .extracting(MeasureResponseDto::getAggregationType)
      .containsExactly(getAggregationTypesAsListForProcessParts());
    evaluationResponse.getResult().getMeasures().forEach(measureResult -> {
      final List<MapResultEntryDto> resultData = measureResult.getData();
      assertThat(resultData)
        .hasSize(3)
        .extracting(MapResultEntryDto::getKey)
        .isSortedAccordingTo(Comparator.reverseOrder());
      assertThat(MapResultUtil.getEntryForKey(resultData, DEFAULT_VARIABLE_VALUE).get().getValue())
        .isEqualTo(calculateExpectedValueGivenDurations(1000., 9000., 2000.).get(measureResult.getAggregationType()));
    });
  }

  @Test
  public void testCustomOrderOnResultValueIsApplied() {
    // given
    OffsetDateTime startDate = OffsetDateTime.now();
    ProcessDefinitionEngineDto processEngineDto = deploySimpleServiceTaskProcess();
    startThreeProcessInstances(startDate, processEngineDto, Arrays.asList(1, 2, 9));

    Map<String, Object> variables = new HashMap<>();
    variables.put(DEFAULT_VARIABLE_NAME, DEFAULT_VARIABLE_VALUE + 2);
    ProcessInstanceEngineDto processInstanceDto4 = engineIntegrationExtension.startProcessInstance(
      processEngineDto.getId(),
      variables
    );
    engineDatabaseExtension.changeAllFlowNodeStartDates(processInstanceDto4.getId(), startDate);
    engineDatabaseExtension.changeAllFlowNodeEndDates(processInstanceDto4.getId(), startDate.plusSeconds(1));

    variables.put(DEFAULT_VARIABLE_NAME, DEFAULT_VARIABLE_VALUE + 3);
    ProcessInstanceEngineDto processInstanceDto5 = engineIntegrationExtension.startProcessInstance(
      processEngineDto.getId(),
      variables
    );
    engineDatabaseExtension.changeAllFlowNodeStartDates(processInstanceDto5.getId(), startDate);
    engineDatabaseExtension.changeAllFlowNodeEndDates(processInstanceDto5.getId(), startDate.plusSeconds(1));

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(PROC_INST_DUR_GROUP_BY_VARIABLE_WITH_PART)
      .setProcessDefinitionKey(processEngineDto.getKey())
      .setProcessDefinitionVersion(processEngineDto.getVersionAsString())
      .setVariableName(DEFAULT_VARIABLE_NAME)
      .setVariableType(DEFAULT_VARIABLE_TYPE)
      .setStartFlowNodeId(START_EVENT)
      .setEndFlowNodeId(END_EVENT)
      .build();
    reportData.getConfiguration().setSorting(new ReportSortingDto(SORT_BY_VALUE, SortOrder.ASC));
    reportData.getConfiguration().setAggregationTypes(getAggregationTypesAsListForProcessParts());

    final AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResponse =
      reportClient.evaluateMapReport(reportData);

    // then
    assertThat(evaluationResponse.getResult().getMeasures())
      .extracting(MeasureResponseDto::getAggregationType)
      .containsExactly(getAggregationTypesAsListForProcessParts());
    evaluationResponse.getResult().getMeasures().forEach(measureResult -> {
      final List<MapResultEntryDto> resultData = measureResult.getData();
      assertThat(resultData)
        .hasSize(3)
        .extracting(MapResultEntryDto::getValue)
        .isSortedAccordingTo(Comparator.naturalOrder());
    });
  }

  @Test
  public void multipleBuckets_numberVariable_invalidBaseline_returnsEmptyResult() {
    // given
    ProcessDefinitionEngineDto processDefinitionDto = deploySimpleServiceTaskProcess();
    Map<String, Object> variables = new HashMap<>();
    variables.put(DEFAULT_VARIABLE_NAME, 10.0);
    engineIntegrationExtension.startProcessInstance(processDefinitionDto.getId(), variables);

    variables.put(DEFAULT_VARIABLE_NAME, 20.0);
    engineIntegrationExtension.startProcessInstance(processDefinitionDto.getId(), variables);

    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(PROC_INST_DUR_GROUP_BY_VARIABLE_WITH_PART)
      .setProcessDefinitionKey(processDefinitionDto.getKey())
      .setProcessDefinitionVersion(processDefinitionDto.getVersionAsString())
      .setVariableName(DEFAULT_VARIABLE_NAME)
      .setVariableType(VariableType.DOUBLE)
      .setStartFlowNodeId(START_EVENT)
      .setEndFlowNodeId(END_EVENT)
      .build();
    reportData.getConfiguration().getCustomBucket().setActive(true);
    reportData.getConfiguration().getCustomBucket().setBaseline(30.0);
    reportData.getConfiguration().getCustomBucket().setBucketSize(5.0);

    final ReportResultResponseDto<List<MapResultEntryDto>> resultDto = reportClient.evaluateMapReport(reportData)
      .getResult();

    // then
    assertThat(resultDto.getInstanceCount()).isEqualTo(2);
    assertThat(resultDto.getFirstMeasureData()).isNotNull();
    assertThat(resultDto.getFirstMeasureData()).isEmpty();
  }

  @SneakyThrows
  @Test
  public void combinedNumberVariableReport_distinctRanges() {
    // given
    ProcessDefinitionEngineDto processDefinitionDto1 = deploySimpleServiceTaskProcess();
    Map<String, Object> variables = new HashMap<>();
    variables.put(DEFAULT_VARIABLE_NAME, 10.0);
    engineIntegrationExtension.startProcessInstance(processDefinitionDto1.getId(), variables);
    variables.put(DEFAULT_VARIABLE_NAME, 20.0);
    engineIntegrationExtension.startProcessInstance(processDefinitionDto1.getId(), variables);

    ProcessDefinitionEngineDto processDefinitionDto2 = deploySimpleServiceTaskProcess();
    variables.put(DEFAULT_VARIABLE_NAME, 50.0);
    engineIntegrationExtension.startProcessInstance(processDefinitionDto2.getId(), variables);
    variables.put(DEFAULT_VARIABLE_NAME, 100.0);
    engineIntegrationExtension.startProcessInstance(processDefinitionDto2.getId(), variables);

    importAllEngineEntitiesFromScratch();

    ProcessReportDataDto reportData1 = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(PROC_INST_DUR_GROUP_BY_VARIABLE_WITH_PART)
      .setProcessDefinitionKey(processDefinitionDto1.getKey())
      .setProcessDefinitionVersion(processDefinitionDto1.getVersionAsString())
      .setVariableName(DEFAULT_VARIABLE_NAME)
      .setVariableType(VariableType.DOUBLE)
      .setStartFlowNodeId(START_EVENT)
      .setEndFlowNodeId(END_EVENT)
      .build();
    reportData1.getConfiguration().getCustomBucket().setActive(true);
    reportData1.getConfiguration().getCustomBucket().setBaseline(5.0);
    reportData1.getConfiguration().getCustomBucket().setBucketSize(10.0);


    ProcessReportDataDto reportData2 = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(PROC_INST_DUR_GROUP_BY_VARIABLE_WITH_PART)
      .setProcessDefinitionKey(processDefinitionDto2.getKey())
      .setProcessDefinitionVersion(processDefinitionDto2.getVersionAsString())
      .setVariableName(DEFAULT_VARIABLE_NAME)
      .setVariableType(VariableType.DOUBLE)
      .setStartFlowNodeId(START_EVENT)
      .setEndFlowNodeId(END_EVENT)
      .build();
    reportData2.getConfiguration().getCustomBucket().setActive(true);
    reportData2.getConfiguration().getCustomBucket().setBaseline(10.0);
    reportData2.getConfiguration().getCustomBucket().setBucketSize(10.0);

    CombinedReportDataDto combinedReportData = new CombinedReportDataDto();

    List<CombinedReportItemDto> reportIds = new ArrayList<>();
    reportIds.add(
      new CombinedReportItemDto(
        reportClient.createSingleProcessReport(new SingleProcessReportDefinitionRequestDto(reportData1))
      ));
    reportIds.add(
      new CombinedReportItemDto(
        reportClient.createSingleProcessReport(new SingleProcessReportDefinitionRequestDto(reportData2))
      ));

    combinedReportData.setReports(reportIds);
    CombinedReportDefinitionRequestDto combinedReport = new CombinedReportDefinitionRequestDto();
    combinedReport.setData(combinedReportData);

    // when
    final IdResponseDto response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateCombinedReportRequest(combinedReport)
      .execute(IdResponseDto.class, Response.Status.OK.getStatusCode());

    // then
    final CombinedProcessReportResultDataDto result = reportClient.evaluateCombinedReportById(response.getId())
      .getResult();
    assertCombinedDoubleVariableResultsAreInCorrectRanges(10.0, 100.0, 10, 2, result.getData());
  }

  @SneakyThrows
  @Test
  public void combinedNumberVariableReport_intersectingRanges() {
    // given
    ProcessDefinitionEngineDto processDefinitionDto1 = deploySimpleServiceTaskProcess();
    Map<String, Object> variables = new HashMap<>();
    variables.put(DEFAULT_VARIABLE_NAME, 10.0);
    engineIntegrationExtension.startProcessInstance(processDefinitionDto1.getId(), variables);
    variables.put(DEFAULT_VARIABLE_NAME, 20.0);
    engineIntegrationExtension.startProcessInstance(processDefinitionDto1.getId(), variables);

    ProcessDefinitionEngineDto processDefinitionDto2 = deploySimpleServiceTaskProcess();
    variables.put(DEFAULT_VARIABLE_NAME, 15.0);
    engineIntegrationExtension.startProcessInstance(processDefinitionDto2.getId(), variables);
    variables.put(DEFAULT_VARIABLE_NAME, 25.0);
    engineIntegrationExtension.startProcessInstance(processDefinitionDto2.getId(), variables);

    importAllEngineEntitiesFromScratch();

    ProcessReportDataDto reportData1 = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(PROC_INST_DUR_GROUP_BY_VARIABLE_WITH_PART)
      .setProcessDefinitionKey(processDefinitionDto1.getKey())
      .setProcessDefinitionVersion(processDefinitionDto1.getVersionAsString())
      .setVariableName(DEFAULT_VARIABLE_NAME)
      .setVariableType(VariableType.DOUBLE)
      .setStartFlowNodeId(START_EVENT)
      .setEndFlowNodeId(END_EVENT)
      .build();
    reportData1.getConfiguration().getCustomBucket().setActive(true);
    reportData1.getConfiguration().getCustomBucket().setBaseline(5.0);
    reportData1.getConfiguration().getCustomBucket().setBucketSize(5.0);


    ProcessReportDataDto reportData2 = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(PROC_INST_DUR_GROUP_BY_VARIABLE_WITH_PART)
      .setProcessDefinitionKey(processDefinitionDto2.getKey())
      .setProcessDefinitionVersion(processDefinitionDto2.getVersionAsString())
      .setVariableName(DEFAULT_VARIABLE_NAME)
      .setVariableType(VariableType.DOUBLE)
      .setStartFlowNodeId(START_EVENT)
      .setEndFlowNodeId(END_EVENT)
      .build();
    reportData2.getConfiguration().getCustomBucket().setActive(true);
    reportData2.getConfiguration().getCustomBucket().setBaseline(10.0);
    reportData2.getConfiguration().getCustomBucket().setBucketSize(5.0);

    CombinedReportDataDto combinedReportData = new CombinedReportDataDto();

    List<CombinedReportItemDto> reportIds = new ArrayList<>();
    reportIds.add(
      new CombinedReportItemDto(
        reportClient.createSingleProcessReport(new SingleProcessReportDefinitionRequestDto(reportData1))
      ));
    reportIds.add(
      new CombinedReportItemDto(
        reportClient.createSingleProcessReport(new SingleProcessReportDefinitionRequestDto(reportData2))
      ));

    combinedReportData.setReports(reportIds);
    CombinedReportDefinitionRequestDto combinedReport = new CombinedReportDefinitionRequestDto();
    combinedReport.setData(combinedReportData);

    // when
    final IdResponseDto response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateCombinedReportRequest(combinedReport)
      .execute(IdResponseDto.class, Response.Status.OK.getStatusCode());

    // then
    final CombinedProcessReportResultDataDto result = reportClient.evaluateCombinedReportById(response.getId())
      .getResult();
    assertCombinedDoubleVariableResultsAreInCorrectRanges(10.0, 25.0, 4, 2, result.getData());
  }

  @SneakyThrows
  @Test
  public void combinedNumberVariableReport_inclusiveRanges() {
    // given
    ProcessDefinitionEngineDto processDefinitionDto1 = deploySimpleServiceTaskProcess();
    Map<String, Object> variables = new HashMap<>();
    variables.put(DEFAULT_VARIABLE_NAME, 10.0);
    engineIntegrationExtension.startProcessInstance(processDefinitionDto1.getId(), variables);
    variables.put(DEFAULT_VARIABLE_NAME, 30.0);
    engineIntegrationExtension.startProcessInstance(processDefinitionDto1.getId(), variables);

    ProcessDefinitionEngineDto processDefinitionDto2 = deploySimpleServiceTaskProcess();
    variables.put(DEFAULT_VARIABLE_NAME, 15.0);
    engineIntegrationExtension.startProcessInstance(processDefinitionDto2.getId(), variables);
    variables.put(DEFAULT_VARIABLE_NAME, 20.0);
    engineIntegrationExtension.startProcessInstance(processDefinitionDto2.getId(), variables);

    importAllEngineEntitiesFromScratch();

    ProcessReportDataDto reportData1 = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(PROC_INST_DUR_GROUP_BY_VARIABLE_WITH_PART)
      .setProcessDefinitionKey(processDefinitionDto1.getKey())
      .setProcessDefinitionVersion(processDefinitionDto1.getVersionAsString())
      .setVariableName(DEFAULT_VARIABLE_NAME)
      .setVariableType(VariableType.DOUBLE)
      .setStartFlowNodeId(START_EVENT)
      .setEndFlowNodeId(END_EVENT)
      .build();
    reportData1.getConfiguration().getCustomBucket().setActive(true);
    reportData1.getConfiguration().getCustomBucket().setBaseline(5.0);
    reportData1.getConfiguration().getCustomBucket().setBucketSize(5.0);


    ProcessReportDataDto reportData2 = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(PROC_INST_DUR_GROUP_BY_VARIABLE_WITH_PART)
      .setProcessDefinitionKey(processDefinitionDto2.getKey())
      .setProcessDefinitionVersion(processDefinitionDto2.getVersionAsString())
      .setVariableName(DEFAULT_VARIABLE_NAME)
      .setVariableType(VariableType.DOUBLE)
      .setStartFlowNodeId(START_EVENT)
      .setEndFlowNodeId(END_EVENT)
      .build();
    reportData2.getConfiguration().getCustomBucket().setActive(true);
    reportData2.getConfiguration().getCustomBucket().setBaseline(10.0);
    reportData2.getConfiguration().getCustomBucket().setBucketSize(5.0);

    CombinedReportDataDto combinedReportData = new CombinedReportDataDto();

    List<CombinedReportItemDto> reportIds = new ArrayList<>();
    reportIds.add(
      new CombinedReportItemDto(
        reportClient.createSingleProcessReport(new SingleProcessReportDefinitionRequestDto(reportData1))
      ));
    reportIds.add(
      new CombinedReportItemDto(
        reportClient.createSingleProcessReport(new SingleProcessReportDefinitionRequestDto(reportData2))
      ));

    combinedReportData.setReports(reportIds);
    CombinedReportDefinitionRequestDto combinedReport = new CombinedReportDefinitionRequestDto();
    combinedReport.setData(combinedReportData);

    // when
    final IdResponseDto response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateCombinedReportRequest(combinedReport)
      .execute(IdResponseDto.class, Response.Status.OK.getStatusCode());

    // then
    final CombinedProcessReportResultDataDto result = reportClient.evaluateCombinedReportById(response.getId())
      .getResult();
    assertCombinedDoubleVariableResultsAreInCorrectRanges(10.0, 30.0, 5, 2, result.getData());
  }

  @Test
  public void takeCorrectActivityOccurrences() {
    // given
    OffsetDateTime startDate = OffsetDateTime.now().minusHours(1);
    ProcessInstanceEngineDto processInstanceDto =
      deployAndStartLoopingProcess(Maps.newHashMap(DEFAULT_VARIABLE_NAME, DEFAULT_VARIABLE_VALUE));
    engineDatabaseExtension.changeFirstFlowNodeInstanceStartDate(START_LOOP, startDate);
    engineDatabaseExtension.changeFirstFlowNodeInstanceEndDate(END_LOOP, startDate.plusSeconds(2));
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(PROC_INST_DUR_GROUP_BY_VARIABLE_WITH_PART)
      .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
      .setVariableName(DEFAULT_VARIABLE_NAME)
      .setVariableType(DEFAULT_VARIABLE_TYPE)
      .setStartFlowNodeId(START_LOOP)
      .setEndFlowNodeId(END_LOOP)
      .build();

    ReportResultResponseDto<List<MapResultEntryDto>> resultDto = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(resultDto.getFirstMeasureData()).hasSize(1);
    assertThat(MapResultUtil.getEntryForKey(resultDto.getFirstMeasureData(), DEFAULT_VARIABLE_VALUE))
      .isPresent().get().extracting(MapResultEntryDto::getValue)
      .isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(2000.));
  }

  @Test
  public void unknownStartReturnsZero() {
    // given
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    engineDatabaseExtension.changeFlowNodeEndDatesForProcessDefinition(
      processInstanceDto.getDefinitionId(),
      OffsetDateTime.now().plusHours(1)
    );
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(PROC_INST_DUR_GROUP_BY_VARIABLE_WITH_PART)
      .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
      .setVariableName(DEFAULT_VARIABLE_NAME)
      .setVariableType(DEFAULT_VARIABLE_TYPE)
      .setStartFlowNodeId("foo")
      .setEndFlowNodeId(END_EVENT)
      .build();

    ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getFirstMeasureData()).isNotNull();
    assertThat(result.getFirstMeasureData().isEmpty()).isTrue();
  }

  @Test
  public void unknownEndReturnsZero() {
    // given
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    engineDatabaseExtension.changeFlowNodeStartDatesForProcessDefinition(
      processInstanceDto.getDefinitionId(),
      OffsetDateTime.now().minusHours(1)
    );
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(PROC_INST_DUR_GROUP_BY_VARIABLE_WITH_PART)
      .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
      .setVariableName(DEFAULT_VARIABLE_NAME)
      .setVariableType(DEFAULT_VARIABLE_TYPE)
      .setStartFlowNodeId(START_EVENT)
      .setEndFlowNodeId("FooFOO")
      .build();
    ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getFirstMeasureData()).isNotNull();
    assertThat(result.getFirstMeasureData().isEmpty()).isTrue();
  }

  @Test
  public void noAvailableProcessInstancesReturnsZero() {
    // when
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(PROC_INST_DUR_GROUP_BY_VARIABLE_WITH_PART)
      .setProcessDefinitionKey("FOOPROC")
      .setProcessDefinitionVersion("1")
      .setVariableName(DEFAULT_VARIABLE_NAME)
      .setVariableType(DEFAULT_VARIABLE_TYPE)
      .setStartFlowNodeId(START_EVENT)
      .setEndFlowNodeId(END_EVENT)
      .build();

    ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getFirstMeasureData()).isNotNull();
    assertThat(result.getFirstMeasureData().isEmpty()).isTrue();
  }

  @Test
  public void otherProcessDefinitionsDoNoAffectResult() {
    // given
    OffsetDateTime startDate = OffsetDateTime.now();
    ProcessDefinitionEngineDto procDefDto = deploySimpleServiceTaskProcess();
    startThreeProcessInstances(startDate, procDefDto, Arrays.asList(1, 2, 9));

    ProcessInstanceEngineDto procInstDto = deployAndStartSimpleServiceTaskProcess();
    engineDatabaseExtension.changeAllFlowNodeStartDates(procInstDto.getId(), startDate);
    engineDatabaseExtension.changeAllFlowNodeEndDates(procInstDto.getId(), startDate.plusSeconds(2));
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(PROC_INST_DUR_GROUP_BY_VARIABLE_WITH_PART)
      .setProcessDefinitionKey(procDefDto.getKey())
      .setProcessDefinitionVersion(procDefDto.getVersionAsString())
      .setVariableName(DEFAULT_VARIABLE_NAME)
      .setVariableType(DEFAULT_VARIABLE_TYPE)
      .setStartFlowNodeId(START_EVENT)
      .setEndFlowNodeId(END_EVENT)
      .build();

    ReportResultResponseDto<List<MapResultEntryDto>> resultDto = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(resultDto.getFirstMeasureData()).hasSize(1);
    assertThat(MapResultUtil.getEntryForKey(resultDto.getFirstMeasureData(), DEFAULT_VARIABLE_VALUE).get().getValue())
      .isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(1000., 9000., 2000.));
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
      .setReportDataType(PROC_INST_DUR_GROUP_BY_VARIABLE_WITH_PART)
      .setProcessDefinitionKey(processKey)
      .setProcessDefinitionVersion(ALL_VERSIONS)
      .setVariableName(DEFAULT_VARIABLE_NAME)
      .setVariableType(VariableType.STRING)
      .setStartFlowNodeId(START_EVENT)
      .setEndFlowNodeId(END_EVENT)
      .build();

    reportData.setTenantIds(selectedTenants);
    ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(selectedTenants.size());
  }

  @Test
  public void filterInReportWorks() {
    // given
    Map<String, Object> variables = new HashMap<>();
    variables.put(DEFAULT_VARIABLE_NAME, DEFAULT_VARIABLE_VALUE);
    OffsetDateTime startDate = OffsetDateTime.now();
    ProcessInstanceEngineDto processInstanceDto =
      deployAndStartSimpleUserTaskProcessWithVariables(variables);
    engineIntegrationExtension.finishAllRunningUserTasks(processInstanceDto.getId());
    engineDatabaseExtension.changeAllFlowNodeStartDates(processInstanceDto.getId(), startDate);
    engineDatabaseExtension.changeAllFlowNodeEndDates(processInstanceDto.getId(), startDate.plusSeconds(1));
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(PROC_INST_DUR_GROUP_BY_VARIABLE_WITH_PART)
      .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
      .setVariableName(DEFAULT_VARIABLE_NAME)
      .setVariableType(DEFAULT_VARIABLE_TYPE)
      .setStartFlowNodeId(START_EVENT)
      .setEndFlowNodeId(TEST_ACTIVITY)
      .build();

    ReportResultResponseDto<List<MapResultEntryDto>> resultDto = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(resultDto.getFirstMeasureData()).isNotNull();
    assertThat(resultDto.getFirstMeasureData()).hasSize(1);
    assertThat(MapResultUtil.getEntryForKey(resultDto.getFirstMeasureData(), DEFAULT_VARIABLE_VALUE).get().getValue())
      .isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(1000.));

    // when
    processInstanceDto = engineIntegrationExtension.startProcessInstance(
      processInstanceDto.getDefinitionId(),
      variables
    );
    engineDatabaseExtension.changeAllFlowNodeStartDates(processInstanceDto.getId(), startDate);
    engineDatabaseExtension.changeAllFlowNodeEndDates(processInstanceDto.getId(), startDate.plusSeconds(4));
    importAllEngineEntitiesFromScratch();
    final RunningInstancesOnlyFilterDto filter = new RunningInstancesOnlyFilterDto();
    filter.setFilterLevel(FilterApplicationLevel.INSTANCE);
    reportData.setFilter(Collections.singletonList(filter));
    resultDto = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(resultDto.getFirstMeasureData()).isNotNull();
    assertThat(resultDto.getFirstMeasureData()).hasSize(1);
    assertThat(MapResultUtil.getEntryForKey(resultDto.getFirstMeasureData(), DEFAULT_VARIABLE_VALUE).get().getValue())
      .isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(4000.));
  }

  @Test
  public void variableTypeIsImportant() {
    // given
    OffsetDateTime startDate = OffsetDateTime.now();
    Map<String, Object> variables = new HashMap<>();
    variables.put(DEFAULT_VARIABLE_NAME, "1");
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess(variables);
    engineDatabaseExtension.changeAllFlowNodeStartDates(processInstanceDto.getId(), startDate);
    engineDatabaseExtension.changeAllFlowNodeEndDates(processInstanceDto.getId(), startDate.plusSeconds(1));
    variables.put(DEFAULT_VARIABLE_NAME, 1);
    ProcessInstanceEngineDto processInstanceDto2 =
      engineIntegrationExtension.startProcessInstance(processInstanceDto.getDefinitionId(), variables);
    engineDatabaseExtension.changeAllFlowNodeStartDates(processInstanceDto2.getId(), startDate);
    engineDatabaseExtension.changeAllFlowNodeEndDates(processInstanceDto2.getId(), startDate.plusSeconds(2));
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(PROC_INST_DUR_GROUP_BY_VARIABLE_WITH_PART)
      .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
      .setVariableName(DEFAULT_VARIABLE_NAME)
      .setVariableType(DEFAULT_VARIABLE_TYPE)
      .setStartFlowNodeId(START_EVENT)
      .setEndFlowNodeId(END_EVENT)
      .build();

    AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResponse =
      reportClient.evaluateMapReport(reportData);

    // then
    ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey()).isEqualTo(processInstanceDto.getProcessDefinitionKey());
    assertThat(resultReportDataDto.getDefinitionVersions()).contains(processInstanceDto.getProcessDefinitionVersion());

    final ReportResultResponseDto<List<MapResultEntryDto>> resultDto = evaluationResponse.getResult();
    assertThat(resultDto.getFirstMeasureData()).isNotNull();
    assertThat(resultDto.getFirstMeasureData()).hasSize(2);
    assertThat(MapResultUtil.getEntryForKey(resultDto.getFirstMeasureData(), "1").get().getValue()).isEqualTo(1000.);
    assertThat(MapResultUtil.getEntryForKey(resultDto.getFirstMeasureData(), MISSING_VARIABLE_KEY)
                 .get()
                 .getValue()).isEqualTo(2000.);
  }

  @Test
  public void otherVariablesDoNotDistortTheResult() {
    // given
    OffsetDateTime startDate = OffsetDateTime.now();
    Map<String, Object> variables = new HashMap<>();
    variables.put("foo1", "bar1");
    variables.put("foo3", "bar1");
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess(variables);
    engineDatabaseExtension.changeAllFlowNodeStartDates(processInstanceDto.getId(), startDate);
    engineDatabaseExtension.changeAllFlowNodeEndDates(processInstanceDto.getId(), startDate.plusSeconds(1));
    variables.clear();
    variables.put("foo2", "bar1");
    ProcessInstanceEngineDto processInstanceDto2 =
      engineIntegrationExtension.startProcessInstance(processInstanceDto.getDefinitionId(), variables);
    engineDatabaseExtension.changeAllFlowNodeStartDates(processInstanceDto2.getId(), startDate);
    engineDatabaseExtension.changeAllFlowNodeEndDates(processInstanceDto2.getId(), startDate.plusSeconds(5));
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(PROC_INST_DUR_GROUP_BY_VARIABLE_WITH_PART)
      .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
      .setVariableName("foo1")
      .setVariableType(DEFAULT_VARIABLE_TYPE)
      .setStartFlowNodeId(START_EVENT)
      .setEndFlowNodeId(END_EVENT)
      .build();
    AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResponse =
      reportClient.evaluateMapReport(reportData);

    // then
    ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey()).isEqualTo(processInstanceDto.getProcessDefinitionKey());
    assertThat(resultReportDataDto.getDefinitionVersions()).contains(processInstanceDto.getProcessDefinitionVersion());

    final ReportResultResponseDto<List<MapResultEntryDto>> resultDto = evaluationResponse.getResult();
    assertThat(resultDto.getFirstMeasureData()).isNotNull();
    assertThat(resultDto.getFirstMeasureData()).hasSize(2);
    assertThat(MapResultUtil.getEntryForKey(resultDto.getFirstMeasureData(), "bar1").get().getValue()).isEqualTo(1000.);
    assertThat(MapResultUtil.getEntryForKey(resultDto.getFirstMeasureData(), MISSING_VARIABLE_KEY)
                 .get()
                 .getValue()).isEqualTo(5000.);
  }

  @Test
  public void worksWithAllVariableTypes() {
    // given
    OffsetDateTime startDate = OffsetDateTime.now();
    OffsetDateTime endDate = startDate.plusSeconds(1);
    Map<String, Object> variables = new HashMap<>();
    variables.put("dateVar", OffsetDateTime.now());
    variables.put("boolVar", true);
    variables.put("shortVar", (short) 2);
    variables.put("intVar", 5);
    variables.put("longVar", 5L);
    variables.put("doubleVar", 5.5);
    variables.put("stringVar", "aString");
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess(variables);
    engineDatabaseExtension.changeAllFlowNodeStartDates(processInstanceDto.getId(), startDate);
    engineDatabaseExtension.changeAllFlowNodeEndDates(processInstanceDto.getId(), endDate);
    importAllEngineEntitiesFromScratch();

    for (Map.Entry<String, Object> entry : variables.entrySet()) {
      // when
      VariableType variableType = varNameToTypeMap.get(entry.getKey());
      ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
        .createReportData()
        .setReportDataType(PROC_INST_DUR_GROUP_BY_VARIABLE_WITH_PART)
        .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
        .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
        .setVariableName(entry.getKey())
        .setVariableType(variableType)
        .setStartFlowNodeId(START_EVENT)
        .setEndFlowNodeId(END_EVENT)
        .build();
      ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData).getResult();

      // then
      final List<MapResultEntryDto> resultData = result.getFirstMeasureData();
      assertThat(resultData).isNotNull();
      if (VariableType.DATE.equals(variableType)) {
        assertThat(resultData).hasSize(1);
        OffsetDateTime temporal = (OffsetDateTime) variables.get(entry.getKey());

        String dateAsString = embeddedOptimizeExtension.formatToHistogramBucketKey(
          temporal.atZoneSimilarLocal(ZoneId.systemDefault()).toOffsetDateTime(),
          ChronoUnit.MONTHS
        );
        assertThat(resultData.get(0).getKey()).isEqualTo(dateAsString);
        assertThat(resultData.get(0).getValue()).isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(1000.));
      } else if (VariableType.getNumericTypes().contains(variableType)) {
        assertThat(resultData
                     .stream()
                     .mapToDouble(resultEntry -> resultEntry.getValue() == null ? 0.0 : resultEntry.getValue())
                     .sum())
          .isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(1000.));
      } else {
        assertThat(resultData).hasSize(1);
        assertThat(resultData.get(0).getKey()).isEqualTo(entry.getValue().toString());
        assertThat(resultData.get(0).getValue()).isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(1000.));
      }
    }
  }

  @SneakyThrows
  @ParameterizedTest
  @MethodSource("staticAggregateByDateUnits")
  public void groupByDateVariableWorksForAllStaticUnits(final AggregateByDateUnit unit) {
    // given
    final ChronoUnit chronoUnit = mapToChronoUnit(unit);
    final int numberOfInstances = 3;
    final String dateVarName = "dateVar";
    final ProcessDefinitionEngineDto def = deploySimpleServiceTaskProcess();
    Map<String, Object> variables = new HashMap<>();
    OffsetDateTime dateVariableValue = OffsetDateTime.parse("2020-06-15T00:00:00+02:00");

    for (int i = 0; i < numberOfInstances; i++) {
      dateVariableValue = dateVariableValue.plus(1, chronoUnit);
      variables.put(dateVarName, dateVariableValue);
      ProcessInstanceEngineDto instance =
        engineIntegrationExtension.startProcessInstance(def.getId(), variables);
      engineDatabaseExtension.changeAllFlowNodeStartDates(instance.getId(), dateVariableValue);
      engineDatabaseExtension.changeAllFlowNodeEndDates(instance.getId(), dateVariableValue.plusSeconds(1));
    }

    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(PROC_INST_DUR_GROUP_BY_VARIABLE_WITH_PART)
      .setProcessDefinitionKey(def.getKey())
      .setProcessDefinitionVersion(def.getVersionAsString())
      .setVariableName(dateVarName)
      .setVariableType(VariableType.DATE)
      .setStartFlowNodeId(START_EVENT)
      .setEndFlowNodeId(END_EVENT)
      .build();
    reportData.getConfiguration().setGroupByDateVariableUnit(unit);
    List<MapResultEntryDto> resultData = reportClient.evaluateMapReport(reportData).getResult().getFirstMeasureData();

    // then
    assertThat(resultData).isNotNull();
    // there is one bucket per instance since the date variables are each one bucket span apart
    assertThat(resultData).hasSize(numberOfInstances);
    final DateTimeFormatter formatter = embeddedOptimizeExtension.getDateTimeFormatter();
    // buckets are in ascending order, so the first bucket is based on the date variable of the first instance
    dateVariableValue = dateVariableValue.minus(numberOfInstances - 1, chronoUnit);
    for (int i = 0; i < numberOfInstances; i++) {
      final String expectedBucketKey = formatter.format(
        truncateToStartOfUnit(
          dateVariableValue.plus(i, chronoUnit),
          chronoUnit
        ));
      assertThat(resultData.get(i).getValue()).isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(1000.));
      assertThat(resultData.get(i).getKey()).isEqualTo(expectedBucketKey);
    }
  }

  @SneakyThrows
  @Test
  public void groupByDateVariableWorksForAutomaticInterval() {
    // given
    final int numberOfInstances = 3;
    final String dateVarName = "dateVar";
    final ProcessDefinitionEngineDto def = deploySimpleServiceTaskProcess();
    Map<String, Object> variables = new HashMap<>();
    OffsetDateTime dateVariableValue = OffsetDateTime.now();

    for (int i = 0; i < numberOfInstances; i++) {
      dateVariableValue = dateVariableValue.plusMinutes(1);
      variables.put(dateVarName, dateVariableValue);
      ProcessInstanceEngineDto instance = engineIntegrationExtension.startProcessInstance(def.getId(), variables);
      engineDatabaseExtension.changeAllFlowNodeStartDates(instance.getId(), dateVariableValue);
      engineDatabaseExtension.changeAllFlowNodeEndDates(instance.getId(), dateVariableValue.plusSeconds(1));
    }

    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(PROC_INST_DUR_GROUP_BY_VARIABLE)
      .setProcessDefinitionKey(def.getKey())
      .setProcessDefinitionVersion(def.getVersionAsString())
      .setVariableName(dateVarName)
      .setVariableType(VariableType.DATE)
      .setStartFlowNodeId(START_EVENT)
      .setEndFlowNodeId(END_EVENT)
      .build();
    final ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData)
      .getResult();

    // then
    List<MapResultEntryDto> resultData = result.getFirstMeasureData();
    assertThat(resultData).isNotNull();
    assertThat(resultData).hasSize(NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION);
    // buckets span across all variable values (buckets are in descending order, so the first bucket is based on the
    // date variable of the last instance)
    DateTimeFormatter formatter = embeddedOptimizeExtension.getDateTimeFormatter();
    final OffsetDateTime startOfFirstBucket = OffsetDateTime.from(formatter.parse(resultData.get(0).getKey()));
    final OffsetDateTime startOfLastBucket = OffsetDateTime
      .from(formatter.parse(resultData.get(NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION - 1).getKey()));
    final OffsetDateTime firstTruncatedDateVariableValue = dateVariableValue.truncatedTo(ChronoUnit.MILLIS);
    final OffsetDateTime lastTruncatedDateVariableValue =
      dateVariableValue.minusMinutes(numberOfInstances).truncatedTo(ChronoUnit.MILLIS);

    assertThat(startOfFirstBucket).isBeforeOrEqualTo(firstTruncatedDateVariableValue);
    assertThat(startOfLastBucket).isAfterOrEqualTo(lastTruncatedDateVariableValue);
  }

  @SneakyThrows
  @Test
  public void groupByDateVariableForAutomaticInterval_MissingInstancesReturnsEmptyResult() {
    // given
    final String dateVarName = "dateVar";
    final ProcessDefinitionEngineDto def = deploySimpleServiceTaskProcess();

    // when
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(PROC_INST_DUR_GROUP_BY_VARIABLE)
      .setProcessDefinitionKey(def.getKey())
      .setProcessDefinitionVersion(def.getVersionAsString())
      .setVariableName(dateVarName)
      .setVariableType(VariableType.DATE)
      .setStartFlowNodeId(START_EVENT)
      .setEndFlowNodeId(END_EVENT)
      .build();
    final ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData)
      .getResult();

    // then
    List<MapResultEntryDto> resultData = result.getFirstMeasureData();
    assertThat(resultData).isNotNull();
    assertThat(resultData).isEmpty();
  }

  private ProcessDefinitionEngineDto deploySimpleServiceTaskProcess() {
    // @formatter:off
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .name("aProcessName")
      .startEvent(START_EVENT)
      .serviceTask()
        .camundaExpression("${true}")
      .endEvent(END_EVENT)
      .done();
    // @formatter:on
    return engineIntegrationExtension.deployProcessAndGetProcessDefinition(processModel);
  }

  private ProcessInstanceEngineDto deployAndStartSimpleUserTaskProcessWithVariables(Map<String, Object> variables) {
    // @formatter:off
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .name("aProcessName")
      .startEvent(START_EVENT)
      .serviceTask(TEST_ACTIVITY)
        .camundaExpression("${true}")
      .userTask("userTask")
      .endEvent(END_EVENT)
      .done();
    // @formatter:on
    return engineIntegrationExtension.deployAndStartProcessWithVariables(processModel, variables);
  }

  private void startThreeProcessInstances(OffsetDateTime activityStartDate,
                                          ProcessDefinitionEngineDto procDefDto,
                                          List<Integer> activityDurationsInSec) {
    Map<String, Object> variables = new HashMap<>();
    variables.put(DEFAULT_VARIABLE_NAME, DEFAULT_VARIABLE_VALUE);
    ProcessInstanceEngineDto processInstanceDto = engineIntegrationExtension.startProcessInstance(
      procDefDto.getId(),
      variables
    );
    ProcessInstanceEngineDto processInstanceDto2 =
      engineIntegrationExtension.startProcessInstance(procDefDto.getId(), variables);
    ProcessInstanceEngineDto processInstanceDto3 =
      engineIntegrationExtension.startProcessInstance(procDefDto.getId(), variables);

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


}
