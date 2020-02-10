/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.filter;

import org.apache.http.HttpStatus;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.FilterOperatorConstants;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessReportResultDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.test.it.extension.EngineVariableValue;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.camunda.optimize.dto.optimize.query.report.FilterOperatorConstants.GREATER_THAN;
import static org.camunda.optimize.dto.optimize.query.report.FilterOperatorConstants.GREATER_THAN_EQUALS;
import static org.camunda.optimize.dto.optimize.query.report.FilterOperatorConstants.IN;
import static org.camunda.optimize.dto.optimize.query.report.FilterOperatorConstants.LESS_THAN;
import static org.camunda.optimize.dto.optimize.query.report.FilterOperatorConstants.LESS_THAN_EQUALS;
import static org.camunda.optimize.dto.optimize.query.report.FilterOperatorConstants.NOT_IN;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNull.notNullValue;

public class VariableQueryFilterIT extends AbstractFilterIT {

  @Test
  public void simpleVariableFilter() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", "value");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", "anotherValue");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    List<ProcessFilterDto> filter = ProcessFilterBuilder
      .filter()
      .variable()
      .name("var")
      .stringType()
      .values(Collections.singletonList("value"))
      .operator(IN)
      .add()
      .buildList();
    RawDataProcessReportResultDto result = evaluateReportWithFilter(processDefinition, filter);

    // then
    assertResults(result, 1);
  }

  @Test
  public void severalVariablesInSameProcessInstanceShouldNotAffectFilter() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("stringVar", "aStringValue");
    variables.put("anotherStringVar", "anotherValue");
    variables.put("boolVar", true);
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    List<ProcessFilterDto> filter = ProcessFilterBuilder
      .filter()
      .variable()
      .name("stringVar")
      .stringType()
      .values(Collections.singletonList("aStringValue"))
      .operator(NOT_IN)
      .add()
      .buildList();

    RawDataProcessReportResultDto result = evaluateReportWithFilter(processDefinition, filter);

    // then
    assertResults(result, 0);
  }

  @Test
  public void stringEqualityFilterWithVariableOfDifferentType() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("stringVar", "aStringValue");
    variables.put("anotherStringVar", "anotherValue");
    variables.put("boolVar", true);
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    List<ProcessFilterDto> filter = ProcessFilterBuilder
      .filter()
      .variable()
      .stringType()
      .name("stringVar")
      .values(Collections.singletonList("aStringValue"))
      .operator(IN)
      .add()
      .buildList();

    RawDataProcessReportResultDto result = evaluateReportWithFilter(processDefinition, filter);

    // then
    assertResults(result, 1);
  }

  @Test
  public void stringInequalityFilterWithVariableOfDifferentTypeAndProcessInstance() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("stringVar", "aStringValue");
    variables.put("boolVar", true);
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.put("anotherStringVar", "aStringValue");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    List<ProcessFilterDto> filter = ProcessFilterBuilder
      .filter()
      .variable()
      .stringType()
      .name("anotherStringVar")
      .operator(NOT_IN)
      .values(Collections.singletonList("aStringValue"))
      .add()
      .buildList();

    RawDataProcessReportResultDto result = evaluateReportWithFilter(processDefinition, filter);

    // then
    assertResults(result, 2);
  }

  @Test
  public void severalStringValueFiltersAreConcatenated() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    Map<String, Object> variables = new HashMap<>();
    variables.put("stringVar", "aStringValue");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.put("stringVar", "anotherValue");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when

    List<String> values = new ArrayList<>();
    values.add("aStringValue");
    values.add("anotherValue");
    List<ProcessFilterDto> filter = ProcessFilterBuilder
      .filter()
      .variable()
      .stringType()
      .name("stringVar")
      .operator(IN)
      .values(values)
      .add()
      .buildList();

    RawDataProcessReportResultDto result = evaluateReportWithFilter(processDefinition, filter);

    // then
    assertResults(result, 2);
  }

  @Test
  public void variablesWithDifferentNameAreFiltered() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("stringVar", "value");
    variables.put("anotherStringVar", "value");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    List<ProcessFilterDto> filter = ProcessFilterBuilder
      .filter()
      .variable()
      .stringType()
      .name("stringVar")
      .operator(IN)
      .values(Collections.singletonList("value"))
      .add()
      .buildList();
    RawDataProcessReportResultDto result = evaluateReportWithFilter(processDefinition, filter);

    // then
    assertResults(result, 1);
  }

  @Test
  public void variablesWithDifferentTypeAreFiltered() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", "1");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", 1);
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    List<ProcessFilterDto> filter = ProcessFilterBuilder
      .filter()
      .variable()
      .name("var")
      .stringType()
      .values(Collections.singletonList("1"))
      .operator(IN)
      .add()
      .buildList();
    RawDataProcessReportResultDto result = evaluateReportWithFilter(processDefinition, filter);

    // then
    assertResults(result, 1);
  }

  @Test
  public void stringInequalityVariableFilter() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", "value");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", "anotherValue");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", "aThirdValue");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    List<ProcessFilterDto> filter = ProcessFilterBuilder
      .filter()
      .variable()
      .name("var")
      .stringType()
      .values(Collections.singletonList("value"))
      .operator(NOT_IN)
      .add()
      .buildList();

    RawDataProcessReportResultDto result = evaluateReportWithFilter(processDefinition, filter);

    // then
    assertResults(result, 2);
  }

  @Test
  public void multipleStringInequalityVariableFilter() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", "1");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", "2");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", "3");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    List<String> values = new ArrayList<>();
    values.add("2");
    values.add("1");

    List<ProcessFilterDto> filter = ProcessFilterBuilder
      .filter()
      .variable()
      .name("var")
      .stringType()
      .values(values)
      .operator(NOT_IN)
      .add()
      .buildList();

    RawDataProcessReportResultDto result = evaluateReportWithFilter(processDefinition, filter);

    // then
    assertResults(result, 1);
  }

  @Test
  public void booleanTrueVariableFilter() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", true);
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", false);
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", false);
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    List<ProcessFilterDto> filter = ProcessFilterBuilder
      .filter()
      .variable()
      .name("var")
      .booleanFalse()
      .add()
      .buildList();

    RawDataProcessReportResultDto result = evaluateReportWithFilter(processDefinition, filter);

    // then
    assertResults(result, 2);
  }

  @Test
  public void booleanFalseVariableFilter() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", true);
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", true);
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", false);
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    List<ProcessFilterDto> filter = ProcessFilterBuilder
      .filter()
      .variable()
      .name("var")
      .booleanTrue()
      .add()
      .buildList();
    RawDataProcessReportResultDto result = evaluateReportWithFilter(processDefinition, filter);

    // then
    assertResults(result, 2);
  }

  @Test
  public void numericLessThanVariableFilter() {
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();

    for (VariableType variableType : VariableType.getNumericTypes()) {
      // given
      Map<String, Object> variables = new HashMap<>();
      variables.put("var", changeNumericValueToType(1, variableType));
      engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
      variables.put("var", changeNumericValueToType(2, variableType));
      engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
      variables.put("var", changeNumericValueToType(10, variableType));
      engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
      embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
      elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

      // when
      List<ProcessFilterDto> filter =
        ProcessFilterBuilder
          .filter()
          .variable()
          .type(variableType)
          .name("var")
          .operator(LESS_THAN)
          .values(Collections.singletonList("5"))
          .add()
          .buildList();

      RawDataProcessReportResultDto result = evaluateReportWithFilter(processDefinition, filter);

      // then
      assertResults(result, 2);

      resetIndexesAndClean();
    }
  }

  private void resetIndexesAndClean() {
    embeddedOptimizeExtension.resetImportStartIndexes();
  }

  @Test
  public void multipleNumericEqualityVariableFilter() {
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();

    // given
    for (VariableType variableType : VariableType.getNumericTypes()) {
      Map<String, Object> variables = new HashMap<>();
      variables.put("var", changeNumericValueToType(1, variableType));
      engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
      variables.put("var", changeNumericValueToType(2, variableType));
      engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
      variables.put("var", changeNumericValueToType(3, variableType));
      engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
      embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
      elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

      // when
      List<String> values = new ArrayList<>();
      values.add("1");
      values.add("2");

      List<ProcessFilterDto> filter =
        ProcessFilterBuilder
          .filter()
          .variable()
          .name("var")
          .values(values)
          .type(variableType)
          .operator(IN)
          .add()
          .buildList();

      RawDataProcessReportResultDto result = evaluateReportWithFilter(processDefinition, filter);

      // then
      assertResults(result, 2);

      resetIndexesAndClean();
    }
  }

  @Test
  public void multipleNumericInequalityVariableFilter() {
    for (VariableType variableType : VariableType.getNumericTypes()) {
      // given
      ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();

      Map<String, Object> variables = new HashMap<>();
      variables.put("var", changeNumericValueToType(1, variableType));
      engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
      variables.put("var", changeNumericValueToType(2, variableType));
      engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
      variables.put("var", changeNumericValueToType(3, variableType));
      engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
      embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
      elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

      // when
      List<String> values = new ArrayList<>();
      values.add("1");
      values.add("2");

      List<ProcessFilterDto> filter =
        ProcessFilterBuilder
          .filter()
          .variable()
          .name("var")
          .values(values)
          .type(variableType)
          .operator(NOT_IN)
          .add()
          .buildList();

      RawDataProcessReportResultDto result = evaluateReportWithFilter(processDefinition, filter);

      // then
      assertResults(result, 1);

      resetIndexesAndClean();
    }

  }

  @Test
  public void numericLessThanEqualVariableFilter() {
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();

    for (VariableType variableType : VariableType.getNumericTypes()) {
      // given
      Map<String, Object> variables = new HashMap<>();
      variables.put("var", changeNumericValueToType(1, variableType));
      engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
      variables.put("var", changeNumericValueToType(2, variableType));
      engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
      variables.put("var", changeNumericValueToType(10, variableType));
      engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
      embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
      elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

      // when

      List<ProcessFilterDto> filter =
        ProcessFilterBuilder
          .filter()
          .variable()
          .name("var")
          .values(Collections.singletonList("2"))
          .type(variableType)
          .operator(LESS_THAN_EQUALS)
          .add()
          .buildList();

      RawDataProcessReportResultDto result = evaluateReportWithFilter(processDefinition, filter);

      // then
      assertResults(result, 2);

      resetIndexesAndClean();
    }
  }

  @Test
  public void numericGreaterThanVariableFilter() {
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();

    for (VariableType variableType : VariableType.getNumericTypes()) {
      // given
      Map<String, Object> variables = new HashMap<>();
      variables.put("var", changeNumericValueToType(1, variableType));
      engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
      variables.put("var", changeNumericValueToType(2, variableType));
      engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
      variables.put("var", changeNumericValueToType(10, variableType));
      engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
      embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
      elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

      // when
      List<ProcessFilterDto> filter = ProcessFilterBuilder
        .filter()
        .variable()
        .type(variableType)
        .operator(GREATER_THAN)
        .values(Collections.singletonList("1"))
        .name("var")
        .add()
        .buildList();

      RawDataProcessReportResultDto result = evaluateReportWithFilter(processDefinition, filter);

      // then
      assertResults(result, 2);

      resetIndexesAndClean();
    }
  }

  @Test
  public void numericGreaterThanEqualVariableFilter() {
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();

    for (VariableType variableType : VariableType.getNumericTypes()) {
      // given
      Map<String, Object> variables = new HashMap<>();
      variables.put("var", changeNumericValueToType(1, variableType));
      engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
      variables.put("var", changeNumericValueToType(2, variableType));
      engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
      variables.put("var", changeNumericValueToType(10, variableType));
      engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
      embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
      elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

      // when
      List<ProcessFilterDto> filter = ProcessFilterBuilder
        .filter()
        .variable()
        .type(variableType)
        .operator(GREATER_THAN_EQUALS)
        .values(Collections.singletonList("2"))
        .name("var")
        .add()
        .buildList();

      RawDataProcessReportResultDto result = evaluateReportWithFilter(processDefinition, filter);

      // then
      assertResults(result, 2);

      resetIndexesAndClean();
    }
  }

  @Test
  public void numericEqualVariableFilter() {
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();

    for (VariableType variableType : VariableType.getNumericTypes()) {
      // given
      Map<String, Object> variables = new HashMap<>();
      variables.put("var", changeNumericValueToType(1, variableType));
      engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
      variables.put("var", changeNumericValueToType(2, variableType));
      engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
      variables.put("var", changeNumericValueToType(10, variableType));
      engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
      embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
      elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

      // when
      List<ProcessFilterDto> filter = ProcessFilterBuilder
        .filter()
        .variable()
        .type(variableType)
        .operator(IN)
        .values(Collections.singletonList("2"))
        .name("var")
        .add()
        .buildList();

      RawDataProcessReportResultDto result = evaluateReportWithFilter(processDefinition, filter);

      // then
      assertResults(result, 1);

      resetIndexesAndClean();
    }
  }

  @Test
  public void numericUnequalVariableFilter() {
    for (VariableType variableType : VariableType.getNumericTypes()) {
      // given
      ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();

      Map<String, Object> variables = new HashMap<>();
      variables.put("var", changeNumericValueToType(1, variableType));
      engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
      variables.put("var", changeNumericValueToType(2, variableType));
      engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
      variables.put("var", changeNumericValueToType(10, variableType));
      engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
      embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
      elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

      // when
      List<ProcessFilterDto> filter = ProcessFilterBuilder
        .filter()
        .variable()
        .type(variableType)
        .operator(NOT_IN)
        .values(Collections.singletonList("2"))
        .name("var")
        .add()
        .buildList();

      RawDataProcessReportResultDto result = evaluateReportWithFilter(processDefinition, filter);

      // then
      assertResults(result, 2);

      resetIndexesAndClean();
    }
  }

  @Test
  public void numericWithinRangeVariableFilter() {
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();

    for (VariableType variableType : VariableType.getNumericTypes()) {
      // given
      Map<String, Object> variables = new HashMap<>();
      variables.put("var", changeNumericValueToType(1, variableType));
      engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
      variables.put("var", changeNumericValueToType(2, variableType));
      engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
      variables.put("var", changeNumericValueToType(10, variableType));
      engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
      embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
      elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

      // when
      List<ProcessFilterDto> filters = ProcessFilterBuilder
        .filter()
        .variable()
        .type(variableType)
        .operator(GREATER_THAN)
        .values(Collections.singletonList("1"))
        .name("var")
        .add()
        .variable()
        .name("var")
        .type(variableType)
        .values(Collections.singletonList("10"))
        .operator(LESS_THAN)
        .add()
        .buildList();


      RawDataProcessReportResultDto result =
        evaluateReportWithFilter(processDefinition.getKey(), String.valueOf(processDefinition.getVersion()), filters);

      // then
      assertResults(result, 1);

      resetIndexesAndClean();
    }
  }

  @Test
  public void numericOffRangeVariableFilter() {
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();

    for (VariableType variableType : VariableType.getNumericTypes()) {
      // given
      Map<String, Object> variables = new HashMap<>();
      variables.put("var", changeNumericValueToType(1, variableType));
      engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
      variables.put("var", changeNumericValueToType(2, variableType));
      engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
      variables.put("var", changeNumericValueToType(10, variableType));
      engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
      embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
      elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

      // when
      List<ProcessFilterDto> filters = ProcessFilterBuilder
        .filter()
        .variable()
        .type(variableType)
        .operator(GREATER_THAN)
        .values(Collections.singletonList("2"))
        .name("var")
        .add()
        .variable()
        .name("var")
        .type(variableType)
        .values(Collections.singletonList("2"))
        .operator(LESS_THAN)
        .add()
        .buildList();

      RawDataProcessReportResultDto result =
        evaluateReportWithFilter(processDefinition.getKey(), String.valueOf(processDefinition.getVersion()), filters);
      // then
      assertResults(result, 0);
      elasticSearchIntegrationTestExtension.cleanAndVerify();
    }
  }

  @Test
  public void dateLessThanOrEqualVariableFilter() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    final OffsetDateTime now = nowDate();
    variables.put("var", now.minusSeconds(2));
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", now.minusSeconds(1));
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", now);
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", now.plusSeconds(10));
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    List<ProcessFilterDto> filter =
      ProcessFilterBuilder
        .filter()
        .variable()
        .dateType()
        .start(null)
        .end(now)
        .name("var")
        .add()
        .buildList();

    RawDataProcessReportResultDto result = evaluateReportWithFilter(processDefinition, filter);

    // then
    assertResults(result, 3);
  }

  @Test
  public void dateGreaterOrEqualThanVariableFilter() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    final OffsetDateTime now = nowDate();
    variables.put("var", now);
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", now.minusSeconds(2));
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", now.plusSeconds(10));
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    List<ProcessFilterDto> filter =
      ProcessFilterBuilder
        .filter()
        .variable()
        .dateType()
        .start(now.minusSeconds(1))
        .end(null)
        .name("var")
        .add()
        .buildList();

    RawDataProcessReportResultDto result = evaluateReportWithFilter(processDefinition, filter);

    // then
    assertResults(result, 2);
  }

  @Test
  public void dateEqualVariableFilter() {
    // given
    final OffsetDateTime now = nowDate();
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", now);
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", now.minusSeconds(2));
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", now.plusSeconds(10));
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    List<ProcessFilterDto> filter =
      ProcessFilterBuilder
        .filter()
        .variable()
        .dateType()
        .start(now)
        .end(now)
        .name("var")
        .add()
        .buildList();

    RawDataProcessReportResultDto result = evaluateReportWithFilter(processDefinition, filter);

    // then
    assertResults(result, 1);
  }

  @Test
  public void dateWithinRangeVariableFilter() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    OffsetDateTime now = nowDate();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", now);
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", now.minusSeconds(2));
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    List<ProcessFilterDto> filter =
      ProcessFilterBuilder
        .filter()
        .variable()
        .dateType()
        .start(now.minusSeconds(1))
        .end(now.plusSeconds(10))
        .name("var")
        .add()
        .buildList();

    RawDataProcessReportResultDto result = evaluateReportWithFilter(processDefinition, filter);

    // then
    assertResults(result, 1);
  }

  @Test
  public void dateOffRangeVariableFilter() {
    // given
    OffsetDateTime now = nowDate();
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", now);
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", now.minusSeconds(2));
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", now.plusSeconds(10));
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    List<ProcessFilterDto> filters =
      ProcessFilterBuilder
        .filter()
        .variable()
        .dateType()
        .start(now.minusSeconds(2))
        .end(now.minusSeconds(1))
        .name("var")
        .add()
        .variable()
        .dateType()
        .name("var")
        .start(now.plusSeconds(1))
        .end(now.plusSeconds(2))
        .add()
        .buildList();

    RawDataProcessReportResultDto result =
      evaluateReportWithFilter(processDefinition.getKey(), String.valueOf(processDefinition.getVersion()), filters);

    // then
    assertResults(result, 0);
  }

  @Test
  public void filterForUndefinedOverwritesOtherFilterData() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();

    Map<String, Object> variables = new HashMap<>();
    variables.put("testVar", "withValue");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);

    variables.put("testVar", null);
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);

    variables.put("testVar", new EngineVariableValue(null, "String"));
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);

    engineIntegrationExtension.startProcessInstance(processDefinition.getId());

    variables = new HashMap<>();
    variables.put("differentStringValue", "test");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    List<ProcessFilterDto> filters =
      ProcessFilterBuilder
        .filter()
        .variable()
        .stringType()
        .filterForUndefined()
        .name("testVar")
        .values(Collections.singletonList("withValue"))
        .operator(FilterOperatorConstants.IN)
        .add()
        .buildList();

    RawDataProcessReportResultDto result =
      evaluateReportWithFilter(processDefinition.getKey(), String.valueOf(processDefinition.getVersion()), filters);

    // then
    assertResults(result, 4);
  }

  private Map<String, VariableType> createVarNameToTypeMap() {
    Map<String, VariableType> varToType = new HashMap<>();
    varToType.put("dateVar", VariableType.DATE);
    varToType.put("boolVar", VariableType.BOOLEAN);
    varToType.put("shortVar", VariableType.SHORT);
    varToType.put("intVar", VariableType.INTEGER);
    varToType.put("longVar", VariableType.LONG);
    varToType.put("doubleVar", VariableType.DOUBLE);
    varToType.put("stringVar", VariableType.STRING);
    return varToType;
  }

  @Test
  public void filterForUndefinedAndNullWorksWithAllVariableTypes() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();

    Map<String, VariableType> varNameToTypeMap = createVarNameToTypeMap();
    Map<String, Object> variables = new HashMap<>();
    variables.put("dateVar", OffsetDateTime.now());
    variables.put("boolVar", true);
    variables.put("shortVar", (short) 2);
    variables.put("intVar", 5);
    variables.put("longVar", 5L);
    variables.put("doubleVar", 5.5);
    variables.put("stringVar", "aString");

    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);

    variables = new HashMap<>();
    variables.put("dateVar", null);
    variables.put("boolVar", null);
    variables.put("shortVar", null);
    variables.put("intVar", null);
    variables.put("longVar", null);
    variables.put("doubleVar", null);
    variables.put("stringVar", null);
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);

    variables = new HashMap<>();
    variables.put("dateVar", new EngineVariableValue(null, "Date"));
    variables.put("boolVar", new EngineVariableValue(null, "Boolean"));
    variables.put("shortVar", new EngineVariableValue(null, "Short"));
    variables.put("intVar", new EngineVariableValue(null, "Integer"));
    variables.put("longVar", new EngineVariableValue(null, "Long"));
    variables.put("doubleVar", new EngineVariableValue(null, "Double"));
    variables.put("stringVar", new EngineVariableValue(null, "String"));
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);

    engineIntegrationExtension.startProcessInstance(processDefinition.getId());

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();


    for (Map.Entry<String, Object> entry : variables.entrySet()) {
      // when
      VariableType variableType = varNameToTypeMap.get(entry.getKey());

      List<ProcessFilterDto> filters =
        ProcessFilterBuilder
          .filter()
          .variable()
          .name(entry.getKey())
          .type(variableType)
          .filterForUndefined()
          .add()
          .buildList();

      RawDataProcessReportResultDto result =
        evaluateReportWithFilter(processDefinition.getKey(), String.valueOf(processDefinition.getVersion()), filters);

      // then
      assertThat(result.getData(), is(notNullValue()));
      final List<RawDataProcessInstanceDto> resultData = result.getData();
      assertThat(resultData.size(), is(3));
    }
  }

  @Test
  public void validationExceptionOnNullValueField() {
    //given
    List<ProcessFilterDto> variableFilterDto = ProcessFilterBuilder.filter()
      .variable()
      .booleanType()
      .values(null)
      .name("foo")
      .add()
      .buildList();

    // when
    Response response = evaluateReportWithFilterAndGetResponse(variableFilterDto);

    // then
    assertThat(response.getStatus(), is(Response.Status.BAD_REQUEST.getStatusCode()));
  }

  @Test
  public void validationExceptionOnNullOnAllDateValueField() {
    //given
    List<ProcessFilterDto> variableFilterDto = ProcessFilterBuilder.filter()
      .variable()
      .dateType()
      .start(null)
      .end(null)
      .name("foo")
      .add()
      .buildList();

    // when
    Response response = evaluateReportWithFilterAndGetResponse(variableFilterDto);

    // then
    assertThat(response.getStatus(), is(Response.Status.BAD_REQUEST.getStatusCode()));
  }

  @Test
  public void validationExceptionOnNullNumericValuesField() {
    //given
    List<ProcessFilterDto> variableFilterDto = ProcessFilterBuilder
      .filter()
      .variable()
      .longType()
      .operator(IN)
      .values(null)
      .name("foo")
      .add()
      .buildList();

    // when
    Response response = evaluateReportWithFilterAndGetResponse(variableFilterDto);

    // then
    assertThat(response.getStatus(), is(Response.Status.BAD_REQUEST.getStatusCode()));
  }

  @Test
  public void validationExceptionOnNullNameField() {
    //given
    List<ProcessFilterDto> variableFilterDto = ProcessFilterBuilder.filter()
      .variable()
      .booleanTrue()
      .name(null)
      .add()
      .buildList();

    // when
    Response response = evaluateReportWithFilterAndGetResponse(variableFilterDto);

    // then
    assertThat(response.getStatus(), is(Response.Status.BAD_REQUEST.getStatusCode()));
  }


  private OffsetDateTime nowDate() {
    return OffsetDateTime.now();
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

  private void assertResults(RawDataProcessReportResultDto report, int piCount) {
    assertThat("PI count", report.getData().size(), is(piCount));
  }

  private ProcessDefinitionEngineDto deploySimpleProcessDefinition() {
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess()
      .startEvent()
      .endEvent()
      .done();
    return engineIntegrationExtension.deployProcessAndGetProcessDefinition(modelInstance);
  }

  private Response evaluateReportWithFilterAndGetResponse(List<ProcessFilterDto> filterList) {
    final String TEST_DEFINITION_KEY = "testDefinition";
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(TEST_DEFINITION_KEY)
      .setProcessDefinitionVersion("1")
      .setReportDataType(ProcessReportDataType.RAW_DATA)
      .build();
    reportData.setFilter(filterList);
    return evaluateReportAndReturnResponse(reportData);
  }

  private RawDataProcessReportResultDto evaluateReportWithFilter(ProcessDefinitionEngineDto processDefinition,
                                                                 List<ProcessFilterDto> filterList) {
    return this.evaluateReportWithFilter(
      processDefinition.getKey(),
      String.valueOf(processDefinition.getVersion()),
      filterList
    );
  }

  private RawDataProcessReportResultDto evaluateReportWithFilter(String processDefinitionKey,
                                                                 String processDefinitionVersion,
                                                                 List<ProcessFilterDto> filter) {
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersion(processDefinitionVersion)
      .setReportDataType(ProcessReportDataType.RAW_DATA)
      .setFilter(filter)
      .build();
    return evaluateReportAndReturnResult(reportData);
  }

}
