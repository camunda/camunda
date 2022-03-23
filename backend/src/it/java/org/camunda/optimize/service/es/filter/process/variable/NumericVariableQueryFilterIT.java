/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.filter.process.variable;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
import org.camunda.optimize.service.es.filter.process.AbstractFilterIT;
import org.camunda.optimize.test.it.extension.EngineVariableValue;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator.GREATER_THAN;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator.GREATER_THAN_EQUALS;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator.IN;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator.LESS_THAN;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator.LESS_THAN_EQUALS;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator.NOT_IN;

public class NumericVariableQueryFilterIT extends AbstractFilterIT {

  private static final String VARIABLE_NAME = "var";

  @ParameterizedTest
  @MethodSource("getNumericTypes")
  public void numericEqualVariableFilter(final VariableType variableType) {
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();

    // given
    Map<String, Object> variables = new HashMap<>();
    variables.put(VARIABLE_NAME, changeNumericValueToType(1, variableType));
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.put(VARIABLE_NAME, changeNumericValueToType(2, variableType));
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.put(VARIABLE_NAME, changeNumericValueToType(10, variableType));
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessFilterDto<?>> filter = ProcessFilterBuilder
      .filter()
      .variable()
      .type(variableType)
      .operator(IN)
      .values(Collections.singletonList("2"))
      .name(VARIABLE_NAME)
      .add()
      .buildList();

    ReportResultResponseDto<List<RawDataProcessInstanceDto>> result = evaluateReportWithFilter(
      processDefinition,
      filter
    );

    // then
    assertThat(result.getData()).hasSize(1);
  }

  private static Stream<Arguments> nullFilterScenarios() {
    return getNumericTypes().stream()
      .flatMap(type -> Stream.of(
        Arguments.of(type, IN, Collections.singletonList(null), 2),
        Arguments.of(type, IN, Lists.newArrayList(null, "100"), 3),
        Arguments.of(type, NOT_IN, Collections.singletonList(null), 2),
        Arguments.of(type, NOT_IN, Lists.newArrayList(null, "100"), 1)
      ));
  }

  @ParameterizedTest
  @MethodSource("nullFilterScenarios")
  public void numericFilterSupportsNullValue(final VariableType variableType,
                                             final FilterOperator operator,
                                             final List<String> filterValues,
                                             final Integer expectedInstanceCount) {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    // instance where the variable is undefined
    engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    // instance where the variable has the value null
    engineIntegrationExtension.startProcessInstance(
      processDefinition.getId(),
      Collections.singletonMap(VARIABLE_NAME, new EngineVariableValue(null, variableType.getId()))
    );
    engineIntegrationExtension.startProcessInstance(
      processDefinition.getId(), ImmutableMap.of(VARIABLE_NAME, changeNumericValueToType(100, variableType))
    );
    engineIntegrationExtension.startProcessInstance(
      processDefinition.getId(), ImmutableMap.of(VARIABLE_NAME, changeNumericValueToType(200, variableType))
    );

    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessFilterDto<?>> filter = ProcessFilterBuilder
      .filter()
      .variable()
      .type(variableType)
      .name(VARIABLE_NAME)
      .operator(operator)
      .values(filterValues)
      .add()
      .buildList();

    ReportResultResponseDto<List<RawDataProcessInstanceDto>> result = evaluateReportWithFilter(
      processDefinition,
      filter
    );

    // then
    assertThat(result.getData()).hasSize(expectedInstanceCount);
  }

  @ParameterizedTest
  @MethodSource("getNumericTypes")
  public void numericInequalityVariableFilter(final VariableType variableType) {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();

    Map<String, Object> variables = new HashMap<>();
    variables.put(VARIABLE_NAME, changeNumericValueToType(1, variableType));
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.put(VARIABLE_NAME, changeNumericValueToType(2, variableType));
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.put(VARIABLE_NAME, changeNumericValueToType(10, variableType));
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessFilterDto<?>> filter = ProcessFilterBuilder
      .filter()
      .variable()
      .type(variableType)
      .operator(NOT_IN)
      .values(Collections.singletonList("2"))
      .name(VARIABLE_NAME)
      .add()
      .buildList();

    ReportResultResponseDto<List<RawDataProcessInstanceDto>> result = evaluateReportWithFilter(
      processDefinition,
      filter
    );

    // then
    assertThat(result.getData()).hasSize(2);
  }

