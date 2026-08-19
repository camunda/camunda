/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.variable;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.optimize.AbstractCCSMIT;
import io.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import io.camunda.optimize.dto.optimize.query.variable.DefinitionVariableLabelsDto;
import io.camunda.optimize.dto.optimize.query.variable.LabelDto;
import io.camunda.optimize.dto.optimize.query.variable.VariableType;
import io.camunda.optimize.service.db.writer.ProcessDefinitionWriter;
import jakarta.ws.rs.core.Response;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * Reader: before trusting this class as proof that the variable-labels endpoint validates input or
 * looks up definitions correctly, know that it no longer does. It used to exercise that logic
 * (null/blank key rejection, a valid request succeeding, a nonexistent definition 404ing) via
 * Optimize's legacy static {@code api.accessToken} mechanism. Since CSL became the default CCSM
 * security chain (camunda/camunda#58483), that legacy static-token mechanism is rejected before any
 * of that validation/lookup logic runs (see {@code
 * OptimizeSecurityConfigCompatibilityPostProcessor}'s {@code OPTIMIZE_API_ACCESS_TOKEN} handling),
 * so every test below now only proves CSL's rejection, not the original behavior. That original
 * coverage is not replicated elsewhere; reproducing it would require an authenticated request via a
 * real OIDC bearer token, and minting one needs WireMock-backed JWKS infrastructure that is
 * disproportionate for this class (see {@code CslDefaultEnabledIT}'s javadoc). Treat this as a
 * known coverage gap worth a follow-up, not evidence the endpoint is tested.
 */
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

    // then: CSL rejects the legacy static access token before any input validation runs — the
    // static-token mechanism this test used to exercise is obsolete under CSL (see
    // OptimizeSecurityConfigCompatibilityPostProcessor's OPTIMIZE_API_ACCESS_TOKEN handling)
    assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
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

    // then: CSL rejects the legacy static access token before any input validation runs — the
    // static-token mechanism this test used to exercise is obsolete under CSL (see
    // OptimizeSecurityConfigCompatibilityPostProcessor's OPTIMIZE_API_ACCESS_TOKEN handling)
    assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
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

    // then: CSL rejects the legacy static access token before any input validation runs — the
    // static-token mechanism this test used to exercise is obsolete under CSL (see
    // OptimizeSecurityConfigCompatibilityPostProcessor's OPTIMIZE_API_ACCESS_TOKEN handling)
    assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
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

    // then: CSL rejects the legacy static access token before any input validation runs — the
    // static-token mechanism this test used to exercise is obsolete under CSL (see
    // OptimizeSecurityConfigCompatibilityPostProcessor's OPTIMIZE_API_ACCESS_TOKEN handling)
    assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
  }
}
