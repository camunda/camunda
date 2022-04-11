/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.retrieval.variable;

import org.camunda.optimize.dto.engine.definition.DecisionDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.InputVariableEntry;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.service.es.report.decision.AbstractDecisionDefinitionIT;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.util.DmnModels.INPUT_AMOUNT_ID;
import static org.camunda.optimize.util.DmnModels.INPUT_CATEGORY_ID;
import static org.camunda.optimize.util.DmnModels.OUTPUT_AUDIT_ID;
import static org.camunda.optimize.util.DmnModels.OUTPUT_CLASSIFICATION_ID;

public class DecisionVariableValueRetrievalIT extends AbstractDecisionDefinitionIT {

  @Test
  public void getInputVariableValues() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto = engineIntegrationExtension.deployDecisionDefinition();
    startDecisionInstanceWithInputs(decisionDefinitionDto, 200.0, "Misc");
    startDecisionInstanceWithInputs(decisionDefinitionDto, 300.0, "Travel Expenses");
    startDecisionInstanceWithInputs(decisionDefinitionDto, 500.0, "somethingElse");

    importAllEngineEntitiesFromScratch();

    // when
    List<String> amountInputVariableValues = variablesClient.getDecisionInputVariableValues(
      decisionDefinitionDto,
      INPUT_AMOUNT_ID,
      VariableType.DOUBLE
    );
    List<String> categoryInputVariableValues = variablesClient.getDecisionInputVariableValues(
      decisionDefinitionDto,
      INPUT_CATEGORY_ID,
      VariableType.STRING
    );

    // then
    assertThat(amountInputVariableValues)
      .hasSize(3)
      .containsExactlyInAnyOrder("200.0", "300.0", "500.0");