  @ParameterizedTest
  @MethodSource("getNumericTypes")
  public void multipleNumericEqualityVariableFilter(final VariableType variableType) {
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();

    // given
    Map<String, Object> variables = new HashMap<>();
    variables.put(VARIABLE_NAME, changeNumericValueToType(1, variableType));
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.put(VARIABLE_NAME, changeNumericValueToType(2, variableType));
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.put(VARIABLE_NAME, changeNumericValueToType(3, variableType));
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    importAllEngineEntitiesFromScratch();

    // when
    List<String> values = new ArrayList<>();
    values.add("1");
    values.add("2");

    List<ProcessFilterDto<?>> filter =
      ProcessFilterBuilder
        .filter()
        .variable()
        .name(VARIABLE_NAME)
        .values(values)
        .type(variableType)
        .operator(IN)
        .add()
        .buildList();

    ReportResultResponseDto<List<RawDataProcessInstanceDto>> result = evaluateReportWithFilter(
      processDefinition,
      filter
    );

    // then
    assertThat(result.getData()).hasSize(2);
  }

  @ParameterizedTest
  @MethodSource("getNumericTypes")
  public void multipleNumericInequalityVariableFilter(final VariableType variableType) {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();

    Map<String, Object> variables = new HashMap<>();
    variables.put(VARIABLE_NAME, changeNumericValueToType(1, variableType));
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.put(VARIABLE_NAME, changeNumericValueToType(2, variableType));
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.put(VARIABLE_NAME, changeNumericValueToType(3, variableType));
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    importAllEngineEntitiesFromScratch();

    // when
    List<String> values = new ArrayList<>();
    values.add("1");
    values.add("2");

    List<ProcessFilterDto<?>> filter =
      ProcessFilterBuilder
        .filter()
        .variable()
        .name(VARIABLE_NAME)
        .values(values)
        .type(variableType)
        .operator(NOT_IN)
        .add()
        .buildList();

    ReportResultResponseDto<List<RawDataProcessInstanceDto>> result = evaluateReportWithFilter(
      processDefinition,
      filter
    );

    // then
    assertThat(result.getData()).hasSize(1);
  }

  @ParameterizedTest
  @MethodSource("getNumericTypes")
  public void numericLessThanVariableFilter(final VariableType variableType) {
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();

    // given
    Map<String, Object> variables = new HashMap<>();
    variables.put(VARIABLE_NAME, changeNumericValueToType(1, variableType));
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.put(VARIABLE_NAME, changeNumericValueToType(2, variableType));
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.put(VARIABLE_NAME, changeNumericValueToType(10, variableType));
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessFilterDto<?>> filter =
      ProcessFilterBuilder
        .filter()
        .variable()
        .type(variableType)
        .name(VARIABLE_NAME)
        .operator(LESS_THAN)
        .values(Collections.singletonList("5"))
        .add()
        .buildList();

    ReportResultResponseDto<List<RawDataProcessInstanceDto>> result = evaluateReportWithFilter(
      processDefinition,
      filter
    );

    // then
    assertThat(result.getData()).hasSize(2);
  }

  @ParameterizedTest
  @MethodSource("getNumericTypes")
  public void numericLessThanEqualVariableFilter(final VariableType variableType) {
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();

    // given
    Map<String, Object> variables = new HashMap<>();
    variables.put(VARIABLE_NAME, changeNumericValueToType(1, variableType));
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.put(VARIABLE_NAME, changeNumericValueToType(2, variableType));
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.put(VARIABLE_NAME, changeNumericValueToType(10, variableType));
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessFilterDto<?>> filter =
      ProcessFilterBuilder
        .filter()
        .variable()
        .name(VARIABLE_NAME)
        .values(Collections.singletonList("2"))
        .type(variableType)
        .operator(LESS_THAN_EQUALS)
        .add()
        .buildList();

    ReportResultResponseDto<List<RawDataProcessInstanceDto>> result = evaluateReportWithFilter(
      processDefinition,
      filter
    );

    // then
    assertThat(result.getData()).hasSize(2);
  }

  @ParameterizedTest
  @MethodSource("getNumericTypes")
  public void numericGreaterThanVariableFilter(final VariableType variableType) {
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();

    // given
    Map<String, Object> variables = new HashMap<>();
    variables.put(VARIABLE_NAME, changeNumericValueToType(1, variableType));
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.put(VARIABLE_NAME, changeNumericValueToType(2, variableType));
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.put(VARIABLE_NAME, changeNumericValueToType(10, variableType));
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessFilterDto<?>> filter = ProcessFilterBuilder
      .filter()
      .variable()
      .type(variableType)
      .operator(GREATER_THAN)
      .values(Collections.singletonList("1"))
      .name(VARIABLE_NAME)
      .add()
      .buildList();

    ReportResultResponseDto<List<RawDataProcessInstanceDto>> result = evaluateReportWithFilter(
      processDefinition,
      filter
    );

    // then
    assertThat(result.getData()).hasSize(2);
  }

