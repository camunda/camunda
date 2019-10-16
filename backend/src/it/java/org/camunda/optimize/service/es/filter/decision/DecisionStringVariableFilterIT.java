/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.filter.decision;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import org.camunda.optimize.dto.engine.DecisionDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.FilterOperatorConstants;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.filter.InputVariableFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.filter.OutputVariableFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.RawDataDecisionReportResultDto;
import org.camunda.optimize.service.es.report.decision.AbstractDecisionDefinitionIT;
import org.camunda.optimize.test.it.extension.EngineVariableValue;
import org.camunda.optimize.test.util.decision.DecisionReportDataBuilder;
import org.camunda.optimize.test.util.decision.DecisionReportDataType;
import org.camunda.optimize.test.util.decision.DecisionTypeRef;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static java.util.stream.Collectors.toList;
import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.test.util.decision.DecisionFilterUtilHelper.createStringInputVariableFilter;
import static org.camunda.optimize.test.util.decision.DecisionFilterUtilHelper.createUndefinedVariableFilterData;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.core.IsNull.notNullValue;

public class DecisionStringVariableFilterIT extends AbstractDecisionDefinitionIT {

  @Test
  public void resultFilterByEqualStringInputVariableValue() {
    // given
    final String categoryInputValueToFilterFor = "Travel Expenses";
    final String inputVariableIdToFilterOn = INPUT_CATEGORY_ID;

    final DecisionDefinitionEngineDto decisionDefinitionDto = engineIntegrationExtensionRule.deployDecisionDefinition();
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(),
      createInputs(100.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(),
      createInputs(200.0, categoryInputValueToFilterFor)
    );

    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    DecisionReportDataDto reportData = createReportWithAllVersionSet(decisionDefinitionDto);
    reportData.setFilter(Lists.newArrayList(createStringInputVariableFilter(
      inputVariableIdToFilterOn, FilterOperatorConstants.IN, categoryInputValueToFilterFor
    )));
    RawDataDecisionReportResultDto result = evaluateRawReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount(), is(1L));
    assertThat(result.getData(), is(notNullValue()));
    assertThat(result.getData().size(), is(1));