    assertThat(categoryInputVariableValues)
      .hasSize(3)
      .containsExactlyInAnyOrder("Misc", "Travel Expenses", "somethingElse");
  }

  @Test
  public void getInputVariableValuesWhenNoInstancesPresentReturnsEmpty() {
    // given a definition with no definition instances
    DecisionDefinitionEngineDto decisionDefinitionDto = engineIntegrationExtension.deployDecisionDefinition();
    importAllEngineEntitiesFromScratch();

    // when
    List<String> amountInputVariableValues = variablesClient.getDecisionInputVariableValues(
      decisionDefinitionDto,
      INPUT_AMOUNT_ID,
      VariableType.DOUBLE
    );

    // then
    assertThat(amountInputVariableValues).isEmpty();
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

    importAllEngineEntitiesFromScratch();

    // when

    List<String> auditOutputVariableValues = variablesClient
      .getDecisionOutputVariableValues(decisionDefinitionDto, OUTPUT_AUDIT_ID, VariableType.BOOLEAN, null);

    // then
    assertThat(auditOutputVariableValues)
      .hasSize(2)
      .containsExactlyInAnyOrder("true", "false");
  }

  @Test
  public void getOutputVariableValuesWhenNoInstancesPresentReturnsEmpty() {
    // given a definition with no definition instances
    DecisionDefinitionEngineDto decisionDefinitionDto = engineIntegrationExtension.deployDecisionDefinition();
    importAllEngineEntitiesFromScratch();

    // when
    List<String> auditOutputVariableValues = variablesClient
      .getDecisionOutputVariableValues(decisionDefinitionDto, OUTPUT_AUDIT_ID, VariableType.BOOLEAN, null);

    // then
    assertThat(auditOutputVariableValues).isEmpty();
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

    importAllEngineEntitiesFromScratch();

    // when
    List<String> amountInputVariableValues = variablesClient.getDecisionInputVariableValues(
      decisionDefinitionDto,
      INPUT_AMOUNT_ID,
      VariableType.DOUBLE
    );

    // then
    assertThat(amountInputVariableValues)
      .isEqualTo(amountInputValues.stream().map(String::valueOf).collect(toList()));
  }

  @Test
  public void inputValuesDoNotContainDuplicates() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto = engineIntegrationExtension.deployDecisionDefinition();
    startDecisionInstanceWithInputs(decisionDefinitionDto, 200.0, "Misc");
    startDecisionInstanceWithInputs(decisionDefinitionDto, 200.0, "Misc");
    startDecisionInstanceWithInputs(decisionDefinitionDto, 200.0, "Misc");

    importAllEngineEntitiesFromScratch();

    // when
    List<String> amountInputVariableValues = variablesClient.getDecisionInputVariableValues(
      decisionDefinitionDto,
      INPUT_AMOUNT_ID,
      VariableType.DOUBLE
    );
    List<String> categoryInputVariableValues = variablesClient.getDecisionInputVariableValues(
      decisionDefinitionDto,
      INPUT_CATEGORY_ID,
      VariableType.STRING
    );

    // then
    assertThat(amountInputVariableValues)
      .hasSize(1)
      .containsExactly("200.0");
    assertThat(categoryInputVariableValues)
      .hasSize(1)
      .containsExactly("Misc");
  }

  @Test
  public void noInputValuesFromAnotherDecisionDefinition() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto1 = engineIntegrationExtension.deployDecisionDefinition();
    startDecisionInstanceWithInputs(decisionDefinitionDto1, 200.0, "Misc");
    DecisionDefinitionEngineDto decisionDefinitionDto2 = deployDecisionDefinitionWithDifferentKey("otherKey");
    startDecisionInstanceWithInputs(decisionDefinitionDto2, 300.0, "Travel Expenses");

    importAllEngineEntitiesFromScratch();

    importAllEngineEntitiesFromScratch();

    // when
    List<String> amountInputVariableValues = variablesClient.getDecisionInputVariableValues(
      decisionDefinitionDto1,
      INPUT_AMOUNT_ID,
      VariableType.DOUBLE
    );

    // then
    assertThat(amountInputVariableValues)
      .hasSize(1)
      .containsExactlyInAnyOrder("200.0");
  }

  @Test
  public void noInputValuesFromAnotherDecisionDefinitionVersion() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto1 = engineIntegrationExtension.deployDecisionDefinition();
    DecisionDefinitionEngineDto decisionDefinitionDto2 = engineIntegrationExtension.deployDecisionDefinition();
    startDecisionInstanceWithInputs(decisionDefinitionDto1, 200.0, "Misc");
    startDecisionInstanceWithInputs(decisionDefinitionDto2, 300.0, "Travel Expenses");

    importAllEngineEntitiesFromScratch();

    importAllEngineEntitiesFromScratch();

    // when
    List<String> amountInputVariableValues = variablesClient.getDecisionInputVariableValues(
      decisionDefinitionDto1,
      INPUT_AMOUNT_ID,
      VariableType.DOUBLE
    );

    // then
    assertThat(amountInputVariableValues)
      .hasSize(1)
      .containsExactlyInAnyOrder("200.0");
  }

  @Test
  public void allInputValuesForDecisionDefinitionVersionAll() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto1 = engineIntegrationExtension.deployDecisionDefinition();
    DecisionDefinitionEngineDto decisionDefinitionDto2 = engineIntegrationExtension.deployDecisionDefinition();
    startDecisionInstanceWithInputs(decisionDefinitionDto1, 200.0, "Misc");
    startDecisionInstanceWithInputs(decisionDefinitionDto2, 300.0, "Travel Expenses");

    importAllEngineEntitiesFromScratch();

    importAllEngineEntitiesFromScratch();

    // when

    List<String> amountInputVariableValues = variablesClient.getDecisionInputVariableValues(
      decisionDefinitionDto1.getKey(),
      "ALL",
      INPUT_AMOUNT_ID,
      VariableType.DOUBLE
    );

    // then
    assertThat(amountInputVariableValues)
      .hasSize(2)
      .containsExactlyInAnyOrder("200.0", "300.0");
  }

  @Test
  public void inputValuesListIsCutByMaxResults() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto = engineIntegrationExtension.deployDecisionDefinition();
    startDecisionInstanceWithInputs(decisionDefinitionDto, 200.0, "Misc");
    startDecisionInstanceWithInputs(decisionDefinitionDto, 300.0, "Misc");
    startDecisionInstanceWithInputs(decisionDefinitionDto, 400.0, "Misc");

    importAllEngineEntitiesFromScratch();

    // when
    List<String> amountInputVariableValues = variablesClient.getDecisionInputVariableValues(
      decisionDefinitionDto,
      INPUT_AMOUNT_ID,
      VariableType.DOUBLE,
      null,
      2,
      0
    );

    // then
    assertThat(amountInputVariableValues)
      .hasSize(2)
      .containsExactlyInAnyOrder("200.0", "300.0");
  }

  @Test
  public void inputValuesListIsCutByAnOffset() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto = engineIntegrationExtension.deployDecisionDefinition();
    startDecisionInstanceWithInputs(decisionDefinitionDto, 200.0, "Misc");
    startDecisionInstanceWithInputs(decisionDefinitionDto, 300.0, "Misc");
    startDecisionInstanceWithInputs(decisionDefinitionDto, 400.0, "Misc");

    importAllEngineEntitiesFromScratch();

    // when
    List<String> amountInputVariableValues = variablesClient.getDecisionInputVariableValues(
      decisionDefinitionDto,
      INPUT_AMOUNT_ID,
      VariableType.DOUBLE,
      null,
      10,
      1
    );

    // then
    assertThat(amountInputVariableValues)
      .hasSize(2)
      .containsExactlyInAnyOrder("300.0", "400.0");
  }

  @Test
  public void inputValuesListIsCutByAnOffsetAndMaxResults() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto = engineIntegrationExtension.deployDecisionDefinition();
    startDecisionInstanceWithInputs(decisionDefinitionDto, 200.0, "Misc");
    startDecisionInstanceWithInputs(decisionDefinitionDto, 300.0, "Misc");
    startDecisionInstanceWithInputs(decisionDefinitionDto, 400.0, "Misc");

    importAllEngineEntitiesFromScratch();

    // when
    List<String> amountInputVariableValues = variablesClient.getDecisionInputVariableValues(
      decisionDefinitionDto,
      INPUT_AMOUNT_ID,
      VariableType.DOUBLE,
      null,
      1,
      1
    );

    // then
    assertThat(amountInputVariableValues)
      .hasSize(1)
      .containsExactly("300.0");
  }

  @Test
  public void getOnlyInputValuesWithSpecifiedPrefix() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto = engineIntegrationExtension.deployDecisionDefinition();
    startDecisionInstanceWithInputs(decisionDefinitionDto, 200.0, "Misc");
    startDecisionInstanceWithInputs(decisionDefinitionDto, 300.0, "Misc");
    startDecisionInstanceWithInputs(decisionDefinitionDto, 400.0, "Travel Expenses");
    startDecisionInstanceWithInputs(decisionDefinitionDto, 400.0, "Travel");

    importAllEngineEntitiesFromScratch();

    // when
    List<String> amountInputVariableValues = variablesClient.getDecisionInputVariableValues(
      decisionDefinitionDto,
      INPUT_CATEGORY_ID,
      VariableType.STRING,
      "Tra"
    );

    // then
    assertThat(amountInputVariableValues)
      .hasSize(2)
      .containsExactlyInAnyOrder("Travel Expenses", "Travel");
  }

  @Test
  public void variableInputValuesFilteredBySubstring() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto = engineIntegrationExtension.deployDecisionDefinition();
    startDecisionInstanceWithInputs(decisionDefinitionDto, 200.0, "Misc");
    startDecisionInstanceWithInputs(decisionDefinitionDto, 300.0, "Misc");
    startDecisionInstanceWithInputs(decisionDefinitionDto, 400.0, "Travel Expenses");
    startDecisionInstanceWithInputs(decisionDefinitionDto, 400.0, "Travel");

    importAllEngineEntitiesFromScratch();

    // when
    List<String> amountInputVariableValues = variablesClient.getDecisionInputVariableValues(
      decisionDefinitionDto,
      INPUT_CATEGORY_ID,
      VariableType.STRING,
      "ave"
    );

    // then
    assertThat(amountInputVariableValues)
      .hasSize(2)
      .containsExactlyInAnyOrder("Travel Expenses", "Travel");
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

    importAllEngineEntitiesFromScratch();

    // when
    List<String> classificationOutputVariableValues = variablesClient
      .getDecisionOutputVariableValues(decisionDefinitionDto, OUTPUT_CLASSIFICATION_ID, VariableType.STRING, "ex");

    // then
    assertThat(classificationOutputVariableValues)
      .hasSize(2)
      .containsExactlyInAnyOrder("exceptional", "day-to-day expense");
  }

  @Test
  public void inputVariableValuesFilteredBySubstringCaseInsensitive() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto = engineIntegrationExtension.deployDecisionDefinition();
    startDecisionInstanceWithInputs(decisionDefinitionDto, 200.0, "Misc");
    startDecisionInstanceWithInputs(decisionDefinitionDto, 300.0, "Misc");
    startDecisionInstanceWithInputs(decisionDefinitionDto, 400.0, "TrAVel Expenses");
    startDecisionInstanceWithInputs(decisionDefinitionDto, 400.0, "Travel");

    importAllEngineEntitiesFromScratch();

    // when
    List<String> amountInputVariableValues = variablesClient.getDecisionInputVariableValues(
      decisionDefinitionDto,
      INPUT_CATEGORY_ID,
      VariableType.STRING,
      "ave"
    );

    // then
    assertThat(amountInputVariableValues)
      .hasSize(2)
      .containsExactlyInAnyOrder("TrAVel Expenses", "Travel");
  }

  @Test
  public void inputVariableValuesFilteredByLargeSubstrings() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto = engineIntegrationExtension.deployDecisionDefinition();
    startDecisionInstanceWithInputs(decisionDefinitionDto, 200.0, "Misc barbarbarbar");
    startDecisionInstanceWithInputs(decisionDefinitionDto, 300.0, "Misc");
    startDecisionInstanceWithInputs(decisionDefinitionDto, 400.0, "Travelbarbarbarbar Expenses");
    startDecisionInstanceWithInputs(decisionDefinitionDto, 400.0, "Travel");

    importAllEngineEntitiesFromScratch();

    // when
    List<String> amountInputVariableValues = variablesClient.getDecisionInputVariableValues(
      decisionDefinitionDto,
      INPUT_CATEGORY_ID,
      VariableType.STRING,
      "barbarbarbar"
    );

    // then
    assertThat(amountInputVariableValues)
      .hasSize(2)
      .containsExactlyInAnyOrder("Misc barbarbarbar", "Travelbarbarbarbar Expenses");
  }

  @Test
  public void numericValuePrefixDoubleVariableWorks() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto = engineIntegrationExtension.deployDecisionDefinition();
    startDecisionInstanceWithInputs(decisionDefinitionDto, 200.0, "Misc");

    importAllEngineEntitiesFromScratch();

    // when
    List<String> amountInputVariableValues = variablesClient.getDecisionInputVariableValues(
      decisionDefinitionDto,
      INPUT_AMOUNT_ID,
      VariableType.STRING,
      "20"
    );

    // then
    assertThat(amountInputVariableValues).hasSize(1);
  }

  @Test
  public void unknownPrefixReturnsEmptyResult() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto = engineIntegrationExtension.deployDecisionDefinition();
    startDecisionInstanceWithInputs(decisionDefinitionDto, 200.0, "Misc");

    importAllEngineEntitiesFromScratch();

    // when
    List<String> amountInputVariableValues = variablesClient.getDecisionInputVariableValues(
      decisionDefinitionDto,
      INPUT_CATEGORY_ID,
      VariableType.STRING,
      "ave"
    );

    // then
    assertThat(amountInputVariableValues).isEmpty();
  }

  @Test
  public void valuePrefixForNonStringVariablesIsIgnored() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto = engineIntegrationExtension.deployDecisionDefinition();
    startDecisionInstanceWithInputs(decisionDefinitionDto, 200.0, "Misc");

    importAllEngineEntitiesFromScratch();

    // when
    List<String> amountInputVariableValues = variablesClient.getDecisionInputVariableValues(
      decisionDefinitionDto,
      INPUT_AMOUNT_ID,
      VariableType.STRING,
      "ave"
    );

    // then
    assertThat(amountInputVariableValues).isEmpty();
  }

  @Test
  public void nullPrefixIsIgnored() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto = engineIntegrationExtension.deployDecisionDefinition();
    startDecisionInstanceWithInputs(decisionDefinitionDto, 200.0, "Misc");

    importAllEngineEntitiesFromScratch();

    // when
    List<String> amountInputVariableValues = variablesClient.getDecisionInputVariableValues(
      decisionDefinitionDto,
      INPUT_AMOUNT_ID,
      VariableType.STRING,
      null
    );

    // then
    assertThat(amountInputVariableValues).hasSize(1);
  }

  @Test
  public void emptyStringPrefixIsIgnored() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto = engineIntegrationExtension.deployDecisionDefinition();
    startDecisionInstanceWithInputs(decisionDefinitionDto, 200.0, "Misc");

    importAllEngineEntitiesFromScratch();

    // when
    List<String> amountInputVariableValues = variablesClient.getDecisionInputVariableValues(
      decisionDefinitionDto,
      INPUT_AMOUNT_ID,
      VariableType.STRING,
      ""
    );

    // then
    assertThat(amountInputVariableValues).hasSize(1);
  }

  private void startDecisionInstanceWithInputs(final DecisionDefinitionEngineDto decisionDefinitionDto,
                                               final double amountValue,
                                               final String category) {
    final HashMap<String, InputVariableEntry> inputs = createInputs(amountValue, category);
    startDecisionInstanceWithInputVars(decisionDefinitionDto.getId(), inputs);
  }

}
