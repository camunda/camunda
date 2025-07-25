/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springdoc.core.models.GroupedOpenApi;

@ExtendWith(MockitoExtension.class)
class OpenApiConfigTest {
  @InjectMocks private OpenApiConfig openApiConfig;

  @Test
  void testInternalApiPaths() {
    final GroupedOpenApi result = openApiConfig.internalApiV1();
    assertThat(result.getPathsToMatch().contains("/v1/internal/**")).isTrue();
  }

  @Test
  void testExternalApiPaths() {
    final GroupedOpenApi result = openApiConfig.externalApiV1();
    assertThat(result.getPathsToMatch().contains("/v1/external/**")).isTrue();
  }

  @Test
  void testPublicApiPaths() {
    final GroupedOpenApi result = openApiConfig.publicApiV1();
    assertThat(result.getPathsToMatch()).containsExactly("/v1/**");
    assertThat(result.getPathsToExclude()).containsExactly("/v1/internal/**", "/v1/external/**");
  }
}
