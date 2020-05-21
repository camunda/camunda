/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.filter.decision;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import org.camunda.optimize.dto.engine.definition.DecisionDefinitionEngineDto;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Collections;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.dto.optimize.query.report.FilterOperatorConstants.IN;
import static org.camunda.optimize.dto.optimize.query.report.FilterOperatorConstants.NOT_IN;
import static org.camunda.optimize.test.util.decision.DecisionFilterUtilHelper.createStringInputVariableFilter;
import static org.camunda.optimize.test.util.decision.DecisionFilterUtilHelper.createUndefinedVariableFilterData;
import static org.camunda.optimize.util.DmnModels.INPUT_CATEGORY_ID;

public class DecisionStringVariableFilterIT extends AbstractDecisionDefinitionIT {

  @Test
  public void resultFilterByEqualStringInputVariableValue() {
    // given
    final String categoryInputValueToFilterFor = "Travel Expenses";
    final String inputVariableIdToFilterOn = INPUT_CATEGORY_ID;

    final DecisionDefinitionEngineDto decisionDefinitionDto = engineIntegrationExtension.deployDecisionDefinition();
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(),
      createInputs(100.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(),
      createInputs(200.0, categoryInputValueToFilterFor)
    );

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    DecisionReportDataDto reportData = createReportWithAllVersionSet(decisionDefinitionDto);
    reportData.setFilter(Lists.newArrayList(createStringInputVariableFilter(
      inputVariableIdToFilterOn, IN, categoryInputValueToFilterFor
    )));
    RawDataDecisionReportResultDto result = reportClient.evaluateRawReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(1L);
    assertThat(result.getData()).hasSize(1);

    assertThat(result.getData().get(0).getInputVariables().get(inputVariableIdToFilterOn).getValue())
      .isEqualTo(categoryInputValueToFilterFor);
  }

  @Test
  public void resultFilterByEqualStringInputVariableMultipleValues() {
    // given
    final String firstCategoryInputValueToFilterFor = "Misc";
    final String secondCategoryInputValueToFilterFor = "Travel Expenses";
    final String inputVariableIdToFilterOn = INPUT_CATEGORY_ID;

    final DecisionDefinitionEngineDto decisionDefinitionDto = engineIntegrationExtension.deployDecisionDefinition();
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

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    DecisionReportDataDto reportData = createReportWithAllVersionSet(decisionDefinitionDto);
    reportData.setFilter(Lists.newArrayList(createStringInputVariableFilter(
      inputVariableIdToFilterOn, IN,
      firstCategoryInputValueToFilterFor, secondCategoryInputValueToFilterFor
    )));
    RawDataDecisionReportResultDto result = reportClient.evaluateRawReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(2L);
    assertThat(result.getData()).hasSize(2);

    assertThat(
      result.getData().stream().map(entry -> entry.getInputVariables().get(inputVariableIdToFilterOn).getValue())
        .collect(toList())
    ).containsExactlyInAnyOrder(firstCategoryInputValueToFilterFor, secondCategoryInputValueToFilterFor);
  }

  public static Stream<Arguments> nullFilterScenarios() {
    return Stream.of(
      Arguments.of(IN, new String[]{null}, 2),
      Arguments.of(IN, new String[]{null, "validMatch"}, 3),
      Arguments.of(NOT_IN, new String[]{null}, 2),
      Arguments.of(NOT_IN, new String[]{null, "validMatch"}, 1)
    );
  }

  @ParameterizedTest
  @MethodSource("nullFilterScenarios")
  public void resultFilterStringInputVariableSupportsNullValue(final String operator,
                                                               final String[] filterValues,
                                                               final Integer expectedInstanceCount) {
    // given
    final String inputClauseId = "TestyTest";
    final String camInputVariable = "putIn";

    final DecisionDefinitionEngineDto decisionDefinitionDto = deploySimpleInputDecisionDefinition(
      inputClauseId,
      camInputVariable,
      DecisionTypeRef.STRING
    );
    // instance where the variable is not defined
    engineIntegrationExtension.startDecisionInstance(
      decisionDefinitionDto.getId(), Collections.singletonMap(camInputVariable, null)
    );
    // instance where the variable has the value
    engineIntegrationExtension.startDecisionInstance(
      decisionDefinitionDto.getId(),
      Collections.singletonMap(camInputVariable, new EngineVariableValue(null, "String"))
    );
    engineIntegrationExtension.startDecisionInstance(
      decisionDefinitionDto.getId(),
      ImmutableMap.of(camInputVariable, "validMatch")
    );
    engineIntegrationExtension.startDecisionInstance(
      decisionDefinitionDto.getId(),
      ImmutableMap.of(camInputVariable, "noMatch")
    );

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    DecisionReportDataDto reportData = createReportWithAllVersionSet(decisionDefinitionDto);
    reportData.setFilter(Lists.newArrayList(createStringInputVariableFilter(
      inputClauseId, operator, filterValues
    )));
    RawDataDecisionReportResultDto result = reportClient.evaluateRawReport(reportData).getResult();

    // then
    assertThat(result.getData()).hasSize(expectedInstanceCount);
  }

