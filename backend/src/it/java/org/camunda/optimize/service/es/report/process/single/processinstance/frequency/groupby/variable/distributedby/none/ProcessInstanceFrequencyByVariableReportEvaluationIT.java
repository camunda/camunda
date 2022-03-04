/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process.single.processinstance.frequency.groupby.variable.distributedby.none;

import com.google.common.collect.ImmutableMap;
import lombok.SneakyThrows;
import org.apache.lucene.search.join.ScoreMode;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.IdResponseDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportItemDto;
import org.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
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
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.rest.optimize.dto.VariableDto;
import org.camunda.optimize.service.es.report.process.AbstractProcessDefinitionIT;
import org.camunda.optimize.service.es.report.util.MapResultUtil;
import org.camunda.optimize.test.it.extension.EngineVariableValue;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.aggregations.bucket.nested.Nested;
import org.elasticsearch.search.aggregations.metrics.ValueCount;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.io.IOException;
import java.sql.SQLException;
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
import java.util.stream.Collectors;

import static com.google.common.collect.ImmutableMap.of;
import static com.google.common.collect.Lists.newArrayList;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.dto.optimize.ReportConstants.MISSING_VARIABLE_KEY;
import static org.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnitMapper.mapToChronoUnit;
import static org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto.SORT_BY_KEY;
import static org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto.SORT_BY_VALUE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.VARIABLES;
import static org.camunda.optimize.service.util.ProcessVariableHelper.getNestedVariableIdField;
import static org.camunda.optimize.service.util.ProcessVariableHelper.getNestedVariableNameField;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.FREQUENCY_AGGREGATION;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_MULTI_ALIAS;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.nestedQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.count;
import static org.elasticsearch.search.aggregations.AggregationBuilders.nested;

public class ProcessInstanceFrequencyByVariableReportEvaluationIT extends AbstractProcessDefinitionIT {

  @Test
  public void simpleReportEvaluation() {
    // given
    Map<String, Object> variables = new HashMap<>();
    variables.put("foo", "bar");
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess(variables);
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(
      processInstanceDto.getProcessDefinitionKey(),
      processInstanceDto.getProcessDefinitionVersion(),
      "foo",
      VariableType.STRING
    );
    AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResponse =
      reportClient.evaluateMapReport(reportData);

    // then
    ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey()).isEqualTo(processInstanceDto.getProcessDefinitionKey());
    assertThat(resultReportDataDto.getDefinitionVersions()).contains(processInstanceDto.getProcessDefinitionVersion());
    assertThat(resultReportDataDto.getView()).isNotNull();
    assertThat(resultReportDataDto.getView().getEntity()).isEqualTo(ProcessViewEntity.PROCESS_INSTANCE);
    assertThat(resultReportDataDto.getView().getFirstProperty()).isEqualTo(ViewProperty.FREQUENCY);
    assertThat(resultReportDataDto.getGroupBy().getType()).isEqualTo(ProcessGroupByType.VARIABLE);
    VariableGroupByDto variableGroupByDto = (VariableGroupByDto) resultReportDataDto.getGroupBy();
    assertThat(variableGroupByDto.getValue().getName()).isEqualTo("foo");
    assertThat(variableGroupByDto.getValue().getType()).isEqualTo(VariableType.STRING);

