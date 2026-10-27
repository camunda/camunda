/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.config;

import io.camunda.security.ConditionalOnSaaSConfigured;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Primary
@Configuration
@ConditionalOnSaaSConfigured
@EnableConfigurationProperties(OpenApiConfigurationProperties.class)
public class SaaSOpenApiConfigurer extends OpenApiConfigurer {

  private static final Logger LOGGER = LoggerFactory.getLogger(SaaSOpenApiConfigurer.class);

  @Override
  public void configureSecurity(final OpenAPI openApi) {
    LOGGER.debug("Configuring OpenAPI security for SaaS deployment");

    openApi
        .getComponents()
        .setSecuritySchemes(
            new LinkedHashMap<>(Map.of(BEARER_SECURITY_SCHEMA_NAME, BEARER_SECURITY_SCHEMA)));

    openApi.setSecurity(List.of(new SecurityRequirement().addList(BEARER_SECURITY_SCHEMA_NAME)));
  }

  @Override
  public String getApiDescription() {
    return """
        API for communicating with a Camunda 8 SaaS cluster.

        **Authentication:**
        - **OAuth 2.0 Authentication**: Log into `/operate/login` to get proper credentials
        - **Bearer Token**: Use the Authorize button in Swagger UI to provide a Bearer token""";
  }
}
