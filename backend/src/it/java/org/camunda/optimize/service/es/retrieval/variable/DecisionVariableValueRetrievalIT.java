/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.retrieval.variable;

import org.camunda.optimize.dto.engine.DecisionDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.InputVariableEntry;
import org.camunda.optimize.dto.optimize.query.variable.DecisionVariableValueRequestDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.service.es.report.decision.AbstractDecisionDefinitionIT;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class DecisionVariableValueRetrievalIT extends AbstractDecisionDefinitionIT {

  @Test
  public void getInputVariableValues() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto = engineIntegrationExtension.deployDecisionDefinition();
    startDecisionInstanceWithInputs(decisionDefinitionDto, 200.0, "Misc");
    startDecisionInstanceWithInputs(decisionDefinitionDto, 300.0, "Travel Expenses");
    startDecisionInstanceWithInputs(decisionDefinitionDto, 500.0, "somethingElse");

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    List<String> amountInputVariableValues = getInputVariableValues(
      decisionDefinitionDto, INPUT_AMOUNT_ID, VariableType.DOUBLE
    );
    List<String> categoryInputVariableValues = getInputVariableValues(
      decisionDefinitionDto, INPUT_CATEGORY_ID, VariableType.STRING
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
    DecisionDefinitionEngineDto decisionDefinitionDto = engineIntegrationExtension.deployDecisionDefinition();
    // audit: false
    startDecisionInstanceWithInputs(decisionDefinitionDto, 200.0, "Misc");
    startDecisionInstanceWithInputs(decisionDefinitionDto, 300.0, "Misc");
    // audit: true
    startDecisionInstanceWithInputs(decisionDefinitionDto, 2000.0, "Misc");
    startDecisionInstanceWithInputs(decisionDefinitionDto, 3000.0, "Misc");
    startDecisionInstanceWithInputs(decisionDefinitionDto, 4000.0, "Misc");

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when

    List<String> auditOutputVariableValues = getOutputVariableValues(
      decisionDefinitionDto, OUTPUT_AUDIT_ID, VariableType.BOOLEAN, null
    );

    // then
    assertThat(auditOutputVariableValues.size(), is(2));
    assertThat(auditOutputVariableValues, hasItem("true"));
    assertThat(auditOutputVariableValues, hasItem("false"));
  }

  @Test
  public void getMoreThan10InputVariableValuesInNumericOrder() {
    // given
    final DecisionDefinitionEngineDto decisionDefinitionDto = engineIntegrationExtension.deployDecisionDefinition();
    final List<Double> amountInputValues = new ArrayList<>();
    IntStream.range(0, 15).forEach(
      i -> {
        amountInputValues.add((double) i);
        startDecisionInstanceWithInputs(decisionDefinitionDto, i, "Misc");
      }
    );

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    List<String> amountInputVariableValues = getInputVariableValues(
      decisionDefinitionDto, INPUT_AMOUNT_ID, VariableType.DOUBLE
    );

    // then
    assertThat(amountInputVariableValues, is(amountInputValues.stream().map(String::valueOf).collect(toList())));
  }


  @Test
  public void inputValuesDoNotContainDuplicates() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto = engineIntegrationExtension.deployDecisionDefinition();
    startDecisionInstanceWithInputs(decisionDefinitionDto, 200.0, "Misc");
    startDecisionInstanceWithInputs(decisionDefinitionDto, 200.0, "Misc");
    startDecisionInstanceWithInputs(decisionDefinitionDto, 200.0, "Misc");

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    List<String> amountInputVariableValues = getInputVariableValues(
      decisionDefinitionDto, INPUT_AMOUNT_ID, VariableType.DOUBLE
    );
    List<String> categoryInputVariableValues = getInputVariableValues(
      decisionDefinitionDto, INPUT_CATEGORY_ID, VariableType.STRING
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
    DecisionDefinitionEngineDto decisionDefinitionDto1 = engineIntegrationExtension.deployDecisionDefinition();
    startDecisionInstanceWithInputs(decisionDefinitionDto1, 200.0, "Misc");
    DecisionDefinitionEngineDto decisionDefinitionDto2 = deployDecisionDefinitionWithDifferentKey("otherKey");
    startDecisionInstanceWithInputs(decisionDefinitionDto2, 300.0, "Travel Expenses");

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    List<String> amountInputVariableValues = getInputVariableValues(
      decisionDefinitionDto1, INPUT_AMOUNT_ID, VariableType.DOUBLE
    );

    // then
    assertThat(amountInputVariableValues.size(), is(1));
    assertThat(amountInputVariableValues, hasItem("200.0"));
  }

  @Test
  public void noInputValuesFromAnotherDecisionDefinitionVersion() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto1 = engineIntegrationExtension.deployDecisionDefinition();
    DecisionDefinitionEngineDto decisionDefinitionDto2 = engineIntegrationExtension.deployDecisionDefinition();
    startDecisionInstanceWithInputs(decisionDefinitionDto1, 200.0, "Misc");
    startDecisionInstanceWithInputs(decisionDefinitionDto2, 300.0, "Travel Expenses");

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    List<String> amountInputVariableValues = getInputVariableValues(
      decisionDefinitionDto1, INPUT_AMOUNT_ID, VariableType.DOUBLE
    );

    // then
    assertThat(amountInputVariableValues.size(), is(1));
    assertThat(amountInputVariableValues, hasItem("200.0"));
  }

  @Test
  public void allInputValuesForDecisionDefinitionVersionAll() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto1 = engineIntegrationExtension.deployDecisionDefinition();
    DecisionDefinitionEngineDto decisionDefinitionDto2 = engineIntegrationExtension.deployDecisionDefinition();
    startDecisionInstanceWithInputs(decisionDefinitionDto1, 200.0, "Misc");
    startDecisionInstanceWithInputs(decisionDefinitionDto2, 300.0, "Travel Expenses");

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    List<String> amountInputVariableValues = getInputVariableValues(
      decisionDefinitionDto1.getKey(), "ALL", INPUT_AMOUNT_ID, VariableType.DOUBLE
    );

    // then
    assertThat(amountInputVariableValues.size(), is(2));
    assertThat(amountInputVariableValues, hasItem("200.0"));
    assertThat(amountInputVariableValues, hasItem("300.0"));
  }

  @Test
  public void inputValuesListIsCutByMaxResults() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto = engineIntegrationExtension.deployDecisionDefinition();
    startDecisionInstanceWithInputs(decisionDefinitionDto, 200.0, "Misc");
    startDecisionInstanceWithInputs(decisionDefinitionDto, 300.0, "Misc");
    startDecisionInstanceWithInputs(decisionDefinitionDto, 400.0, "Misc");

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    List<String> amountInputVariableValues = getInputVariableValues(
      decisionDefinitionDto, INPUT_AMOUNT_ID, VariableType.DOUBLE, null, 2, 0
    );

    // then
    assertThat(amountInputVariableValues.size(), is(2));
    assertThat(amountInputVariableValues, hasItem("200.0"));
    assertThat(amountInputVariableValues, hasItem("300.0"));
  }

  @Test
  public void inputValuesListIsCutByAnOffset() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto = engineIntegrationExtension.deployDecisionDefinition();
    startDecisionInstanceWithInputs(decisionDefinitionDto, 200.0, "Misc");
    startDecisionInstanceWithInputs(decisionDefinitionDto, 300.0, "Misc");
    startDecisionInstanceWithInputs(decisionDefinitionDto, 400.0, "Misc");

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    List<String> amountInputVariableValues = getInputVariableValues(
      decisionDefinitionDto, INPUT_AMOUNT_ID, VariableType.DOUBLE, null, 10, 1
    );

    // then
    assertThat(amountInputVariableValues.size(), is(2));
    assertThat(amountInputVariableValues, hasItem("300.0"));
    assertThat(amountInputVariableValues, hasItem("400.0"));
  }

  @Test
  public void inputValuesListIsCutByAnOffsetAndMaxResults() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto = engineIntegrationExtension.deployDecisionDefinition();
    startDecisionInstanceWithInputs(decisionDefinitionDto, 200.0, "Misc");
    startDecisionInstanceWithInputs(decisionDefinitionDto, 300.0, "Misc");
    startDecisionInstanceWithInputs(decisionDefinitionDto, 400.0, "Misc");

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    List<String> amountInputVariableValues = getInputVariableValues(
      decisionDefinitionDto, INPUT_AMOUNT_ID, VariableType.DOUBLE, null, 1, 1
    );

    // then
    assertThat(amountInputVariableValues.size(), is(1));
    assertThat(amountInputVariableValues, hasItem("300.0"));
  }

  @Test
  public void getOnlyInputValuesWithSpecifiedPrefix() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto = engineIntegrationExtension.deployDecisionDefinition();
    startDecisionInstanceWithInputs(decisionDefinitionDto, 200.0, "Misc");
    startDecisionInstanceWithInputs(decisionDefinitionDto, 300.0, "Misc");
    startDecisionInstanceWithInputs(decisionDefinitionDto, 400.0, "Travel Expenses");
    startDecisionInstanceWithInputs(decisionDefinitionDto, 400.0, "Travel");

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    List<String> amountInputVariableValues = getInputVariableValues(
      decisionDefinitionDto, INPUT_CATEGORY_ID, VariableType.STRING, "Tra"
    );

    // then
    assertThat(amountInputVariableValues.size(), is(2));
    assertThat(amountInputVariableValues, hasItem("Travel Expenses"));
    assertThat(amountInputVariableValues, hasItem("Travel"));
  }

  @Test
  public void variableInputValuesFilteredBySubstring() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto = engineIntegrationExtension.deployDecisionDefinition();
    startDecisionInstanceWithInputs(decisionDefinitionDto, 200.0, "Misc");
    startDecisionInstanceWithInputs(decisionDefinitionDto, 300.0, "Misc");
    startDecisionInstanceWithInputs(decisionDefinitionDto, 400.0, "Travel Expenses");
    startDecisionInstanceWithInputs(decisionDefinitionDto, 400.0, "Travel");

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    List<String> amountInputVariableValues = getInputVariableValues(
      decisionDefinitionDto, INPUT_CATEGORY_ID, VariableType.STRING, "ave"
    );

    // then
    assertThat(amountInputVariableValues.size(), is(2));
    assertThat(amountInputVariableValues, hasItem("Travel Expenses"));
    assertThat(amountInputVariableValues, hasItem("Travel"));
  }

  @Test
  public void getOnlyOutputValuesWithSpecifiedPrefixAndSubstring() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto = engineIntegrationExtension.deployDecisionDefinition();
    // classification: "day-to-day expense"
    startDecisionInstanceWithInputs(decisionDefinitionDto, 200.0, "Misc");
    // classification: "budget"
    startDecisionInstanceWithInputs(decisionDefinitionDto, 300.0, "Misc");
    // classification: "exceptional"
    startDecisionInstanceWithInputs(decisionDefinitionDto, 2000.0, "Misc");
    startDecisionInstanceWithInputs(decisionDefinitionDto, 3000.0, "Misc");
    startDecisionInstanceWithInputs(decisionDefinitionDto, 4000.0, "Misc");

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    List<String> classificationOutputVariableValues = getOutputVariableValues(
      decisionDefinitionDto, OUTPUT_CLASSIFICATION_ID, VariableType.STRING, "ex"
    );

    // then
    assertThat(classificationOutputVariableValues.size(), is(2));
    assertThat(classificationOutputVariableValues, hasItem("exceptional"));
    assertThat(classificationOutputVariableValues, hasItem("day-to-day expense"));
  }

  @Test
  public void inputVariableValuesFilteredBySubstringCaseInsensitive() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto = engineIntegrationExtension.deployDecisionDefinition();
    startDecisionInstanceWithInputs(decisionDefinitionDto, 200.0, "Misc");
    startDecisionInstanceWithInputs(decisionDefinitionDto, 300.0, "Misc");
    startDecisionInstanceWithInputs(decisionDefinitionDto, 400.0, "TrAVel Expenses");
    startDecisionInstanceWithInputs(decisionDefinitionDto, 400.0, "Travel");

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    List<String> amountInputVariableValues = getInputVariableValues(
      decisionDefinitionDto, INPUT_CATEGORY_ID, VariableType.STRING, "ave"
    );

    // then
    assertThat(amountInputVariableValues.size(), is(2));
    assertThat(amountInputVariableValues, hasItem("TrAVel Expenses"));
    assertThat(amountInputVariableValues, hasItem("Travel"));
  }

  @Test
  public void inputVariableValuesFilteredByLargeSubstrings() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto = engineIntegrationExtension.deployDecisionDefinition();
    startDecisionInstanceWithInputs(decisionDefinitionDto, 200.0, "Misc barbarbarbar");
    startDecisionInstanceWithInputs(decisionDefinitionDto, 300.0, "Misc");
    startDecisionInstanceWithInputs(decisionDefinitionDto, 400.0, "Travelbarbarbarbar Expenses");
    startDecisionInstanceWithInputs(decisionDefinitionDto, 400.0, "Travel");

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    List<String> amountInputVariableValues = getInputVariableValues(
      decisionDefinitionDto, INPUT_CATEGORY_ID, VariableType.STRING, "barbarbarbar"
    );

    // then
    assertThat(amountInputVariableValues.size(), is(2));
    assertThat(amountInputVariableValues, hasItem("Misc barbarbarbar"));
    assertThat(amountInputVariableValues, hasItem("Travelbarbarbarbar Expenses"));
  }

  @Test
  public void numericValuePrefixDoubleVariableWorks() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto = engineIntegrationExtension.deployDecisionDefinition();
    startDecisionInstanceWithInputs(decisionDefinitionDto, 200.0, "Misc");

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    List<String> amountInputVariableValues = getInputVariableValues(
      decisionDefinitionDto, INPUT_AMOUNT_ID, VariableType.STRING, "20"
    );

    // then
    assertThat(amountInputVariableValues.size(), is(1));
  }

  @Test
  public void unknownPrefixReturnsEmptyResult() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto = engineIntegrationExtension.deployDecisionDefinition();
    startDecisionInstanceWithInputs(decisionDefinitionDto, 200.0, "Misc");

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    List<String> amountInputVariableValues = getInputVariableValues(
      decisionDefinitionDto, INPUT_CATEGORY_ID, VariableType.STRING, "ave"
    );

    // then
    assertThat(amountInputVariableValues.size(), is(0));
  }

  @Test
  public void valuePrefixForNonStringVariablesIsIgnored() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto = engineIntegrationExtension.deployDecisionDefinition();
    startDecisionInstanceWithInputs(decisionDefinitionDto, 200.0, "Misc");

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    List<String> amountInputVariableValues = getInputVariableValues(
      decisionDefinitionDto, INPUT_AMOUNT_ID, VariableType.STRING, "ave"
    );

    // then
    assertThat(amountInputVariableValues.size(), is(0));
  }

  @Test
  public void nullPrefixIsIgnored() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto = engineIntegrationExtension.deployDecisionDefinition();
    startDecisionInstanceWithInputs(decisionDefinitionDto, 200.0, "Misc");

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    List<String> amountInputVariableValues = getInputVariableValues(
      decisionDefinitionDto, INPUT_AMOUNT_ID, VariableType.STRING, null
    );

    // then
    assertThat(amountInputVariableValues.size(), is(1));
  }

  @Test
  public void emptyStringPrefixIsIgnored() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto = engineIntegrationExtension.deployDecisionDefinition();
    startDecisionInstanceWithInputs(decisionDefinitionDto, 200.0, "Misc");

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    List<String> amountInputVariableValues = getInputVariableValues(
      decisionDefinitionDto, INPUT_AMOUNT_ID, VariableType.STRING, ""
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
                                              final VariableType variableType) {
    return getInputVariableValues(
      decisionDefinitionEngineDto,
      variableId,
      variableType,
      null
    );
  }

  private List<String> getInputVariableValues(final DecisionDefinitionEngineDto decisionDefinitionEngineDto,
                                              final String variableId,
                                              final VariableType variableType,
                                              final String valueFilter) {
    return getInputVariableValues(
      decisionDefinitionEngineDto,
      variableId,
      variableType,
      valueFilter,
      Integer.MAX_VALUE,
      0
    );
  }

  private List<String> getInputVariableValues(final DecisionDefinitionEngineDto decisionDefinitionEngineDto,
                                              final String variableId,
                                              final VariableType variableType,
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
                                              final VariableType variableType) {

    return getInputVariableValues(
      decisionDefinitionKey, decisionDefinitionVersion, variableId, variableType, null, Integer.MAX_VALUE, 0
    );
  }

  private List<String> getInputVariableValues(final String decisionDefinitionKey,
                                              final String decisionDefinitionVersion,
                                              final String variableId,
                                              final VariableType variableType,
                                              final String valueFilter,
                                              final Integer numResults,
                                              final Integer offset) {
    final DecisionVariableValueRequestDto queryParams = createVariableRequest(
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

  private DecisionVariableValueRequestDto createVariableRequest(final String decisionDefinitionKey,
                                                                final String decisionDefinitionVersion,
                                                                final String variableId,
                                                                final VariableType variableType,
                                                                final String valueFilter,
                                                                final Integer numResults,
                                                                final Integer offset) {
    DecisionVariableValueRequestDto requestDto = new DecisionVariableValueRequestDto();
    requestDto.setDecisionDefinitionKey(decisionDefinitionKey);
    requestDto.setDecisionDefinitionVersion(decisionDefinitionVersion);
    requestDto.setVariableId(variableId);
    requestDto.setVariableType(variableType);
    requestDto.setValueFilter(valueFilter);
    requestDto.setResultOffset(offset);
    requestDto.setNumResults(numResults);
    return requestDto;
  }

  private List<String> getInputVariableValues(DecisionVariableValueRequestDto variableValueRequestDto) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildDecisionInputVariableValuesRequest(variableValueRequestDto)
      .executeAndReturnList(String.class, Response.Status.OK.getStatusCode());
  }

  private List<String> getOutputVariableValues(final DecisionDefinitionEngineDto decisionDefinitionEngineDto,
                                               final String variableId,
                                               final VariableType variableType,
                                               final String valueFilter) {
    DecisionVariableValueRequestDto variableRequest = createVariableRequest(
      decisionDefinitionEngineDto.getKey(),
      decisionDefinitionEngineDto.getVersionAsString(),
      variableId,
      variableType,
      valueFilter,
      Integer.MAX_VALUE,
      0
    );
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildDecisionOutputVariableValuesRequest(variableRequest)
      .executeAndReturnList(String.class, Response.Status.OK.getStatusCode());
  }

}
