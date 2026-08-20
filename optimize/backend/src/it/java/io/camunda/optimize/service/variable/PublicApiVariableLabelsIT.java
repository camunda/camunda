/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.variable;

import static io.camunda.optimize.rest.providers.GenericExceptionMapper.BAD_REQUEST_ERROR_CODE;
import static io.camunda.optimize.rest.providers.GenericExceptionMapper.NOT_FOUND_ERROR_CODE;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.optimize.AbstractCCSMIT;
import io.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import io.camunda.optimize.dto.optimize.query.variable.DefinitionVariableLabelsDto;
import io.camunda.optimize.dto.optimize.query.variable.LabelDto;
import io.camunda.optimize.dto.optimize.query.variable.VariableType;
import io.camunda.optimize.dto.optimize.rest.ErrorResponseDto;
import io.camunda.optimize.service.db.writer.ProcessDefinitionWriter;
import jakarta.ws.rs.core.Response;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

public class PublicApiVariableLabelsIT extends AbstractCCSMIT {

  private static final String TEST_ACCESS_TOKEN = "test-access-token";

  @BeforeEach
  public void configurePublicApiToken() {
    embeddedOptimizeExtension
        .getConfigurationService()
        .getOptimizeApiConfiguration()
        .setAccessToken(TEST_ACCESS_TOKEN);
  }

  @Test
  public void shouldReturn400WithFieldNameWhenDefinitionKeyIsNull() {
    // given
    final DefinitionVariableLabelsDto request = new DefinitionVariableLabelsDto(null, List.of());

    // when
    final Response response =
        embeddedOptimizeExtension
            .getRequestExecutor()
            .buildModifyVariableLabelsRequest(request)
            .withBearerToken(TEST_ACCESS_TOKEN)
            .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    final ErrorResponseDto errorResponse = response.readEntity(ErrorResponseDto.class);
    assertThat(errorResponse.getErrorCode()).isEqualTo(BAD_REQUEST_ERROR_CODE);
    assertThat(errorResponse.getDetailedMessage()).contains("definitionKey");
  }

  @Test
  public void shouldReturn400WithFieldNameWhenDefinitionKeyIsBlank() {
    // given
    final DefinitionVariableLabelsDto request = new DefinitionVariableLabelsDto("  ", List.of());

    // when
    final Response response =
        embeddedOptimizeExtension
            .getRequestExecutor()
            .buildModifyVariableLabelsRequest(request)
            .withBearerToken(TEST_ACCESS_TOKEN)
            .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    final ErrorResponseDto errorResponse = response.readEntity(ErrorResponseDto.class);
    assertThat(errorResponse.getErrorCode()).isEqualTo(BAD_REQUEST_ERROR_CODE);
    assertThat(errorResponse.getDetailedMessage()).contains("definitionKey");
  }

  @Test
  public void shouldReturn200WhenRequestIsValid() {
    // given
    final String definitionKey = "my-process";
    embeddedOptimizeExtension
        .getBean(ProcessDefinitionWriter.class)
        .importProcessDefinitions(
            List.of(
                ProcessDefinitionOptimizeDto.builder()
                    .id("my-process:1")
                    .key(definitionKey)
                    .version("1")
                    .name(definitionKey)
                    .bpmn20Xml("<definitions/>")
                    .build()));
    databaseIntegrationTestExtension.refreshAllOptimizeIndices();

    final LabelDto label = new LabelDto("book availability", "bookAvailable", VariableType.BOOLEAN);
    final DefinitionVariableLabelsDto request =
        new DefinitionVariableLabelsDto(definitionKey, List.of(label));

    // when
    final Response response =
        embeddedOptimizeExtension
            .getRequestExecutor()
            .buildModifyVariableLabelsRequest(request)
            .withBearerToken(TEST_ACCESS_TOKEN)
            .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
  }

  @Test
  public void shouldReturn404WhenDefinitionKeyIsValidButDefinitionDoesNotExist() {
    // given
    final DefinitionVariableLabelsDto request =
        new DefinitionVariableLabelsDto("nonexistent-process", List.of());

    // when — validation passes, service throws NotFoundException
    final Response response =
        embeddedOptimizeExtension
            .getRequestExecutor()
            .buildModifyVariableLabelsRequest(request)
            .withBearerToken(TEST_ACCESS_TOKEN)
            .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
    final ErrorResponseDto errorResponse = response.readEntity(ErrorResponseDto.class);
    assertThat(errorResponse.getErrorCode()).isEqualTo(NOT_FOUND_ERROR_CODE);
  }
}
