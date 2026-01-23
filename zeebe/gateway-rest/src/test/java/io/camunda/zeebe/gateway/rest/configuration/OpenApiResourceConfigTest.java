/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.configuration;

import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.zeebe.gateway.rest.RestTest;
import io.camunda.zeebe.gateway.rest.config.OpenApiResourceConfig;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Tests for OpenApiResourceConfig swagger redirect functionality. Verifies that the swagger
 * endpoint redirects are properly configured when swagger is enabled or disabled.
 */
class OpenApiResourceConfigTest extends RestTest {

  @Nested
  @WebMvcTest(useDefaultFilters = false, properties = "camunda.rest.swagger.enabled=true")
  @Import(OpenApiResourceConfig.class)
  class SwaggerEnabled extends RestTest {

    @MockitoBean private CamundaAuthenticationProvider authenticationProvider;

    @Test
    void shouldRedirectSwaggerToSwaggerUiIndexHtml() {
      // when/then
      webClient
          .get()
          .uri("/swagger")
          .exchange()
          .expectStatus()
          .isFound()
          .expectHeader()
          .location("/swagger-ui/index.html");
    }

    @Test
    void shouldRedirectSwaggerWithTrailingSlashToSwaggerUiIndexHtml() {
      // when/then
      webClient
          .get()
          .uri("/swagger/")
          .exchange()
          .expectStatus()
          .isFound()
          .expectHeader()
          .location("/swagger-ui/index.html");
    }
  }

  @Nested
  @WebMvcTest(useDefaultFilters = false, properties = "camunda.rest.swagger.enabled=false")
  @Import(OpenApiResourceConfig.class)
  class SwaggerDisabled extends RestTest {

    @MockitoBean private CamundaAuthenticationProvider authenticationProvider;

    @Test
    void shouldNotRedirectSwaggerWhenDisabled() {
      // when/then
      webClient.get().uri("/swagger").exchange().expectStatus().isNotFound();
    }

    @Test
    void shouldNotRedirectSwaggerWithTrailingSlashWhenDisabled() {
      // when/then
      webClient.get().uri("/swagger/").exchange().expectStatus().isNotFound();
    }
  }

  @Nested
  @WebMvcTest(useDefaultFilters = false)
  @Import(OpenApiResourceConfig.class)
  class SwaggerDefaultBehavior extends RestTest {

    @MockitoBean private CamundaAuthenticationProvider authenticationProvider;

    @Test
    void shouldEnableSwaggerByDefaultWhenPropertyNotSet() {
      // when/then - swagger should be enabled by default (matchIfMissing = true)
      webClient
          .get()
          .uri("/swagger")
          .exchange()
          .expectStatus()
          .isFound()
          .expectHeader()
          .location("/swagger-ui/index.html");
    }

    @Test
    void shouldRedirectSwaggerWithTrailingSlashByDefault() {
      // when/then
      webClient
          .get()
          .uri("/swagger/")
          .exchange()
          .expectStatus()
          .isFound()
          .expectHeader()
          .location("/swagger-ui/index.html");
    }
  }
}
