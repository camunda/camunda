/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import org.camunda.optimize.OptimizeRequestExecutor;
import org.camunda.optimize.dto.optimize.query.variable.DecisionVariableValueRequestDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.test.it.extension.ElasticSearchIntegrationTestExtensionRule;
import org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtensionRule;
import org.camunda.optimize.test.it.extension.EngineIntegrationExtensionRule;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.util.List;
import java.util.stream.Stream;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class DecisionVariableValuesRestServiceIT {

  private static final String TEST_VARIANT_INPUTS = "inputs";
  private static final String TEST_VARIANT_OUTPUTS = "outputs";

  @RegisterExtension
  @Order(1)
  public ElasticSearchIntegrationTestExtensionRule elasticSearchIntegrationTestExtensionRule = new ElasticSearchIntegrationTestExtensionRule();
  @RegisterExtension
  @Order(2)
  public EngineIntegrationExtensionRule engineIntegrationExtensionRule = new EngineIntegrationExtensionRule();
  @RegisterExtension
  @Order(3)
  public EmbeddedOptimizeExtensionRule embeddedOptimizeExtensionRule = new EmbeddedOptimizeExtensionRule();

  @ParameterizedTest
  @MethodSource("getInputOutputArgs")
  public void getVariableValuesWithoutAuthentication(String inputOutput) {
    // when
    Response response = getExecutor(inputOutput, null)
      .withoutAuthentication()
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @ParameterizedTest
  @MethodSource("getInputOutputArgs")
  public void getVariableValues(String inputOutput) {
    // given
    DecisionVariableValueRequestDto request = generateDefaultVariableRequest();

    // when
    List responseList = getExecutor(inputOutput, request)
      .executeAndReturnList(String.class, 200);

    // then
    assertThat(responseList.isEmpty(), is(true));
  }

  @ParameterizedTest
  @MethodSource("getInputOutputArgs")
  public void missingVariableIdQueryParamThrowsError(String inputOutput) {
    // given
    DecisionVariableValueRequestDto request = generateDefaultVariableRequest();
    request.setVariableId(null);

    // when
    Response response = getExecutor(inputOutput, request)
      .execute();

    // then
    assertThat(response.getStatus(), is(500));
  }

  @ParameterizedTest
  @MethodSource("getInputOutputArgs")
  public void missingVariableTypeQueryParamThrowsError(String inputOutput) {
    // given
    DecisionVariableValueRequestDto request = generateDefaultVariableRequest();
    request.setVariableType(null);

    // when
    Response response = getExecutor(inputOutput, request)
      .execute();

    // then
    assertThat(response.getStatus(), is(500));
  }

  @ParameterizedTest
  @MethodSource("getInputOutputArgs")
  public void missingDecisionDefinitionKeyQueryParamThrowsError(String inputOutput) {
    // given
    DecisionVariableValueRequestDto request = generateDefaultVariableRequest();
    request.setDecisionDefinitionKey(null);

    // when
    Response response = getExecutor(inputOutput, request)
      .execute();

    // then
    assertThat(response.getStatus(), is(500));
  }

  @ParameterizedTest
  @MethodSource("getInputOutputArgs")
  public void missingDecisionDefinitionVersionQueryParamThrowsError(String inputOutput) {
    // given
    DecisionVariableValueRequestDto request = generateDefaultVariableRequest();
    request.setDecisionDefinitionVersions(null);

    // when
    Response response = getExecutor(inputOutput, request)
      .execute();

    // then
    assertThat(response.getStatus(), is(500));
  }

  private static Stream<String> getInputOutputArgs() {
    return Stream.of(TEST_VARIANT_INPUTS, TEST_VARIANT_OUTPUTS);
  }

  private OptimizeRequestExecutor getExecutor(String inputsOrOutputs, DecisionVariableValueRequestDto requestDto) {
    switch (inputsOrOutputs) {
      case TEST_VARIANT_INPUTS:
        return embeddedOptimizeExtensionRule
          .getRequestExecutor()
          .buildDecisionInputVariableValuesRequest(requestDto);
      case TEST_VARIANT_OUTPUTS:
        return embeddedOptimizeExtensionRule
          .getRequestExecutor()
          .buildDecisionOutputVariableValuesRequest(requestDto);
      default:
        throw new RuntimeException("unsupported type " + inputsOrOutputs);
    }
  }

  private DecisionVariableValueRequestDto generateDefaultVariableRequest() {
    DecisionVariableValueRequestDto requestDto = new DecisionVariableValueRequestDto();
    requestDto.setDecisionDefinitionKey("aKey");
    requestDto.setDecisionDefinitionVersion("aVersion");
    requestDto.setVariableId("foo");
    requestDto.setVariableType(VariableType.STRING);
    return requestDto;
  }

}
