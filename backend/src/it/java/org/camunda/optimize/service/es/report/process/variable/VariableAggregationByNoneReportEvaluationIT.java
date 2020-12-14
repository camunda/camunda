/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process.variable;

import com.google.common.collect.ImmutableMap;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.result.NumberResultDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResultDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.report.process.AbstractProcessDefinitionIT;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType.AVERAGE;
import static org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType.MAX;
import static org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType.MEDIAN;
import static org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType.MIN;
import static org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType.SUM;
import static org.camunda.optimize.test.util.ProcessReportDataType.VARIABLE_AGGREGATION_GROUP_BY_NONE;

public class VariableAggregationByNoneReportEvaluationIT extends AbstractProcessDefinitionIT {

  private static final String TEST_VARIABLE = "testVariable";

  @Test
  public void hasCorrectResponseConfiguration() {
    // given
    Map<String, Object> variables = ImmutableMap.of(TEST_VARIABLE, 12);
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleProcessWithVariables(variables);
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(TEST_VARIABLE, VariableType.INTEGER);
    AuthorizedProcessReportEvaluationResultDto<NumberResultDto> evaluationResponse =
      reportClient.evaluateNumberReport(reportData);

    // then
    ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey()).isEqualTo(processInstanceDto.getProcessDefinitionKey());
    assertThat(resultReportDataDto.getDefinitionVersions()).containsExactly(processInstanceDto.getProcessDefinitionVersion());
    assertThat(resultReportDataDto.getView()).isNotNull();
    assertThat(resultReportDataDto.getView().getEntity()).isEqualTo(ProcessViewEntity.VARIABLE);
    assertThat(resultReportDataDto.getView().getProperty())
      .isEqualTo(ProcessViewProperty.VARIABLE(TEST_VARIABLE, VariableType.INTEGER));
    assertThat(resultReportDataDto.getGroupBy().getType()).isEqualTo(ProcessGroupByType.NONE);

