/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.OptimizeRequestExecutor;
import org.camunda.optimize.dto.engine.definition.DecisionDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.variable.DecisionVariableNameResponseDto;
import org.camunda.optimize.dto.optimize.query.variable.DecisionVariableNameRequestDto;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class DecisionVariableNamesRestServiceIT extends AbstractIT {

  private static final String TEST_VARIANT_INPUTS = "inputs";
  private static final String TEST_VARIANT_OUTPUTS = "outputs";

  @ParameterizedTest
  @MethodSource("getInputOutputArgs")
  public void getVariableNamesWithoutAuthentication(String inputOutput) {
    // given
    final DecisionDefinitionEngineDto decisionDefinitionEngineDto = deployDefinitionAndStartInstance();
    DecisionVariableNameRequestDto request = generateDefaultVariableNameRequest(decisionDefinitionEngineDto);

    // when
    List<DecisionVariableNameResponseDto> responseList = getExecutor(inputOutput, request)
      .withoutAuthentication()
      .executeAndReturnList(DecisionVariableNameResponseDto.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(responseList).isNotEmpty();
  }

  @ParameterizedTest
  @MethodSource("getInputOutputArgs")
  public void getVariableNamesWithAuthentication(String inputOutput) {
    // given
    final DecisionDefinitionEngineDto decisionDefinitionEngineDto = deployDefinitionAndStartInstance();
    DecisionVariableNameRequestDto request = generateDefaultVariableNameRequest(decisionDefinitionEngineDto);

    // when
    List<DecisionVariableNameResponseDto> responseList = getExecutor(inputOutput, request)
      .executeAndReturnList(DecisionVariableNameResponseDto.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(responseList).isNotEmpty();
  }

  @ParameterizedTest
  @MethodSource("getInputOutputArgs")
  public void missingDecisionDefinitionKeyQueryParamThrowsError(String inputOutput) {
    // given
    final DecisionDefinitionEngineDto decisionDefinitionEngineDto = deployDefinitionAndStartInstance();
    DecisionVariableNameRequestDto request = generateDefaultVariableNameRequest(decisionDefinitionEngineDto);
    request.setDecisionDefinitionKey(null);

    // when
    Response response = getExecutor(inputOutput, request)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @ParameterizedTest
  @MethodSource("getInputOutputArgs")
  public void missingDecisionDefinitionVersionQueryParamDoesNotThrowError(String inputOutput) {
    // given
    final DecisionDefinitionEngineDto decisionDefinitionEngineDto = deployDefinitionAndStartInstance();
    DecisionVariableNameRequestDto request = generateDefaultVariableNameRequest(decisionDefinitionEngineDto);
    request.setDecisionDefinitionVersions(null);

    // when
    Response response = getExecutor(inputOutput, request)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
  }

  private DecisionVariableNameRequestDto generateDefaultVariableNameRequest(final DecisionDefinitionEngineDto definition) {
    DecisionVariableNameRequestDto requestDto = new DecisionVariableNameRequestDto();
    requestDto.setDecisionDefinitionKey(definition.getKey());
    requestDto.setDecisionDefinitionVersion(definition.getVersionAsString());
    return requestDto;
  }

  private DecisionDefinitionEngineDto deployDefinitionAndStartInstance() {
    final DecisionDefinitionEngineDto decisionDefinitionEngineDto =
      engineIntegrationExtension.deployAndStartDecisionDefinition();
    importAllEngineEntitiesFromScratch();
    return decisionDefinitionEngineDto;
  }

  private static Stream<String> getInputOutputArgs() {
    return Stream.of(TEST_VARIANT_INPUTS, TEST_VARIANT_OUTPUTS);
  }

  private OptimizeRequestExecutor getExecutor(String inputsOrOutputs, DecisionVariableNameRequestDto requestDto) {
    switch (inputsOrOutputs) {
      case TEST_VARIANT_INPUTS:
        return embeddedOptimizeExtension
          .getRequestExecutor()
          .buildDecisionInputVariableNamesRequest(requestDto);
      case TEST_VARIANT_OUTPUTS:
        return embeddedOptimizeExtension
          .getRequestExecutor()
          .buildDecisionOutputVariableNamesRequest(requestDto);
      default:
        throw new RuntimeException("unsupported type " + inputsOrOutputs);
    }
  }

}
