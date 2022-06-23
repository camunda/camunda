/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.process.single.processinstance.duration.groupby.variable.distributedby.none;

import lombok.SneakyThrows;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.IdResponseDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportItemDto;
import org.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DurationUnit;
import org.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
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
import org.camunda.optimize.rest.optimize.dto.VariableDto;
import org.camunda.optimize.service.es.report.process.AbstractProcessDefinitionIT;
import org.camunda.optimize.service.es.report.util.MapResultUtil;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.test.it.extension.EngineVariableValue;
import org.camunda.optimize.service.util.TemplatedProcessReportDataBuilder;
import org.camunda.optimize.util.BpmnModels;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.google.common.collect.ImmutableMap.of;
import static com.google.common.collect.Lists.newArrayList;
import static java.time.temporal.ChronoUnit.MILLIS;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.dto.optimize.ReportConstants.MISSING_VARIABLE_KEY;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.ComparisonOperator.GREATER_THAN_EQUALS;
import static org.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnitMapper.mapToChronoUnit;
import static org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto.SORT_BY_KEY;
import static org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto.SORT_BY_VALUE;
import static org.camunda.optimize.test.util.DateModificationHelper.truncateToStartOfUnit;
import static org.camunda.optimize.test.util.DurationAggregationUtil.calculateExpectedValueGivenDurationsDefaultAggr;
import static org.camunda.optimize.test.util.DurationAggregationUtil.getSupportedAggregationTypes;
import static org.camunda.optimize.service.util.ProcessReportDataType.PROC_INST_DUR_GROUP_BY_VARIABLE;
import static org.camunda.optimize.service.util.ProcessReportDataType.PROC_INST_FREQ_GROUP_BY_VARIABLE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION;
import static org.camunda.optimize.util.BpmnModels.getSingleServiceTaskProcess;

public class ProcessInstanceDurationByVariableReportEvaluationIT extends AbstractProcessDefinitionIT {

