/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.config;

import io.camunda.authentication.ConditionalOnUnprotectedApi;
import io.camunda.security.ConditionalOnSelfManagedConfigured;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

@Configuration
@ConditionalOnSelfManagedConfigured
@EnableConfigurationProperties(OpenApiConfigurationProperties.class)
public class SelfManagedOpenApiConfigurer extends OpenApiConfigurer {

  public static final String BASIC_SECURITY_SCHEMA_NAME = "basicAuth";
  private static final Logger LOGGER = LoggerFactory.getLogger(SelfManagedOpenApiConfigurer.class);

  @Override
  public void configureSecurity(final OpenAPI openApi) {
    LOGGER.debug("Configuring OpenAPI security for Self-Managed deployment");

    openApi.setSecurity(
        List.of(
            new SecurityRequirement().addList(BEARER_SECURITY_SCHEMA_NAME),
            new SecurityRequirement().addList(BASIC_SECURITY_SCHEMA_NAME)));
  }

  @Override
  public String getApiDescription() {
    return """
        API for communicating with a Camunda 8 Self-Managed cluster.

        **Authentication Options:**
        - **Basic Authentication**: Use the Authorize button to provide username/password credentials
        - **OAuth 2.0 Authentication**: Log into `/operate/login` to get proper credentials
        - **Bearer Token**: Use the Authorize button in Swagger UI to provide a Bearer token""";
  }

  @Bean
  @ConditionalOnUnprotectedApi
  public SwaggerCsrfPropertyOverride swaggerCsrfPropertyOverride(
      final ConfigurableEnvironment environment) {
    LOGGER.debug(
        "SelfManagedOpenApiConfigurer: Disabling CSRF for Swagger UI in unprotected API mode");

    final var propertySource =
        new MapPropertySource(
            "swaggerCsrfOverride", Map.of("springdoc.swagger-ui.csrf.enabled", "false"));

    environment.getPropertySources().addFirst(propertySource);

    LOGGER.debug("SelfManagedOpenApiConfigurer: Successfully disabled CSRF for Swagger UI");

    return new SwaggerCsrfPropertyOverride();
  }

  public static class SwaggerCsrfPropertyOverride {
    // Marker class to indicate the property override has been applied
  }
}
