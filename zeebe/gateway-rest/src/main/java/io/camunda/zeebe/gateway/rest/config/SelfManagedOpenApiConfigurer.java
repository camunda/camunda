/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.config;

import io.camunda.security.ConditionalOnSelfManagedConfigured;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnSelfManagedConfigured
@EnableConfigurationProperties(OpenApiConfigurationProperties.class)
public class SelfManagedOpenApiConfigurer extends OpenApiConfigurer {

  public static final String BASIC_SECURITY_SCHEMA_NAME = "basicAuth";
  public static final SecurityScheme BASIC_SECURITY_SCHEMA =
      new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("basic");
  private static final Logger LOGGER = LoggerFactory.getLogger(SelfManagedOpenApiConfigurer.class);

  @Override
  public void configureSecurity(final OpenAPI openApi) {
    LOGGER.debug("Configuring OpenAPI security for Self-Managed deployment");

    addBearerAuthentication(openApi);
    openApi.getComponents().addSecuritySchemes(BASIC_SECURITY_SCHEMA_NAME, BASIC_SECURITY_SCHEMA);
    openApi.addSecurityItem(new SecurityRequirement().addList(BASIC_SECURITY_SCHEMA_NAME));
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
}
