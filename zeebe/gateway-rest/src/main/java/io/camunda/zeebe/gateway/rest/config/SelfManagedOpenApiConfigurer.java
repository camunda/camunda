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
public class SelfManagedOpenApiConfigurer implements OpenApiConfigurer {

  public static final String BEARER_SECURITY_SCHEMA_NAME = "bearerAuth";
  public static final String BASIC_SECURITY_SCHEMA_NAME = "basicAuth";
  public static final String OIDC_SECURITY_SCHEMA_NAME = "oidc";
  public static final SecurityScheme BEARER_SECURITY_SCHEMA =
      new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT");
  public static final SecurityScheme BASIC_SECURITY_SCHEMA =
      new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("basic");
  private static final Logger LOGGER = LoggerFactory.getLogger(SelfManagedOpenApiConfigurer.class);
  private final OpenApiConfigurationProperties properties;

  public SelfManagedOpenApiConfigurer(final OpenApiConfigurationProperties properties) {
    this.properties = properties;
  }

  @Override
  public void configureSecurity(final OpenAPI openApi) {
    LOGGER.debug("Configuring OpenAPI security for Self-Managed deployment");

    openApi.getComponents().addSecuritySchemes(BEARER_SECURITY_SCHEMA_NAME, BEARER_SECURITY_SCHEMA);
    openApi.addSecurityItem(new SecurityRequirement().addList(BEARER_SECURITY_SCHEMA_NAME));

    LOGGER.debug("Adding Basic Auth security scheme");
    openApi.getComponents().addSecuritySchemes(BASIC_SECURITY_SCHEMA_NAME, BASIC_SECURITY_SCHEMA);
    openApi.addSecurityItem(new SecurityRequirement().addList(BASIC_SECURITY_SCHEMA_NAME));

    if (properties.getSelfManagedAuth().getOpenIdConnectDiscoveryUrl() != null
        && !properties.getSelfManagedAuth().getOpenIdConnectDiscoveryUrl().trim().isEmpty()) {

      LOGGER.debug(
          "Adding OIDC security scheme with discovery URL: {}",
          properties.getSelfManagedAuth().getOpenIdConnectDiscoveryUrl());

      final SecurityScheme oidcSecurityScheme =
          createOidcSecurityScheme(properties.getSelfManagedAuth().getOpenIdConnectDiscoveryUrl());

      openApi.getComponents().addSecuritySchemes(OIDC_SECURITY_SCHEMA_NAME, oidcSecurityScheme);
      openApi.addSecurityItem(new SecurityRequirement().addList(OIDC_SECURITY_SCHEMA_NAME));
    }
  }

  @Override
  public String getApiDescription() {
    return """
        API for communicating with a Camunda 8 Self-Managed cluster.

        **Authentication Options:**
        - **Basic Authentication**: Use username/password credentials
        - **OIDC Authentication**: Both OAuth2 flow and Bearer token supported
          - Use Swagger UI's Authorize flow, or
          - Log into `/operate/login` to get proper credentials""";
  }

  private SecurityScheme createOidcSecurityScheme(final String discoveryUrl) {
    // For OpenAPI documentation, we use the OpenID Connect URL directly
    // The actual authorization and token URLs will be discovered at runtime by the OIDC client
    // We don't need to specify them explicitly in the OpenAPI spec when using openIdConnectUrl
    return new SecurityScheme()
        .type(SecurityScheme.Type.OPENIDCONNECT)
        .openIdConnectUrl(discoveryUrl);
  }
}
