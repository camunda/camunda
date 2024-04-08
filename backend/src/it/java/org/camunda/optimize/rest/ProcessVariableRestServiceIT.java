/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.AbstractIT.OPENSEARCH_PASSING;
import static org.camunda.optimize.dto.optimize.query.variable.VariableType.BOOLEAN;

import jakarta.ws.rs.core.Response;
import java.util.Collections;
import java.util.List;
import org.camunda.optimize.AbstractPlatformIT;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameRequestDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameResponseDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableValueRequestDto;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag(OPENSEARCH_PASSING)
public class ProcessVariableRestServiceIT extends AbstractPlatformIT {

  @Test
  public void getVariableNamesWithoutAuthentication() {
    // given
    ProcessVariableNameRequestDto variableRequestDto = new ProcessVariableNameRequestDto();
    variableRequestDto.setProcessDefinitionKey("zhoka");
    variableRequestDto.setProcessDefinitionVersion("boka");

    // when
    Response response =
        embeddedOptimizeExtension
            .getRequestExecutor()
            .buildProcessVariableNamesRequest(Collections.singletonList(variableRequestDto), false)
            .withoutAuthentication()
            .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
  }

  @Test
  public void getVariableNames() {
    // when
    List<ProcessVariableNameResponseDto> responseList =
        variablesClient.getProcessVariableNames("akey", "aVersion");

    // then
    assertThat(responseList.isEmpty()).isTrue();
  }

  @Test
  public void getVariableNamesWithoutDefinitionVersionDoesNotFail() {
    // given
    ProcessVariableNameRequestDto variableRequestDto = new ProcessVariableNameRequestDto();
    variableRequestDto.setProcessDefinitionKey("akey");

    // when
    List<ProcessVariableNameResponseDto> responseList =
        variablesClient.getProcessVariableNames(variableRequestDto);

    // then
    assertThat(responseList.isEmpty()).isTrue();
  }

  @Test
  public void getVariableValuesWithoutAuthentication() {
    // when
    Response response =
        embeddedOptimizeExtension
            .getRequestExecutor()
            .buildProcessVariableValuesRequest(new ProcessVariableValueRequestDto())
            .withoutAuthentication()
            .execute();

    // then the status code is not authorized
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
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
    List responseList = variablesClient.getProcessVariableValues(requestDto);

    // then
    assertThat(responseList.isEmpty()).isTrue();
  }

  @Test
  public void getVariableValuesWithoutDefinitionVersionDoesNotFail() {
    // given
    ProcessVariableValueRequestDto requestDto = new ProcessVariableValueRequestDto();
    requestDto.setProcessDefinitionKey("aKey");
    requestDto.setName("bla");
    requestDto.setType(BOOLEAN);

    // when
    Response response =
        embeddedOptimizeExtension
            .getRequestExecutor()
            .buildProcessVariableValuesRequest(requestDto)
            .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
  }
}
