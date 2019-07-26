/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import org.camunda.optimize.OptimizeRequestExecutor;
import org.camunda.optimize.dto.optimize.importing.DecisionDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.query.variable.DecisionVariableNameRequestDto;
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

import static org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule.DEFAULT_ENGINE_ALIAS;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_DEFINITION_TYPE;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@RunWith(Parameterized.class)
public class DecisionVariableNamesRestServiceIT {

  private static final String TEST_VARIANT_INPUTS = "inputs";
  private static final String TEST_VARIANT_OUTPUTS = "outputs";
  private static final String DECISION_KEY = "decisionKey";
  private static final String DECISION_VERSION = "1";

  @Parameterized.Parameters
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[][]{
      {TEST_VARIANT_INPUTS}, {TEST_VARIANT_OUTPUTS}
    });
  }

  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  private Function<DecisionVariableNameRequestDto, OptimizeRequestExecutor> getVariablesExecutorFunction;

  @Rule
  public RuleChain chain = RuleChain
    .outerRule(elasticSearchRule).around(engineRule).around(embeddedOptimizeRule);

  public DecisionVariableNamesRestServiceIT(String inputsOrOutputs) {
    switch (inputsOrOutputs) {
      case TEST_VARIANT_INPUTS:
        getVariablesExecutorFunction = this::getInputVariableNameResponse;
        break;
      case TEST_VARIANT_OUTPUTS:
        getVariablesExecutorFunction = this::getOutputVariableNameResponse;
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
    deployDefinition();
    DecisionVariableNameRequestDto request = generateDefaultVariableRequest();

    // when
    List responseList = getVariablesExecutorFunction.apply(request)
      .executeAndReturnList(String.class, 200);

    // then
    assertThat(responseList.isEmpty(), is(true));
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
    elasticSearchRule.addEntryToElasticsearch(DECISION_DEFINITION_TYPE, definition.getId(), definition);
  }

  @Test
  public void missingDecisionDefinitionKeyQueryParamThrowsError() {
    // given
    DecisionVariableNameRequestDto request = generateDefaultVariableRequest();
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
    DecisionVariableNameRequestDto request = generateDefaultVariableRequest();
    request.setDecisionDefinitionVersions(null);

    // when
    Response response = getVariablesExecutorFunction.apply(request)
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

  private OptimizeRequestExecutor getInputVariableNameResponse(DecisionVariableNameRequestDto requestDto) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildDecisionInputVariableNamesRequest(requestDto);
  }

  private OptimizeRequestExecutor getOutputVariableNameResponse(DecisionVariableNameRequestDto requestDto) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildDecisionOutputVariableNamesRequest(requestDto);
  }

}
