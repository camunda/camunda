/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameRequestDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameResponseDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableValueRequestDto;
import org.camunda.optimize.test.it.extension.ElasticSearchIntegrationTestExtensionRule;
import org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtensionRule;
import org.camunda.optimize.test.it.extension.EngineIntegrationExtensionRule;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import javax.ws.rs.core.Response;
import java.util.List;

import static org.camunda.optimize.dto.optimize.query.variable.VariableType.BOOLEAN;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;


public class ProcessVariableRestServiceIT {

  @RegisterExtension
  @Order(1)
  public ElasticSearchIntegrationTestExtensionRule elasticSearchIntegrationTestExtensionRule = new ElasticSearchIntegrationTestExtensionRule();
  @RegisterExtension
  @Order(2)
  public EngineIntegrationExtensionRule engineIntegrationExtensionRule = new EngineIntegrationExtensionRule();
  @RegisterExtension
  @Order(3)
  public EmbeddedOptimizeExtensionRule embeddedOptimizeExtensionRule = new EmbeddedOptimizeExtensionRule();

  @Test
  public void getVariableNamesWithoutAuthentication() {
    // given
    ProcessVariableNameRequestDto variableRequestDto = new ProcessVariableNameRequestDto();
    variableRequestDto.setProcessDefinitionKey("zhoka");
    variableRequestDto.setProcessDefinitionVersion("boka");

    // when
    Response response = embeddedOptimizeExtensionRule
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
        embeddedOptimizeExtensionRule
            .getRequestExecutor()
            .buildProcessVariableNamesRequest(variableRequestDto)
            .executeAndReturnList(ProcessVariableNameResponseDto.class, 200);

    // then the status code is not authorized
    assertThat(responseList.isEmpty(), is(true));
  }

  @Test
  public void getVariableValuesWithoutAuthentication() {
    // when
    Response response = embeddedOptimizeExtensionRule
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
    List responseList = embeddedOptimizeExtensionRule
            .getRequestExecutor()
            .buildProcessVariableValuesRequest(requestDto)
            .executeAndReturnList(String.class, 200);

    // then
    assertThat(responseList.isEmpty(), is(true));
  }


}