  @ParameterizedTest
  @MethodSource("getNumericTypes")
  public void numericGreaterThanEqualVariableFilter(final VariableType variableType) {
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();

    // given
    Map<String, Object> variables = new HashMap<>();
    variables.put(VARIABLE_NAME, changeNumericValueToType(1, variableType));
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.put(VARIABLE_NAME, changeNumericValueToType(2, variableType));
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.put(VARIABLE_NAME, changeNumericValueToType(10, variableType));
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessFilterDto<?>> filter = ProcessFilterBuilder
      .filter()
      .variable()
      .type(variableType)
      .operator(GREATER_THAN_EQUALS)
      .values(Collections.singletonList("2"))
      .name(VARIABLE_NAME)
      .add()
      .buildList();

    ReportResultResponseDto<List<RawDataProcessInstanceDto>> result = evaluateReportWithFilter(
      processDefinition,
      filter
    );

    // then
    assertThat(result.getData()).hasSize(2);
  }

  @ParameterizedTest
  @MethodSource("getNumericTypes")
  public void numericWithinRangeVariableFilter(final VariableType variableType) {
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();

    // given
    Map<String, Object> variables = new HashMap<>();
    variables.put(VARIABLE_NAME, changeNumericValueToType(1, variableType));
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.put(VARIABLE_NAME, changeNumericValueToType(2, variableType));
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.put(VARIABLE_NAME, changeNumericValueToType(10, variableType));
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessFilterDto<?>> filters = ProcessFilterBuilder
      .filter()
      .variable()
      .type(variableType)
      .operator(GREATER_THAN)
      .values(Collections.singletonList("1"))
      .name(VARIABLE_NAME)
      .add()
      .variable()
      .name(VARIABLE_NAME)
      .type(variableType)
      .values(Collections.singletonList("10"))
      .operator(LESS_THAN)
      .add()
      .buildList();


    ReportResultResponseDto<List<RawDataProcessInstanceDto>> result =
      evaluateReportWithFilter(processDefinition.getKey(), String.valueOf(processDefinition.getVersion()), filters);

    // then
    assertThat(result.getData()).hasSize(1);
  }

  @ParameterizedTest
  @MethodSource("getNumericTypes")
  public void numericOffRangeVariableFilter(final VariableType variableType) {
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();

    // given
    Map<String, Object> variables = new HashMap<>();
    variables.put(VARIABLE_NAME, changeNumericValueToType(1, variableType));
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.put(VARIABLE_NAME, changeNumericValueToType(2, variableType));
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.put(VARIABLE_NAME, changeNumericValueToType(10, variableType));
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessFilterDto<?>> filters = ProcessFilterBuilder
      .filter()
      .variable()
      .type(variableType)
      .operator(GREATER_THAN)
      .values(Collections.singletonList("2"))
      .name(VARIABLE_NAME)
      .add()
      .variable()
      .name(VARIABLE_NAME)
      .type(variableType)
      .values(Collections.singletonList("2"))
      .operator(LESS_THAN)
      .add()
      .buildList();

    ReportResultResponseDto<List<RawDataProcessInstanceDto>> result =
      evaluateReportWithFilter(processDefinition.getKey(), String.valueOf(processDefinition.getVersion()), filters);
    // then
    assertThat(result.getData()).isEmpty();
    elasticSearchIntegrationTestExtension.cleanAndVerify();
  }

  @ParameterizedTest
  @MethodSource("getRelativeOperators")
  public void resultFilterByNumericVariableValueNullFailsForRelativeOperators(final FilterOperator operator) {
    // given
    deploySimpleProcessDefinition();
    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessFilterDto<?>> filter = ProcessFilterBuilder
      .filter()
      .variable()
      .doubleType()
      .operator(operator)
      .values(Collections.singletonList(null))
      .name(VARIABLE_NAME)
      .add()
      .buildList();
    final Response evaluateHttpResponse = evaluateReportWithFilterAndGetResponse(filter);

    // then
    assertThat(evaluateHttpResponse.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  private Object changeNumericValueToType(int value, VariableType type) {
    switch (type) {
      case INTEGER:
        return value;
      case LONG:
        return (long) value;
      case SHORT:
        return (short) value;
      case DOUBLE:
        return (double) value;
    }
    return value;
  }

  private Response evaluateReportWithFilterAndGetResponse(List<ProcessFilterDto<?>> filterList) {
    final String TEST_DEFINITION_KEY = "testDefinition";
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(TEST_DEFINITION_KEY)
      .setProcessDefinitionVersion("1")
      .setReportDataType(ProcessReportDataType.RAW_DATA)
      .build();
    reportData.setFilter(filterList);
    return reportClient.evaluateReportAndReturnResponse(reportData);
  }

  private static Set<VariableType> getNumericTypes() {
    return VariableType.getNumericTypes();
  }

  private static Set<FilterOperator> getRelativeOperators() {
    return FilterOperator.RELATIVE_OPERATORS;
  }
}
