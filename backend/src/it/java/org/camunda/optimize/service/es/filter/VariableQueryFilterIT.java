/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.filter;

import com.fasterxml.jackson.core.type.TypeReference;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessReportResultDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.dto.optimize.rest.report.ProcessReportEvaluationResultDto;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.ws.rs.core.Response;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.camunda.optimize.service.es.filter.FilterOperatorConstants.GREATER_THAN;
import static org.camunda.optimize.service.es.filter.FilterOperatorConstants.GREATER_THAN_EQUALS;
import static org.camunda.optimize.service.es.filter.FilterOperatorConstants.IN;
import static org.camunda.optimize.service.es.filter.FilterOperatorConstants.LESS_THAN;
import static org.camunda.optimize.service.es.filter.FilterOperatorConstants.LESS_THAN_EQUALS;
import static org.camunda.optimize.service.es.filter.FilterOperatorConstants.NOT_IN;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createProcessReportDataViewRawAsTable;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;


public class VariableQueryFilterIT {

  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();
  @Rule
  public RuleChain chain = RuleChain
    .outerRule(elasticSearchRule).around(engineRule).around(embeddedOptimizeRule);

  private final String TEST_DEFINITION_KEY = "testDefinition";

  private RawDataProcessReportResultDto evaluateReportAndReturnResult(final ProcessReportDataDto reportData) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      // @formatter:off
      .execute(new TypeReference<ProcessReportEvaluationResultDto<RawDataProcessReportResultDto>>() {})
      // @formatter:on
      .getResult();
  }

  private Response evaluateReportWithFilterAndGetResponse(String processDefinitionKey, List<ProcessFilterDto> filterList) {
    ProcessReportDataDto reportData = createProcessReportDataViewRawAsTable(processDefinitionKey, "1");
    reportData.setFilter(filterList);
    return evaluateReportAndReturnResponse(reportData);
  }

  private Response evaluateReportAndReturnResponse(ProcessReportDataDto reportData) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      .execute();
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
    ProcessReportDataDto reportData =
      createProcessReportDataViewRawAsTable(processDefinitionKey, processDefinitionVersion);
    reportData.setFilter(filter);
    return evaluateReportAndReturnResult(reportData);
  }

  @Test
  public void simpleVariableFilter() throws Exception {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", "value");
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", "anotherValue");
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

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
  public void severalVariablesInSameProcessInstanceShouldNotAffectFilter() throws Exception {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("stringVar", "aStringValue");
    variables.put("anotherStringVar", "anotherValue");
    variables.put("boolVar", true);
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

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
  public void stringEqualityFilterWithVariableOfDifferentType() throws Exception {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("stringVar", "aStringValue");
    variables.put("anotherStringVar", "anotherValue");
    variables.put("boolVar", true);
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

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
  public void stringInequalityFilterWithVariableOfDifferentTypeAndProcessInstance() throws Exception {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("stringVar", "aStringValue");
    variables.put("boolVar", true);
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    variables.put("anotherStringVar", "aStringValue");
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

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
  public void severalStringValueFiltersAreConcatenated() throws Exception {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    engineRule.startProcessInstance(processDefinition.getId());
    Map<String, Object> variables = new HashMap<>();
    variables.put("stringVar", "aStringValue");
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    variables.put("stringVar", "anotherValue");
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

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
  public void variablesWithDifferentNameAreFiltered() throws Exception {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("stringVar", "value");
    variables.put("anotherStringVar", "value");
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

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
  public void variablesWithDifferentTypeAreFiltered() throws Exception {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", "1");
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", 1);
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

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
  public void stringInequalityVariableFilter() throws Exception {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", "value");
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", "anotherValue");
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", "aThirdValue");
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

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
  public void multipleStringInequalityVariableFilter() throws Exception {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", "1");
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", "2");
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", "3");
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

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
  public void booleanTrueVariableFilter() throws Exception {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", true);
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", false);
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", false);
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

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
  public void booleanFalseVariableFilter() throws Exception {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", true);
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", true);
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", false);
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

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
  public void numericLessThanVariableFilter() throws Exception {
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();

    for (VariableType variableType : VariableType.getNumericTypes()) {
      // given
      Map<String, Object> variables = new HashMap<>();
      variables.put("var", changeNumericValueToType(1, variableType));
      engineRule.startProcessInstance(processDefinition.getId(), variables);
      variables.put("var", changeNumericValueToType(2, variableType));
      engineRule.startProcessInstance(processDefinition.getId(), variables);
      variables.put("var", changeNumericValueToType(10, variableType));
      engineRule.startProcessInstance(processDefinition.getId(), variables);
      embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
      elasticSearchRule.refreshAllOptimizeIndices();

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
    embeddedOptimizeRule.resetImportStartIndexes();
  }

  @Test
  public void multipleNumericEqualityVariableFilter() throws Exception {
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();

    // given
    for (VariableType variableType : VariableType.getNumericTypes()) {
      Map<String, Object> variables = new HashMap<>();
      variables.put("var", changeNumericValueToType(1, variableType));
      engineRule.startProcessInstance(processDefinition.getId(), variables);
      variables.put("var", changeNumericValueToType(2, variableType));
      engineRule.startProcessInstance(processDefinition.getId(), variables);
      variables.put("var", changeNumericValueToType(3, variableType));
      engineRule.startProcessInstance(processDefinition.getId(), variables);
      embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
      elasticSearchRule.refreshAllOptimizeIndices();

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
  public void multipleNumericInequalityVariableFilter() throws Exception {
    for (VariableType variableType : VariableType.getNumericTypes()) {
      // given
      ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();

      Map<String, Object> variables = new HashMap<>();
      variables.put("var", changeNumericValueToType(1, variableType));
      engineRule.startProcessInstance(processDefinition.getId(), variables);
      variables.put("var", changeNumericValueToType(2, variableType));
      engineRule.startProcessInstance(processDefinition.getId(), variables);
      variables.put("var", changeNumericValueToType(3, variableType));
      engineRule.startProcessInstance(processDefinition.getId(), variables);
      embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
      elasticSearchRule.refreshAllOptimizeIndices();

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
  public void numericLessThanEqualVariableFilter() throws Exception {

    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();

    for (VariableType variableType : VariableType.getNumericTypes()) {
      // given
      Map<String, Object> variables = new HashMap<>();
      variables.put("var", changeNumericValueToType(1, variableType));
      engineRule.startProcessInstance(processDefinition.getId(), variables);
      variables.put("var", changeNumericValueToType(2, variableType));
      engineRule.startProcessInstance(processDefinition.getId(), variables);
      variables.put("var", changeNumericValueToType(10, variableType));
      engineRule.startProcessInstance(processDefinition.getId(), variables);
      embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
      elasticSearchRule.refreshAllOptimizeIndices();

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
  public void numericGreaterThanVariableFilter() throws Exception {
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();

    for (VariableType variableType : VariableType.getNumericTypes()) {
      // given
      Map<String, Object> variables = new HashMap<>();
      variables.put("var", changeNumericValueToType(1, variableType));
      engineRule.startProcessInstance(processDefinition.getId(), variables);
      variables.put("var", changeNumericValueToType(2, variableType));
      engineRule.startProcessInstance(processDefinition.getId(), variables);
      variables.put("var", changeNumericValueToType(10, variableType));
      engineRule.startProcessInstance(processDefinition.getId(), variables);
      embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
      elasticSearchRule.refreshAllOptimizeIndices();

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
  public void numericGreaterThanEqualVariableFilter() throws Exception {
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();

    for (VariableType variableType : VariableType.getNumericTypes()) {
      // given
      Map<String, Object> variables = new HashMap<>();
      variables.put("var", changeNumericValueToType(1, variableType));
      engineRule.startProcessInstance(processDefinition.getId(), variables);
      variables.put("var", changeNumericValueToType(2, variableType));
      engineRule.startProcessInstance(processDefinition.getId(), variables);
      variables.put("var", changeNumericValueToType(10, variableType));
      engineRule.startProcessInstance(processDefinition.getId(), variables);
      embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
      elasticSearchRule.refreshAllOptimizeIndices();

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
  public void numericEqualVariableFilter() throws Exception {
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();

    for (VariableType variableType : VariableType.getNumericTypes()) {
      // given
      Map<String, Object> variables = new HashMap<>();
      variables.put("var", changeNumericValueToType(1, variableType));
      engineRule.startProcessInstance(processDefinition.getId(), variables);
      variables.put("var", changeNumericValueToType(2, variableType));
      engineRule.startProcessInstance(processDefinition.getId(), variables);
      variables.put("var", changeNumericValueToType(10, variableType));
      engineRule.startProcessInstance(processDefinition.getId(), variables);
      embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
      elasticSearchRule.refreshAllOptimizeIndices();

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
  public void numericUnequalVariableFilter() throws Exception {
    for (VariableType variableType : VariableType.getNumericTypes()) {
      // given
      ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();

      Map<String, Object> variables = new HashMap<>();
      variables.put("var", changeNumericValueToType(1, variableType));
      engineRule.startProcessInstance(processDefinition.getId(), variables);
      variables.put("var", changeNumericValueToType(2, variableType));
      engineRule.startProcessInstance(processDefinition.getId(), variables);
      variables.put("var", changeNumericValueToType(10, variableType));
      engineRule.startProcessInstance(processDefinition.getId(), variables);
      embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
      elasticSearchRule.refreshAllOptimizeIndices();

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
  public void numericWithinRangeVariableFilter() throws Exception {
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();

    for (VariableType variableType : VariableType.getNumericTypes()) {
      // given
      Map<String, Object> variables = new HashMap<>();
      variables.put("var", changeNumericValueToType(1, variableType));
      engineRule.startProcessInstance(processDefinition.getId(), variables);
      variables.put("var", changeNumericValueToType(2, variableType));
      engineRule.startProcessInstance(processDefinition.getId(), variables);
      variables.put("var", changeNumericValueToType(10, variableType));
      engineRule.startProcessInstance(processDefinition.getId(), variables);
      embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
      elasticSearchRule.refreshAllOptimizeIndices();

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
  public void numericOffRangeVariableFilter() throws Exception {
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();

    for (VariableType variableType : VariableType.getNumericTypes()) {
      // given
      Map<String, Object> variables = new HashMap<>();
      variables.put("var", changeNumericValueToType(1, variableType));
      engineRule.startProcessInstance(processDefinition.getId(), variables);
      variables.put("var", changeNumericValueToType(2, variableType));
      engineRule.startProcessInstance(processDefinition.getId(), variables);
      variables.put("var", changeNumericValueToType(10, variableType));
      engineRule.startProcessInstance(processDefinition.getId(), variables);
      embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
      elasticSearchRule.refreshAllOptimizeIndices();

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
      elasticSearchRule.cleanAndVerify();
    }
  }

  @Test
  public void dateLessThanOrEqualVariableFilter() throws Exception {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    final OffsetDateTime now = nowDate();
    variables.put("var", now.minusSeconds(2));
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", now.minusSeconds(1));
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", now);
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", now.plusSeconds(10));
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

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
  public void dateGreaterOrEqualThanVariableFilter() throws Exception {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    final OffsetDateTime now = nowDate();
    variables.put("var", now);
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", now.minusSeconds(2));
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", now.plusSeconds(10));
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

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
  public void dateEqualVariableFilter() throws Exception {
    // given
    final OffsetDateTime now = nowDate();
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", now);
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", now.minusSeconds(2));
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", now.plusSeconds(10));
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

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
  public void dateWithinRangeVariableFilter() throws Exception {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    OffsetDateTime now = nowDate();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", now);
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", now.minusSeconds(2));
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

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
  public void dateOffRangeVariableFilter() throws Exception {
    // given
    OffsetDateTime now = nowDate();
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", now);
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", now.minusSeconds(2));
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", now.plusSeconds(10));
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

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
    Response response = evaluateReportWithFilterAndGetResponse(TEST_DEFINITION_KEY, variableFilterDto);

    // then
    assertThat(response.getStatus(), is(500));
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
    Response response = evaluateReportWithFilterAndGetResponse(TEST_DEFINITION_KEY, variableFilterDto);

    // then
    assertThat(response.getStatus(), is(500));
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
    Response response = evaluateReportWithFilterAndGetResponse(TEST_DEFINITION_KEY, variableFilterDto);

    // then
    assertThat(response.getStatus(), is(500));
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
    Response response = evaluateReportWithFilterAndGetResponse(TEST_DEFINITION_KEY, variableFilterDto);

    // then
    assertThat(response.getStatus(), is(500));
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

  private ProcessDefinitionEngineDto deploySimpleProcessDefinition() throws IOException {
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess()
      .startEvent()
      .endEvent()
      .done();
    return engineRule.deployProcessAndGetProcessDefinition(modelInstance);
  }


}
