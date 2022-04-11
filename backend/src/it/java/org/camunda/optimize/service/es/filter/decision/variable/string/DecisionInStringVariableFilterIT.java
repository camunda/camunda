/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.filter.decision.variable.string;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import org.camunda.optimize.dto.engine.definition.DecisionDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.RawDataDecisionInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator;
import org.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
import org.camunda.optimize.service.es.report.decision.AbstractDecisionDefinitionIT;
import org.camunda.optimize.test.it.extension.EngineVariableValue;
import org.camunda.optimize.test.util.decision.DecisionTypeRef;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator.IN;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator.NOT_IN;
import static org.camunda.optimize.test.util.decision.DecisionFilterUtilHelper.createStringInputVariableFilter;
import static org.camunda.optimize.util.DmnModels.INPUT_CATEGORY_ID;

public class DecisionInStringVariableFilterIT extends AbstractDecisionDefinitionIT {

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

    importAllEngineEntitiesFromScratch();

    // when
    DecisionReportDataDto reportData = createReportWithAllVersionSet(decisionDefinitionDto);
    reportData.setFilter(Lists.newArrayList(createStringInputVariableFilter(
      inputVariableIdToFilterOn, IN, categoryInputValueToFilterFor
    )));
    ReportResultResponseDto<List<RawDataDecisionInstanceDto>> result = reportClient.evaluateDecisionRawReport(reportData).getResult();

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

    importAllEngineEntitiesFromScratch();

    // when
    DecisionReportDataDto reportData = createReportWithAllVersionSet(decisionDefinitionDto);
    reportData.setFilter(Lists.newArrayList(createStringInputVariableFilter(
      inputVariableIdToFilterOn, IN,
      firstCategoryInputValueToFilterFor, secondCategoryInputValueToFilterFor
    )));
    ReportResultResponseDto<List<RawDataDecisionInstanceDto>> result = reportClient.evaluateDecisionRawReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(2L);
    assertThat(result.getData()).hasSize(2);

    assertThat(
      result.getData().stream().map(entry -> entry.getInputVariables().get(inputVariableIdToFilterOn).getValue())
        .collect(toList())
    ).containsExactlyInAnyOrder(firstCategoryInputValueToFilterFor, secondCategoryInputValueToFilterFor);
  }

  private static Stream<Arguments> nullFilterScenarios() {
    return Stream.of(
      Arguments.of(IN, new String[]{null}, 2),
      Arguments.of(IN, new String[]{null, "validMatch"}, 3),
      Arguments.of(NOT_IN, new String[]{null}, 2),
      Arguments.of(NOT_IN, new String[]{null, "validMatch"}, 1)
    );
  }

  @ParameterizedTest
  @MethodSource("nullFilterScenarios")
  public void resultFilterStringInputVariableSupportsNullValue(final FilterOperator operator,
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
    // instance where the variable has the value null
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

    importAllEngineEntitiesFromScratch();

    // when
    DecisionReportDataDto reportData = createReportWithAllVersionSet(decisionDefinitionDto);
    reportData.setFilter(Lists.newArrayList(createStringInputVariableFilter(
      inputClauseId, operator, filterValues
    )));
    ReportResultResponseDto<List<RawDataDecisionInstanceDto>> result = reportClient.evaluateDecisionRawReport(reportData).getResult();

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

    importAllEngineEntitiesFromScratch();

    // when
    DecisionReportDataDto reportData = createReportWithAllVersionSet(decisionDefinitionDto);
    reportData.setFilter(Lists.newArrayList(createStringInputVariableFilter(
      inputVariableIdToFilterOn, NOT_IN, categoryInputValueToExclude
    )));
    ReportResultResponseDto<List<RawDataDecisionInstanceDto>> result = reportClient.evaluateDecisionRawReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(1L);
    assertThat(result.getData()).hasSize(1);

    assertThat(result.getData().get(0).getInputVariables().get(inputVariableIdToFilterOn).getValue())
      .isEqualTo(expectedCategoryInputValue);
  }

}