  @Test
  public void resultFilterByNotEqualStringInputVariableValue() {
    // given
    final String expectedCategoryInputValue = "Misc";
    final String categoryInputValueToExclude = "Travel Expenses";
    final String inputVariableIdToFilterOn = INPUT_CATEGORY_ID;

    final DecisionDefinitionEngineDto decisionDefinitionDto = engineIntegrationExtension.deployDecisionDefinition();
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(),
      createInputs(100.0, expectedCategoryInputValue)
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(),
      createInputs(200.0, categoryInputValueToExclude)
    );

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    DecisionReportDataDto reportData = createReportWithAllVersionSet(decisionDefinitionDto);
    reportData.setFilter(Lists.newArrayList(createStringInputVariableFilter(
      inputVariableIdToFilterOn, FilterOperatorConstants.NOT_IN, categoryInputValueToExclude
    )));
    RawDataDecisionReportResultDto result = reportClient.evaluateRawReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(1L);
    assertThat(result.getData()).hasSize(1);

    assertThat(result.getData().get(0).getInputVariables().get(inputVariableIdToFilterOn).getValue())
      .isEqualTo(expectedCategoryInputValue);
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
    engineIntegrationExtension.startDecisionInstance(
      decisionDefinitionDto.getId(),
      ImmutableMap.of(camInputVariable, "testValidMatch")
    );
    engineIntegrationExtension.startDecisionInstance(
      decisionDefinitionDto.getId(),
      ImmutableMap.of(camInputVariable, "whateverElse")
    );
    engineIntegrationExtension.startDecisionInstance(
      decisionDefinitionDto.getId(),
      Collections.singletonMap(camInputVariable, null)
    );
    engineIntegrationExtension.startDecisionInstance(
      decisionDefinitionDto.getId(),
      Collections.singletonMap(camInputVariable, new EngineVariableValue(null, "String"))
    );

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    DecisionReportDataDto reportData = createReportWithAllVersionSet(decisionDefinitionDto);

    InputVariableFilterDto variableFilterDto = new InputVariableFilterDto();
    variableFilterDto.setData(createUndefinedVariableFilterData(inputClauseId));

    reportData.setFilter(Collections.singletonList(variableFilterDto));
    RawDataDecisionReportResultDto result = reportClient.evaluateRawReport(reportData).getResult();


    // then
    assertThat(result.getData()).hasSize(2);
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

    engineIntegrationExtension.startDecisionInstance(
      decisionDefinitionDto.getId(),
      Collections.singletonMap(camInputVariable, null)
    );

    engineIntegrationExtension.startDecisionInstance(
      decisionDefinitionDto.getId(),
      ImmutableMap.of(camInputVariable, "testValidMatch")
    );
    engineDatabaseExtension.setDecisionOutputStringVariableValueToNull(outputClauseId, "testValidMatch");

    engineIntegrationExtension.startDecisionInstance(
      decisionDefinitionDto.getId(),
      ImmutableMap.of(camInputVariable, "noMatchingOutputValue")
    );

    engineIntegrationExtension.startDecisionInstance(
      decisionDefinitionDto.getId(),
      ImmutableMap.of(camInputVariable, "testValidMatch")
    );

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    DecisionReportDataDto reportData = createReportWithAllVersionSet(decisionDefinitionDto);

    OutputVariableFilterDto variableFilterDto = new OutputVariableFilterDto();
    variableFilterDto.setData(createUndefinedVariableFilterData(outputClauseId));

    reportData.setFilter(Collections.singletonList(variableFilterDto));
    RawDataDecisionReportResultDto result = reportClient.evaluateRawReport(reportData).getResult();

    // then
    assertThat(result.getData()).hasSize(3);
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
