/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import org.apache.http.HttpStatus;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameRequestDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameResponseDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableValueRequestDto;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.util.List;

import static org.camunda.optimize.dto.optimize.query.variable.VariableType.BOOLEAN;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class ProcessVariableRestServiceIT extends AbstractIT {

  @Test
  public void getVariableNamesWithoutAuthentication() {
    // given
    ProcessVariableNameRequestDto variableRequestDto = new ProcessVariableNameRequestDto();
    variableRequestDto.setProcessDefinitionKey("zhoka");
    variableRequestDto.setProcessDefinitionVersion("boka");

    // when
    Response response = embeddedOptimizeExtension
            .getRequestExecutor()
            .buildProcessVariableNamesRequest(variableRequestDto)
            .withoutAuthentication()
            .execute();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(Response.Status.UNAUTHORIZED.getStatusCode()));
  }

  @Test
  public void getVariableNames() {
    // given
    ProcessVariableNameRequestDto variableRequestDto = new ProcessVariableNameRequestDto();
    variableRequestDto.setProcessDefinitionKey("akey");
    variableRequestDto.setProcessDefinitionVersion("aVersion");

    // when
    List<ProcessVariableNameResponseDto> responseList =
        embeddedOptimizeExtension
            .getRequestExecutor()
            .buildProcessVariableNamesRequest(variableRequestDto)
            .executeAndReturnList(ProcessVariableNameResponseDto.class, Response.Status.OK.getStatusCode());

    // then the status code is not authorized
    assertThat(responseList.isEmpty(), is(true));
  }

  @Test
  public void getVariableValuesWithoutAuthentication() {
    // when
    Response response = embeddedOptimizeExtension
            .getRequestExecutor()
            .buildProcessVariableValuesRequest(new ProcessVariableValueRequestDto())
            .withoutAuthentication()
            .execute();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(Response.Status.UNAUTHORIZED.getStatusCode()));
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
    List responseList = embeddedOptimizeExtension
            .getRequestExecutor()
            .buildProcessVariableValuesRequest(requestDto)
            .executeAndReturnList(String.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(responseList.isEmpty(), is(true));
  }


}
