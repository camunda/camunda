/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.filter.decision.variable;

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

import javax.ws.rs.core.Response;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static java.lang.Double.parseDouble;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator.GREATER_THAN;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator.IN;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator.LESS_THAN_EQUALS;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator.NOT_IN;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator.RELATIVE_OPERATORS;
import static org.camunda.optimize.test.util.decision.DecisionFilterUtilHelper.createNumericInputVariableFilter;
import static org.camunda.optimize.util.DmnModels.INPUT_AMOUNT_ID;

public class DecisionNumberVariableFilterIT extends AbstractDecisionDefinitionIT {

  @Test
  public void resultFilterByEqualNumberInputVariable() {
    // given
    final String categoryInputValueToFilterFor = "200.0";
    final String inputVariableIdToFilterOn = INPUT_AMOUNT_ID;

    final DecisionDefinitionEngineDto decisionDefinitionDto = engineIntegrationExtension.deployDecisionDefinition();
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(),
      createInputs(100.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(),
      createInputs(parseDouble(categoryInputValueToFilterFor), "Misc")
    );

    importAllEngineEntitiesFromScratch();

    // when
    DecisionReportDataDto reportData = createReportWithAllVersionSet(decisionDefinitionDto);
    reportData.setFilter(Lists.newArrayList(createNumericInputVariableFilter(
      inputVariableIdToFilterOn, IN, categoryInputValueToFilterFor
    )));
    ReportResultResponseDto<List<RawDataDecisionInstanceDto>> result =
      reportClient.evaluateDecisionRawReport(reportData)
      .getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(1L);
    assertThat(result.getData()).hasSize(1);

    assertThat(result.getData().get(0).getInputVariables().get(inputVariableIdToFilterOn).getValue())
      .isEqualTo(categoryInputValueToFilterFor);
  }

  @Test
  public void resultFilterByEqualNumberInputVariableMultiple() {
    // given
    final String firstCategoryInputValueToFilterFor = "200.0";
    final String secondCategoryInputValueToFilterFor = "300.0";
    final String inputVariableIdToFilterOn = INPUT_AMOUNT_ID;

    final DecisionDefinitionEngineDto decisionDefinitionDto = engineIntegrationExtension.deployDecisionDefinition();
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(),
      createInputs(100.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(),
      createInputs(parseDouble(firstCategoryInputValueToFilterFor), "Misc")
    );

    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(),
      createInputs(parseDouble(secondCategoryInputValueToFilterFor), "Misc")
    );

    importAllEngineEntitiesFromScratch();

