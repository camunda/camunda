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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
@ConditionalOnSaaSConfigured
@Primary
public class SaaSOpenApiConfigurer extends OpenApiConfigurer {

  private static final Logger LOGGER = LoggerFactory.getLogger(SaaSOpenApiConfigurer.class);

  @Override
  public void configureSecurity(final OpenAPI openApi) {
    LOGGER.debug("Configuring OpenAPI security for SaaS deployment");

    addBearerAuthentication(openApi);
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
