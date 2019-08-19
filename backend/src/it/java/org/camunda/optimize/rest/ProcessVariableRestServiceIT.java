/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameRequestDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableValueRequestDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameResponseDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.ws.rs.core.Response;
import java.util.List;

import static org.camunda.optimize.dto.optimize.query.variable.VariableType.BOOLEAN;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;


public class ProcessVariableRestServiceIT {

  private EngineIntegrationRule engineRule = new EngineIntegrationRule();
  private ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  private EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  @Rule
  public RuleChain chain = RuleChain
      .outerRule(elasticSearchRule).around(engineRule).around(embeddedOptimizeRule);

  @Test
  public void getVariableNamesWithoutAuthentication() {
    // given
    ProcessVariableNameRequestDto variableRequestDto = new ProcessVariableNameRequestDto();
    variableRequestDto.setProcessDefinitionKey("zhoka");
    variableRequestDto.setProcessDefinitionVersion("boka");

    // when
    Response response = embeddedOptimizeRule
            .getRequestExecutor()
            .buildProcessVariableNamesRequest(variableRequestDto)
            .withoutAuthentication()
            .execute();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void getVariableNames() {
    // given
    ProcessVariableNameRequestDto variableRequestDto = new ProcessVariableNameRequestDto();
    variableRequestDto.setProcessDefinitionKey("akey");
    variableRequestDto.setProcessDefinitionVersion("aVersion");

    // when
    List<ProcessVariableNameResponseDto> responseList =
        embeddedOptimizeRule
            .getRequestExecutor()
            .buildProcessVariableNamesRequest(variableRequestDto)
            .executeAndReturnList(ProcessVariableNameResponseDto.class, 200);

    // then the status code is not authorized
    assertThat(responseList.isEmpty(), is(true));
  }

  @Test
  public void getVariableValuesWithoutAuthentication() {
    // when
    Response response = embeddedOptimizeRule
            .getRequestExecutor()
            .buildProcessVariableValuesRequest(new ProcessVariableValueRequestDto())
            .withoutAuthentication()
            .execute();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void getVariableValues() {
    // given
    ProcessVariableValueRequestDto requestDto = new ProcessVariableValueRequestDto();
    requestDto.setProcessDefinitionKey("aKey");
    requestDto.setProcessDefinitionVersion("aVersion");
    requestDto.setName("bla");
    requestDto.setType(BOOLEAN);

    // when
    List responseList = embeddedOptimizeRule
            .getRequestExecutor()
            .buildProcessVariableValuesRequest(requestDto)
            .executeAndReturnList(String.class, 200);

    // then
    assertThat(responseList.isEmpty(), is(true));
  }


}
