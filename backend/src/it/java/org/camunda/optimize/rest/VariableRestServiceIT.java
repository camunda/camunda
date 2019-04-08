/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import org.camunda.optimize.dto.optimize.query.variable.VariableRetrievalDto;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.ws.rs.core.Response;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;


public class VariableRestServiceIT {

  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  @Rule
  public RuleChain chain = RuleChain
      .outerRule(elasticSearchRule).around(engineRule).around(embeddedOptimizeRule);

  @Test
  public void getVariablesWithoutAuthentication() {
    // when
    Response response = embeddedOptimizeRule
            .getRequestExecutor()
            .buildGetVariablesRequest("zhoka", "boka")
            .withoutAuthentication()
            .execute();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void getVariables() {

    // when
    List<VariableRetrievalDto> responseList =
        embeddedOptimizeRule
            .getRequestExecutor()
            .buildGetVariablesRequest("aKey", "aVersion")
            .executeAndReturnList(VariableRetrievalDto.class, 200);

    // then the status code is not authorized
    assertThat(responseList.isEmpty(), is(true));
  }

  @Test
  public void getVariableValuesWithoutAuthentication() {
    // when
    Response response = embeddedOptimizeRule
            .getRequestExecutor()
            .buildGetVariableValuesRequest()
            .withoutAuthentication()
            .execute();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void getVariableValues() {
    // when
    List responseList = embeddedOptimizeRule
            .getRequestExecutor()
            .buildGetVariableValuesRequest()
            .addSingleQueryParam("processDefinitionKey", "aKey")
            .addSingleQueryParam("processDefinitionVersion", "aVersion")
            .addSingleQueryParam("name", "bla")
            .addSingleQueryParam("type", "Boolean")
            .executeAndReturnList(String.class, 200);

    // then
    assertThat(responseList.isEmpty(), is(true));
  }


}
