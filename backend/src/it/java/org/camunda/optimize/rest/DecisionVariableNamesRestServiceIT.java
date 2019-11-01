/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.OptimizeRequestExecutor;
import org.camunda.optimize.dto.optimize.importing.DecisionDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.query.variable.DecisionVariableNameRequestDto;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.util.List;
import java.util.stream.Stream;

import static org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtension.DEFAULT_ENGINE_ALIAS;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_DEFINITION_INDEX_NAME;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class DecisionVariableNamesRestServiceIT extends AbstractIT {

  private static final String TEST_VARIANT_INPUTS = "inputs";
  private static final String TEST_VARIANT_OUTPUTS = "outputs";
  private static final String DECISION_KEY = "decisionKey";
  private static final String DECISION_VERSION = "1";

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
    deployDefinition();
    DecisionVariableNameRequestDto request = generateDefaultVariableRequest();

    // when
    List responseList = getExecutor(inputOutput, request)
      .executeAndReturnList(String.class, 200);

    // then
    assertThat(responseList.isEmpty(), is(true));
  }

  @ParameterizedTest
  @MethodSource("getInputOutputArgs")
  public void missingDecisionDefinitionKeyQueryParamThrowsError(String inputOutput) {
    // given
    DecisionVariableNameRequestDto request = generateDefaultVariableRequest();
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
    DecisionVariableNameRequestDto request = generateDefaultVariableRequest();
    request.setDecisionDefinitionVersions(null);

    // when
    Response response = getExecutor(inputOutput, request)
      .execute();

    // then
    assertThat(response.getStatus(), is(500));
  }

  private DecisionVariableNameRequestDto generateDefaultVariableRequest() {
    DecisionVariableNameRequestDto requestDto = new DecisionVariableNameRequestDto();
    requestDto.setDecisionDefinitionKey(DECISION_KEY);
    requestDto.setDecisionDefinitionVersion(DECISION_VERSION);
    return requestDto;
  }

  private void deployDefinition() {
    DecisionDefinitionOptimizeDto definition = new DecisionDefinitionOptimizeDto();
    definition.setId("fooId");
    definition.setName("fooName");
    definition.setKey(DECISION_KEY);
    definition.setVersion(DECISION_VERSION);
    definition.setTenantId(null);
    definition.setDmn10Xml("someXml");
    definition.setEngine(DEFAULT_ENGINE_ALIAS);
    elasticSearchIntegrationTestExtension.addEntryToElasticsearch(DECISION_DEFINITION_INDEX_NAME, definition.getId(), definition);
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
