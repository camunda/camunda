/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import org.camunda.optimize.OptimizeRequestExecutor;
import org.camunda.optimize.dto.optimize.query.variable.DecisionVariableValueRequestDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import javax.ws.rs.core.Response;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@RunWith(Parameterized.class)
public class DecisionVariableValuesRestServiceIT {

  private static final String TEST_VARIANT_INPUTS = "inputs";
  private static final String TEST_VARIANT_OUTPUTS = "outputs";

  @Parameterized.Parameters
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[][]{
      {TEST_VARIANT_INPUTS}, {TEST_VARIANT_OUTPUTS}
    });
  }

  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  private Function<DecisionVariableValueRequestDto, OptimizeRequestExecutor> getVariablesExecutorFunction;

  @Rule
  public RuleChain chain = RuleChain
    .outerRule(elasticSearchRule).around(engineRule).around(embeddedOptimizeRule);

  public DecisionVariableValuesRestServiceIT(String inputsOrOutputs) {
    switch (inputsOrOutputs) {
      case TEST_VARIANT_INPUTS:
        getVariablesExecutorFunction = this::getInputVariableValueResponse;
        break;
      case TEST_VARIANT_OUTPUTS:
        getVariablesExecutorFunction = this::getOutputVariableValueResponse;
        break;
      default:
        throw new RuntimeException("unsupported type " + inputsOrOutputs);
    }
  }

  @Test
  public void getVariableValuesWithoutAuthentication() {
    // when
    Response response = getVariablesExecutorFunction.apply(null)
      .withoutAuthentication()
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void getVariableValues() {
    // given
    DecisionVariableValueRequestDto request = generateDefaultVariableRequest();

    // when
    List responseList = getVariablesExecutorFunction.apply(request)
      .executeAndReturnList(String.class, 200);

    // then
    assertThat(responseList.isEmpty(), is(true));
  }

  @Test
  public void missingVariableIdQueryParamThrowsError() {
    // given
    DecisionVariableValueRequestDto request = generateDefaultVariableRequest();
    request.setVariableId(null);

    // when
    Response response = getVariablesExecutorFunction.apply(request)
      .execute();

    // then
    assertThat(response.getStatus(), is(500));
  }

  @Test
  public void missingVariableTypeQueryParamThrowsError() {
    // given
    DecisionVariableValueRequestDto request = generateDefaultVariableRequest();
    request.setVariableType(null);

    // when
    Response response = getVariablesExecutorFunction.apply(request)
      .execute();

    // then
    assertThat(response.getStatus(), is(500));
  }

  @Test
  public void missingDecisionDefinitionKeyQueryParamThrowsError() {
    // given
    DecisionVariableValueRequestDto request = generateDefaultVariableRequest();
    request.setDecisionDefinitionKey(null);

    // when
    Response response = getVariablesExecutorFunction.apply(request)
      .execute();

    // then
    assertThat(response.getStatus(), is(500));
  }

  @Test
  public void missingDecisionDefinitionVersionQueryParamThrowsError() {
    // given
    DecisionVariableValueRequestDto request = generateDefaultVariableRequest();
    request.setDecisionDefinitionVersions(null);

    // when
    Response response = getVariablesExecutorFunction.apply(request)
      .execute();

    // then
    assertThat(response.getStatus(), is(500));
  }

  private DecisionVariableValueRequestDto generateDefaultVariableRequest() {
    DecisionVariableValueRequestDto requestDto = new DecisionVariableValueRequestDto();
    requestDto.setDecisionDefinitionKey("aKey");
    requestDto.setDecisionDefinitionVersion("aVersion");
    requestDto.setVariableId("foo");
    requestDto.setVariableType(VariableType.STRING);
    return requestDto;
  }

  private OptimizeRequestExecutor getInputVariableValueResponse(DecisionVariableValueRequestDto requestDto) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildDecisionInputVariableValuesRequest(requestDto);
  }

  private OptimizeRequestExecutor getOutputVariableValueResponse(DecisionVariableValueRequestDto requestDto) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildDecisionOutputVariableValuesRequest(requestDto);
  }

}
