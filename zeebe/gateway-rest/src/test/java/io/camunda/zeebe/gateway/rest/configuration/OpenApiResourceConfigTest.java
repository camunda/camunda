/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.configuration;

import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.zeebe.gateway.rest.config.OpenApiConfigurer;
import io.camunda.zeebe.gateway.rest.controller.TopologyController;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Tests for OpenApiResourceConfig swagger redirect functionality. Verifies that the swagger
 * endpoint redirects are properly configured when swagger is enabled or disabled.
 */
class OpenApiResourceConfigTest {

  @Nested
  @WebMvcTest(
      value = {TopologyController.class},
      properties = "camunda.rest.swagger.enabled=true")
  class SwaggerEnabled extends RestApiConfigurationTest {

    @MockitoBean private OpenApiConfigurer openApiConfigurer;
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
  @WebMvcTest(
      value = {TopologyController.class},
      properties = "camunda.rest.swagger.enabled=false")
  class SwaggerDisabled extends RestApiConfigurationTest {

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
  @WebMvcTest(value = {TopologyController.class})
  class SwaggerDefaultBehavior extends RestApiConfigurationTest {

    @MockitoBean private OpenApiConfigurer openApiConfigurer;
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