    // when
    DecisionReportDataDto reportData = createReportWithAllVersionSet(decisionDefinitionDto);
    reportData.setFilter(Lists.newArrayList(createNumericInputVariableFilter(
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
    return getNumericTypes().stream()
      .flatMap(type -> Stream.of(
        Arguments.of(type, IN, new String[]{null}, 2),
        Arguments.of(type, IN, new String[]{null, "100"}, 3),
        Arguments.of(type, NOT_IN, new String[]{null}, 2),
        Arguments.of(type, NOT_IN, new String[]{null, "100"}, 1)
      ));
  }

  @ParameterizedTest
  @MethodSource("nullFilterScenarios")
  public void resultFilterNumberInputVariableSupportsNullValue(final DecisionTypeRef type,
                                                               final FilterOperator operator,
                                                               final String[] filterValues,
                                                               final Integer expectedInstanceCount) {
    // given
    final String inputClauseId = "TestyTest";
    final String camInputVariable = "putIn";
    final DecisionDefinitionEngineDto decisionDefinitionDto = deploySimpleInputDecisionDefinition(
      inputClauseId, camInputVariable, type
    );
    // instance where the variable is not defined
    engineIntegrationExtension.startDecisionInstance(
      decisionDefinitionDto.getId(), Collections.singletonMap(camInputVariable, null)
    );
    // instance where the variable is set to null
    engineIntegrationExtension.startDecisionInstance(
      decisionDefinitionDto.getId(),
      Collections.singletonMap(camInputVariable, new EngineVariableValue(null, type.getId()))
    );
    engineIntegrationExtension.startDecisionInstance(
      decisionDefinitionDto.getId(), ImmutableMap.of(camInputVariable, "100")
    );
    engineIntegrationExtension.startDecisionInstance(
      decisionDefinitionDto.getId(), ImmutableMap.of(camInputVariable, "200")
    );

    importAllEngineEntitiesFromScratch();

    // when
    DecisionReportDataDto reportData = createReportWithAllVersionSet(decisionDefinitionDto);
    reportData.setFilter(Lists.newArrayList(createNumericInputVariableFilter(
      inputClauseId, operator, filterValues
    )));
    ReportResultResponseDto<List<RawDataDecisionInstanceDto>> result = reportClient.evaluateDecisionRawReport(reportData).getResult();

    // then
    assertThat(result.getData()).hasSize(expectedInstanceCount);
  }

  @Test
  public void resultFilterByNotEqualNumberInputVariable() {
    // given
    final String expectedCategoryInputValue = "100.0";
    final String categoryInputValueToExclude = "200.0";
    final String inputVariableIdToFilterOn = INPUT_AMOUNT_ID;

    final DecisionDefinitionEngineDto decisionDefinitionDto = engineIntegrationExtension.deployDecisionDefinition();
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(),
      createInputs(parseDouble(expectedCategoryInputValue), "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(),
      createInputs(parseDouble(categoryInputValueToExclude), "Misc")
    );

    importAllEngineEntitiesFromScratch();

    // when
    DecisionReportDataDto reportData = createReportWithAllVersionSet(decisionDefinitionDto);
    reportData.setFilter(Lists.newArrayList(createNumericInputVariableFilter(
      inputVariableIdToFilterOn, NOT_IN, categoryInputValueToExclude
    )));
    ReportResultResponseDto<List<RawDataDecisionInstanceDto>> result = reportClient.evaluateDecisionRawReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(1L);
    assertThat(result.getData()).hasSize(1);

    assertThat(result.getData().get(0).getInputVariables().get(inputVariableIdToFilterOn).getValue())
      .isEqualTo(expectedCategoryInputValue);
  }

  @Test
  public void resultFilterByLessOrEqualNumberInputVariable() {
    // given
    final String categoryInputValueToFilterFor = "200.0";

    final DecisionDefinitionEngineDto decisionDefinitionDto = engineIntegrationExtension.deployDecisionDefinition();
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(),
      createInputs(100.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(),
      createInputs(parseDouble(categoryInputValueToFilterFor), "Misc")
    );

    importAllEngineEntitiesFromScratch();

    // when
    DecisionReportDataDto reportData = createReportWithAllVersionSet(decisionDefinitionDto);
    reportData.setFilter(Lists.newArrayList(createNumericInputVariableFilter(
      INPUT_AMOUNT_ID, LESS_THAN_EQUALS, categoryInputValueToFilterFor
    )));
    ReportResultResponseDto<List<RawDataDecisionInstanceDto>> result = reportClient.evaluateDecisionRawReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(2L);
    assertThat(result.getData()).hasSize(2);
  }

  @Test
  public void resultFilterByGreaterThanNumberInputVariable() {
    // given
    final String categoryInputValueToFilter = "100.0";
    final String inputVariableIdToFilterOn = INPUT_AMOUNT_ID;

    final DecisionDefinitionEngineDto decisionDefinitionDto = engineIntegrationExtension.deployDecisionDefinition();
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(),
      createInputs(100.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(),
      createInputs(200.0, "Misc")
    );

    importAllEngineEntitiesFromScratch();

    // when
    DecisionReportDataDto reportData = createReportWithAllVersionSet(decisionDefinitionDto);
    reportData.setFilter(Lists.newArrayList(createNumericInputVariableFilter(
      inputVariableIdToFilterOn, GREATER_THAN, categoryInputValueToFilter
    )));
    ReportResultResponseDto<List<RawDataDecisionInstanceDto>> result = reportClient.evaluateDecisionRawReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(1L);
    assertThat(result.getData()).hasSize(1);

    assertThat(result.getData().get(0).getInputVariables().get(inputVariableIdToFilterOn).getValue())
      .isEqualTo("200.0");
  }

  @ParameterizedTest
  @MethodSource("getRelativeOperators")
  public void resultFilterByNumericInputVariableValueNullFailsForRelativeOperators(final FilterOperator operator) {
    // given
    final String inputClauseId = "TestyTest";
    final String camInputVariable = "putIn";
    final DecisionDefinitionEngineDto decisionDefinitionDto = deploySimpleInputDecisionDefinition(
      inputClauseId, camInputVariable, DecisionTypeRef.DOUBLE
    );

    importAllEngineEntitiesFromScratch();

    // when
    DecisionReportDataDto reportData = createReportWithAllVersionSet(decisionDefinitionDto);
    reportData.setFilter(Lists.newArrayList(createNumericInputVariableFilter(
      inputClauseId, operator, new String[]{null}
    )));
    final Response evaluateHttpResponse = embeddedOptimizeExtension.getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      .execute();

    // then
    assertThat(evaluateHttpResponse.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  private static Set<DecisionTypeRef> getNumericTypes() {
    return DecisionTypeRef.getNumericTypes();
  }

  private static Set<FilterOperator> getRelativeOperators() {
    return RELATIVE_OPERATORS;
  }

}
