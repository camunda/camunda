/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.config;

import static org.camunda.bpm.model.xml.test.assertions.ModelAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

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
    assertTrue(result.getPathsToMatch().contains("/v1/internal/**"));
  }

  @Test
  void testExternalApiPaths() {
    final GroupedOpenApi result = openApiConfig.externalApiV1();
    assertTrue(result.getPathsToMatch().contains("/v1/external/**"));
  }

  @Test
  void testPublicApiPaths() {
    final GroupedOpenApi result = openApiConfig.publicApiV1();
    assertThat(result.getPathsToMatch()).containsExactly("/v1/**");
    assertThat(result.getPathsToExclude()).containsExactly("/v1/internal/**", "/v1/external/**");
  }
}