    final ReportResultResponseDto<List<MapResultEntryDto>> result = evaluationResponse.getResult();
    assertThat(result.getInstanceCount()).isEqualTo(1L);
    assertThat(result.getFirstMeasureData()).isNotNull();
    assertThat(result.getFirstMeasureData()).hasSize(1);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), "bar").get().getValue()).isEqualTo(1.);
  }

  @Test
  public void simpleReportEvaluationById() {
    // given
    Map<String, Object> variables = new HashMap<>();
    variables.put("foo", "bar");
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleServiceTaskProcess(variables);
    importAllEngineEntitiesFromScratch();
    String reportId = createAndStoreDefaultReportDefinition(
      processInstance.getProcessDefinitionKey(),
      processInstance.getProcessDefinitionVersion(),
      "foo",
      VariableType.STRING
    );

    // when
    AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResponse =
      reportClient.evaluateMapReportById(reportId);

    // then
    ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey()).isEqualTo(processInstance.getProcessDefinitionKey());
    assertThat(resultReportDataDto.getDefinitionVersions()).contains(processInstance.getProcessDefinitionVersion());
    assertThat(resultReportDataDto.getView()).isNotNull();
    assertThat(resultReportDataDto.getView().getEntity()).isEqualTo(ProcessViewEntity.PROCESS_INSTANCE);
    assertThat(resultReportDataDto.getView().getFirstProperty()).isEqualTo(ViewProperty.FREQUENCY);
    assertThat(resultReportDataDto.getGroupBy().getType()).isEqualTo(ProcessGroupByType.VARIABLE);
    VariableGroupByDto variableGroupByDto = (VariableGroupByDto) resultReportDataDto.getGroupBy();
    assertThat(variableGroupByDto.getValue().getName()).isEqualTo("foo");
    assertThat(variableGroupByDto.getValue().getType()).isEqualTo(VariableType.STRING);

    final ReportResultResponseDto<List<MapResultEntryDto>> result = evaluationResponse.getResult();
    assertThat(result.getInstanceCount()).isEqualTo(1L);
    assertThat(result.getFirstMeasureData()).isNotNull();
    assertThat(result.getFirstMeasureData()).hasSize(1);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), "bar").get().getValue()).isEqualTo(1.);
  }

  @Test
  public void otherProcessDefinitionsDoNotAffectResult() {
    // given
    Map<String, Object> variables = new HashMap<>();
    variables.put("foo", "bar");
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess(variables);
    variables.put("foo", "bar2");
    deployAndStartSimpleServiceTaskProcess(variables);
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(
      processInstanceDto.getProcessDefinitionKey(),
      processInstanceDto.getProcessDefinitionVersion(),
      "foo",
      VariableType.STRING
    );
    AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResponse =
      reportClient.evaluateMapReport(reportData);

    // then
    final ReportResultResponseDto<List<MapResultEntryDto>> result = evaluationResponse.getResult();
    assertThat(result.getFirstMeasureData()).isNotNull();
    assertThat(result.getFirstMeasureData()).hasSize(1);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), "bar").get().getValue()).isEqualTo(1.);
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
    ProcessReportDataDto reportData =
      createReport(processKey, ALL_VERSIONS, DEFAULT_VARIABLE_NAME, DEFAULT_VARIABLE_TYPE);
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
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess(variables);
    variables.put("foo", "bar2");
    engineIntegrationExtension.startProcessInstance(processInstanceDto.getDefinitionId(), variables);
    engineIntegrationExtension.startProcessInstance(processInstanceDto.getDefinitionId(), variables);
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(
      processInstanceDto.getProcessDefinitionKey(),
      processInstanceDto.getProcessDefinitionVersion(),
      "foo",
      VariableType.STRING
    );
    AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResponse =
      reportClient.evaluateMapReport(reportData);

    // then
    final ReportResultResponseDto<List<MapResultEntryDto>> resultDto = evaluationResponse.getResult();
    assertThat(resultDto.getFirstMeasureData()).isNotNull();
    assertThat(resultDto.getFirstMeasureData()).hasSize(2);
    assertThat(MapResultUtil.getEntryForKey(resultDto.getFirstMeasureData(), "bar1").get().getValue()).isEqualTo(1.);
    assertThat(MapResultUtil.getEntryForKey(resultDto.getFirstMeasureData(), "bar2").get().getValue()).isEqualTo(2.);
  }

  @Test
  public void multipleVariableValuesInOneInstance() {
    // given
    final VariableDto listVar = variablesClient.createListJsonObjectVariableDto(List.of("value1", "value2"));
    Map<String, Object> variables = new HashMap<>();
    variables.put("listVar", listVar);
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess(variables);
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(
      processInstanceDto.getProcessDefinitionKey(),
      processInstanceDto.getProcessDefinitionVersion(),
      "listVar",
      VariableType.STRING
    );
    AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResponse =
      reportClient.evaluateMapReport(reportData);

    // then
    final ReportResultResponseDto<List<MapResultEntryDto>> resultDto = evaluationResponse.getResult();
    assertThat(resultDto.getFirstMeasureData()).isNotNull();
    assertThat(resultDto.getFirstMeasureData()).hasSize(2);
    assertThat(MapResultUtil.getEntryForKey(resultDto.getFirstMeasureData(), "value1")).isPresent()
      .map(MapResultEntryDto::getValue)
      .contains(1.);
    assertThat(MapResultUtil.getEntryForKey(resultDto.getFirstMeasureData(), "value2"))
      .isPresent()
      .map(MapResultEntryDto::getValue)
      .contains(1.);
  }

  @Test
  public void numberVariable_customBuckets() {
    // given
    final String varName = "doubleVar";
    Map<String, Object> variables = new HashMap<>();
    variables.put(varName, 100.0);
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess(variables);

    variables.put(varName, 200.0);
    engineIntegrationExtension.startProcessInstance(processInstanceDto.getDefinitionId(), variables);

    variables.put(varName, 300.0);
    engineIntegrationExtension.startProcessInstance(processInstanceDto.getDefinitionId(), variables);

    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(
      processInstanceDto.getProcessDefinitionKey(),
      processInstanceDto.getProcessDefinitionVersion(),
      varName,
      VariableType.DOUBLE,
      10.0,
      100.0
    );
    AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResponse =
      reportClient.evaluateMapReport(reportData);

    // then
    final List<MapResultEntryDto> resultData = evaluationResponse.getResult().getFirstMeasureData();
    assertThat(resultData).isNotNull();
    assertThat(resultData).hasSize(3);
    assertThat(resultData.stream()
                 .map(MapResultEntryDto::getKey)
                 .collect(toList()))
      .containsExactly("10.00", "110.00", "210.00");
    assertThat(resultData.get(0).getValue()).isEqualTo(1L);
    assertThat(resultData.get(1).getValue()).isEqualTo(1L);
    assertThat(resultData.get(2).getValue()).isEqualTo(1L);
  }

  @SneakyThrows
  @Test
  public void combinedNumberVariableReport_distinctRanges() {
    // given
    final String varName = "doubleVar";
    Map<String, Object> variables = new HashMap<>();

    variables.put(varName, 10.0);
    ProcessInstanceEngineDto processInstanceDto1 = deployAndStartSimpleServiceTaskProcess(variables);
    variables.put(varName, 20.0);
    engineIntegrationExtension.startProcessInstance(processInstanceDto1.getDefinitionId(), variables);

    variables.put(varName, 50.0);
    ProcessInstanceEngineDto processInstanceDto2 = deployAndStartSimpleServiceTaskProcess(variables);
    variables.put(varName, 100.0);
    engineIntegrationExtension.startProcessInstance(processInstanceDto2.getDefinitionId(), variables);

    importAllEngineEntitiesFromScratch();

    ProcessReportDataDto reportData1 = createReport(
      processInstanceDto1.getProcessDefinitionKey(),
      processInstanceDto1.getProcessDefinitionVersion(),
      varName,
      VariableType.DOUBLE,
      10.0,
      10.0
    );

    ProcessReportDataDto reportData2 = createReport(
      processInstanceDto2.getProcessDefinitionKey(),
      processInstanceDto2.getProcessDefinitionVersion(),
      varName,
      VariableType.DOUBLE,
      5.0,
      10.0
    );

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
    final String varName = "doubleVar";
    Map<String, Object> variables = new HashMap<>();

    variables.put(varName, 10.0);
    ProcessInstanceEngineDto processInstanceDto1 = deployAndStartSimpleServiceTaskProcess(variables);
    variables.put(varName, 20.0);
    engineIntegrationExtension.startProcessInstance(processInstanceDto1.getDefinitionId(), variables);

    variables.put(varName, 15.0);
    ProcessInstanceEngineDto processInstanceDto2 = deployAndStartSimpleServiceTaskProcess(variables);
    variables.put(varName, 25.0);
    engineIntegrationExtension.startProcessInstance(processInstanceDto2.getDefinitionId(), variables);

    importAllEngineEntitiesFromScratch();

    ProcessReportDataDto reportData1 = createReport(
      processInstanceDto1.getProcessDefinitionKey(),
      processInstanceDto1.getProcessDefinitionVersion(),
      varName,
      VariableType.DOUBLE,
      10.0,
      5.0
    );

    ProcessReportDataDto reportData2 = createReport(
      processInstanceDto2.getProcessDefinitionKey(),
      processInstanceDto2.getProcessDefinitionVersion(),
      varName,
      VariableType.DOUBLE,
      5.0,
      5.0
    );

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
    final String varName = "doubleVar";
    Map<String, Object> variables = new HashMap<>();

    variables.put(varName, 10.0);
    ProcessInstanceEngineDto processInstanceDto1 = deployAndStartSimpleServiceTaskProcess(variables);
    variables.put(varName, 30.0);
    engineIntegrationExtension.startProcessInstance(processInstanceDto1.getDefinitionId(), variables);

    variables.put(varName, 15.0);
    ProcessInstanceEngineDto processInstanceDto2 = deployAndStartSimpleServiceTaskProcess(variables);
    variables.put(varName, 25.0);
    engineIntegrationExtension.startProcessInstance(processInstanceDto2.getDefinitionId(), variables);

    importAllEngineEntitiesFromScratch();

    ProcessReportDataDto reportData1 = createReport(
      processInstanceDto1.getProcessDefinitionKey(),
      processInstanceDto1.getProcessDefinitionVersion(),
      varName,
      VariableType.DOUBLE,
      10.0,
      5.0
    );

    ProcessReportDataDto reportData2 = createReport(
      processInstanceDto2.getProcessDefinitionKey(),
      processInstanceDto2.getProcessDefinitionVersion(),
      varName,
      VariableType.DOUBLE,
      5.0,
      5.0
    );

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
  public void numberVariable_invalidBaseline_returnsEmptyResult() {
    // given
    final String varName = "doubleVar";
    Map<String, Object> variables = new HashMap<>();
    variables.put(varName, 10.0);
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess(variables);

    variables.put(varName, 20.0);
    engineIntegrationExtension.startProcessInstance(processInstanceDto.getDefinitionId(), variables);

    importAllEngineEntitiesFromScratch();

    // when the baseline is larger than the max. variable value
    ProcessReportDataDto reportData = createReport(
      processInstanceDto.getProcessDefinitionKey(),
      processInstanceDto.getProcessDefinitionVersion(),
      varName,
      VariableType.DOUBLE,
      30.0,
      5.0
    );
    AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResponse =
      reportClient.evaluateMapReport(reportData);

    // then the report returns an empty result
    final List<MapResultEntryDto> resultData = evaluationResponse.getResult().getFirstMeasureData();
    assertThat(resultData).isNotNull();
    assertThat(resultData).isEmpty();
  }

  @Test
  public void numberVariable_negativeValues_defaultBaselineWorks() {
    // given
    final String varName = "intVar";
    Map<String, Object> variables = new HashMap<>();
    variables.put(varName, -1);
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess(variables);

    variables.put(varName, -5);
    engineIntegrationExtension.startProcessInstance(processInstanceDto.getDefinitionId(), variables);

    importAllEngineEntitiesFromScratch();

    // when there is no baseline set
    ProcessReportDataDto reportData = createReport(
      processInstanceDto.getProcessDefinitionKey(),
      processInstanceDto.getProcessDefinitionVersion(),
      varName,
      VariableType.INTEGER
    );
    AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResponse =
      reportClient.evaluateMapReport(reportData);

    // then the result includes all instances
    final List<MapResultEntryDto> resultData = evaluationResponse.getResult().getFirstMeasureData();
    assertThat(resultData).isNotNull();
    assertThat(resultData.stream().mapToDouble(MapResultEntryDto::getValue).sum()).isEqualTo(2.0);
  }

  @Test
  public void shortVariable_valuesSmallerThanBaseline() {
    // given
    final String varName = "shortVar";
    Map<String, Object> variables = new HashMap<>();
    variables.put(varName, (short) -10);
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess(variables);

    variables.put(varName, (short) 8);
    engineIntegrationExtension.startProcessInstance(processInstanceDto.getDefinitionId(), variables);

    variables.put(varName, (short) 20);
    engineIntegrationExtension.startProcessInstance(processInstanceDto.getDefinitionId(), variables);

    importAllEngineEntitiesFromScratch();

    // when there is a baseline set
    ProcessReportDataDto reportData = createReport(
      processInstanceDto.getProcessDefinitionKey(),
      processInstanceDto.getProcessDefinitionVersion(),
      varName,
      VariableType.SHORT,
      -1.0,
      10.0
    );
    AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResponse =
      reportClient.evaluateMapReport(reportData);

    // then buckets start from the baseline and values below it are not included
    final List<MapResultEntryDto> resultData = evaluationResponse.getResult().getFirstMeasureData();
    assertThat(resultData).isNotNull();
    assertThat(resultData.get(0).getKey()).isEqualTo("-1");
    assertThat(resultData.get(0).getValue()).isEqualTo(1.0);
    assertThat(resultData.get(1).getKey()).isEqualTo("9");
    assertThat(resultData.get(1).getValue()).isEqualTo(0.0);
    assertThat(resultData.get(2).getKey()).isEqualTo("19");
    assertThat(resultData.get(2).getValue()).isEqualTo(1.0);
  }

  @Test
  public void intVariable_valuesSmallerThanBaseline() {
    // given
    final String varName = "intVar";
    Map<String, Object> variables = new HashMap<>();
    variables.put(varName, -10);
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess(variables);

    variables.put(varName, 8);
    engineIntegrationExtension.startProcessInstance(processInstanceDto.getDefinitionId(), variables);

    variables.put(varName, 20);
    engineIntegrationExtension.startProcessInstance(processInstanceDto.getDefinitionId(), variables);

    importAllEngineEntitiesFromScratch();

    // when there is a baseline set
    ProcessReportDataDto reportData = createReport(
      processInstanceDto.getProcessDefinitionKey(),
      processInstanceDto.getProcessDefinitionVersion(),
      varName,
      VariableType.INTEGER,
      -1.0,
      10.0
    );
    AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResponse =
      reportClient.evaluateMapReport(reportData);

    // then buckets start from the baseline and values below it are not included
    final List<MapResultEntryDto> resultData = evaluationResponse.getResult().getFirstMeasureData();
    assertThat(resultData).isNotNull();
    assertThat(resultData.get(0).getKey()).isEqualTo("-1");
    assertThat(resultData.get(0).getValue()).isEqualTo(1.0);
    assertThat(resultData.get(1).getKey()).isEqualTo("9");
    assertThat(resultData.get(1).getValue()).isEqualTo(0.0);
    assertThat(resultData.get(2).getKey()).isEqualTo("19");
    assertThat(resultData.get(2).getValue()).isEqualTo(1.0);
  }

  @Test
  public void doubleVariable_valuesSmallerThanBaseline() {
    // given
    final String varName = "doubleVar";
    Map<String, Object> variables = new HashMap<>();
    variables.put(varName, -10.0);
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess(variables);

    variables.put(varName, 8.0);
    engineIntegrationExtension.startProcessInstance(processInstanceDto.getDefinitionId(), variables);

    variables.put(varName, 20.0);
    engineIntegrationExtension.startProcessInstance(processInstanceDto.getDefinitionId(), variables);

    importAllEngineEntitiesFromScratch();

    // when there is a baseline set
    ProcessReportDataDto reportData = createReport(
      processInstanceDto.getProcessDefinitionKey(),
      processInstanceDto.getProcessDefinitionVersion(),
      varName,
      VariableType.DOUBLE,
      -1.0,
      10.0
    );
    AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResponse =
      reportClient.evaluateMapReport(reportData);

    // then buckets start from the baseline and values below it are not included
    final List<MapResultEntryDto> resultData = evaluationResponse.getResult().getFirstMeasureData();
    assertThat(resultData).isNotNull();
    assertThat(resultData.get(0).getKey()).isEqualTo("-1.00");
    assertThat(resultData.get(0).getValue()).isEqualTo(1.0);
    assertThat(resultData.get(1).getKey()).isEqualTo("9.00");
    assertThat(resultData.get(1).getValue()).isEqualTo(0.0);
    assertThat(resultData.get(2).getKey()).isEqualTo("19.00");
    assertThat(resultData.get(2).getValue()).isEqualTo(1.0);
  }

  @Test
  public void longVariable_valuesSmallerThanBaseline() {
    // given
    final String varName = "longVar";
    Map<String, Object> variables = new HashMap<>();
    variables.put(varName, (long) -10);
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess(variables);

    variables.put(varName, (long) 8);
    engineIntegrationExtension.startProcessInstance(processInstanceDto.getDefinitionId(), variables);

    variables.put(varName, (long) 20);
    engineIntegrationExtension.startProcessInstance(processInstanceDto.getDefinitionId(), variables);

    importAllEngineEntitiesFromScratch();

    // when there is a baseline set
    ProcessReportDataDto reportData = createReport(
      processInstanceDto.getProcessDefinitionKey(),
      processInstanceDto.getProcessDefinitionVersion(),
      varName,
      VariableType.LONG,
      -1.0,
      10.0
    );
    AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResponse =
      reportClient.evaluateMapReport(reportData);

    // then buckets start from the baseline and values below it are not included
    final List<MapResultEntryDto> resultData = evaluationResponse.getResult().getFirstMeasureData();
    assertThat(resultData).isNotNull();
    assertThat(resultData.get(0).getKey()).isEqualTo("-1");
    assertThat(resultData.get(0).getValue()).isEqualTo(1.0);
    assertThat(resultData.get(1).getKey()).isEqualTo("9");
    assertThat(resultData.get(1).getValue()).isEqualTo(0.0);
    assertThat(resultData.get(2).getKey()).isEqualTo("19");
    assertThat(resultData.get(2).getValue()).isEqualTo(1.0);
  }

  @Test
  public void numberVariable_negativeValues_negativeBaseline() {
    // given
    final String varName = "intVar";
    Map<String, Object> variables = new HashMap<>();
    variables.put(varName, -1);
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess(variables);

    variables.put(varName, -5);
    engineIntegrationExtension.startProcessInstance(processInstanceDto.getDefinitionId(), variables);

    importAllEngineEntitiesFromScratch();

    // when there is no baseline set
    ProcessReportDataDto reportData = createReport(
      processInstanceDto.getProcessDefinitionKey(),
      processInstanceDto.getProcessDefinitionVersion(),
      varName,
      VariableType.INTEGER,
      -10.0,
      null
    );
    AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResponse =
      reportClient.evaluateMapReport(reportData);

    // then the baseline is correct and the result includes all instances
    final List<MapResultEntryDto> resultData = evaluationResponse.getResult().getFirstMeasureData();
    assertThat(resultData).isNotNull();
    assertThat(resultData.get(0).getKey()).isEqualTo("-10");
    assertThat(resultData.stream()
                 .map(MapResultEntryDto::getValue)
                 .filter(value -> value != 0.0)
                 .collect(toList()))
      .containsExactly(1.0, 1.0);
  }

  @Test
  public void doubleVariable_bucketKeysHaveTwoDecimalPlaces() {
    // given
    final String varName = "doubleVar";
    Map<String, Object> variables = new HashMap<>();
    variables.put(varName, 1.0);
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess(variables);

    variables.put(varName, 5.0);
    engineIntegrationExtension.startProcessInstance(processInstanceDto.getDefinitionId(), variables);

    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(
      processInstanceDto.getProcessDefinitionKey(),
      processInstanceDto.getProcessDefinitionVersion(),
      varName,
      VariableType.DOUBLE
    );
    AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResponse =
      reportClient.evaluateMapReport(reportData);

    // then
    final List<MapResultEntryDto> resultData = evaluationResponse.getResult().getFirstMeasureData();
    assertThat(resultData).isNotNull();
    assertThat(resultData)
      .extracting(MapResultEntryDto::getKey)
      .allMatch(key -> key.length() - key.indexOf(".") - 1 == 2); // key should have two chars after the decimal
  }

  @Test
  public void numberVariable_largeValues_notTooManyAutomaticBuckets() {
    // given
    final String varName = "longVar";
    Map<String, Object> variables = new HashMap<>();
    variables.put(varName, 9_100_000_000_000_000_000L);
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess(variables);

    variables.put(varName, -920_000_000_000_000_000L);
    engineIntegrationExtension.startProcessInstance(processInstanceDto.getDefinitionId(), variables);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(
      processInstanceDto.getProcessDefinitionKey(),
      processInstanceDto.getProcessDefinitionVersion(),
      varName,
      VariableType.LONG
    );
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

  @Test
  public void testCustomOrderOnResultKeyIsApplied() {
    // given
    Map<String, Object> variables = new HashMap<>();
    variables.put("foo", "bar1");
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess(variables);
    variables.put("foo", "bar2");
    engineIntegrationExtension.startProcessInstance(processInstanceDto.getDefinitionId(), variables);
    engineIntegrationExtension.startProcessInstance(processInstanceDto.getDefinitionId(), variables);
    variables.put("foo", "bar3");
    engineIntegrationExtension.startProcessInstance(processInstanceDto.getDefinitionId(), variables);
    engineIntegrationExtension.startProcessInstance(processInstanceDto.getDefinitionId(), variables);
    engineIntegrationExtension.startProcessInstance(processInstanceDto.getDefinitionId(), variables);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(
      processInstanceDto.getProcessDefinitionKey(),
      processInstanceDto.getProcessDefinitionVersion(),
      "foo",
      VariableType.STRING
    );
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
  public void testCustomOrderOnResultValueIsApplied() {
    // given
    Map<String, Object> variables = new HashMap<>();
    variables.put("foo", "bar1");
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess(variables);
    variables.put("foo", "bar2");
    engineIntegrationExtension.startProcessInstance(processInstanceDto.getDefinitionId(), variables);
    engineIntegrationExtension.startProcessInstance(processInstanceDto.getDefinitionId(), variables);
    variables.put("foo", "bar3");
    engineIntegrationExtension.startProcessInstance(processInstanceDto.getDefinitionId(), variables);
    engineIntegrationExtension.startProcessInstance(processInstanceDto.getDefinitionId(), variables);
    engineIntegrationExtension.startProcessInstance(processInstanceDto.getDefinitionId(), variables);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(
      processInstanceDto.getProcessDefinitionKey(),
      processInstanceDto.getProcessDefinitionVersion(),
      "foo",
      VariableType.STRING
    );
    reportData.getConfiguration().setSorting(new ReportSortingDto(SORT_BY_VALUE, SortOrder.ASC));
    final ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData)
      .getResult();

    // then
    final List<MapResultEntryDto> resultData = result.getFirstMeasureData();
    assertThat(resultData).hasSize(3);
    final List<Double> bucketValues = resultData.stream().map(MapResultEntryDto::getValue).collect(Collectors.toList());
    assertThat(bucketValues).isSortedAccordingTo(Comparator.naturalOrder());
  }

  @Test
  public void variableTypeIsImportant() {
    // given
    Map<String, Object> variables = new HashMap<>();
    variables.put("foo", "1");
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess(variables);
    variables.put("foo", 1);
    engineIntegrationExtension.startProcessInstance(processInstanceDto.getDefinitionId(), variables);
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(
      processInstanceDto.getProcessDefinitionKey(),
      processInstanceDto.getProcessDefinitionVersion(),
      "foo",
      VariableType.STRING
    );
    AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResponse =
      reportClient.evaluateMapReport(reportData);

    // then
    final ReportResultResponseDto<List<MapResultEntryDto>> result = evaluationResponse.getResult();
    assertThat(result.getFirstMeasureData()).isNotNull();
    assertThat(result.getFirstMeasureData()).hasSize(2);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), "1").get().getValue()).isEqualTo(1.);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), MISSING_VARIABLE_KEY)
                 .get()
                 .getValue()).isEqualTo(1.);
  }

  @Test
  public void otherVariablesDoNotDistortTheResult() {
    // given
    Map<String, Object> variables = new HashMap<>();
    variables.put("foo1", "bar1");
    variables.put("foo2", "bar1");
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess(variables);
    engineIntegrationExtension.startProcessInstance(processInstanceDto.getDefinitionId(), variables);
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(
      processInstanceDto.getProcessDefinitionKey(),
      processInstanceDto.getProcessDefinitionVersion(),
      "foo1",
      VariableType.STRING
    );
    AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResponse =
      reportClient.evaluateMapReport(reportData);

    // then
    final ReportResultResponseDto<List<MapResultEntryDto>> result = evaluationResponse.getResult();
    assertThat(result.getFirstMeasureData()).isNotNull();
    assertThat(result.getFirstMeasureData()).hasSize(1);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), "bar1").get().getValue()).isEqualTo(2.);
  }

  @Test
  public void worksWithAllVariableTypes() {
    // given
    Map<String, Object> variables = new HashMap<>();
    variables.put("dateVar", OffsetDateTime.now());
    variables.put("boolVar", true);
    variables.put("shortVar", (short) 2);
    variables.put("intVar", 3);
    variables.put("longVar", 4L);
    variables.put("doubleVar", 5.5);
    variables.put("stringVar", "aString");
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess(variables);
    importAllEngineEntitiesFromScratch();

    for (Map.Entry<String, Object> entry : variables.entrySet()) {
      // when
      VariableType variableType = varNameToTypeMap.get(entry.getKey());
      ProcessReportDataDto reportData = createReport(
        processInstanceDto.getProcessDefinitionKey(),
        processInstanceDto.getProcessDefinitionVersion(),
        entry.getKey(),
        variableType
      );
      List<MapResultEntryDto> resultData = reportClient.evaluateMapReport(reportData).getResult().getFirstMeasureData();

      // then

      assertThat(resultData).isNotNull();
      String expectedKey;
      if (VariableType.DATE.equals(variableType)) {
        OffsetDateTime temporal = (OffsetDateTime) variables.get(entry.getKey());
        expectedKey = embeddedOptimizeExtension.formatToHistogramBucketKey(
          temporal.atZoneSimilarLocal(ZoneId.systemDefault()).toOffsetDateTime(),
          ChronoUnit.MONTHS
        );
        assertThat(resultData).hasSize(1);
        assertThat(resultData.get(0).getKey()).isEqualTo(expectedKey);
        assertThat(resultData.get(0).getValue()).isEqualTo(1.);
      } else if (VariableType.getNumericTypes().contains(variableType)) {
        assertThat(resultData
                     .stream()
                     .mapToDouble(resultEntry -> resultEntry.getValue() == null ? 0.0 : resultEntry.getValue())
                     .sum())
          .isEqualTo(1.0);
      } else {
        assertThat(resultData).hasSize(1);
        expectedKey = String.valueOf(entry.getValue());
        assertThat(resultData.get(0).getKey()).isEqualTo(expectedKey);
      }
    }
  }

  @Test
  public void multipleVariablesWithSameNameInOneProcessInstanceAreCountedOnlyOnce() throws SQLException {
    // given
    Map<String, Object> variables = ImmutableMap.of("testVar", "withValue", "testVarTemp", "withValue");
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess(variables);
    engineDatabaseExtension.changeVariableName(processInstanceDto.getId(), "testVarTemp", "testVar");

    importAllEngineEntitiesFromScratch();

    assertThat(getVariableInstanceCount("testVar")).isEqualTo(2);

    // when
    ProcessReportDataDto reportData = createReport(
      processInstanceDto.getProcessDefinitionKey(),
      processInstanceDto.getProcessDefinitionVersion(),
      "testVar",
      VariableType.STRING
    );
    AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResponse =
      reportClient.evaluateMapReport(reportData);

    // then
    final ReportResultResponseDto<List<MapResultEntryDto>> result = evaluationResponse.getResult();
    assertThat(result.getFirstMeasureData()).isNotNull();
    assertThat(result.getFirstMeasureData()).hasSize(1);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), "withValue").get().getValue()).isEqualTo(1.);
  }

  @Test
  public void missingVariablesAggregationWorksForUndefinedAndNullVariables() {
    // given
    // 1 process instance with 'testVar'
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess(of("testVar", "withValue"));

    // 4 process instances without 'testVar'
    engineIntegrationExtension.startProcessInstance(
      processInstanceDto.getDefinitionId(),
      Collections.singletonMap("testVar", null)
    );
    engineIntegrationExtension.startProcessInstance(
      processInstanceDto.getDefinitionId(),
      Collections.singletonMap("testVar", new EngineVariableValue(null, "String"))
    );
    engineIntegrationExtension.startProcessInstance(processInstanceDto.getDefinitionId());
    engineIntegrationExtension.startProcessInstance(
      processInstanceDto.getDefinitionId(),
      of("differentStringValue", "test")
    );

    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(
      processInstanceDto.getProcessDefinitionKey(),
      processInstanceDto.getProcessDefinitionVersion(),
      "testVar",
      VariableType.STRING
    );
    AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResponse =
      reportClient.evaluateMapReport(reportData);

    // then
    final ReportResultResponseDto<List<MapResultEntryDto>> result = evaluationResponse.getResult();
    assertThat(result.getFirstMeasureData()).isNotNull();
    assertThat(result.getFirstMeasureData()).hasSize(2);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), "withValue").get().getValue()).isEqualTo(1.);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), "missing").get().getValue()).isEqualTo(4.);
  }

  @Test
  public void missingVariablesAggregationForNullInputVariableOfTypeDouble_sortingByKeyDoesNotFail() {
    // given a process instance with variable with non null value and one instance with variable with null value
    final String varName = "doubleVar";
    final Double varValue = 1.0;
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess(of(
      varName,
      varValue
    ));

    engineIntegrationExtension.startProcessInstance(
      processInstanceDto.getDefinitionId(),
      Collections.singletonMap(varName, new EngineVariableValue(null, "Double"))
    );

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(
      processInstanceDto.getProcessDefinitionKey(),
      processInstanceDto.getProcessDefinitionVersion(),
      varName,
      VariableType.DOUBLE
    );
    final AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResponse =
      reportClient.evaluateMapReport(reportData);

    // then
    final ReportResultResponseDto<List<MapResultEntryDto>> result = evaluationResponse.getResult();
    assertThat(result.getFirstMeasureData()).isNotNull();
    assertThat(result.getFirstMeasureData()).hasSize(2);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), "1.00").get().getValue()).isEqualTo(1.);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), "missing").get().getValue()).isEqualTo(1.);
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
    ProcessReportDataDto reportData = createReport(
      processInstanceDto.getProcessDefinitionKey(),
      processInstanceDto.getProcessDefinitionVersion(),
      "dateVar",
      VariableType.DATE
    );

    AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> response =
      reportClient.evaluateMapReport(reportData);

    // then
    final List<MapResultEntryDto> resultData = response.getResult().getFirstMeasureData();
    final List<String> resultKeys = resultData.stream().map(MapResultEntryDto::getKey).collect(Collectors.toList());
    assertThat(resultKeys).isSortedAccordingTo(Comparator.naturalOrder());
  }

  @Test
  public void dateFilterInReport() {
    // given
    Map<String, Object> variables = new HashMap<>();
    variables.put("foo", "bar");
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleServiceTaskProcess(variables);
    OffsetDateTime past = engineIntegrationExtension.getHistoricProcessInstance(processInstance.getId()).getStartTime();
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(
      processInstance.getProcessDefinitionKey(),
      processInstance.getProcessDefinitionVersion(),
      "foo",
      VariableType.STRING
    );
    reportData.setFilter(ProcessFilterBuilder.filter()
                           .fixedInstanceStartDate()
                           .start(null)
                           .end(past.minusSeconds(1L))
                           .add()
                           .buildList());
    ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getFirstMeasureData()).isNotNull();
    assertThat(result.getFirstMeasureData()).isEmpty();

    // when
    reportData.setFilter(ProcessFilterBuilder.filter()
                           .fixedInstanceStartDate()
                           .start(past)
                           .end(null)
                           .add()
                           .buildList());
    result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getFirstMeasureData()).isNotNull();
    assertThat(result.getFirstMeasureData()).hasSize(1);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), "bar").get().getValue()).isEqualTo(1.);
  }

  @Test
  public void optimizeExceptionOnViewEntityIsNull() {
    // given
    ProcessReportDataDto dataDto = createReport("123", "1", "foo", VariableType.STRING);
    dataDto.getView().setEntity(null);

    // when
    Response response = reportClient.evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void optimizeExceptionOnViewPropertyIsNull() {
    // given
    ProcessReportDataDto dataDto = createReport("123", "1", "foo", VariableType.STRING);
    dataDto.getView().setProperties((ViewProperty) null);

    // when
    Response response = reportClient.evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void optimizeExceptionOnGroupByTypeIsNull() {
    // given
    ProcessReportDataDto dataDto = createReport("123", "1", "foo", VariableType.STRING);
    dataDto.getGroupBy().setType(null);

    // when
    Response response = reportClient.evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void optimizeExceptionOnGroupByValueNameIsNull() {
    // given
    ProcessReportDataDto dataDto = createReport("123", "1", "foo", VariableType.STRING);
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
    ProcessReportDataDto dataDto = createReport("123", "1", "foo", VariableType.STRING);
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
    final ProcessDefinitionEngineDto def = deploySimpleServiceTaskProcessAndGetDefinition();
    Map<String, Object> variables = new HashMap<>();
    OffsetDateTime dateVariableValue = OffsetDateTime.parse("2020-06-15T00:00:00+02:00");

    for (int i = 0; i < numberOfInstances; i++) {
      dateVariableValue = dateVariableValue.plus(1, chronoUnit);
      variables.put(dateVarName, dateVariableValue);
      engineIntegrationExtension.startProcessInstance(def.getId(), variables);
    }

    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(
      def.getKey(),
      def.getVersionAsString(),
      dateVarName,
      VariableType.DATE
    );
    reportData.getConfiguration().setGroupByDateVariableUnit(unit);
    List<MapResultEntryDto> resultData = reportClient.evaluateMapReport(reportData).getResult().getFirstMeasureData();

    // then
    assertThat(resultData).isNotNull();
    // there is one bucket per instance since the date variables are each one bucket span apart
    assertThat(resultData).hasSize(numberOfInstances);
    // buckets are in ascending order, so the first bucket is based on the date variable of the first instance
    dateVariableValue = dateVariableValue.minus(numberOfInstances - 1, chronoUnit);
    for (int i = 0; i < numberOfInstances; i++) {
      final String expectedBucketKey = embeddedOptimizeExtension.formatToHistogramBucketKey(
        dateVariableValue.plus(i, chronoUnit),
        chronoUnit
      );
      assertThat(resultData.get(i).getValue()).isEqualTo(1);
      assertThat(resultData.get(i).getKey()).isEqualTo(expectedBucketKey);
    }
  }

  @SneakyThrows
  @Test
  public void groupByDateVariableWorksForAutomaticInterval() {
    // given
    final int numberOfInstances = 3;
    final String dateVarName = "dateVar";
    final ProcessDefinitionEngineDto def = deploySimpleServiceTaskProcessAndGetDefinition();
    OffsetDateTime dateVariableValue = OffsetDateTime.now();
    Map<String, Object> variables = new HashMap<>();

    for (int i = 0; i < numberOfInstances; i++) {
      dateVariableValue = dateVariableValue.plusMinutes(i);
      variables.put(dateVarName, dateVariableValue);
      engineIntegrationExtension.startProcessInstance(def.getId(), variables);
    }

    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(
      def.getKey(),
      def.getVersionAsString(),
      dateVarName,
      VariableType.DATE
    );
    final ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData)
      .getResult();

    // then
    List<MapResultEntryDto> resultData = result.getFirstMeasureData();
    assertThat(resultData).isNotNull();
    assertThat(resultData).hasSize(NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION);
    // the bucket span covers the earliest and the latest date variable value
    DateTimeFormatter formatter = embeddedOptimizeExtension.getDateTimeFormatter();
    final OffsetDateTime startOfFirstBucket = OffsetDateTime.from(formatter.parse(resultData.get(0).getKey()));
    final OffsetDateTime startOfLastBucket = OffsetDateTime
      .from(formatter.parse(resultData.get(NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION - 1).getKey()));
    final OffsetDateTime firstTruncatedDateVariableValue = dateVariableValue.truncatedTo(ChronoUnit.MILLIS);
    final OffsetDateTime lastTruncatedDateVariableValue =
      dateVariableValue.minusMinutes(numberOfInstances).truncatedTo(ChronoUnit.MILLIS);

    assertThat(startOfFirstBucket).isBeforeOrEqualTo(firstTruncatedDateVariableValue);
    assertThat(startOfLastBucket).isAfterOrEqualTo(lastTruncatedDateVariableValue);
    assertThat(result.getFirstMeasureData().stream().mapToDouble(MapResultEntryDto::getValue).sum())
      .isEqualTo(3.0); // each instance falls into one bucket
  }

  @SneakyThrows
  @Test
  public void groupByDateVariableForAutomaticInterval_MissingInstancesReturnsEmptyResult() {
    // given
    final String dateVarName = "dateVar";
    final ProcessDefinitionEngineDto def = deploySimpleServiceTaskProcessAndGetDefinition();

    // when
    ProcessReportDataDto reportData = createReport(
      def.getKey(),
      def.getVersionAsString(),
      dateVarName,
      VariableType.DATE
    );
    final ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData)
      .getResult();

    // then
    List<MapResultEntryDto> resultData = result.getFirstMeasureData();
    assertThat(resultData).isNotNull();
    assertThat(resultData).isEmpty();
  }

  private String createAndStoreDefaultReportDefinition(String processDefinitionKey,
                                                       String processDefinitionVersion,
                                                       String variableName,
                                                       VariableType variableType) {
    ProcessReportDataDto reportData = createReport(
      processDefinitionKey,
      processDefinitionVersion,
      variableName,
      variableType
    );
    return createNewReport(reportData);
  }

  private ProcessReportDataDto createReport(String processDefinitionKey,
                                            String processDefinitionVersion,
                                            String variableName,
                                            VariableType variableType) {
    return TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersion(processDefinitionVersion)
      .setVariableName(variableName)
      .setVariableType(variableType)
      .setReportDataType(ProcessReportDataType.PROC_INST_FREQ_GROUP_BY_VARIABLE)
      .build();
  }

  private ProcessReportDataDto createReport(String processDefinitionKey,
                                            String processDefinitionVersion,
                                            String variableName,
                                            VariableType variableType,
                                            final Double baseline,
                                            final Double groupByNumberVariableBucketSize) {
    ProcessReportDataDto reportDataDto = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersion(processDefinitionVersion)
      .setVariableName(variableName)
      .setVariableType(variableType)
      .setReportDataType(ProcessReportDataType.PROC_INST_FREQ_GROUP_BY_VARIABLE)
      .build();
    reportDataDto.getConfiguration().getCustomBucket().setActive(true);
    reportDataDto.getConfiguration().getCustomBucket().setBaseline(baseline);
    reportDataDto.getConfiguration().getCustomBucket().setBucketSize(groupByNumberVariableBucketSize);
    return reportDataDto;
  }

  private Integer getVariableInstanceCount(String variableName) {
    final QueryBuilder query = nestedQuery(
      VARIABLES,
      boolQuery().must(termQuery(getNestedVariableNameField(), variableName)),
      ScoreMode.None
    );
    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(query)
      .fetchSource(false)
      .size(0);

    SearchRequest searchRequest = new SearchRequest()
      .indices(PROCESS_INSTANCE_MULTI_ALIAS)
      .source(searchSourceBuilder);

    String VARIABLE_COUNT_AGGREGATION = VARIABLES + FREQUENCY_AGGREGATION;
    String NESTED_VARIABLE_AGGREGATION = "nestedAggregation";
    searchSourceBuilder.aggregation(
      nested(
        NESTED_VARIABLE_AGGREGATION,
        VARIABLES
      )
        .subAggregation(
          count(VARIABLE_COUNT_AGGREGATION)
            .field(getNestedVariableIdField())
        )
    );

    SearchResponse searchResponse;
    try {
      searchResponse = elasticSearchIntegrationTestExtension.getOptimizeElasticClient().search(searchRequest);
    } catch (IOException | ElasticsearchStatusException e) {
      throw new OptimizeIntegrationTestException(
        "Cannot evaluate variable instance count in process instance indices.",
        e
      );
    }

    Nested nestedAgg = searchResponse.getAggregations().get(NESTED_VARIABLE_AGGREGATION);
    ValueCount countAggregator = nestedAgg.getAggregations().get(VARIABLE_COUNT_AGGREGATION);
    return Long.valueOf(countAggregator.getValue()).intValue();
  }

}