    assertThat(
      result.getData().get(0).getInputVariables().get(inputVariableIdToFilterOn).getValue(),
      is(categoryInputValueToFilterFor)
    );
  }

  @Test
  public void resultFilterByEqualStringInputVariableMultipleValues() {
    // given
    final String firstCategoryInputValueToFilterFor = "Misc";
    final String secondCategoryInputValueToFilterFor = "Travel Expenses";
    final String inputVariableIdToFilterOn = INPUT_CATEGORY_ID;

    final DecisionDefinitionEngineDto decisionDefinitionDto = engineIntegrationExtensionRule.deployDecisionDefinition();
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(),
      createInputs(100.0, firstCategoryInputValueToFilterFor)
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(),
      createInputs(200.0, secondCategoryInputValueToFilterFor)
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(),
      createInputs(300.0, "Software License Costs")
    );

    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    DecisionReportDataDto reportData = createReportWithAllVersionSet(decisionDefinitionDto);
    reportData.setFilter(Lists.newArrayList(createStringInputVariableFilter(
      inputVariableIdToFilterOn, FilterOperatorConstants.IN,
      firstCategoryInputValueToFilterFor, secondCategoryInputValueToFilterFor
    )));
    RawDataDecisionReportResultDto result = evaluateRawReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount(), is(2L));
    assertThat(result.getData(), is(notNullValue()));
    assertThat(result.getData().size(), is(2));

    assertThat(
      result.getData().stream().map(entry -> entry.getInputVariables().get(inputVariableIdToFilterOn).getValue())
        .collect(toList()),
      containsInAnyOrder(firstCategoryInputValueToFilterFor, secondCategoryInputValueToFilterFor)
    );
  }

  @Test
  public void resultFilterByNotEqualStringInputVariableValue() {
    // given
    final String expectedCategoryInputValue = "Misc";
    final String categoryInputValueToExclude = "Travel Expenses";
    final String inputVariableIdToFilterOn = INPUT_CATEGORY_ID;

    final DecisionDefinitionEngineDto decisionDefinitionDto = engineIntegrationExtensionRule.deployDecisionDefinition();
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(),
      createInputs(100.0, expectedCategoryInputValue)
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(),
      createInputs(200.0, categoryInputValueToExclude)
    );

    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    DecisionReportDataDto reportData = createReportWithAllVersionSet(decisionDefinitionDto);
    reportData.setFilter(Lists.newArrayList(createStringInputVariableFilter(
      inputVariableIdToFilterOn, FilterOperatorConstants.NOT_IN, categoryInputValueToExclude
    )));
    RawDataDecisionReportResultDto result = evaluateRawReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount(), is(1L));
    assertThat(result.getData(), is(notNullValue()));
    assertThat(result.getData().size(), is(1));

    assertThat(
      result.getData().get(0).getInputVariables().get(inputVariableIdToFilterOn).getValue(),
      is(expectedCategoryInputValue)
    );
  }

  @Test
  public void filterInputVariableForUndefined() {
    // given
    final String inputClauseId = "TestyTest";
    final String camInputVariable = "putIn";

    final DecisionDefinitionEngineDto decisionDefinitionDto = deploySimpleInputDecisionDefinition(
      inputClauseId,
      camInputVariable,
      DecisionTypeRef.STRING
    );
    engineIntegrationExtensionRule.startDecisionInstance(
      decisionDefinitionDto.getId(),
      ImmutableMap.of(camInputVariable, "testValidMatch")
    );
    engineIntegrationExtensionRule.startDecisionInstance(
      decisionDefinitionDto.getId(),
      ImmutableMap.of(camInputVariable, "whateverElse")
    );
    engineIntegrationExtensionRule.startDecisionInstance(
      decisionDefinitionDto.getId(),
      Collections.singletonMap(camInputVariable, null)
    );
    engineIntegrationExtensionRule.startDecisionInstance(
      decisionDefinitionDto.getId(),
      Collections.singletonMap(camInputVariable, new EngineVariableValue(null, "String"))
    );

    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    DecisionReportDataDto reportData = createReportWithAllVersionSet(decisionDefinitionDto);

    InputVariableFilterDto variableFilterDto = new InputVariableFilterDto();
    variableFilterDto.setData(createUndefinedVariableFilterData(inputClauseId));

    reportData.setFilter(Collections.singletonList(variableFilterDto));
    RawDataDecisionReportResultDto result = evaluateRawReport(reportData).getResult();


    // then
    assertThat(result.getData(), is(notNullValue()));
    assertThat(result.getData().size(), is(2));
  }

  @Test
  public void filterOutputVariableForUndefined() {
    // given
    final String outputClauseId = "TestOutput";
    final String camInputVariable = "input";

    final DecisionDefinitionEngineDto decisionDefinitionDto = deploySimpleOutputDecisionDefinition(
      outputClauseId,
      camInputVariable,
      "testValidMatch",
      DecisionTypeRef.STRING
    );

    engineIntegrationExtensionRule.startDecisionInstance(
      decisionDefinitionDto.getId(),
      Collections.singletonMap(camInputVariable, null)
    );

    engineIntegrationExtensionRule.startDecisionInstance(
      decisionDefinitionDto.getId(),
      ImmutableMap.of(camInputVariable, "testValidMatch")
    );
    engineDatabaseExtensionRule.setDecisionOutputStringVariableValueToNull(outputClauseId, "testValidMatch");

    engineIntegrationExtensionRule.startDecisionInstance(
      decisionDefinitionDto.getId(),
      ImmutableMap.of(camInputVariable, "noMatchingOutputValue")
    );

    engineIntegrationExtensionRule.startDecisionInstance(
      decisionDefinitionDto.getId(),
      ImmutableMap.of(camInputVariable, "testValidMatch")
    );

    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    DecisionReportDataDto reportData = createReportWithAllVersionSet(decisionDefinitionDto);

    OutputVariableFilterDto variableFilterDto = new OutputVariableFilterDto();
    variableFilterDto.setData(createUndefinedVariableFilterData(outputClauseId));

    reportData.setFilter(Collections.singletonList(variableFilterDto));
    RawDataDecisionReportResultDto result = evaluateRawReport(reportData).getResult();

    // then
    assertThat(result.getData(), is(notNullValue()));
    assertThat(result.getData().size(), is(3));
  }

  private DecisionReportDataDto createReportWithAllVersionSet(DecisionDefinitionEngineDto decisionDefinitionDto) {
    return DecisionReportDataBuilder
      .create()
      .setDecisionDefinitionKey(decisionDefinitionDto.getKey())
      .setDecisionDefinitionVersion(ALL_VERSIONS)
      .setReportDataType(DecisionReportDataType.RAW_DATA)
      .build();
  }
}
