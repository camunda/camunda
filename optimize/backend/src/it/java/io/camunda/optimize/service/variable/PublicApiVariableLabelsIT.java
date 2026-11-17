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
import static io.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.CCSM_PROFILE;
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
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

public class PublicApiVariableLabelsIT extends AbstractCCSMIT {

  private static final String TEST_ACCESS_TOKEN = "test-access-token";

  // This class is inherently about the legacy static api.accessToken mechanism, which CSL's bearer
  // chain does not support (no unprotected-API escape hatch on /api/public/**). Without pinning
  // cslEnabled=false, the token would be rejected before any of the endpoint logic these tests
  // target ever runs, turning every assertion here into an unrelated 401. Pin explicitly rather
  // than rely on the ambient CI matrix value. api.accessToken having no CSL equivalent is a
  // product gap, tracked separately at camunda/camunda#60639.
  @Override
  protected void startAndUseNewOptimizeInstance() {
    startAndUseNewOptimizeInstance(Map.of("optimize.security.csl.enabled", "false"), CCSM_PROFILE);
  }

  // A single @BeforeEach makes the restart-then-configure dependency explicit in code, rather
  // than relying on JUnit Jupiter's execution order for multiple @BeforeEach methods in one
  // class, which is deterministic but intentionally unspecified (@Order has no effect on
  // @BeforeEach/@AfterEach — it only orders @Test methods, extension fields, and test classes).
  // Splitting this into two methods previously relied on that unspecified order to configure the
  // access token on the instance this restart just created, rather than one about to be replaced.
  @BeforeEach
  public void bootWithCslDisabledAndConfigurePublicApiToken() {
    startAndUseNewOptimizeInstance();
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
