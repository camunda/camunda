/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.config;

import io.camunda.security.ConditionalOnSaaSConfigured;
import io.camunda.zeebe.gateway.rest.ConditionalOnRestGatewayEnabled;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnRestGatewayEnabled
@ConditionalOnSaaSConfigured
@ConditionalOnProperty(
    name = "camunda.rest.swagger.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class SaaSOpenApiConfigurer implements OpenApiConfigurer {

  public static final String BEARER_SECURITY_SCHEMA_NAME = "bearerAuth";
  public static final SecurityScheme BEARER_SECURITY_SCHEMA =
      new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT");
  private static final Logger LOGGER = LoggerFactory.getLogger(SaaSOpenApiConfigurer.class);

  @Override
  public void configureSecurity(final OpenAPI openApi) {
    LOGGER.debug("Configuring OpenAPI security for SaaS deployment");

    openApi.getComponents().addSecuritySchemes(BEARER_SECURITY_SCHEMA_NAME, BEARER_SECURITY_SCHEMA);

    openApi.addSecurityItem(new SecurityRequirement().addList(BEARER_SECURITY_SCHEMA_NAME));
  }

  @Override
  public String getApiDescription() {
    return """
        API for communicating with a Camunda 8 SaaS cluster.

        **Authentication:**
        - **OIDC Authentication**: Log into `/operate/login` to get proper credentials
        - **Bearer Token**: Use the Authorize button in Swagger UI to provide a Bearer token""";
  }
}