    final NumberResultDto resultDto = evaluationResponse.getResult();
    assertThat(resultDto.getInstanceCount()).isEqualTo(1L);
    assertThat(resultDto.getData()).isNotNull();
    assertThat(resultDto.getData()).isEqualTo(12.);
  }

  @Test
  public void supportsAllNumericVariableTypes() {
    // given
    Map<String, Object> variables = createAllNumericVariables();
    deployAndStartSimpleProcessWithVariables(variables);
    importAllEngineEntitiesFromScratch();

    for (String variable : variables.keySet()) {
      // when
      final VariableType variableType = varNameToTypeMap.get(variable);
      ProcessReportDataDto reportData = createReport(variable, variableType);
      NumberResultDto evaluationResponse = reportClient.evaluateNumberReport(reportData).getResult();

      // then
      assertThat(evaluationResponse.getInstanceCount()).isEqualTo(1L);
      final Double resultAsDouble = ((Number) variables.get(variable)).doubleValue();
      assertThat(evaluationResponse.getData()).isEqualTo(resultAsDouble);
    }
  }

  @Test
  public void acrossSeveralProcessInstances() {
    // given
    Map<String, Object> variables = new HashMap<>();
    variables.put(TEST_VARIABLE, 1);
    final ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcessWithVariables(variables);
    variables.put(TEST_VARIABLE, 3);
    engineIntegrationExtension.startProcessInstance(processInstance.getDefinitionId(), variables);
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(TEST_VARIABLE, VariableType.INTEGER);
    reportData.getConfiguration().setAggregationType(AVERAGE);
    NumberResultDto evaluationResponse = reportClient.evaluateNumberReport(reportData).getResult();

    // then
    assertThat(evaluationResponse.getInstanceCount()).isEqualTo(2L);
    assertThat(evaluationResponse.getData()).isEqualTo(2.);
  }

  @Test
  public void rationalNumberAsResult() {
    // given
    Map<String, Object> variables = new HashMap<>();
    variables.put(TEST_VARIABLE, 1);
    final ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcessWithVariables(variables);
    variables.put(TEST_VARIABLE, 4);
    engineIntegrationExtension.startProcessInstance(processInstance.getDefinitionId(), variables);
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(TEST_VARIABLE, VariableType.INTEGER);
    reportData.getConfiguration().setAggregationType(AVERAGE);
    NumberResultDto evaluationResponse = reportClient.evaluateNumberReport(reportData).getResult();

    // then
    assertThat(evaluationResponse.getInstanceCount()).isEqualTo(2L);
    assertThat(evaluationResponse.getData()).isEqualTo(2.5);
  }

  @Test
  public void acrossSeveralProcessDefinitions() {
    // given
    Map<String, Object> variables = new HashMap<>();
    variables.put(TEST_VARIABLE, 1);
    deployAndStartSimpleProcessWithVariables(variables);
    variables.put(TEST_VARIABLE, 3);
    deployAndStartSimpleProcessWithVariables(variables);
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(TEST_VARIABLE, VariableType.INTEGER);
    reportData.setProcessDefinitionVersion(ALL_VERSIONS);
    reportData.getConfiguration().setAggregationType(AVERAGE);
    NumberResultDto evaluationResponse = reportClient.evaluateNumberReport(reportData).getResult();

    // then
    assertThat(evaluationResponse.getInstanceCount()).isEqualTo(2L);
    assertThat(evaluationResponse.getData()).isEqualTo(2.);
  }

  @Test
  public void onlyVariablesWithSameTypeAreConsidered() {
    // given
    Map<String, Object> variables = new HashMap<>();
    variables.put(TEST_VARIABLE, 1);
    final ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcessWithVariables(variables);
    variables.put(TEST_VARIABLE, 3.0);
    engineIntegrationExtension.startProcessInstance(processInstance.getDefinitionId(), variables);
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(TEST_VARIABLE, VariableType.INTEGER);
    reportData.getConfiguration().setAggregationType(AVERAGE);
    NumberResultDto evaluationResponse = reportClient.evaluateNumberReport(reportData).getResult();

    // then
    assertThat(evaluationResponse.getInstanceCount()).isEqualTo(2L);
    assertThat(evaluationResponse.getData()).isEqualTo(1.);
  }

  @ParameterizedTest
  @MethodSource("aggregationTypes")
  public void supportsAllAggregationTypes(final AggregationType aggregationType, final Double expectedResult) {
    // given
    Map<String, Object> variables = new HashMap<>();
    variables.put(TEST_VARIABLE, 1);
    final ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcessWithVariables(variables);
    variables.put(TEST_VARIABLE, 5);
    engineIntegrationExtension.startProcessInstance(processInstance.getDefinitionId(), variables);
    variables.put(TEST_VARIABLE, 6);
    engineIntegrationExtension.startProcessInstance(processInstance.getDefinitionId(), variables);
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(TEST_VARIABLE, VariableType.INTEGER);
    reportData.getConfiguration().setAggregationType(aggregationType);
    NumberResultDto evaluationResponse = reportClient.evaluateNumberReport(reportData).getResult();

    // then
    assertThat(evaluationResponse.getInstanceCount()).isEqualTo(3L);
    assertThat(evaluationResponse.getData()).isEqualTo(expectedResult);
  }

  private static Stream<Arguments> aggregationTypes() {
    return Stream.of(
      Arguments.of(MIN, 1.),
      Arguments.of(MAX, 6.),
      Arguments.of(MEDIAN, 5.),
      Arguments.of(AVERAGE, 4.),
      Arguments.of(SUM, 12.)
    );
  }

  @Test
  public void noResultForNonExistingVariable() {
    // when
    ProcessReportDataDto reportData = createReport(TEST_VARIABLE, VariableType.INTEGER);
    NumberResultDto evaluationResponse = reportClient.evaluateNumberReport(reportData).getResult();

    // then
    assertThat(evaluationResponse.getInstanceCount()).isEqualTo(0L);
    assertThat(evaluationResponse.getData()).isNull();
  }

  @ParameterizedTest
  @MethodSource("nonNumericVariableTypes")
  public void unsupportedVariableTypesThrowError(final VariableType variableType) {
    // when
    ProcessReportDataDto reportData = createReport(TEST_VARIABLE, variableType);
    Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void appliesFilter() {
    // given
    Map<String, Object> variables = new HashMap<>();
    variables.put(TEST_VARIABLE, 1);
    final ProcessDefinitionEngineDto definition = deploySimpleOneUserTasksDefinition();
    engineIntegrationExtension.startProcessInstance(definition.getId(), variables);
    engineIntegrationExtension.finishAllRunningUserTasks();
    variables.put(TEST_VARIABLE, 3);
    engineIntegrationExtension.startProcessInstance(definition.getId(), variables);
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(TEST_VARIABLE, VariableType.INTEGER);
    reportData.setFilter(ProcessFilterBuilder.filter().completedInstancesOnly().add().buildList());
    NumberResultDto evaluationResponse = reportClient.evaluateNumberReport(reportData).getResult();

    // then
    assertThat(evaluationResponse.getInstanceCount()).isEqualTo(1L);
    assertThat(evaluationResponse.getData()).isEqualTo(1.);
  }

  @ParameterizedTest
  @MethodSource("identityFilters")
  public void identityFilterAppliesToInstances(final List<ProcessFilterDto<?>> filtersToApply) {
    // given
    final ProcessDefinitionEngineDto definition = deploySimpleServiceTaskProcessAndGetDefinition();
    engineIntegrationExtension.startProcessInstance(definition.getId());
    engineIntegrationExtension.startProcessInstance(definition.getId());
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(TEST_VARIABLE, VariableType.INTEGER);
    reportData.setFilter(filtersToApply);
    NumberResultDto evaluationResponse = reportClient.evaluateNumberReport(reportData).getResult();

    // then
    assertThat(evaluationResponse.getInstanceCount()).isZero();
    assertThat(evaluationResponse.getInstanceCountWithoutFilters()).isEqualTo(2L);
  }

  private static Stream<VariableType> nonNumericVariableTypes() {
    return Arrays.stream(VariableType.values()).filter(type -> !VariableType.getNumericTypes().contains(type));
  }

  private Map<String, Object> createAllNumericVariables() {
    Map<String, Object> variables = new HashMap<>();
    variables.put("shortVar", (short) 2);
    variables.put("intVar", 5);
    variables.put("longVar", 10L);
    variables.put("doubleVar", 4.0);
    return variables;
  }

  private ProcessReportDataDto createReport(final String variableName, final VariableType variableType) {
    return TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(TEST_PROCESS)
      .setProcessDefinitionVersion("1")
      .setVariableName(variableName)
      .setVariableType(variableType)
      .setReportDataType(VARIABLE_AGGREGATION_GROUP_BY_NONE)
      .build();
  }
}
