/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.compatibility;

import com.google.common.collect.ImmutableMap;
import org.assertj.core.groups.Tuple;
import org.camunda.optimize.dto.engine.definition.DecisionDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.SimpleDefinitionDto;
import org.camunda.optimize.dto.optimize.query.definition.DefinitionResponseDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.variable.DecisionVariableNameRequestDto;
import org.camunda.optimize.dto.optimize.query.variable.DecisionVariableNameResponseDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedDecisionReportEvaluationResponseDto;
import org.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
import org.camunda.optimize.service.es.report.decision.AbstractDecisionDefinitionIT;
import org.camunda.optimize.test.util.decision.DecisionReportDataBuilder;
import org.camunda.optimize.test.util.decision.DecisionReportDataType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class DmnCompatibilityIT extends AbstractDecisionDefinitionIT {

  private static final String INPUT_CUSTOMER_STATUS_ID = "input1";
  private static final String INPUT_ORDER_SUM_ID = "input2";
  private static final String INPUT_CUSTOMER_STATUS_VAR = "status";
  private static final String INPUT_ORDER_SUM_VAR = "sum";
  private static final String OUTPUT_CHECK_RESULT_ID = "output1";
  private static final String OUTPUT_REASON_ID = "output2";

  @ParameterizedTest
  @MethodSource("compatibleDmnVersionDiagrams")
  public void getInputVariableNames(final String pathToDiagram) {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto =
      engineIntegrationExtension.deployDecisionDefinition(pathToDiagram);

    importAllEngineEntitiesFromScratch();

    // when
    List<DecisionVariableNameResponseDto> variableResponse = variablesClient.getDecisionInputVariableNames(
      new DecisionVariableNameRequestDto(
        decisionDefinitionDto.getKey(),
        decisionDefinitionDto.getVersionAsString(),
        decisionDefinitionDto.getTenantId().orElse(null)
      )
    );

    // then
    assertThat(variableResponse).hasSize(2);
    assertThat(variableResponse.get(0).getName()).isEqualTo("Customer Status");
    assertThat(variableResponse.get(0).getId()).isNotNull();
    assertThat(variableResponse.get(0).getType()).isEqualTo(VariableType.STRING);
    assertThat(variableResponse.get(1).getName()).isEqualTo("Order Sum");
    assertThat(variableResponse.get(1).getId()).isNotNull();
    assertThat(variableResponse.get(1).getType()).isEqualTo(VariableType.DOUBLE);
  }

  @ParameterizedTest
  @MethodSource("compatibleDmnVersionDiagrams")
  public void getOutputVariableNames(final String pathToDiagram) {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto =
      engineIntegrationExtension.deployDecisionDefinition(pathToDiagram);

    importAllEngineEntitiesFromScratch();

    // when
    List<DecisionVariableNameResponseDto> variableResponse = variablesClient.getDecisionOutputVariableNames(
      new DecisionVariableNameRequestDto(
        decisionDefinitionDto.getKey(),
        decisionDefinitionDto.getVersionAsString(),
        decisionDefinitionDto.getTenantId().orElse(null)
      )
    );

    // then
    assertThat(variableResponse).hasSize(2);
    assertThat(variableResponse.get(0).getName()).isEqualTo("Check Result");
    assertThat(variableResponse.get(0).getId()).isNotNull();
    assertThat(variableResponse.get(0).getType()).isEqualTo(VariableType.STRING);
    assertThat(variableResponse.get(1).getName()).isEqualTo("Reason");
    assertThat(variableResponse.get(1).getId()).isNotNull();
    assertThat(variableResponse.get(1).getType()).isEqualTo(VariableType.STRING);
  }

  @ParameterizedTest
  @MethodSource("compatibleDmnVersionDiagrams")
  public void getInputVariableValues(final String pathToDiagram) {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto =
      engineIntegrationExtension.deployDecisionDefinition(pathToDiagram);
    startDecisionInstanceWithInputs(decisionDefinitionDto, "bronze", 200.0);
    startDecisionInstanceWithInputs(decisionDefinitionDto, "silver", 300.0);
    startDecisionInstanceWithInputs(decisionDefinitionDto, "gold", 500.0);

    importAllEngineEntitiesFromScratch();

    // when
    List<String> customerStatusVariableValues = variablesClient.getDecisionInputVariableValues(
      decisionDefinitionDto,
      INPUT_CUSTOMER_STATUS_ID,
      VariableType.STRING
    );
    List<String> orderSumInputVariableValues = variablesClient.getDecisionInputVariableValues(
      decisionDefinitionDto,
      INPUT_ORDER_SUM_ID,
      VariableType.DOUBLE
    );

    // then
    assertThat(customerStatusVariableValues).hasSize(3);
    assertThat(customerStatusVariableValues).contains("bronze", "silver", "gold");

    assertThat(orderSumInputVariableValues).hasSize(3);
    assertThat(orderSumInputVariableValues).contains("200.0", "300.0", "500.0");
  }

  @ParameterizedTest
  @MethodSource("compatibleDmnVersionDiagrams")
  public void getOutputVariableValues(final String pathToDiagram) {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto =
      engineIntegrationExtension.deployDecisionDefinition(pathToDiagram);
    startDecisionInstanceWithInputs(decisionDefinitionDto, "bronze", 200.0);
    startDecisionInstanceWithInputs(decisionDefinitionDto, "silver", 300.0);
    startDecisionInstanceWithInputs(decisionDefinitionDto, "gold", 500.0);

    importAllEngineEntitiesFromScratch();

    // when
    List<String> output1 = variablesClient.getDecisionOutputVariableValues(
      decisionDefinitionDto,
      OUTPUT_CHECK_RESULT_ID,
      VariableType.STRING
    );
    List<String> output2 = variablesClient.getDecisionOutputVariableValues(
      decisionDefinitionDto,
      OUTPUT_REASON_ID,
      VariableType.STRING
    );

    // then
    assertThat(output1).hasSize(2);
    assertThat(output1).contains("notok", "ok");

    assertThat(output2).hasSize(3);
    assertThat(output2).contains(
      "work on your status first, as bronze you're not going to get anything",
      "you little fish will get what you want",
      "you get anything you want"
    );
  }

  @ParameterizedTest
  @MethodSource("compatibleDmnVersionDiagrams")
  public void evaluateByInputVariable(final String pathToDiagram) {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto =
      engineIntegrationExtension.deployDecisionDefinition(pathToDiagram);
    startDecisionInstanceWithInputs(decisionDefinitionDto, "gold", 500.0);
    startDecisionInstanceWithInputs(decisionDefinitionDto, "gold", 500.0);

    importAllEngineEntitiesFromScratch();

    // when
    final ReportResultResponseDto<List<MapResultEntryDto>> result = evaluateDecisionInstanceFrequencyByInputVariable(
      decisionDefinitionDto, INPUT_CUSTOMER_STATUS_ID
    ).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(2L);
    assertThat(result.getFirstMeasureData())
      .isNotNull()
      .hasSize(1)
      .extracting(MapResultEntryDto::getKey, MapResultEntryDto::getValue)
      .containsExactly(Tuple.tuple("gold", 2.0));
  }

  @ParameterizedTest
  @MethodSource("compatibleDmnVersionDiagrams")
  public void evaluateByOutputVariable(final String pathToDiagram) {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto =
      engineIntegrationExtension.deployDecisionDefinition(pathToDiagram);
    startDecisionInstanceWithInputs(decisionDefinitionDto, "gold", 500.0);
    startDecisionInstanceWithInputs(decisionDefinitionDto, "gold", 500.0);

    importAllEngineEntitiesFromScratch();

    // when
    final ReportResultResponseDto<List<MapResultEntryDto>> result = evaluateDecisionInstanceFrequencyByOutputVariable(
      decisionDefinitionDto, OUTPUT_CHECK_RESULT_ID
    ).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(2L);
    assertThat(result.getFirstMeasureData())
      .isNotNull()
      .hasSize(1)
      .extracting(MapResultEntryDto::getKey, MapResultEntryDto::getValue)
      .containsExactly(Tuple.tuple("ok", 2.0));
  }

  @ParameterizedTest
  @MethodSource("compatibleDmnVersionDiagrams")
  public void getDefinitions(final String pathToDiagram) {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto =
      engineIntegrationExtension.deployDecisionDefinition(pathToDiagram);

    importAllEngineEntitiesFromScratch();

    // when
    final List<DefinitionResponseDto> definitions = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetDefinitions()
      .executeAndReturnList(DefinitionResponseDto.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(definitions)
      .hasSize(1)
      .extracting(SimpleDefinitionDto::getKey)
      .containsExactly(decisionDefinitionDto.getKey());
  }

  private AuthorizedDecisionReportEvaluationResponseDto<List<MapResultEntryDto>> evaluateDecisionInstanceFrequencyByInputVariable(
    final DecisionDefinitionEngineDto decisionDefinitionDto,
    final String variableId) {
    DecisionReportDataDto reportData = DecisionReportDataBuilder.create()
      .setDecisionDefinitionKey(decisionDefinitionDto.getKey())
      .setDecisionDefinitionVersion(decisionDefinitionDto.getVersionAsString())
      .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_INPUT_VARIABLE)
      .setVariableId(variableId)
      .setVariableName(null)
      .setVariableType(VariableType.STRING)
      .build();
    return reportClient.evaluateMapReport(reportData);
  }

  private AuthorizedDecisionReportEvaluationResponseDto<List<MapResultEntryDto>> evaluateDecisionInstanceFrequencyByOutputVariable(
    final DecisionDefinitionEngineDto decisionDefinitionDto,
    final String variableId) {
    DecisionReportDataDto reportData = DecisionReportDataBuilder.create()
      .setDecisionDefinitionKey(decisionDefinitionDto.getKey())
      .setDecisionDefinitionVersion(decisionDefinitionDto.getVersionAsString())
      .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_OUTPUT_VARIABLE)
      .setVariableId(variableId)
      .setVariableName(null)
      .setVariableType(VariableType.STRING)
      .build();
    return reportClient.evaluateMapReport(reportData);
  }

  private void startDecisionInstanceWithInputs(final DecisionDefinitionEngineDto decisionDefinitionDto,
                                               final String customerStatus,
                                               final double orderSum) {
    final Map<String, Object> inputs = ImmutableMap.of(INPUT_CUSTOMER_STATUS_VAR, customerStatus,
                                                       INPUT_ORDER_SUM_VAR, orderSum
    );
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto.getId(), inputs);
  }

  private static Stream<String> compatibleDmnVersionDiagrams() {
    return Stream.of(
      "dmn/compatibility/Example-DMN-1.1.dmn",
      "dmn/compatibility/Example-DMN-1.2.dmn",
      "dmn/compatibility/Example-DMN-1.3.dmn"
    );
  }
}
