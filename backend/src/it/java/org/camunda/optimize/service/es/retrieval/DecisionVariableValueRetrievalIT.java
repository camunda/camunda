/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.retrieval;

import org.camunda.optimize.dto.engine.DecisionDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.InputVariableEntry;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.service.es.report.decision.AbstractDecisionDefinitionIT;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;
import static org.camunda.optimize.rest.DecisionVariablesRestService.DECISION_DEFINITION_KEY;
import static org.camunda.optimize.rest.DecisionVariablesRestService.DECISION_DEFINITION_VERSION;
import static org.camunda.optimize.rest.DecisionVariablesRestService.NUM_RESULTS;
import static org.camunda.optimize.rest.DecisionVariablesRestService.RESULT_OFFSET;
import static org.camunda.optimize.rest.DecisionVariablesRestService.VALUE_FILTER;
import static org.camunda.optimize.rest.DecisionVariablesRestService.VARIABLE_ID;
import static org.camunda.optimize.rest.DecisionVariablesRestService.VARIABLE_TYPE;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;


public class DecisionVariableValueRetrievalIT extends AbstractDecisionDefinitionIT {

  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  @Rule
  public RuleChain chain = RuleChain
    .outerRule(elasticSearchRule).around(engineRule).around(embeddedOptimizeRule);

  @Test
  public void getInputVariableValues() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto = engineRule.deployDecisionDefinition();
    startDecisionInstanceWithInputs(decisionDefinitionDto, 200.0, "Misc");
    startDecisionInstanceWithInputs(decisionDefinitionDto, 300.0, "Travel Expenses");
    startDecisionInstanceWithInputs(decisionDefinitionDto, 500.0, "somethingElse");

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    List<String> amountInputVariableValues = getInputVariableValues(
      decisionDefinitionDto, INPUT_AMOUNT_ID, VariableType.DOUBLE.getId()
    );
    List<String> categoryInputVariableValues = getInputVariableValues(
      decisionDefinitionDto, INPUT_CATEGORY_ID, VariableType.STRING.getId()
    );

    // then
    assertThat(amountInputVariableValues.size(), is(3));
    assertThat(amountInputVariableValues, hasItem("200.0"));
    assertThat(amountInputVariableValues, hasItem("300.0"));
    assertThat(amountInputVariableValues, hasItem("500.0"));

