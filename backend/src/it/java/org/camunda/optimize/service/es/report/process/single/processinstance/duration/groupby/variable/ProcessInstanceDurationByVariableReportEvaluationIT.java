/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process.single.processinstance.duration.groupby.variable;

import lombok.SneakyThrows;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DurationFilterUnit;
import org.camunda.optimize.dto.optimize.query.report.single.group.GroupByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.VariableGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.result.ReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import org.camunda.optimize.dto.optimize.query.sorting.SortingDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResultDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.report.process.AbstractProcessDefinitionIT;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.test.it.extension.EngineVariableValue;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.google.common.collect.ImmutableMap.of;
import static com.google.common.collect.Lists.newArrayList;
import static java.time.temporal.ChronoUnit.MILLIS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.dto.optimize.ReportConstants.MISSING_VARIABLE_KEY;
import static org.camunda.optimize.dto.optimize.query.report.FilterOperatorConstants.GREATER_THAN_EQUALS;
import static org.camunda.optimize.dto.optimize.query.sorting.SortingDto.SORT_BY_KEY;
import static org.camunda.optimize.dto.optimize.query.sorting.SortingDto.SORT_BY_VALUE;
import static org.camunda.optimize.service.es.filter.DateHistogramBucketLimiterUtil.mapToChronoUnit;
import static org.camunda.optimize.test.util.DateModificationHelper.truncateToStartOfUnit;
import static org.camunda.optimize.test.util.DurationAggregationUtil.calculateExpectedValueGivenDurationsDefaultAggr;
import static org.camunda.optimize.test.util.ProcessReportDataType.COUNT_PROC_INST_FREQ_GROUP_BY_VARIABLE;
import static org.camunda.optimize.test.util.ProcessReportDataType.PROC_INST_DUR_GROUP_BY_VARIABLE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION;

public class ProcessInstanceDurationByVariableReportEvaluationIT extends AbstractProcessDefinitionIT {

  private final List<AggregationType> aggregationTypes = AggregationType.getAggregationTypesAsListWithoutSum();

  @Test
  public void simpleReportEvaluation() throws SQLException {
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

    AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto> evaluationResponse = reportClient.evaluateMapReport(
      reportData);

    // then
    ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey()).isEqualTo(processInstanceDto.getProcessDefinitionKey());
    assertThat(resultReportDataDto.getDefinitionVersions()).contains(processInstanceDto.getProcessDefinitionVersion());
    assertThat(resultReportDataDto.getView()).isNotNull();
    assertThat(resultReportDataDto.getView().getEntity()).isEqualTo(ProcessViewEntity.PROCESS_INSTANCE);
    assertThat(resultReportDataDto.getView().getProperty()).isEqualTo(ProcessViewProperty.DURATION);
    assertThat(resultReportDataDto.getGroupBy().getType()).isEqualTo(ProcessGroupByType.VARIABLE);
    VariableGroupByDto variableGroupByDto = (VariableGroupByDto) resultReportDataDto.getGroupBy();
    assertThat(variableGroupByDto.getValue().getName()).isEqualTo("foo");
    assertThat(variableGroupByDto.getValue().getType()).isEqualTo(VariableType.STRING);

    final ReportMapResultDto resultDto = evaluationResponse.getResult();
    assertThat(resultDto.getInstanceCount()).isEqualTo(1);
    assertThat(resultDto.getData()).isNotNull();
    assertThat(resultDto.getData()).hasSize(1);

