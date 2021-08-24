/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.query.variable.ExternalProcessVariableRequestDto;
import org.camunda.optimize.dto.optimize.rest.ValidationErrorResponseDto;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.rest.IngestionRestService.QUERY_PARAMETER_ACCESS_TOKEN;
import static org.camunda.optimize.rest.constants.RestConstants.AUTH_COOKIE_TOKEN_VALUE_PREFIX;
import static org.camunda.optimize.rest.providers.BeanConstraintViolationExceptionHandler.THE_REQUEST_BODY_WAS_INVALID;

public class VariableIngestionRestIT extends AbstractIT {

  @Test
  public void ingestExternalVariables() {
    // given
    final List<ExternalProcessVariableRequestDto> variables = IntStream.range(0, 10)
      .mapToObj(i -> ingestionClient.createExternalVariable().setId("id" + i))
      .collect(toList());

    // when
    final Response response = ingestionClient.ingestVariablesAndReturnResponse(variables);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
  }

  @Test
  public void ingestExternalVariables_emptyBatch() {
    // when
    final Response response = ingestionClient.ingestVariablesAndReturnResponse(Collections.emptyList());

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
  }

  @Test
  public void ingestExternalVariable_customAccessToken() {
    // given
    final ExternalProcessVariableRequestDto variable = ingestionClient.createExternalVariable();

    final String accessToken = "aToken";
    embeddedOptimizeExtension.getConfigurationService().getVariableIngestionConfiguration().setAccessToken(accessToken);

    // when
    final Response response =
      ingestionClient.ingestVariablesAndReturnResponse(Collections.singletonList(variable), accessToken);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
  }

  @Test
  public void ingestExternalVariable_accessTokenAsQueryParam() {
    // given
    final ExternalProcessVariableRequestDto variable = ingestionClient.createExternalVariable();

    final String accessToken = "aToken";
    embeddedOptimizeExtension.getConfigurationService().getVariableIngestionConfiguration().setAccessToken(accessToken);

    // when
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildIngestExternalVariables(Collections.singletonList(variable), null)
      .addSingleQueryParam(QUERY_PARAMETER_ACCESS_TOKEN, accessToken)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
  }

  @Test
  public void ingestExternalVariable_accessTokenUsingBearerScheme() {
    // given
    final ExternalProcessVariableRequestDto variable = ingestionClient.createExternalVariable();

    final String accessToken = "aToken";
    embeddedOptimizeExtension.getConfigurationService().getVariableIngestionConfiguration().setAccessToken(accessToken);

    // when
    final Response ingestResponse =
      ingestionClient.ingestVariablesAndReturnResponse(
        Collections.singletonList(variable),
        AUTH_COOKIE_TOKEN_VALUE_PREFIX + accessToken
      );

    // then
    assertThat(ingestResponse.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
  }

  @Test
  public void ingestExternalVariable_incorrectToken() {
    // given
    final ExternalProcessVariableRequestDto variable = ingestionClient.createExternalVariable();

    // when
    final Response response =
      ingestionClient.ingestVariablesAndReturnResponse(Collections.singletonList(variable), "falseToken");

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @Test
  public void ingestExternalVariable_invalidVariable() {
    // given
    final ExternalProcessVariableRequestDto invalidVariable = new ExternalProcessVariableRequestDto()
      .setId("")
      .setName("  ")
      .setValue("value")
      .setProcessInstanceId("")
      .setProcessDefinitionKey(" ");

    // when
    final ValidationErrorResponseDto response = embeddedOptimizeExtension.getRequestExecutor()
      .buildIngestExternalVariables(
        Collections.singletonList(invalidVariable),
        ingestionClient.getVariableIngestionToken()
      )
      .execute(ValidationErrorResponseDto.class, Response.Status.BAD_REQUEST.getStatusCode());

    // then
    assertThat(response.getErrorMessage()).isEqualTo(THE_REQUEST_BODY_WAS_INVALID);
    assertThat(response.getValidationErrors())
      .hasSize(4)
      .extracting(ValidationErrorResponseDto.ValidationError::getProperty)
      .map(property -> property.split("\\.")[1])
      .containsExactlyInAnyOrder(
        ExternalProcessVariableRequestDto.Fields.id,
        ExternalProcessVariableRequestDto.Fields.name,
        ExternalProcessVariableRequestDto.Fields.processInstanceId,
        ExternalProcessVariableRequestDto.Fields.processDefinitionKey
      );
    assertThat(response.getValidationErrors())
      .extracting(ValidationErrorResponseDto.ValidationError::getErrorMessage)
      .doesNotContainNull();
  }

  @Test
  public void ingestExternalVariable_invalidAndValidVariables() {
    // given
    final ExternalProcessVariableRequestDto validVariable = ingestionClient.createExternalVariable();
    final ExternalProcessVariableRequestDto invalidVariable = ingestionClient.createExternalVariable();
    invalidVariable.setId(null);

    // when
    final ValidationErrorResponseDto response = embeddedOptimizeExtension.getRequestExecutor()
      .buildIngestExternalVariables(
        List.of(validVariable, invalidVariable),
        ingestionClient.getVariableIngestionToken()
      )
      .execute(ValidationErrorResponseDto.class, Response.Status.BAD_REQUEST.getStatusCode());

    // then
    assertThat(response.getErrorMessage()).isEqualTo(THE_REQUEST_BODY_WAS_INVALID);
    assertThat(response.getValidationErrors())
      .hasSize(1)
      .extracting(ValidationErrorResponseDto.ValidationError::getProperty)
      .containsExactly("element[1]." + ExternalProcessVariableRequestDto.Fields.id);
    assertThat(response.getValidationErrors())
      .extracting(ValidationErrorResponseDto.ValidationError::getErrorMessage)
      .doesNotContainNull();
  }

  @Test
  public void ingestExternalVariable_nullValueAllowed() {
    // given
    final ExternalProcessVariableRequestDto variable = ingestionClient.createExternalVariable();
    variable.setValue(null);

    // when
    final Response response =
      ingestionClient.ingestVariablesAndReturnResponse(Collections.singletonList(variable));

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
  }

}
