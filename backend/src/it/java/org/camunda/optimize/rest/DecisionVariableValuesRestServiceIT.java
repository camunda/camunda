/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.OptimizeRequestExecutor;
import org.camunda.optimize.dto.optimize.query.variable.DecisionVariableValueRequestDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.util.List;
import java.util.stream.Stream;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class DecisionVariableValuesRestServiceIT extends AbstractIT {

  private static final String TEST_VARIANT_INPUTS = "inputs";
  private static final String TEST_VARIANT_OUTPUTS = "outputs";

  @ParameterizedTest(name = "get variable values without authentication for type {0}")
  @MethodSource("getInputOutputArgs")
  public void getVariableValuesWithoutAuthentication(String inputOutput) {
    // when
    Response response = getExecutor(inputOutput, null)
      .withoutAuthentication()
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @ParameterizedTest(name = "get variable values for type {0}")
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

  @ParameterizedTest(name = "missing variable id query param throws error for type {0}")
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

  @ParameterizedTest(name = "missing variable type query param throws error for type {0}")
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

  @ParameterizedTest(name = "missing decision definition key query param throws error for type {0}")
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

  @ParameterizedTest(name = "missing decision definition version query param throws error for type {0}")
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
        return embeddedOptimizeExtension
          .getRequestExecutor()
          .buildDecisionInputVariableValuesRequest(requestDto);
      case TEST_VARIANT_OUTPUTS:
        return embeddedOptimizeExtension
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