    assertThat(categoryInputVariableValues.size(), is(3));
    assertThat(categoryInputVariableValues, hasItem("Misc"));
    assertThat(categoryInputVariableValues, hasItem("Travel Expenses"));
    assertThat(categoryInputVariableValues, hasItem("somethingElse"));
  }

  @Test
  public void getOutputVariableValues() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto = engineRule.deployDecisionDefinition();
    // audit: false
    startDecisionInstanceWithInputs(decisionDefinitionDto, 200.0, "Misc");
    startDecisionInstanceWithInputs(decisionDefinitionDto, 300.0, "Misc");
    // audit: true
    startDecisionInstanceWithInputs(decisionDefinitionDto, 2000.0, "Misc");
    startDecisionInstanceWithInputs(decisionDefinitionDto, 3000.0, "Misc");
    startDecisionInstanceWithInputs(decisionDefinitionDto, 4000.0, "Misc");

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when

    List<String> auditOutputVariableValues = getOutputVariableValues(
      decisionDefinitionDto, OUTPUT_AUDIT_ID, VariableType.BOOLEAN.getId(), null
    );

    // then
    assertThat(auditOutputVariableValues.size(), is(2));
    assertThat(auditOutputVariableValues, hasItem("true"));
    assertThat(auditOutputVariableValues, hasItem("false"));
  }

  @Test
  public void getMoreThan10InputVariableValuesInNumericOrder() {
    // given
    final DecisionDefinitionEngineDto decisionDefinitionDto = engineRule.deployDecisionDefinition();
    final List<Double> amountInputValues = new ArrayList<>();
    IntStream.range(0, 15).forEach(
      i -> {
        amountInputValues.add((double) i);
        startDecisionInstanceWithInputs(decisionDefinitionDto, i, "Misc");
      }
    );

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    List<String> amountInputVariableValues = getInputVariableValues(
      decisionDefinitionDto, INPUT_AMOUNT_ID, VariableType.DOUBLE.getId()
    );

    // then
    assertThat(amountInputVariableValues, is(amountInputValues.stream().map(String::valueOf).collect(toList())));
  }


  @Test
  public void inputValuesDoNotContainDuplicates() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto = engineRule.deployDecisionDefinition();
    startDecisionInstanceWithInputs(decisionDefinitionDto, 200.0, "Misc");
    startDecisionInstanceWithInputs(decisionDefinitionDto, 200.0, "Misc");
    startDecisionInstanceWithInputs(decisionDefinitionDto, 200.0, "Misc");

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    List<String> amountInputVariableValues = getInputVariableValues(
      decisionDefinitionDto, INPUT_AMOUNT_ID, VariableType.DOUBLE.getId()
    );
    List<String> categoryInputVariableValues = getInputVariableValues(
      decisionDefinitionDto, INPUT_CATEGORY_ID, VariableType.STRING.getId()
    );

    // then
    assertThat(amountInputVariableValues.size(), is(1));
    assertThat(amountInputVariableValues, hasItem("200.0"));

    assertThat(categoryInputVariableValues.size(), is(1));
    assertThat(categoryInputVariableValues, hasItem("Misc"));
  }

  @Test
  public void noInputValuesFromAnotherDecisionDefinition() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto1 = engineRule.deployDecisionDefinition();
    startDecisionInstanceWithInputs(decisionDefinitionDto1, 200.0, "Misc");
    DecisionDefinitionEngineDto decisionDefinitionDto2 = deployDecisionDefinitionWithDifferentKey("otherKey");
    startDecisionInstanceWithInputs(decisionDefinitionDto2, 300.0, "Travel Expenses");

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    List<String> amountInputVariableValues = getInputVariableValues(
      decisionDefinitionDto1, INPUT_AMOUNT_ID, VariableType.DOUBLE.getId()
    );

    // then
    assertThat(amountInputVariableValues.size(), is(1));
    assertThat(amountInputVariableValues, hasItem("200.0"));
  }

  @Test
  public void noInputValuesFromAnotherDecisionDefinitionVersion() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto1 = engineRule.deployDecisionDefinition();
    DecisionDefinitionEngineDto decisionDefinitionDto2 = engineRule.deployDecisionDefinition();
    startDecisionInstanceWithInputs(decisionDefinitionDto1, 200.0, "Misc");
    startDecisionInstanceWithInputs(decisionDefinitionDto2, 300.0, "Travel Expenses");

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    List<String> amountInputVariableValues = getInputVariableValues(
      decisionDefinitionDto1, INPUT_AMOUNT_ID, VariableType.DOUBLE.getId()
    );

    // then
    assertThat(amountInputVariableValues.size(), is(1));
    assertThat(amountInputVariableValues, hasItem("200.0"));
  }

  @Test
  public void allInputValuesForDecisionDefinitionVersionAll() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto1 = engineRule.deployDecisionDefinition();
    DecisionDefinitionEngineDto decisionDefinitionDto2 = engineRule.deployDecisionDefinition();
    startDecisionInstanceWithInputs(decisionDefinitionDto1, 200.0, "Misc");
    startDecisionInstanceWithInputs(decisionDefinitionDto2, 300.0, "Travel Expenses");

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    List<String> amountInputVariableValues = getInputVariableValues(
      decisionDefinitionDto1.getKey(), "ALL", INPUT_AMOUNT_ID, VariableType.DOUBLE.getId()
    );

    // then
    assertThat(amountInputVariableValues.size(), is(2));
    assertThat(amountInputVariableValues, hasItem("200.0"));
    assertThat(amountInputVariableValues, hasItem("300.0"));
  }

  @Test
  public void inputValuesListIsCutByMaxResults() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto = engineRule.deployDecisionDefinition();
    startDecisionInstanceWithInputs(decisionDefinitionDto, 200.0, "Misc");
    startDecisionInstanceWithInputs(decisionDefinitionDto, 300.0, "Misc");
    startDecisionInstanceWithInputs(decisionDefinitionDto, 400.0, "Misc");

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    List<String> amountInputVariableValues = getInputVariableValues(
      decisionDefinitionDto, INPUT_AMOUNT_ID, VariableType.DOUBLE.getId(), null, 2, null
    );

    // then
    assertThat(amountInputVariableValues.size(), is(2));
    assertThat(amountInputVariableValues, hasItem("200.0"));
    assertThat(amountInputVariableValues, hasItem("300.0"));
  }

  @Test
  public void inputValuesListIsCutByAnOffset() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto = engineRule.deployDecisionDefinition();
    startDecisionInstanceWithInputs(decisionDefinitionDto, 200.0, "Misc");
    startDecisionInstanceWithInputs(decisionDefinitionDto, 300.0, "Misc");
    startDecisionInstanceWithInputs(decisionDefinitionDto, 400.0, "Misc");

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    List<String> amountInputVariableValues = getInputVariableValues(
      decisionDefinitionDto, INPUT_AMOUNT_ID, VariableType.DOUBLE.getId(), null, 10, 1
    );

    // then
    assertThat(amountInputVariableValues.size(), is(2));
    assertThat(amountInputVariableValues, hasItem("300.0"));
    assertThat(amountInputVariableValues, hasItem("400.0"));
  }

  @Test
  public void inputValuesListIsCutByAnOffsetAndMaxResults() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto = engineRule.deployDecisionDefinition();
    startDecisionInstanceWithInputs(decisionDefinitionDto, 200.0, "Misc");
    startDecisionInstanceWithInputs(decisionDefinitionDto, 300.0, "Misc");
    startDecisionInstanceWithInputs(decisionDefinitionDto, 400.0, "Misc");

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    List<String> amountInputVariableValues = getInputVariableValues(
      decisionDefinitionDto, INPUT_AMOUNT_ID, VariableType.DOUBLE.getId(), null, 1, 1
    );

    // then
    assertThat(amountInputVariableValues.size(), is(1));
    assertThat(amountInputVariableValues, hasItem("300.0"));
  }

  @Test
  public void getOnlyInputValuesWithSpecifiedPrefix() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto = engineRule.deployDecisionDefinition();
    startDecisionInstanceWithInputs(decisionDefinitionDto, 200.0, "Misc");
    startDecisionInstanceWithInputs(decisionDefinitionDto, 300.0, "Misc");
    startDecisionInstanceWithInputs(decisionDefinitionDto, 400.0, "Travel Expenses");
    startDecisionInstanceWithInputs(decisionDefinitionDto, 400.0, "Travel");

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    List<String> amountInputVariableValues = getInputVariableValues(
      decisionDefinitionDto, INPUT_CATEGORY_ID, VariableType.STRING.getId(), "Tra"
    );

    // then
    assertThat(amountInputVariableValues.size(), is(2));
    assertThat(amountInputVariableValues, hasItem("Travel Expenses"));
    assertThat(amountInputVariableValues, hasItem("Travel"));
  }

  @Test
  public void variableInputValuesFilteredBySubstring() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto = engineRule.deployDecisionDefinition();
    startDecisionInstanceWithInputs(decisionDefinitionDto, 200.0, "Misc");
    startDecisionInstanceWithInputs(decisionDefinitionDto, 300.0, "Misc");
    startDecisionInstanceWithInputs(decisionDefinitionDto, 400.0, "Travel Expenses");
    startDecisionInstanceWithInputs(decisionDefinitionDto, 400.0, "Travel");

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    List<String> amountInputVariableValues = getInputVariableValues(
      decisionDefinitionDto, INPUT_CATEGORY_ID, VariableType.STRING.getId(), "ave"
    );

    // then
    assertThat(amountInputVariableValues.size(), is(2));
    assertThat(amountInputVariableValues, hasItem("Travel Expenses"));
    assertThat(amountInputVariableValues, hasItem("Travel"));
  }

  @Test
  public void getOnlyOutputValuesWithSpecifiedPrefixAndSubstring() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto = engineRule.deployDecisionDefinition();
    // classification: "day-to-day expense"
    startDecisionInstanceWithInputs(decisionDefinitionDto, 200.0, "Misc");
    // classification: "budget"
    startDecisionInstanceWithInputs(decisionDefinitionDto, 300.0, "Misc");
    // classification: "exceptional"
    startDecisionInstanceWithInputs(decisionDefinitionDto, 2000.0, "Misc");
    startDecisionInstanceWithInputs(decisionDefinitionDto, 3000.0, "Misc");
    startDecisionInstanceWithInputs(decisionDefinitionDto, 4000.0, "Misc");

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    List<String> classificationOutputVariableValues = getOutputVariableValues(
      decisionDefinitionDto, OUTPUT_CLASSIFICATION_ID, VariableType.STRING.getId(), "ex"
    );

    // then
    assertThat(classificationOutputVariableValues.size(), is(2));
    assertThat(classificationOutputVariableValues, hasItem("exceptional"));
    assertThat(classificationOutputVariableValues, hasItem("day-to-day expense"));
  }

  @Test
  public void inputVariableValuesFilteredBySubstringCaseInsensitive() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto = engineRule.deployDecisionDefinition();
    startDecisionInstanceWithInputs(decisionDefinitionDto, 200.0, "Misc");
    startDecisionInstanceWithInputs(decisionDefinitionDto, 300.0, "Misc");
    startDecisionInstanceWithInputs(decisionDefinitionDto, 400.0, "TrAVel Expenses");
    startDecisionInstanceWithInputs(decisionDefinitionDto, 400.0, "Travel");

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    List<String> amountInputVariableValues = getInputVariableValues(
      decisionDefinitionDto, INPUT_CATEGORY_ID, VariableType.STRING.getId(), "ave"
    );

    // then
    assertThat(amountInputVariableValues.size(), is(2));
    assertThat(amountInputVariableValues, hasItem("TrAVel Expenses"));
    assertThat(amountInputVariableValues, hasItem("Travel"));
  }

  @Test
  public void inputVariableValuesFilteredByLargeSubstrings() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto = engineRule.deployDecisionDefinition();
    startDecisionInstanceWithInputs(decisionDefinitionDto, 200.0, "Misc barbarbarbar");
    startDecisionInstanceWithInputs(decisionDefinitionDto, 300.0, "Misc");
    startDecisionInstanceWithInputs(decisionDefinitionDto, 400.0, "Travelbarbarbarbar Expenses");
    startDecisionInstanceWithInputs(decisionDefinitionDto, 400.0, "Travel");

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    List<String> amountInputVariableValues = getInputVariableValues(
      decisionDefinitionDto, INPUT_CATEGORY_ID, VariableType.STRING.getId(), "barbarbarbar"
    );

    // then
    assertThat(amountInputVariableValues.size(), is(2));
    assertThat(amountInputVariableValues, hasItem("Misc barbarbarbar"));
    assertThat(amountInputVariableValues, hasItem("Travelbarbarbarbar Expenses"));
  }

  @Test
  public void numericValuePrefixDoubleVariableWorks() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto = engineRule.deployDecisionDefinition();
    startDecisionInstanceWithInputs(decisionDefinitionDto, 200.0, "Misc");

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    List<String> amountInputVariableValues = getInputVariableValues(
      decisionDefinitionDto, INPUT_AMOUNT_ID, VariableType.STRING.getId(), "20"
    );

    // then
    assertThat(amountInputVariableValues.size(), is(1));
  }

  @Test
  public void unknownPrefixReturnsEmptyResult() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto = engineRule.deployDecisionDefinition();
    startDecisionInstanceWithInputs(decisionDefinitionDto, 200.0, "Misc");

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    List<String> amountInputVariableValues = getInputVariableValues(
      decisionDefinitionDto, INPUT_CATEGORY_ID, VariableType.STRING.getId(), "ave"
    );

    // then
    assertThat(amountInputVariableValues.size(), is(0));
  }

  @Test
  public void valuePrefixForNonStringVariablesIsIgnored() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto = engineRule.deployDecisionDefinition();
    startDecisionInstanceWithInputs(decisionDefinitionDto, 200.0, "Misc");

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    List<String> amountInputVariableValues = getInputVariableValues(
      decisionDefinitionDto, INPUT_AMOUNT_ID, VariableType.STRING.getId(), "ave"
    );

    // then
    assertThat(amountInputVariableValues.size(), is(0));
  }

  @Test
  public void nullPrefixIsIgnored() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto = engineRule.deployDecisionDefinition();
    startDecisionInstanceWithInputs(decisionDefinitionDto, 200.0, "Misc");

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    List<String> amountInputVariableValues = getInputVariableValues(
      decisionDefinitionDto, INPUT_AMOUNT_ID, VariableType.STRING.getId(), null
    );

    // then
    assertThat(amountInputVariableValues.size(), is(1));
  }

  @Test
  public void emptyStringPrefixIsIgnored() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto = engineRule.deployDecisionDefinition();
    startDecisionInstanceWithInputs(decisionDefinitionDto, 200.0, "Misc");

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    List<String> amountInputVariableValues = getInputVariableValues(
      decisionDefinitionDto, INPUT_AMOUNT_ID, VariableType.STRING.getId(), ""
    );

    // then
    assertThat(amountInputVariableValues.size(), is(1));
  }

  private void startDecisionInstanceWithInputs(final DecisionDefinitionEngineDto decisionDefinitionDto,
                                               final double amountValue,
                                               final String category) {
    final HashMap<String, InputVariableEntry> inputs = createInputs(amountValue, category);
    startDecisionInstanceWithInputVars(decisionDefinitionDto.getId(), inputs);
  }

  private List<String> getInputVariableValues(final DecisionDefinitionEngineDto decisionDefinitionEngineDto,
                                              final String variableId,
                                              final String variableType) {
    return getInputVariableValues(
      decisionDefinitionEngineDto,
      variableId,
      variableType,
      null
    );
  }

  private List<String> getInputVariableValues(final DecisionDefinitionEngineDto decisionDefinitionEngineDto,
                                              final String variableId,
                                              final String variableType,
                                              final String valueFilter) {
    return getInputVariableValues(
      decisionDefinitionEngineDto,
      variableId,
      variableType,
      valueFilter,
      null,
      null
    );
  }

  private List<String> getInputVariableValues(final DecisionDefinitionEngineDto decisionDefinitionEngineDto,
                                              final String variableId,
                                              final String variableType,
                                              final String valueFilter,
                                              final Integer numResults,
                                              final Integer offset) {
    return getInputVariableValues(
      decisionDefinitionEngineDto.getKey(),
      String.valueOf(decisionDefinitionEngineDto.getVersion()),
      variableId,
      variableType,
      valueFilter,
      numResults,
      offset
    );
  }

  private List<String> getInputVariableValues(final String decisionDefinitionKey,
                                              final String decisionDefinitionVersion,
                                              final String variableId,
                                              final String variableType) {

    return getInputVariableValues(
      decisionDefinitionKey, decisionDefinitionVersion, variableId, variableType, null, null, null
    );
  }

  private List<String> getInputVariableValues(final String decisionDefinitionKey,
                                              final String decisionDefinitionVersion,
                                              final String variableId,
                                              final String variableType,
                                              final String valueFilter,
                                              final Integer numResults,
                                              final Integer offset) {
    final Map<String, Object> queryParams = createQueryParamMap(
      decisionDefinitionKey,
      decisionDefinitionVersion,
      variableId,
      variableType,
      valueFilter,
      numResults,
      offset
    );
    return getInputVariableValues(queryParams);
  }

  private Map<String, Object> createQueryParamMap(final String decisionDefinitionKey,
                                                  final String decisionDefinitionVersion,
                                                  final String variableId,
                                                  final String variableType,
                                                  final String valueFilter,
                                                  final Integer numResults,
                                                  final Integer offset) {
    final Map<String, Object> queryParams = new HashMap<>();
    queryParams.put(DECISION_DEFINITION_KEY, decisionDefinitionKey);
    queryParams.put(DECISION_DEFINITION_VERSION, decisionDefinitionVersion);
    queryParams.put(VARIABLE_ID, variableId);
    queryParams.put(VARIABLE_TYPE, variableType);
    queryParams.put(VALUE_FILTER, valueFilter);
    queryParams.put(NUM_RESULTS, numResults);
    queryParams.put(RESULT_OFFSET, offset);
    return queryParams;
  }

  private List<String> getInputVariableValues(Map<String, Object> queryParams) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildGetDecisionInputVariableValuesRequest()
      .addQueryParams(queryParams)
      .executeAndReturnList(String.class, 200);
  }

  private List<String> getOutputVariableValues(final DecisionDefinitionEngineDto decisionDefinitionEngineDto,
                                               final String variableId,
                                               final String variableType) {
    return getOutputVariableValues(decisionDefinitionEngineDto, variableId, variableType, null);
  }

  private List<String> getOutputVariableValues(final DecisionDefinitionEngineDto decisionDefinitionEngineDto,
                                               final String variableId,
                                               final String variableType,
                                               final String valueFilter) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildGetDecisionOutputVariableValuesRequest()
      .addQueryParams(createQueryParamMap(
        decisionDefinitionEngineDto.getKey(),
        String.valueOf(decisionDefinitionEngineDto.getVersion()),
        variableId,
        variableType,
        valueFilter,
        null,
        null
      ))
      .executeAndReturnList(String.class, 200);
  }

}
