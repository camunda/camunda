/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import org.camunda.optimize.OptimizeRequestExecutor;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.camunda.optimize.rest.DecisionVariablesRestService.DECISION_DEFINITION_KEY;
import static org.camunda.optimize.rest.DecisionVariablesRestService.DECISION_DEFINITION_VERSION;
import static org.camunda.optimize.rest.DecisionVariablesRestService.VARIABLE_ID;
import static org.camunda.optimize.rest.DecisionVariablesRestService.VARIABLE_TYPE;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@RunWith(Parameterized.class)
public class DecisionVariablesRestServiceIT {

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

  private Function<Map<String, Object>, OptimizeRequestExecutor> getVariablesExecutorFunction;

  @Rule
  public RuleChain chain = RuleChain
    .outerRule(elasticSearchRule).around(engineRule).around(embeddedOptimizeRule);

  public DecisionVariablesRestServiceIT(String inputsOrOutputs) {
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
    Map<String, Object> queryParams = generateDefaultParameterMap();

    // when
    List responseList = getVariablesExecutorFunction.apply(queryParams)
      .executeAndReturnList(String.class, 200);

    // then
    assertThat(responseList.isEmpty(), is(true));
  }

  @Test
  public void missingVariableIdQueryParamThrowsError() {
    // given
    Map<String, Object> queryParams = generateDefaultParameterMap();
    queryParams.remove(VARIABLE_ID);

    // when
    Response response = getVariablesExecutorFunction.apply(queryParams)
      .execute();

    // then
    assertThat(response.getStatus(), is(500));
  }

  @Test
  public void missingVariableTypeQueryParamThrowsError() {
    // given
    Map<String, Object> queryParams = generateDefaultParameterMap();
    queryParams.remove(VARIABLE_TYPE);

    // when
    Response response = getVariablesExecutorFunction.apply(queryParams)
      .execute();

    // then
    assertThat(response.getStatus(), is(500));
  }

  @Test
  public void missingDecisionDefinitionKeyQueryParamThrowsError() {
    // given
    Map<String, Object> queryParams = generateDefaultParameterMap();
    queryParams.remove(DECISION_DEFINITION_KEY);

    // when
    Response response = getVariablesExecutorFunction.apply(queryParams)
      .execute();

    // then
    assertThat(response.getStatus(), is(500));
  }

  @Test
  public void missingDecisionDefinitionVersionQueryParamThrowsError() {
    // given
    Map<String, Object> queryParams = generateDefaultParameterMap();
    queryParams.remove(DECISION_DEFINITION_VERSION);

    // when
    Response response = getVariablesExecutorFunction.apply(queryParams)
      .execute();

    // then
    assertThat(response.getStatus(), is(500));
  }

  private Map<String, Object> generateDefaultParameterMap() {
    Map<String, Object> queryParams = new HashMap<>();
    queryParams.put(VARIABLE_ID, "foo");
    queryParams.put(VARIABLE_TYPE, "string");
    queryParams.put(DECISION_DEFINITION_KEY, "aKey");
    queryParams.put(DECISION_DEFINITION_VERSION, "aVersion");
    return queryParams;
  }

  private OptimizeRequestExecutor getInputVariableValueResponse(Map<String, Object> queryParams) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildGetDecisionInputVariableValuesRequest()
      .addQueryParams(queryParams);
  }

  private OptimizeRequestExecutor getOutputVariableValueResponse(Map<String, Object> queryParams) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildGetDecisionOutputVariableValuesRequest()
      .addQueryParams(queryParams);
  }

}