    final List<MapResultEntryDto> resultData = resultDto.getData();
    final MapResultEntryDto resultEntry = resultData.get(0);
    assertThat(resultEntry).isNotNull();
    assertThat(resultEntry.getKey()).isEqualTo("bar");
    assertThat(resultEntry.getValue()).isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(1000L));
  }

  @Test
  public void simpleReportEvaluationById() throws SQLException {
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

    createNewReport(reportData);

    // when
    AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto> evaluationResponse = reportClient.evaluateMapReport(
      reportData);

    // then
    ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey()).isEqualTo(processInstance.getProcessDefinitionKey());
    assertThat(resultReportDataDto.getDefinitionVersions()).contains(processInstance.getProcessDefinitionVersion());
    assertThat(resultReportDataDto.getView()).isNotNull();
    assertThat(resultReportDataDto.getView().getEntity()).isEqualTo(ProcessViewEntity.PROCESS_INSTANCE);
    assertThat(resultReportDataDto.getView().getProperty()).isEqualTo(ProcessViewProperty.DURATION);
    assertThat(resultReportDataDto.getGroupBy().getType()).isEqualTo(ProcessGroupByType.VARIABLE);
    VariableGroupByDto variableGroupByDto = (VariableGroupByDto) resultReportDataDto.getGroupBy();
    assertThat(variableGroupByDto.getValue().getName()).isEqualTo("foo");
    assertThat(variableGroupByDto.getValue().getType()).isEqualTo(VariableType.STRING);

    final ReportMapResultDto resultDto = evaluationResponse.getResult();
    assertThat(resultDto.getInstanceCount()).isEqualTo(1);
    assertThat(resultDto.getData()).isNotNull();
    assertThat(resultDto.getData()).hasSize(1);

    final List<MapResultEntryDto> resultData = resultDto.getData();
    final MapResultEntryDto resultEntry = resultData.get(0);
    assertThat(resultEntry).isNotNull();
    assertThat(resultEntry.getKey()).isEqualTo("bar");
    assertThat(resultEntry.getValue()).isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(1000L));
  }

  @Test
  public void testCustomOrderOnResultKeyIsApplied() throws SQLException {
    // given
    Map<String, Object> variables = new HashMap<>();
    variables.put("foo", "bar1");
    ProcessDefinitionEngineDto processDefinitionDto = deploySimpleServiceTaskProcess();
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
    reportData.getConfiguration().setSorting(new SortingDto(SORT_BY_KEY, SortOrder.DESC));
    final ReportMapResultDto result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    final List<MapResultEntryDto> resultData = result.getData();
    assertThat(resultData).hasSize(3);
    final List<String> resultKeys = resultData.stream().map(MapResultEntryDto::getKey).collect(Collectors.toList());
    // expect ascending order
    assertThat(resultKeys).isSortedAccordingTo(Comparator.reverseOrder());
  }

  @Test
  public void testCustomOrderOnResultValueIsApplied() throws SQLException {
    // given
    Map<String, Object> variables = new HashMap<>();
    variables.put("foo", "bar1");
    ProcessDefinitionEngineDto processDefinitionDto = deploySimpleServiceTaskProcess();
    startProcessInstanceShiftedBySeconds(variables, processDefinitionDto.getId(), 1);
    variables.put("foo", "bar2");
    startProcessInstanceShiftedBySeconds(variables, processDefinitionDto.getId(), 1);
    startProcessInstanceShiftedBySeconds(variables, processDefinitionDto.getId(), 9);
    variables.put("foo", "bar3");
    startProcessInstanceShiftedBySeconds(variables, processDefinitionDto.getId(), 2);

    importAllEngineEntitiesFromScratch();

    aggregationTypes.forEach((AggregationType aggType) -> {
      // when
      final ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
        .createReportData()
        .setReportDataType(PROC_INST_DUR_GROUP_BY_VARIABLE)
        .setProcessDefinitionKey(processDefinitionDto.getKey())
        .setProcessDefinitionVersion(ALL_VERSIONS)
        .setVariableName("foo")
        .setVariableType(VariableType.STRING)
        .build();
      reportData.getConfiguration().setSorting(new SortingDto(SORT_BY_VALUE, SortOrder.ASC));
      reportData.getConfiguration().setAggregationType(aggType);
      final ReportMapResultDto result = reportClient.evaluateMapReport(reportData).getResult();

      // then
      final List<MapResultEntryDto> resultData = result.getData();
      assertThat(resultData).hasSize(3);
      final List<Long> bucketValues = resultData.stream()
        .map(MapResultEntryDto::getValue)
        .collect(Collectors.toList());
      assertThat(bucketValues).isSortedAccordingTo(Comparator.naturalOrder());
    });
  }

  @Test
  public void dateVariablesAreSortedDescByDefault() {
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
      .setReportDataType(COUNT_PROC_INST_FREQ_GROUP_BY_VARIABLE)
      .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
      .setVariableName("dateVar")
      .setVariableType(VariableType.DATE)
      .build();

    AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto> response =
      reportClient.evaluateMapReport(reportData);

    // then
    final List<MapResultEntryDto> resultData = response.getResult().getData();
    final List<String> resultKeys = resultData.stream().map(MapResultEntryDto::getKey).collect(Collectors.toList());
    // expect descending order
    assertThat(resultKeys).isSortedAccordingTo(Comparator.reverseOrder());
  }

  @Test
  public void otherProcessDefinitionsDoNoAffectResult() throws SQLException {
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
    AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto> evaluationResponse = reportClient.evaluateMapReport(
      reportData);

    // then
    ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey()).isEqualTo(processInstanceDto.getProcessDefinitionKey());
    assertThat(resultReportDataDto.getDefinitionVersions()).contains(processInstanceDto.getProcessDefinitionVersion());

    final List<MapResultEntryDto> resultData = evaluationResponse.getResult().getData();
    assertThat(resultData).isNotNull();
    assertThat(resultData).hasSize(1);
    assertThat(resultData.get(0).getKey()).isEqualTo("bar2");
    assertThat(resultData.get(0).getValue()).isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(1000L));
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
    ReportMapResultDto result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(selectedTenants.size());
  }

  @Test
  public void multipleProcessInstances() throws SQLException {
    // given
    Map<String, Object> variables = new HashMap<>();
    variables.put("foo", "bar1");
    ProcessDefinitionEngineDto processDefinitionDto = deploySimpleServiceTaskProcess();
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

    AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto> evaluationResponse = reportClient.evaluateMapReport(
      reportData);

    // then
    ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey()).isEqualTo(processDefinitionDto.getKey());
    assertThat(resultReportDataDto.getDefinitionVersions()).contains(processDefinitionDto.getVersionAsString());

    final ReportMapResultDto result = evaluationResponse.getResult();
    assertThat(result.getIsComplete()).isTrue();
    assertThat(result.getData()).isNotNull();
    assertThat(result.getEntryForKey("bar1").get().getValue())
      .isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(1000L));
    assertThat(result.getEntryForKey("bar2").get().getValue())
      .isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(1000L, 9000L, 2000L));
  }

  @Test
  public void multipleBuckets_resultLimitedByConfig() throws SQLException {
    // given
    Map<String, Object> variables = new HashMap<>();
    variables.put("foo", "bar1");
    ProcessDefinitionEngineDto processDefinitionDto = deploySimpleServiceTaskProcess();
    startProcessInstanceShiftedBySeconds(variables, processDefinitionDto.getId(), 1);
    variables.put("foo", "bar2");
    startProcessInstanceShiftedBySeconds(variables, processDefinitionDto.getId(), 1);

    importAllEngineEntitiesFromScratch();

    embeddedOptimizeExtension.getConfigurationService().setEsAggregationBucketLimit(1);

    // when
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(PROC_INST_DUR_GROUP_BY_VARIABLE)
      .setProcessDefinitionKey(processDefinitionDto.getKey())
      .setProcessDefinitionVersion(String.valueOf(processDefinitionDto.getVersion()))
      .setVariableName("foo")
      .setVariableType(VariableType.STRING)
      .build();

    AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto> evaluationResponse = reportClient.evaluateMapReport(
      reportData);

    // then
    final ReportMapResultDto resultDto = evaluationResponse.getResult();
    assertThat(resultDto.getInstanceCount()).isEqualTo(2);
    assertThat(resultDto.getData()).isNotNull();
    assertThat(resultDto.getData()).hasSize(1);
    assertThat(resultDto.getIsComplete()).isFalse();
  }

  @Test
  public void calculateDurationForRunningProcessInstances() throws SQLException {
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
    final List<ProcessFilterDto<?>> testExecutionStateFilter = ProcessFilterBuilder.filter()
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
      .setFilter(testExecutionStateFilter)
      .build();
    ReportMapResultDto resultDto = reportClient.evaluateMapReport(reportData).getResult();

    // then
    final List<MapResultEntryDto> resultData = resultDto.getData();
    assertThat(resultData).isNotNull();
    assertThat(resultData).hasSize(1);
    assertThat(resultData.get(0).getValue()).isEqualTo(runningProcInstStartDate.until(now, MILLIS));
  }

  @Test
  public void calculateDurationForCompletedProcessInstances() throws SQLException {
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
    final List<ProcessFilterDto<?>> testExecutionStateFilter = ProcessFilterBuilder.filter()
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
      .setFilter(testExecutionStateFilter)
      .build();
    ReportMapResultDto resultDto = reportClient.evaluateMapReport(reportData).getResult();

    // then
    final List<MapResultEntryDto> resultData = resultDto.getData();
    assertThat(resultData).isNotNull();
    assertThat(resultData).hasSize(1);
    assertThat(resultData.get(0).getValue()).isEqualTo(1000L);
  }

  @Test
  public void calculateDurationForRunningAndCompletedProcessInstances() throws SQLException {
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
    ReportMapResultDto resultDto = reportClient.evaluateMapReport(reportData).getResult();

    // then
    final List<MapResultEntryDto> resultData = resultDto.getData();
    assertThat(resultData).isNotNull();
    assertThat(resultData).hasSize(1);
    assertThat(resultData.get(0).getValue())
      .isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(1000L, runningProcInstStartDate.until(now, MILLIS)));
  }

  @Test
  public void durationFilterWorksForRunningProcessInstances() throws SQLException {
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
    final List<ProcessFilterDto<?>> testExecutionStateFilter = ProcessFilterBuilder.filter()
      .duration()
      .operator(GREATER_THAN_EQUALS)
      .unit(DurationFilterUnit.HOURS)
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
      .setFilter(testExecutionStateFilter)
      .build();
    ReportMapResultDto resultDto = reportClient.evaluateMapReport(reportData).getResult();

    // then
    final List<MapResultEntryDto> resultData = resultDto.getData();
    assertThat(resultData).isNotNull();
    assertThat(resultData).hasSize(1);
    assertThat(resultData.get(0).getValue()).isEqualTo(runningProcInstStartDate.until(now, MILLIS));
  }

  @Test
  public void variableTypeIsImportant() throws SQLException {
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
    reportData.getConfiguration().setAggregationType(AggregationType.MAX);
    AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto> evaluationResponse = reportClient.evaluateMapReport(
      reportData);

    // then
    ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey()).isEqualTo(processInstanceDto.getProcessDefinitionKey());
    assertThat(resultReportDataDto.getDefinitionVersions()).contains(processInstanceDto.getProcessDefinitionVersion());

    final List<MapResultEntryDto> resultData = evaluationResponse.getResult().getData();
    assertThat(resultData).isNotNull();
    assertThat(resultData).hasSize(2);
    assertThat(resultData.get(0).getKey()).isEqualTo("1");
    assertThat(resultData.get(0).getValue()).isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(1000L));
    assertThat(resultData.get(1).getKey()).isEqualTo(MISSING_VARIABLE_KEY);
    assertThat(resultData.get(1).getValue()).isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(1000L));
  }

  @Test
  public void otherVariablesDoNotDistortTheResult() throws SQLException {
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
    AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto> evaluationResponse = reportClient.evaluateMapReport(
      reportData);

    // then
    ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey()).isEqualTo(processInstanceDto.getProcessDefinitionKey());
    assertThat(resultReportDataDto.getDefinitionVersions()).contains(processInstanceDto.getProcessDefinitionVersion());

    final List<MapResultEntryDto> resultData = evaluationResponse.getResult().getData();
    assertThat(resultData).isNotNull();
    assertThat(resultData).hasSize(1);
    assertThat(resultData.get(0).getKey()).isEqualTo("bar1");
    assertThat(resultData.get(0).getValue()).isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(1000L));
  }

  @Test
  public void worksWithAllVariableTypes() throws SQLException {
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

      ReportMapResultDto resultDto = reportClient.evaluateMapReport(reportData).getResult();

      // then
      assertThat(resultDto.getData()).isNotNull();

      final List<MapResultEntryDto> resultData = resultDto.getData();
      assertThat(resultData).hasSize(1);
      if (VariableType.DATE.equals(variableType)) {
        OffsetDateTime temporal = (OffsetDateTime) variables.get(entry.getKey());
        String dateAsString = embeddedOptimizeExtension.formatToHistogramBucketKey(
          temporal.atZoneSimilarLocal(ZoneId.systemDefault()).toOffsetDateTime(),
          ChronoUnit.MONTHS
        );
        assertThat(resultData.get(0).getKey()).isEqualTo(dateAsString);
        assertThat(resultData.get(0).getValue()).isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(1000L));
      } else {
        assertThat(resultData.get(0).getValue()).isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(1000L));
      }
    }
  }

  @Test
  public void missingVariablesAggregationWorksForUndefinedAndNullVariables() throws SQLException {
    // given

    // 1 process instance with 'testVar'
    OffsetDateTime testEndDate = OffsetDateTime.now();
    OffsetDateTime testStartDate = testEndDate.minusSeconds(2);

    final ProcessDefinitionEngineDto definition = deploySimpleServiceTaskProcess();

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
    AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto> evaluationResponse = reportClient.evaluateMapReport(
      reportData);


    // then
    final ReportMapResultDto result = evaluationResponse.getResult();
    assertThat(result.getData()).isNotNull();
    assertThat(result.getData()).hasSize(2);
    assertThat(result.getEntryForKey("withValue").get().getValue()).isEqualTo(testStartDate.until(testEndDate, MILLIS));
    assertThat(result.getEntryForKey("missing").get().getValue())
      .isEqualTo(
        calculateExpectedValueGivenDurationsDefaultAggr(
          missingTestStartDate.until(missingTestStartDate.plus(200, MILLIS), MILLIS),
          missingTestStartDate.until(missingTestStartDate.plus(400, MILLIS), MILLIS),
          missingTestStartDate.until(missingTestStartDate.plus(3000, MILLIS), MILLIS),
          missingTestStartDate.until(missingTestStartDate.plus(10000, MILLIS), MILLIS)
        ));
  }

  @Test
  public void missingVariablesAggregationsForNullVariableOfTypeDouble_sortingByKeyDoesNotFail() throws SQLException {
    // given a process instance with variable of non null value and one instance with variable of null value
    final String varName = "testVar";
    final Double varValue = 1.0;
    OffsetDateTime testEndDate = OffsetDateTime.now();
    OffsetDateTime testStartDate = testEndDate.minusSeconds(2);

    final ProcessDefinitionEngineDto definition = deploySimpleServiceTaskProcess();

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
    final AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto> evaluationResponse =
      reportClient.evaluateMapReport(reportData);

    // then
    final ReportMapResultDto result = evaluationResponse.getResult();
    assertThat(result.getData()).isNotNull();
    assertThat(result.getData()).hasSize(2);
    assertThat(result.getEntryForKey(String.valueOf(varValue)).get().getValue())
      .isEqualTo(testStartDate.until(testEndDate, MILLIS));
    assertThat(result.getEntryForKey("missing").get().getValue()).isEqualTo(400L);
  }

  @Test
  public void filterInReportWorks() throws SQLException {
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
                           .fixedStartDate()
                           .start(null)
                           .end(startDate.minusSeconds(1L))
                           .add()
                           .buildList());
    ReportMapResultDto resultDto = reportClient.evaluateMapReport(reportData).getResult();

    // then
    List<MapResultEntryDto> resultData = resultDto.getData();
    assertThat(resultData).isNotNull();
    assertThat(resultData).isEmpty();

    // when
    reportData.setFilter(ProcessFilterBuilder.filter().fixedStartDate().start(startDate).end(null).add().buildList());
    resultDto = reportClient.evaluateMapReport(reportData).getResult();

    // then
    resultData = resultDto.getData();
    assertThat(resultData).hasSize(1);
    assertThat(resultData.get(0).getKey()).isEqualTo("bar");
    assertThat(resultData.get(0).getValue()).isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(1000L));
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

    //when
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

    dataDto.getView().setProperty(null);

    //when
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

    //when
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

    //when
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

    //when
    Response response = reportClient.evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
  }

  @SneakyThrows
  @ParameterizedTest
  @MethodSource("staticGroupByDateUnits")
  public void groupByDateVariableWorksForAllStaticUnits(final GroupByDateUnit unit) {
    // given
    final ChronoUnit chronoUnit = mapToChronoUnit(unit);
    final int numberOfInstances = 3;
    final String dateVarName = "dateVar";
    final ProcessDefinitionEngineDto def = deploySimpleServiceTaskProcess();
    OffsetDateTime variableValue = OffsetDateTime.now();
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
    List<MapResultEntryDto> resultData = reportClient.evaluateMapReport(reportData).getResult().getData();

    // then
    // there is one bucket per instance since the date variables are each one bucket span apart
    assertThat(resultData).isNotNull();
    assertThat(resultData).hasSize(numberOfInstances);
    // buckets are in descending order, so the first bucket is based on the date variable of the last instance
    final DateTimeFormatter formatter = embeddedOptimizeExtension.getDateTimeFormatter();
    for (int i = 0; i < numberOfInstances; i++) {
      final String expectedBucketKey = formatter.format(
        truncateToStartOfUnit(
          variableValue.minus(chronoUnit.getDuration().multipliedBy(i)),
          chronoUnit
        ));
      assertThat(resultData.get(i).getValue())
        .isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(1000L));
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
    List<MapResultEntryDto> resultData = reportClient.evaluateMapReport(reportData).getResult().getData();

    // then
    assertThat(resultData).isNotNull();
    assertThat(resultData).hasSize(NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION);
    // bucket span covers the values of all date variables (buckets are in descending order, so the first bucket is
    // based on the date variable of the last instance)
    DateTimeFormatter formatter = embeddedOptimizeExtension.getDateTimeFormatter();
    final OffsetDateTime startOfFirstBucket = OffsetDateTime.from(formatter.parse(resultData.get(0).getKey()));
    final OffsetDateTime startOfLastBucket = OffsetDateTime.from(
      formatter.parse(resultData.get(NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION - 1).getKey()));
    final OffsetDateTime firstTruncatedDateVariableValue = dateVariableValue.plusMinutes(numberOfInstances)
      .truncatedTo(ChronoUnit.MILLIS);
    final OffsetDateTime lastTruncatedDateVariableValue = dateVariableValue.truncatedTo(ChronoUnit.MILLIS);

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
      .build();
    List<MapResultEntryDto> resultData = reportClient.evaluateMapReport(reportData).getResult().getData();

    // then
    assertThat(resultData).isNotNull();
    assertThat(resultData).isEmpty();
  }

  private ProcessInstanceEngineDto deployAndStartSimpleServiceTaskProcess(Map<String, Object> variables) {
    return deployAndStartSimpleProcesses(variables).get(0);
  }

  private List<ProcessInstanceEngineDto> deployAndStartSimpleProcesses(Map<String, Object> variables) {
    ProcessDefinitionEngineDto processDefinition = deploySimpleServiceTaskProcess();
    return IntStream.range(0, 1)
      .mapToObj(i -> {
        ProcessInstanceEngineDto processInstanceEngineDto =
          engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
        processInstanceEngineDto.setProcessDefinitionKey(processDefinition.getKey());
        processInstanceEngineDto.setProcessDefinitionVersion(String.valueOf(processDefinition.getVersion()));
        return processInstanceEngineDto;
      })
      .collect(Collectors.toList());
  }

  private void startProcessInstanceShiftedBySeconds(Map<String, Object> variables,
                                                    String processDefinitionId,
                                                    int secondsToShift) throws SQLException {
    ProcessInstanceEngineDto processInstanceDto2;
    OffsetDateTime startDate;
    OffsetDateTime endDate;
    processInstanceDto2 = engineIntegrationExtension.startProcessInstance(processDefinitionId, variables);
    startDate = OffsetDateTime.now();
    endDate = startDate.plusSeconds(secondsToShift);
    engineDatabaseExtension.changeProcessInstanceStartDate(processInstanceDto2.getId(), startDate);
    engineDatabaseExtension.changeProcessInstanceEndDate(processInstanceDto2.getId(), endDate);
  }

  private ProcessDefinitionEngineDto deploySimpleServiceTaskProcess() {
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .name("aProcessName")
      .startEvent()
      .serviceTask()
      .camundaExpression("${true}")
      .endEvent()
      .done();
    return engineIntegrationExtension.deployProcessAndGetProcessDefinition(processModel);
  }

  private ProcessInstanceEngineDto startProcessWithVariablesAndDates(final ProcessDefinitionEngineDto definition,
                                                                     final Map<String, Object> variables,
                                                                     final OffsetDateTime startDate,
                                                                     final OffsetDateTime endDate) throws SQLException {
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
                                     final OffsetDateTime endDate) throws SQLException {
    startProcessWithVariablesAndDates(definition, new HashMap<>(), startDate, endDate);
  }

  private ProcessInstanceEngineDto deployAndStartUserTaskProcessWithVariables(final Map<String, Object> variables) {
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .startEvent()
      .userTask()
      .endEvent()
      .done();
    return engineIntegrationExtension.deployAndStartProcessWithVariables(processModel, variables);
  }

}