  @Test
  public void simpleReportEvaluation() {
    // given
    OffsetDateTime startDate = OffsetDateTime.now();
    OffsetDateTime endDate = startDate.plusSeconds(1);
    Map<String, Object> variables = new HashMap<>();
    variables.put("foo", "bar");
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess(variables);
    engineDatabaseExtension.changeProcessInstanceStartDate(processInstanceDto.getId(), startDate);
    engineDatabaseExtension.changeProcessInstanceEndDate(processInstanceDto.getId(), endDate);
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(PROC_INST_DUR_GROUP_BY_VARIABLE)
      .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
      .setVariableName("foo")
      .setVariableType(VariableType.STRING)
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
    assertThat(variableGroupByDto.getValue().getName()).isEqualTo("foo");
    assertThat(variableGroupByDto.getValue().getType()).isEqualTo(VariableType.STRING);

    final ReportResultResponseDto<List<MapResultEntryDto>> resultDto = evaluationResponse.getResult();
    assertThat(resultDto.getInstanceCount()).isEqualTo(1);
    assertThat(resultDto.getFirstMeasureData()).isNotNull();
    assertThat(resultDto.getFirstMeasureData()).hasSize(1);

    final List<MapResultEntryDto> resultData = resultDto.getFirstMeasureData();
    final MapResultEntryDto resultEntry = resultData.get(0);
    assertThat(resultEntry).isNotNull();
    assertThat(resultEntry.getKey()).isEqualTo("bar");
    assertThat(resultEntry.getValue()).isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(1000.));
  }

  @Test
  public void simpleReportEvaluationById() {
    // given
    OffsetDateTime startDate = OffsetDateTime.now();
    OffsetDateTime endDate = startDate.plusSeconds(1);
    Map<String, Object> variables = new HashMap<>();
    variables.put("foo", "bar");
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleServiceTaskProcess(variables);
    engineDatabaseExtension.changeProcessInstanceStartDate(processInstance.getId(), startDate);
    engineDatabaseExtension.changeProcessInstanceEndDate(processInstance.getId(), endDate);
    importAllEngineEntitiesFromScratch();
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(PROC_INST_DUR_GROUP_BY_VARIABLE)
      .setProcessDefinitionKey(processInstance.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstance.getProcessDefinitionVersion())
      .setVariableName("foo")
      .setVariableType(VariableType.STRING)
      .build();

    final String reportId = createNewReport(reportData);

    // when
    AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResponse =
      reportClient.evaluateMapReportById(reportId);

    // then
    ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey()).isEqualTo(processInstance.getProcessDefinitionKey());
    assertThat(resultReportDataDto.getDefinitionVersions()).contains(processInstance.getProcessDefinitionVersion());
    assertThat(resultReportDataDto.getView()).isNotNull();
    assertThat(resultReportDataDto.getView().getEntity()).isEqualTo(ProcessViewEntity.PROCESS_INSTANCE);
    assertThat(resultReportDataDto.getView().getFirstProperty()).isEqualTo(ViewProperty.DURATION);
    assertThat(resultReportDataDto.getGroupBy().getType()).isEqualTo(ProcessGroupByType.VARIABLE);
    VariableGroupByDto variableGroupByDto = (VariableGroupByDto) resultReportDataDto.getGroupBy();
    assertThat(variableGroupByDto.getValue().getName()).isEqualTo("foo");
    assertThat(variableGroupByDto.getValue().getType()).isEqualTo(VariableType.STRING);

    final ReportResultResponseDto<List<MapResultEntryDto>> resultDto = evaluationResponse.getResult();
    assertThat(resultDto.getInstanceCount()).isEqualTo(1);
    assertThat(resultDto.getFirstMeasureData()).isNotNull();
    assertThat(resultDto.getFirstMeasureData()).hasSize(1);

    final List<MapResultEntryDto> resultData = resultDto.getFirstMeasureData();
    final MapResultEntryDto resultEntry = resultData.get(0);
    assertThat(resultEntry).isNotNull();
    assertThat(resultEntry.getKey()).isEqualTo("bar");
    assertThat(resultEntry.getValue()).isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(1000.));
  }

  @Test
  public void customOrderOnResultKeyIsApplied() {
    // given
    Map<String, Object> variables = new HashMap<>();
    variables.put("foo", "bar1");
    ProcessDefinitionEngineDto processDefinitionDto = engineIntegrationExtension.deployProcessAndGetProcessDefinition(
      getSingleServiceTaskProcess());
    startProcessInstanceShiftedBySeconds(variables, processDefinitionDto.getId(), 1);
    variables.put("foo", "bar2");
    startProcessInstanceShiftedBySeconds(variables, processDefinitionDto.getId(), 1);
    startProcessInstanceShiftedBySeconds(variables, processDefinitionDto.getId(), 9);
    variables.put("foo", "bar3");
    startProcessInstanceShiftedBySeconds(variables, processDefinitionDto.getId(), 2);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(PROC_INST_DUR_GROUP_BY_VARIABLE)
      .setProcessDefinitionKey(processDefinitionDto.getKey())
      .setProcessDefinitionVersion(ALL_VERSIONS)
      .setVariableName("foo")
      .setVariableType(VariableType.STRING)
      .build();
    reportData.getConfiguration().setSorting(new ReportSortingDto(SORT_BY_KEY, SortOrder.DESC));
    final ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData)
      .getResult();

    // then
    final List<MapResultEntryDto> resultData = result.getFirstMeasureData();
    assertThat(resultData).hasSize(3);
    final List<String> resultKeys = resultData.stream().map(MapResultEntryDto::getKey).collect(Collectors.toList());
    // expect ascending order
    assertThat(resultKeys).isSortedAccordingTo(Comparator.reverseOrder());
  }

  @Test
  public void customOrderOnResultValueIsApplied() {
    // given
    Map<String, Object> variables = new HashMap<>();
    variables.put("foo", "bar1");
    ProcessDefinitionEngineDto processDefinitionDto = engineIntegrationExtension.deployProcessAndGetProcessDefinition(
      getSingleServiceTaskProcess());
    startProcessInstanceShiftedBySeconds(variables, processDefinitionDto.getId(), 1);
    variables.put("foo", "bar2");
    startProcessInstanceShiftedBySeconds(variables, processDefinitionDto.getId(), 1);
    startProcessInstanceShiftedBySeconds(variables, processDefinitionDto.getId(), 9);
    variables.put("foo", "bar3");
    startProcessInstanceShiftedBySeconds(variables, processDefinitionDto.getId(), 2);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(PROC_INST_DUR_GROUP_BY_VARIABLE)
      .setProcessDefinitionKey(processDefinitionDto.getKey())
      .setProcessDefinitionVersion(ALL_VERSIONS)
      .setVariableName("foo")
      .setVariableType(VariableType.STRING)
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
      final List<MapResultEntryDto> resultData = measureResult.getData();
      assertThat(resultData)
        .hasSize(3)
        .extracting(MapResultEntryDto::getValue)
        .isSortedAccordingTo(Comparator.naturalOrder());
    });
  }

  @Test
  public void dateVariablesAreSortedAscByDefault() {
    // given
    Map<String, Object> variables = new HashMap<>();
    variables.put("dateVar", OffsetDateTime.now());

    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess(variables);

    variables = Collections.singletonMap("dateVar", OffsetDateTime.now().minusDays(2));
    engineIntegrationExtension.startProcessInstance(processInstanceDto.getDefinitionId(), variables);

    variables = Collections.singletonMap("dateVar", OffsetDateTime.now().minusDays(1));
    engineIntegrationExtension.startProcessInstance(processInstanceDto.getDefinitionId(), variables);

    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(PROC_INST_FREQ_GROUP_BY_VARIABLE)
      .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
      .setVariableName("dateVar")
      .setVariableType(VariableType.DATE)
      .build();

    AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> response =
      reportClient.evaluateMapReport(reportData);

    // then
    final List<MapResultEntryDto> resultData = response.getResult().getFirstMeasureData();
    final List<String> resultKeys = resultData.stream().map(MapResultEntryDto::getKey).collect(Collectors.toList());
    assertThat(resultKeys).isSortedAccordingTo(Comparator.naturalOrder());
  }

  @Test
  public void otherProcessDefinitionsDoNoAffectResult() {
    // given
    OffsetDateTime startDate = OffsetDateTime.now();
    OffsetDateTime endDate = startDate.plusSeconds(1);
    Map<String, Object> variables = new HashMap<>();
    variables.put("foo", "bar");
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess(variables);
    engineDatabaseExtension.changeProcessInstanceStartDate(processInstanceDto.getId(), startDate);
    engineDatabaseExtension.changeProcessInstanceEndDate(processInstanceDto.getId(), endDate);
    variables.put("foo", "bar2");
    processInstanceDto = deployAndStartSimpleServiceTaskProcess(variables);
    engineDatabaseExtension.changeProcessInstanceStartDate(processInstanceDto.getId(), startDate);
    engineDatabaseExtension.changeProcessInstanceEndDate(processInstanceDto.getId(), endDate);
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(PROC_INST_DUR_GROUP_BY_VARIABLE)
      .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
      .setVariableName("foo")
      .setVariableType(VariableType.STRING)
      .build();
    AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResponse =
      reportClient.evaluateMapReport(reportData);

    // then
    ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey()).isEqualTo(processInstanceDto.getProcessDefinitionKey());
    assertThat(resultReportDataDto.getDefinitionVersions()).contains(processInstanceDto.getProcessDefinitionVersion());

    final List<MapResultEntryDto> resultData = evaluationResponse.getResult().getFirstMeasureData();
    assertThat(resultData).isNotNull();
    assertThat(resultData).hasSize(1);
    assertThat(resultData.get(0).getKey()).isEqualTo("bar2");
    assertThat(resultData.get(0).getValue()).isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(1000.));
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
      .setReportDataType(PROC_INST_DUR_GROUP_BY_VARIABLE)
      .setProcessDefinitionKey(processKey)
      .setProcessDefinitionVersion(ALL_VERSIONS)
      .setVariableName(DEFAULT_VARIABLE_NAME)
      .setVariableType(VariableType.STRING)
      .build();

    reportData.setTenantIds(selectedTenants);
    ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(selectedTenants.size());
  }

  @Test
  public void multipleProcessInstances() {
    // given
    Map<String, Object> variables = new HashMap<>();
    variables.put("foo", "bar1");
    ProcessDefinitionEngineDto processDefinitionDto = engineIntegrationExtension.deployProcessAndGetProcessDefinition(
      getSingleServiceTaskProcess());
    startProcessInstanceShiftedBySeconds(variables, processDefinitionDto.getId(), 1);
    variables.put("foo", "bar2");
    startProcessInstanceShiftedBySeconds(variables, processDefinitionDto.getId(), 1);
    startProcessInstanceShiftedBySeconds(variables, processDefinitionDto.getId(), 9);
    startProcessInstanceShiftedBySeconds(variables, processDefinitionDto.getId(), 2);
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(PROC_INST_DUR_GROUP_BY_VARIABLE)
      .setProcessDefinitionKey(processDefinitionDto.getKey())
      .setProcessDefinitionVersion(String.valueOf(processDefinitionDto.getVersion()))
      .setVariableName("foo")
      .setVariableType(VariableType.STRING)
      .build();

    AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResponse =
      reportClient.evaluateMapReport(reportData);

    // then
    ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey()).isEqualTo(processDefinitionDto.getKey());
    assertThat(resultReportDataDto.getDefinitionVersions()).contains(processDefinitionDto.getVersionAsString());

    final ReportResultResponseDto<List<MapResultEntryDto>> result = evaluationResponse.getResult();
    assertThat(result.getFirstMeasureData()).isNotNull();
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), "bar1").get().getValue())
      .isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(1000.));
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), "bar2").get().getValue())
      .isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(1000., 9000., 2000.));
  }

  @Test
  public void multipleVariableValuesInOneInstance() {
    // given
    final ProcessDefinitionEngineDto processDefinitionDto =
      engineIntegrationExtension.deployProcessAndGetProcessDefinition(getSingleServiceTaskProcess());
    final VariableDto listVar = variablesClient.createListJsonObjectVariableDto(List.of("value1", "value2"));
    Map<String, Object> variables = new HashMap<>();
    variables.put("listVar", listVar);
    startProcessInstanceShiftedBySeconds(variables, processDefinitionDto.getId(), 1);
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(PROC_INST_DUR_GROUP_BY_VARIABLE)
      .setProcessDefinitionKey(processDefinitionDto.getKey())
      .setProcessDefinitionVersion(String.valueOf(processDefinitionDto.getVersion()))
      .setVariableName("listVar")
      .setVariableType(VariableType.STRING)
      .build();

    AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResponse =
      reportClient.evaluateMapReport(reportData);

    // then
    final ReportResultResponseDto<List<MapResultEntryDto>> result = evaluationResponse.getResult();
    assertThat(result.getFirstMeasureData()).isNotNull();
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), "value1"))
      .isPresent()
      .map(MapResultEntryDto::getValue)
      .contains(calculateExpectedValueGivenDurationsDefaultAggr(1000.));
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), "value2"))
      .isPresent()
      .map(MapResultEntryDto::getValue)
      .contains(calculateExpectedValueGivenDurationsDefaultAggr(1000.));
  }

  @Test
  public void numberVariable_customBuckets() {
    // given
    ProcessDefinitionEngineDto processDefinitionDto = engineIntegrationExtension.deployProcessAndGetProcessDefinition(
      getSingleServiceTaskProcess());
    Map<String, Object> variables = new HashMap<>();
    variables.put("foo", 100.0);
    startProcessInstanceShiftedBySeconds(variables, processDefinitionDto.getId(), 1);
    variables.put("foo", 200.0);
    startProcessInstanceShiftedBySeconds(variables, processDefinitionDto.getId(), 1);
    variables.put("foo", 300.0);
    startProcessInstanceShiftedBySeconds(variables, processDefinitionDto.getId(), 1);

    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(PROC_INST_DUR_GROUP_BY_VARIABLE)
      .setProcessDefinitionKey(processDefinitionDto.getKey())
      .setProcessDefinitionVersion(String.valueOf(processDefinitionDto.getVersion()))
      .setVariableName("foo")
      .setVariableType(VariableType.DOUBLE)
      .build();
    reportData.getConfiguration().getCustomBucket().setActive(true);
    reportData.getConfiguration().getCustomBucket().setBaseline(10.0);
    reportData.getConfiguration().getCustomBucket().setBucketSize(100.0);

    final ReportResultResponseDto<List<MapResultEntryDto>> resultDto = reportClient.evaluateMapReport(reportData)
      .getResult();

    // then
    assertThat(resultDto.getInstanceCount()).isEqualTo(3);
    assertThat(resultDto.getFirstMeasureData()).isNotNull();
    assertThat(resultDto.getFirstMeasureData()).hasSize(3);
    assertThat(resultDto.getFirstMeasureData().stream()
                 .map(MapResultEntryDto::getKey)
                 .collect(toList()))
      .containsExactly("10.00", "110.00", "210.00");
    assertThat(resultDto.getFirstMeasureData().get(0).getValue()).isEqualTo(1000L);
    assertThat(resultDto.getFirstMeasureData().get(1).getValue()).isEqualTo(1000L);
    assertThat(resultDto.getFirstMeasureData().get(2).getValue()).isEqualTo(1000L);
  }

  @Test
  public void numberVariable_customBuckets_negativeValuesAndNegativeBaseline() {
    // given
    ProcessDefinitionEngineDto processDefinitionDto = engineIntegrationExtension.deployProcessAndGetProcessDefinition(
      getSingleServiceTaskProcess());
    Map<String, Object> variables = new HashMap<>();
    variables.put("foo", -100.0);
    startProcessInstanceShiftedBySeconds(variables, processDefinitionDto.getId(), 1);
    variables.put("foo", -200.0);
    startProcessInstanceShiftedBySeconds(variables, processDefinitionDto.getId(), 1);

    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(PROC_INST_DUR_GROUP_BY_VARIABLE)
      .setProcessDefinitionKey(processDefinitionDto.getKey())
      .setProcessDefinitionVersion(String.valueOf(processDefinitionDto.getVersion()))
      .setVariableName("foo")
      .setVariableType(VariableType.DOUBLE)
      .build();
    reportData.getConfiguration().getCustomBucket().setActive(true);
    reportData.getConfiguration().getCustomBucket().setBaseline(-300.0);
    reportData.getConfiguration().getCustomBucket().setBucketSize(null);

    final ReportResultResponseDto<List<MapResultEntryDto>> resultDto = reportClient.evaluateMapReport(reportData)
      .getResult();

    // then the correct baseline is used and both instances are included in the result
    assertThat(resultDto.getInstanceCount()).isEqualTo(2);
    assertThat(resultDto.getFirstMeasureData()).isNotNull();
    assertThat(resultDto.getFirstMeasureData().get(0).getKey()).isEqualTo("-300.00");
    assertThat(resultDto.getFirstMeasureData()
                 .stream()
                 .map(MapResultEntryDto::getValue)
                 .filter(Objects::nonNull)
                 .collect(toList()))
      .containsExactly(1000.0, 1000.0);
  }

  @Test
  public void numberVariable_invalidBaseline_returnsEmptyResult() {
    // given
    ProcessDefinitionEngineDto processDefinitionDto = engineIntegrationExtension.deployProcessAndGetProcessDefinition(
      getSingleServiceTaskProcess());
    Map<String, Object> variables = new HashMap<>();
    variables.put("foo", 10.0);
    startProcessInstanceShiftedBySeconds(variables, processDefinitionDto.getId(), 1);
    variables.put("foo", 20.0);
    startProcessInstanceShiftedBySeconds(variables, processDefinitionDto.getId(), 1);

    importAllEngineEntitiesFromScratch();

    // when the baseline is larger than the max. variable value
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(PROC_INST_DUR_GROUP_BY_VARIABLE)
      .setProcessDefinitionKey(processDefinitionDto.getKey())
      .setProcessDefinitionVersion(String.valueOf(processDefinitionDto.getVersion()))
      .setVariableName("foo")
      .setVariableType(VariableType.DOUBLE)
      .build();
    reportData.getConfiguration().getCustomBucket().setActive(true);
    reportData.getConfiguration().getCustomBucket().setBaseline(30.0);
    reportData.getConfiguration().getCustomBucket().setBucketSize(5.0);

    final ReportResultResponseDto<List<MapResultEntryDto>> resultDto = reportClient.evaluateMapReport(reportData)
      .getResult();

    // then the result is empty
    assertThat(resultDto.getFirstMeasureData()).isNotNull();
    assertThat(resultDto.getFirstMeasureData()).isEmpty();
  }

  @Test
  public void negativeNumberVariable_defaultBaselineWorks() {
    // given
    ProcessDefinitionEngineDto processDefinitionDto = engineIntegrationExtension.deployProcessAndGetProcessDefinition(
      getSingleServiceTaskProcess());
    Map<String, Object> variables = new HashMap<>();
    variables.put("foo", -1);
    startProcessInstanceShiftedBySeconds(variables, processDefinitionDto.getId(), 1);
    variables.put("foo", -5);
    startProcessInstanceShiftedBySeconds(variables, processDefinitionDto.getId(), 1);

    importAllEngineEntitiesFromScratch();

    // when there is no baseline set
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(PROC_INST_DUR_GROUP_BY_VARIABLE)
      .setProcessDefinitionKey(processDefinitionDto.getKey())
      .setProcessDefinitionVersion(String.valueOf(processDefinitionDto.getVersion()))
      .setVariableName("foo")
      .setVariableType(VariableType.INTEGER)
      .build();

    final ReportResultResponseDto<List<MapResultEntryDto>> resultDto = reportClient.evaluateMapReport(reportData)
      .getResult();

    // then the result includes all instances
    assertThat(resultDto.getFirstMeasureData()).isNotNull();
    assertThat(resultDto.getFirstMeasureData())
      .filteredOn(result -> result.getValue() != null)
      .extracting(MapResultEntryDto::getValue)
      .containsExactlyInAnyOrder(1000.0, 1000.0);
  }

  @Test
  public void doubleVariable_bucketKeysHaveTwoDecimalPlaces() {
    // given
    ProcessDefinitionEngineDto processDefinitionDto = engineIntegrationExtension.deployProcessAndGetProcessDefinition(
      getSingleServiceTaskProcess());
    Map<String, Object> variables = new HashMap<>();
    variables.put("foo", 1.0);
    startProcessInstanceShiftedBySeconds(variables, processDefinitionDto.getId(), 1);
    variables.put("foo", 5.0);
    startProcessInstanceShiftedBySeconds(variables, processDefinitionDto.getId(), 1);

    importAllEngineEntitiesFromScratch();

    // when there is no baseline set
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(PROC_INST_DUR_GROUP_BY_VARIABLE)
      .setProcessDefinitionKey(processDefinitionDto.getKey())
      .setProcessDefinitionVersion(String.valueOf(processDefinitionDto.getVersion()))
      .setVariableName("foo")
      .setVariableType(VariableType.DOUBLE)
      .build();

    final ReportResultResponseDto<List<MapResultEntryDto>> resultDto = reportClient.evaluateMapReport(reportData)
      .getResult();

    // then the result includes all instances
    assertThat(resultDto.getFirstMeasureData()).isNotNull();
    assertThat(resultDto.getFirstMeasureData())
      .extracting(MapResultEntryDto::getKey)
      .allMatch(key -> key.length() - key.indexOf(".") - 1 == 2); // key should have two chars after the decimal
  }

  @Test
  public void numberVariable_largeValues_notTooManyAutomaticBuckets() {
    // given
    final ProcessDefinitionEngineDto processDefinitionDto =
      engineIntegrationExtension.deployProcessAndGetProcessDefinition(getSingleServiceTaskProcess());
    Map<String, Object> variables = new HashMap<>();
    variables.put("longVar", 9_100_000_000_000_000_000L);
    startProcessInstanceShiftedBySeconds(variables, processDefinitionDto.getId(), 1);
    variables.put("longVar", -920_000_000_000_000_000L);
    startProcessInstanceShiftedBySeconds(variables, processDefinitionDto.getId(), 1);

    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(PROC_INST_DUR_GROUP_BY_VARIABLE)
      .setProcessDefinitionKey(processDefinitionDto.getKey())
      .setProcessDefinitionVersion(String.valueOf(processDefinitionDto.getVersion()))
      .setVariableName("longVar")
      .setVariableType(VariableType.LONG)
      .build();

    final List<MapResultEntryDto> resultData = reportClient.evaluateMapReport(reportData)
      .getResult()
      .getFirstMeasureData();

    // then the amount of buckets does not exceed NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION
    // (a precaution to avoid too many buckets for distributed reports)
    assertThat(resultData)
      .isNotNull()
      .isNotEmpty()
      .hasSizeLessThanOrEqualTo(NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION);
  }

  @SneakyThrows
  @Test
  public void combinedNumberVariableReport_distinctRanges() {
    // given
    ProcessDefinitionEngineDto processDefinitionDto1 = engineIntegrationExtension.deployProcessAndGetProcessDefinition(
      getSingleServiceTaskProcess());
    Map<String, Object> variables = new HashMap<>();
    variables.put("doubleVar", 10.0);
    startProcessInstanceShiftedBySeconds(variables, processDefinitionDto1.getId(), 1);
    variables.put("doubleVar", 20.0);
    startProcessInstanceShiftedBySeconds(variables, processDefinitionDto1.getId(), 1);

    ProcessDefinitionEngineDto processDefinitionDto2 = engineIntegrationExtension.deployProcessAndGetProcessDefinition(
      getSingleServiceTaskProcess());
    variables.put("doubleVar", 50.0);
    startProcessInstanceShiftedBySeconds(variables, processDefinitionDto2.getId(), 1);
    variables.put("doubleVar", 100.0);
    startProcessInstanceShiftedBySeconds(variables, processDefinitionDto2.getId(), 1);

    importAllEngineEntitiesFromScratch();

    ProcessReportDataDto reportData1 = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(PROC_INST_DUR_GROUP_BY_VARIABLE)
      .setProcessDefinitionKey(processDefinitionDto1.getKey())
      .setProcessDefinitionVersion(String.valueOf(processDefinitionDto1.getVersion()))
      .setVariableName("doubleVar")
      .setVariableType(VariableType.DOUBLE)
      .build();
    reportData1.getConfiguration().getCustomBucket().setActive(true);
    reportData1.getConfiguration().getCustomBucket().setBucketSize(10.0);


    ProcessReportDataDto reportData2 = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(PROC_INST_DUR_GROUP_BY_VARIABLE)
      .setProcessDefinitionKey(processDefinitionDto2.getKey())
      .setProcessDefinitionVersion(String.valueOf(processDefinitionDto2.getVersion()))
      .setVariableName("doubleVar")
      .setVariableType(VariableType.DOUBLE)
      .build();
    reportData2.getConfiguration().getCustomBucket().setActive(true);
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
    ProcessDefinitionEngineDto processDefinitionDto1 = engineIntegrationExtension.deployProcessAndGetProcessDefinition(
      getSingleServiceTaskProcess());
    Map<String, Object> variables = new HashMap<>();
    variables.put("doubleVar", 10.0);
    startProcessInstanceShiftedBySeconds(variables, processDefinitionDto1.getId(), 1);
    variables.put("doubleVar", 20.0);
    startProcessInstanceShiftedBySeconds(variables, processDefinitionDto1.getId(), 1);

    ProcessDefinitionEngineDto processDefinitionDto2 = engineIntegrationExtension.deployProcessAndGetProcessDefinition(
      getSingleServiceTaskProcess());
    variables.put("doubleVar", 15.0);
    startProcessInstanceShiftedBySeconds(variables, processDefinitionDto2.getId(), 1);
    variables.put("doubleVar", 25.0);
    startProcessInstanceShiftedBySeconds(variables, processDefinitionDto2.getId(), 1);

    importAllEngineEntitiesFromScratch();

    ProcessReportDataDto reportData1 = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(PROC_INST_DUR_GROUP_BY_VARIABLE)
      .setProcessDefinitionKey(processDefinitionDto1.getKey())
      .setProcessDefinitionVersion(String.valueOf(processDefinitionDto1.getVersion()))
      .setVariableName("doubleVar")
      .setVariableType(VariableType.DOUBLE)
      .build();
    reportData1.getConfiguration().getCustomBucket().setActive(true);
    reportData1.getConfiguration().getCustomBucket().setBucketSize(5.0);


    ProcessReportDataDto reportData2 = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(PROC_INST_DUR_GROUP_BY_VARIABLE)
      .setProcessDefinitionKey(processDefinitionDto2.getKey())
      .setProcessDefinitionVersion(String.valueOf(processDefinitionDto2.getVersion()))
      .setVariableName("doubleVar")
      .setVariableType(VariableType.DOUBLE)
      .build();
    reportData2.getConfiguration().getCustomBucket().setActive(true);
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
    ProcessDefinitionEngineDto processDefinitionDto1 = engineIntegrationExtension.deployProcessAndGetProcessDefinition(
      getSingleServiceTaskProcess());
    Map<String, Object> variables = new HashMap<>();
    variables.put("doubleVar", 10.0);
    startProcessInstanceShiftedBySeconds(variables, processDefinitionDto1.getId(), 1);
    variables.put("doubleVar", 30.0);
    startProcessInstanceShiftedBySeconds(variables, processDefinitionDto1.getId(), 1);

    ProcessDefinitionEngineDto processDefinitionDto2 = engineIntegrationExtension.deployProcessAndGetProcessDefinition(
      getSingleServiceTaskProcess());
    variables.put("doubleVar", 15.0);
    startProcessInstanceShiftedBySeconds(variables, processDefinitionDto2.getId(), 1);
    variables.put("doubleVar", 20.0);
    startProcessInstanceShiftedBySeconds(variables, processDefinitionDto2.getId(), 1);

    importAllEngineEntitiesFromScratch();

    ProcessReportDataDto reportData1 = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(PROC_INST_DUR_GROUP_BY_VARIABLE)
      .setProcessDefinitionKey(processDefinitionDto1.getKey())
      .setProcessDefinitionVersion(String.valueOf(processDefinitionDto1.getVersion()))
      .setVariableName("doubleVar")
      .setVariableType(VariableType.DOUBLE)
      .build();
    reportData1.getConfiguration().getCustomBucket().setActive(true);
    reportData1.getConfiguration().getCustomBucket().setBucketSize(5.0);


    ProcessReportDataDto reportData2 = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(PROC_INST_DUR_GROUP_BY_VARIABLE)
      .setProcessDefinitionKey(processDefinitionDto2.getKey())
      .setProcessDefinitionVersion(String.valueOf(processDefinitionDto2.getVersion()))
      .setVariableName("doubleVar")
      .setVariableType(VariableType.DOUBLE)
      .build();
    reportData2.getConfiguration().getCustomBucket().setActive(true);
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
  public void calculateDurationForRunningProcessInstances() {
    // given
    OffsetDateTime now = OffsetDateTime.now();
    LocalDateUtil.setCurrentTime(now);

    Map<String, Object> variables = new HashMap<>();
    variables.put("foo", "bar");

    // 1 completed proc inst
    ProcessInstanceEngineDto completeProcessInstanceDto = deployAndStartUserTaskProcessWithVariables(variables);
    engineIntegrationExtension.finishAllRunningUserTasks(completeProcessInstanceDto.getId());

    OffsetDateTime completedProcInstStartDate = now.minusDays(2);
    OffsetDateTime completedProcInstEndDate = completedProcInstStartDate.plus(1000, MILLIS);
    engineDatabaseExtension.changeProcessInstanceStartDate(
      completeProcessInstanceDto.getId(),
      completedProcInstStartDate
    );
    engineDatabaseExtension.changeProcessInstanceEndDate(completeProcessInstanceDto.getId(), completedProcInstEndDate);

    // 1 running proc inst
    final ProcessInstanceEngineDto newRunningProcessInstance = engineIntegrationExtension.startProcessInstance(
      completeProcessInstanceDto.getDefinitionId(),
      variables
    );
    OffsetDateTime runningProcInstStartDate = now.minusDays(1);
    engineDatabaseExtension.changeProcessInstanceStartDate(newRunningProcessInstance.getId(), runningProcInstStartDate);

    importAllEngineEntitiesFromScratch();

    // when
    final List<ProcessFilterDto<?>> runningInstanceFilter = ProcessFilterBuilder.filter()
      .runningInstancesOnly()
      .add()
      .buildList();

    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(PROC_INST_DUR_GROUP_BY_VARIABLE)
      .setProcessDefinitionKey(completeProcessInstanceDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(completeProcessInstanceDto.getProcessDefinitionVersion())
      .setVariableName("foo")
      .setVariableType(VariableType.STRING)
      .setFilter(runningInstanceFilter)
      .build();
    ReportResultResponseDto<List<MapResultEntryDto>> resultDto = reportClient.evaluateMapReport(reportData).getResult();

    // then
    final List<MapResultEntryDto> resultData = resultDto.getFirstMeasureData();
    assertThat(resultData).isNotNull();
    assertThat(resultData).hasSize(1);
    assertThat(resultData.get(0).getValue()).isEqualTo(runningProcInstStartDate.until(now, MILLIS));
  }

  @Test
  public void calculateDurationForCompletedProcessInstances() {
    // given
    OffsetDateTime now = OffsetDateTime.now();
    LocalDateUtil.setCurrentTime(now);

    Map<String, Object> variables = new HashMap<>();
    variables.put("foo", "bar");

    // 1 completed proc inst
    ProcessInstanceEngineDto completeProcessInstanceDto = deployAndStartUserTaskProcessWithVariables(variables);
    engineIntegrationExtension.finishAllRunningUserTasks(completeProcessInstanceDto.getId());

    OffsetDateTime completedProcInstStartDate = now.minusDays(2);
    OffsetDateTime completedProcInstEndDate = completedProcInstStartDate.plus(1000, MILLIS);
    engineDatabaseExtension.changeProcessInstanceStartDate(
      completeProcessInstanceDto.getId(),
      completedProcInstStartDate
    );
    engineDatabaseExtension.changeProcessInstanceEndDate(completeProcessInstanceDto.getId(), completedProcInstEndDate);

    // 1 running proc inst
    final ProcessInstanceEngineDto newRunningProcessInstance = engineIntegrationExtension.startProcessInstance(
      completeProcessInstanceDto.getDefinitionId(),
      variables
    );
    OffsetDateTime runningProcInstStartDate = now.minusDays(1);
    engineDatabaseExtension.changeProcessInstanceStartDate(newRunningProcessInstance.getId(), runningProcInstStartDate);

    importAllEngineEntitiesFromScratch();

    // when
    final List<ProcessFilterDto<?>> completedInstanceFilter = ProcessFilterBuilder.filter()
      .completedInstancesOnly()
      .add()
      .buildList();

    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(PROC_INST_DUR_GROUP_BY_VARIABLE)
      .setProcessDefinitionKey(completeProcessInstanceDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(completeProcessInstanceDto.getProcessDefinitionVersion())
      .setVariableName("foo")
      .setVariableType(VariableType.STRING)
      .setFilter(completedInstanceFilter)
      .build();
    ReportResultResponseDto<List<MapResultEntryDto>> resultDto = reportClient.evaluateMapReport(reportData).getResult();

    // then
    final List<MapResultEntryDto> resultData = resultDto.getFirstMeasureData();
    assertThat(resultData).isNotNull();
    assertThat(resultData).hasSize(1);
    assertThat(resultData.get(0).getValue()).isEqualTo(1000.);
  }

  @Test
  public void calculateDurationForRunningAndCompletedProcessInstances() {
    // given
    OffsetDateTime now = OffsetDateTime.now();
    LocalDateUtil.setCurrentTime(now);

    Map<String, Object> variables = new HashMap<>();
    variables.put("foo", "bar");

    // 1 completed proc inst
    ProcessInstanceEngineDto completeProcessInstanceDto = deployAndStartUserTaskProcessWithVariables(variables);
    engineIntegrationExtension.finishAllRunningUserTasks(completeProcessInstanceDto.getId());

    OffsetDateTime completedProcInstStartDate = now.minusDays(2);
    OffsetDateTime completedProcInstEndDate = completedProcInstStartDate.plus(1000, MILLIS);
    engineDatabaseExtension.changeProcessInstanceStartDate(
      completeProcessInstanceDto.getId(),
      completedProcInstStartDate
    );
    engineDatabaseExtension.changeProcessInstanceEndDate(completeProcessInstanceDto.getId(), completedProcInstEndDate);

    // 1 running proc inst
    final ProcessInstanceEngineDto newRunningProcessInstance = engineIntegrationExtension.startProcessInstance(
      completeProcessInstanceDto.getDefinitionId(),
      variables
    );
    OffsetDateTime runningProcInstStartDate = now.minusDays(1);
    engineDatabaseExtension.changeProcessInstanceStartDate(newRunningProcessInstance.getId(), runningProcInstStartDate);

    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(PROC_INST_DUR_GROUP_BY_VARIABLE)
      .setProcessDefinitionKey(completeProcessInstanceDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(completeProcessInstanceDto.getProcessDefinitionVersion())
      .setVariableName("foo")
      .setVariableType(VariableType.STRING)
      .build();
    ReportResultResponseDto<List<MapResultEntryDto>> resultDto = reportClient.evaluateMapReport(reportData).getResult();

    // then
    final List<MapResultEntryDto> resultData = resultDto.getFirstMeasureData();
    assertThat(resultData).isNotNull();
    assertThat(resultData).hasSize(1);
    assertThat(resultData.get(0).getValue())
      .isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(
        1000.,
        (double) runningProcInstStartDate.until(now, MILLIS)
      ));
  }

  @Test
  public void durationFilterWorksForRunningProcessInstances() {
    // given
    OffsetDateTime now = OffsetDateTime.now();
    LocalDateUtil.setCurrentTime(now);

    Map<String, Object> variables = new HashMap<>();
    variables.put("foo", "bar");

    // 1 completed proc inst
    ProcessInstanceEngineDto completeProcessInstanceDto = deployAndStartUserTaskProcessWithVariables(variables);
    engineIntegrationExtension.finishAllRunningUserTasks(completeProcessInstanceDto.getId());

    OffsetDateTime completedProcInstStartDate = now.minusDays(2);
    OffsetDateTime completedProcInstEndDate = completedProcInstStartDate.plus(1000, MILLIS);
    engineDatabaseExtension.changeProcessInstanceStartDate(
      completeProcessInstanceDto.getId(),
      completedProcInstStartDate
    );
    engineDatabaseExtension.changeProcessInstanceEndDate(completeProcessInstanceDto.getId(), completedProcInstEndDate);

    // 1 running proc inst
    final ProcessInstanceEngineDto newRunningProcessInstance = engineIntegrationExtension.startProcessInstance(
      completeProcessInstanceDto.getDefinitionId(),
      variables
    );
    OffsetDateTime runningProcInstStartDate = now.minusDays(1);
    engineDatabaseExtension.changeProcessInstanceStartDate(newRunningProcessInstance.getId(), runningProcInstStartDate);

    importAllEngineEntitiesFromScratch();

    // when
    final List<ProcessFilterDto<?>> durationFilter = ProcessFilterBuilder.filter()
      .duration()
      .operator(GREATER_THAN_EQUALS)
      .unit(DurationUnit.HOURS)
      .value(1L)
      .add()
      .buildList();

    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(PROC_INST_DUR_GROUP_BY_VARIABLE)
      .setProcessDefinitionKey(completeProcessInstanceDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(completeProcessInstanceDto.getProcessDefinitionVersion())
      .setVariableName("foo")
      .setVariableType(VariableType.STRING)
      .setFilter(durationFilter)
      .build();
    ReportResultResponseDto<List<MapResultEntryDto>> resultDto = reportClient.evaluateMapReport(reportData).getResult();

    // then
    final List<MapResultEntryDto> resultData = resultDto.getFirstMeasureData();
    assertThat(resultData).isNotNull();
    assertThat(resultData).hasSize(1);
    assertThat(resultData.get(0).getValue()).isEqualTo(runningProcInstStartDate.until(now, MILLIS));
  }

  @Test
  public void variableTypeIsImportant() {
    // given
    OffsetDateTime startDate = OffsetDateTime.now();
    OffsetDateTime endDate = startDate.plusSeconds(1);
    Map<String, Object> variables = new HashMap<>();
    variables.put("foo", "1");
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess(variables);
    engineDatabaseExtension.changeProcessInstanceStartDate(processInstanceDto.getId(), startDate);
    engineDatabaseExtension.changeProcessInstanceEndDate(processInstanceDto.getId(), endDate);
    variables.put("foo", 1);
    ProcessInstanceEngineDto processInstanceDto2 =
      engineIntegrationExtension.startProcessInstance(processInstanceDto.getDefinitionId(), variables);
    engineDatabaseExtension.changeProcessInstanceStartDate(processInstanceDto2.getId(), startDate);
    engineDatabaseExtension.changeProcessInstanceEndDate(processInstanceDto2.getId(), endDate);
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(PROC_INST_DUR_GROUP_BY_VARIABLE)
      .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
      .setVariableName("foo")
      .setVariableType(VariableType.STRING)
      .build();
    reportData.getConfiguration().setAggregationTypes(new AggregationDto(AggregationType.MAX));
    AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResponse =
      reportClient.evaluateMapReport(reportData);

    // then
    ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey()).isEqualTo(processInstanceDto.getProcessDefinitionKey());
    assertThat(resultReportDataDto.getDefinitionVersions()).contains(processInstanceDto.getProcessDefinitionVersion());

    final List<MapResultEntryDto> resultData = evaluationResponse.getResult().getFirstMeasureData();
    assertThat(resultData).isNotNull();
    assertThat(resultData).hasSize(2);
    assertThat(resultData.get(0).getKey()).isEqualTo("1");
    assertThat(resultData.get(0).getValue()).isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(1000.));
    assertThat(resultData.get(1).getKey()).isEqualTo(MISSING_VARIABLE_KEY);
    assertThat(resultData.get(1).getValue()).isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(1000.));
  }

  @Test
  public void otherVariablesDoNotDistortTheResult() {
    // given
    OffsetDateTime startDate = OffsetDateTime.now();
    OffsetDateTime endDate = startDate.plusSeconds(1);
    Map<String, Object> variables = new HashMap<>();
    variables.put("foo1", "bar1");
    variables.put("foo2", "bar1");
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess(variables);
    engineDatabaseExtension.changeProcessInstanceStartDate(processInstanceDto.getId(), startDate);
    engineDatabaseExtension.changeProcessInstanceEndDate(processInstanceDto.getId(), endDate);
    ProcessInstanceEngineDto processInstanceDto2 =
      engineIntegrationExtension.startProcessInstance(processInstanceDto.getDefinitionId(), variables);
    engineDatabaseExtension.changeProcessInstanceStartDate(processInstanceDto2.getId(), startDate);
    engineDatabaseExtension.changeProcessInstanceEndDate(processInstanceDto2.getId(), endDate);
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(PROC_INST_DUR_GROUP_BY_VARIABLE)
      .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
      .setVariableName("foo1")
      .setVariableType(VariableType.STRING)
      .build();
    AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResponse =
      reportClient.evaluateMapReport(reportData);

    // then
    ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey()).isEqualTo(processInstanceDto.getProcessDefinitionKey());
    assertThat(resultReportDataDto.getDefinitionVersions()).contains(processInstanceDto.getProcessDefinitionVersion());

    final List<MapResultEntryDto> resultData = evaluationResponse.getResult().getFirstMeasureData();
    assertThat(resultData).isNotNull();
    assertThat(resultData).hasSize(1);
    assertThat(resultData.get(0).getKey()).isEqualTo("bar1");
    assertThat(resultData.get(0).getValue()).isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(1000.));
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
    engineDatabaseExtension.changeProcessInstanceStartDate(processInstanceDto.getId(), startDate);
    engineDatabaseExtension.changeProcessInstanceEndDate(processInstanceDto.getId(), endDate);
    importAllEngineEntitiesFromScratch();

    for (Map.Entry<String, Object> entry : variables.entrySet()) {
      // when
      VariableType variableType = varNameToTypeMap.get(entry.getKey());
      ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
        .createReportData()
        .setReportDataType(PROC_INST_DUR_GROUP_BY_VARIABLE)
        .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
        .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
        .setVariableName(entry.getKey())
        .setVariableType(variableType)
        .build();

      ReportResultResponseDto<List<MapResultEntryDto>> resultDto = reportClient.evaluateMapReport(reportData)
        .getResult();

      // then
      assertThat(resultDto.getFirstMeasureData()).isNotNull();

      final List<MapResultEntryDto> resultData = resultDto.getFirstMeasureData();
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
        assertThat(resultData.get(0).getValue()).isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(1000.));
      }
    }
  }

  @Test
  public void missingVariablesAggregationWorksForUndefinedAndNullVariables() {
    // given

    // 1 process instance with 'testVar'
    OffsetDateTime testEndDate = OffsetDateTime.now();
    OffsetDateTime testStartDate = testEndDate.minusSeconds(2);

    final ProcessDefinitionEngineDto definition = engineIntegrationExtension.deployProcessAndGetProcessDefinition(
      getSingleServiceTaskProcess());

    startProcessWithVariablesAndDates(
      definition,
      of("testVar", "withValue"),
      testStartDate,
      testEndDate
    );

    // 4 process instances without 'testVar'
    OffsetDateTime missingTestStartDate = testEndDate.minusDays(1);

    startProcessWithVariablesAndDates(
      definition,
      Collections.singletonMap("testVar", null),
      missingTestStartDate,
      missingTestStartDate.plus(200, MILLIS)
    );

    startProcessWithVariablesAndDates(
      definition,
      Collections.singletonMap("testVar", new EngineVariableValue(null, "String")),
      missingTestStartDate,
      missingTestStartDate.plus(400, MILLIS)
    );

    startProcessWithDates(
      definition,
      missingTestStartDate,
      missingTestStartDate.plus(3000, MILLIS)
    );

    startProcessWithVariablesAndDates(
      definition,
      of("differentStringValue", "test"),
      missingTestStartDate,
      missingTestStartDate.plus(10000, MILLIS)
    );

    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(PROC_INST_DUR_GROUP_BY_VARIABLE)
      .setProcessDefinitionKey(definition.getKey())
      .setProcessDefinitionVersion(definition.getVersionAsString())
      .setVariableName("testVar")
      .setVariableType(VariableType.STRING)
      .build();
    AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResponse =
      reportClient.evaluateMapReport(reportData);

    // then
    final ReportResultResponseDto<List<MapResultEntryDto>> result = evaluationResponse.getResult();
    assertThat(result.getFirstMeasureData()).isNotNull();
    assertThat(result.getFirstMeasureData()).hasSize(2);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), "withValue").get().getValue()).isEqualTo(
      testStartDate.until(testEndDate, MILLIS));
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), "missing").get().getValue())
      .isEqualTo(
        calculateExpectedValueGivenDurationsDefaultAggr(
          (double) missingTestStartDate.until(missingTestStartDate.plus(200, MILLIS), MILLIS),
          (double) missingTestStartDate.until(missingTestStartDate.plus(400, MILLIS), MILLIS),
          (double) missingTestStartDate.until(missingTestStartDate.plus(3000, MILLIS), MILLIS),
          (double) missingTestStartDate.until(missingTestStartDate.plus(10000, MILLIS), MILLIS)
        ));
  }

  @Test
  public void missingVariablesAggregationsForNullVariableOfTypeDouble_sortingByKeyDoesNotFail() {
    // given a process instance with variable of non null value and one instance with variable of null value
    final String varName = "testVar";
    final Double varValue = 1.0;
    OffsetDateTime testEndDate = OffsetDateTime.now();
    OffsetDateTime testStartDate = testEndDate.minusSeconds(2);

    final ProcessDefinitionEngineDto definition = engineIntegrationExtension.deployProcessAndGetProcessDefinition(
      getSingleServiceTaskProcess());

    startProcessWithVariablesAndDates(
      definition,
      of(varName, varValue),
      testStartDate,
      testEndDate
    );

    OffsetDateTime missingTestStartDate = testEndDate.minusDays(1);

    startProcessWithVariablesAndDates(
      definition,
      Collections.singletonMap(varName, new EngineVariableValue(null, "Double")),
      missingTestStartDate,
      missingTestStartDate.plus(400, MILLIS)
    );

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(PROC_INST_DUR_GROUP_BY_VARIABLE)
      .setProcessDefinitionKey(definition.getKey())
      .setProcessDefinitionVersion(definition.getVersionAsString())
      .setVariableName(varName)
      .setVariableType(VariableType.DOUBLE)
      .build();
    final AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResponse =
      reportClient.evaluateMapReport(reportData);

    // then
    final ReportResultResponseDto<List<MapResultEntryDto>> result = evaluationResponse.getResult();
    assertThat(result.getFirstMeasureData()).isNotNull();
    assertThat(result.getFirstMeasureData()).hasSize(2);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), "1.00").get().getValue())
      .isEqualTo(testStartDate.until(testEndDate, MILLIS));
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), "missing").get().getValue()).isEqualTo(400.);
  }

  @Test
  public void filterInReportWorks() {
    // given
    OffsetDateTime startDate = OffsetDateTime.now();
    OffsetDateTime endDate = startDate.plusSeconds(1);
    Map<String, Object> variables = new HashMap<>();
    variables.put("foo", "bar");
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleServiceTaskProcess(variables);
    engineDatabaseExtension.changeProcessInstanceStartDate(processInstance.getId(), startDate);
    engineDatabaseExtension.changeProcessInstanceEndDate(processInstance.getId(), endDate);
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(PROC_INST_DUR_GROUP_BY_VARIABLE)
      .setProcessDefinitionKey(processInstance.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstance.getProcessDefinitionVersion())
      .setVariableName("foo")
      .setVariableType(VariableType.STRING)
      .build();

    reportData.setFilter(ProcessFilterBuilder.filter()
                           .fixedInstanceStartDate()
                           .start(null)
                           .end(startDate.minusSeconds(1L))
                           .add()
                           .buildList());
    ReportResultResponseDto<List<MapResultEntryDto>> resultDto = reportClient.evaluateMapReport(reportData).getResult();

    // then
    List<MapResultEntryDto> resultData = resultDto.getFirstMeasureData();
    assertThat(resultData).isNotNull();
    assertThat(resultData).isEmpty();

    // when
    reportData.setFilter(ProcessFilterBuilder.filter()
                           .fixedInstanceStartDate()
                           .start(startDate)
                           .end(null)
                           .add()
                           .buildList());
    resultDto = reportClient.evaluateMapReport(reportData).getResult();

    // then
    resultData = resultDto.getFirstMeasureData();
    assertThat(resultData).hasSize(1);
    assertThat(resultData.get(0).getKey()).isEqualTo("bar");
    assertThat(resultData.get(0).getValue()).isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(1000.));
  }

  @Test
  public void optimizeExceptionOnViewEntityIsNull() {
    // given
    ProcessReportDataDto dataDto = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(PROC_INST_DUR_GROUP_BY_VARIABLE)
      .setProcessDefinitionKey("123")
      .setProcessDefinitionVersion("1")
      .setVariableName("foo")
      .setVariableType(VariableType.STRING)
      .build();

    dataDto.getView().setEntity(null);

    // when
    Response response = reportClient.evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void optimizeExceptionOnViewPropertyIsNull() {
    // given
    ProcessReportDataDto dataDto = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(PROC_INST_DUR_GROUP_BY_VARIABLE)
      .setProcessDefinitionKey("123")
      .setProcessDefinitionVersion("1")
      .setVariableName("foo")
      .setVariableType(VariableType.STRING)
      .build();

    dataDto.getView().setProperties((ViewProperty) null);

    // when
    Response response = reportClient.evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void optimizeExceptionOnGroupByTypeIsNull() {
    // given
    ProcessReportDataDto dataDto = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(PROC_INST_DUR_GROUP_BY_VARIABLE)
      .setProcessDefinitionKey("123")
      .setProcessDefinitionVersion("1")
      .setVariableName("foo")
      .setVariableType(VariableType.STRING)
      .build();

    dataDto.getGroupBy().setType(null);

    // when
    Response response = reportClient.evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void optimizeExceptionOnGroupByValueNameIsNull() {
    // given
    ProcessReportDataDto dataDto = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(PROC_INST_DUR_GROUP_BY_VARIABLE)
      .setProcessDefinitionKey("123")
      .setProcessDefinitionVersion("1")
      .setVariableName("foo")
      .setVariableType(VariableType.STRING)
      .build();

    VariableGroupByDto groupByDto = (VariableGroupByDto) dataDto.getGroupBy();
    groupByDto.getValue().setName(null);

    // when
    Response response = reportClient.evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
  }

  @Test
  public void optimizeExceptionOnGroupByValueTypeIsNull() {
    // given
    ProcessReportDataDto dataDto = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(PROC_INST_DUR_GROUP_BY_VARIABLE)
      .setProcessDefinitionKey("123")
      .setProcessDefinitionVersion("1")
      .setVariableName("foo")
      .setVariableType(VariableType.STRING)
      .build();

    VariableGroupByDto groupByDto = (VariableGroupByDto) dataDto.getGroupBy();
    groupByDto.getValue().setType(null);

    // when
    Response response = reportClient.evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
  }

  @SneakyThrows
  @ParameterizedTest
  @MethodSource("staticAggregateByDateUnits")
  public void groupByDateVariableWorksForAllStaticUnits(final AggregateByDateUnit unit) {
    // given
    final ChronoUnit chronoUnit = mapToChronoUnit(unit);
    final int numberOfInstances = 3;
    final String dateVarName = "dateVar";
    final ProcessDefinitionEngineDto def = engineIntegrationExtension.deployProcessAndGetProcessDefinition(
      getSingleServiceTaskProcess());
    OffsetDateTime variableValue = OffsetDateTime.parse("2020-06-15T00:00:00+02:00");
    Map<String, Object> variables = new HashMap<>();

    for (int i = 0; i < numberOfInstances; i++) {
      variableValue = variableValue.plus(1, chronoUnit);
      variables.put(dateVarName, variableValue);
      startProcessWithVariablesAndDates(def, variables, variableValue, variableValue.plusSeconds(1));
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
      .build();
    reportData.getConfiguration().setGroupByDateVariableUnit(unit);
    List<MapResultEntryDto> resultData = reportClient.evaluateMapReport(reportData).getResult().getFirstMeasureData();

    // then
    // there is one bucket per instance since the date variables are each one bucket span apart
    assertThat(resultData).isNotNull();
    assertThat(resultData).hasSize(numberOfInstances);
    // buckets are in ascending order, so the first bucket is based on the date variable of the first instance
    variableValue = variableValue.minus(numberOfInstances - 1, chronoUnit);
    final DateTimeFormatter formatter = embeddedOptimizeExtension.getDateTimeFormatter();
    for (int i = 0; i < numberOfInstances; i++) {
      final String expectedBucketKey = formatter.format(
        truncateToStartOfUnit(
          variableValue.plus(chronoUnit.getDuration().multipliedBy(i)),
          chronoUnit
        ));
      assertThat(resultData.get(i).getValue())
        .isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(1000.));
      assertThat(resultData.get(i).getKey()).isEqualTo(expectedBucketKey);
    }
  }

  @SneakyThrows
  @Test
  public void groupByDateVariableWorksForAutomaticInterval() {
    // given
    final int numberOfInstances = 3;
    final String dateVarName = "dateVar";
    final ProcessDefinitionEngineDto def = engineIntegrationExtension.deployProcessAndGetProcessDefinition(
      getSingleServiceTaskProcess());
    final OffsetDateTime dateVariableValue = OffsetDateTime.now();
    Map<String, Object> variables = new HashMap<>();

    for (int i = 0; i < numberOfInstances; i++) {
      variables.put(dateVarName, dateVariableValue.plusMinutes(i));
      startProcessWithVariablesAndDates(def, variables, dateVariableValue, dateVariableValue.plusSeconds(1));
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
      .build();
    final ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData)
      .getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(numberOfInstances);
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(numberOfInstances);
    List<MapResultEntryDto> resultData = result.getFirstMeasureData();
    assertThat(resultData).isNotNull();
    assertThat(resultData).hasSize(NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION);
    // bucket span covers the values of all date variables (buckets are in descending order, so the first bucket is
    // based on the date variable of the last instance)
    DateTimeFormatter formatter = embeddedOptimizeExtension.getDateTimeFormatter();
    final OffsetDateTime startOfFirstBucket = OffsetDateTime.from(formatter.parse(resultData.get(0).getKey()));
    final OffsetDateTime startOfLastBucket = OffsetDateTime.from(
      formatter.parse(resultData.get(NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION - 1).getKey()));
    final OffsetDateTime firstTruncatedDateVariableValue = dateVariableValue.plusMinutes(numberOfInstances)
      .truncatedTo(MILLIS);
    final OffsetDateTime lastTruncatedDateVariableValue = dateVariableValue.truncatedTo(ChronoUnit.MILLIS);

    assertThat(startOfFirstBucket).isBeforeOrEqualTo(firstTruncatedDateVariableValue);
    assertThat(startOfLastBucket).isAfterOrEqualTo(lastTruncatedDateVariableValue);
    assertThat(result.getFirstMeasureData())
      .extracting(MapResultEntryDto::getValue)
      .filteredOn(Objects::nonNull)
      .containsExactly(1000., 1000., 1000.); // each instance with duration 1000. falls into one bucket
  }

  @SneakyThrows
  @Test
  public void groupByDateVariableForAutomaticInterval_MissingInstancesReturnsEmptyResult() {
    // given
    final String dateVarName = "dateVar";
    final ProcessDefinitionEngineDto def = engineIntegrationExtension.deployProcessAndGetProcessDefinition(
      getSingleServiceTaskProcess());

    // when
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(PROC_INST_DUR_GROUP_BY_VARIABLE)
      .setProcessDefinitionKey(def.getKey())
      .setProcessDefinitionVersion(def.getVersionAsString())
      .setVariableName(dateVarName)
      .setVariableType(VariableType.DATE)
      .build();
    final ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData)
      .getResult();

    // then
    List<MapResultEntryDto> resultData = result.getFirstMeasureData();
    assertThat(resultData).isNotNull();
    assertThat(resultData).isEmpty();
  }

  private void startProcessInstanceShiftedBySeconds(Map<String, Object> variables,
                                                    String processDefinitionId,
                                                    int secondsToShift) {
    ProcessInstanceEngineDto processInstanceDto2;
    OffsetDateTime startDate;
    OffsetDateTime endDate;
    processInstanceDto2 = engineIntegrationExtension.startProcessInstance(processDefinitionId, variables);
    startDate = OffsetDateTime.now();
    endDate = startDate.plusSeconds(secondsToShift);
    engineDatabaseExtension.changeProcessInstanceStartDate(processInstanceDto2.getId(), startDate);
    engineDatabaseExtension.changeProcessInstanceEndDate(processInstanceDto2.getId(), endDate);
  }

  private ProcessInstanceEngineDto startProcessWithVariablesAndDates(final ProcessDefinitionEngineDto definition,
                                                                     final Map<String, Object> variables,
                                                                     final OffsetDateTime startDate,
                                                                     final OffsetDateTime endDate) {
    ProcessInstanceEngineDto processInstance = engineIntegrationExtension.startProcessInstance(
      definition.getId(),
      variables
    );
    engineDatabaseExtension.changeProcessInstanceStartDate(processInstance.getId(), startDate);
    engineDatabaseExtension.changeProcessInstanceEndDate(processInstance.getId(), endDate);
    return processInstance;
  }

  private void startProcessWithDates(final ProcessDefinitionEngineDto definition,
                                     final OffsetDateTime startDate,
                                     final OffsetDateTime endDate) {
    startProcessWithVariablesAndDates(definition, new HashMap<>(), startDate, endDate);
  }

  private ProcessInstanceEngineDto deployAndStartUserTaskProcessWithVariables(final Map<String, Object> variables) {
    return engineIntegrationExtension.deployAndStartProcessWithVariables(
      BpmnModels.getSingleUserTaskDiagram(),
      variables
    );
  }

}
