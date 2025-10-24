/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * Abstract base class for OpenAPI configurers that provides common security scheme definitions and
 * utilities. Subclasses should implement deployment-specific security configurations for SaaS vs
 * Self-Managed deployments.
 */
public abstract class OpenApiConfigurer {

  public static final String BEARER_SECURITY_SCHEMA_NAME = "bearerAuth";
  public static final SecurityScheme BEARER_SECURITY_SCHEMA =
      new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT");

  /**
   * Configures the OpenAPI security schemes for the specific deployment type. Ensure that the
   * `securitySchemes` section in the OpenAPI YAML file mirrors the security schemes defined in
   * `OpenApiResourceConfig.java`. This helps client generators produce better boilerplate code for
   * authentication. Any changes to the security configuration in the Java file should be reflected
   * in the YAML specification.
   *
   * @param openApi the OpenAPI object to configure
   */
  public abstract void configureSecurity(OpenAPI openApi);

  /**
   * Gets the API description for the specific deployment type.
   *
   * @return the API description text
   */
  public abstract String getApiDescription();
}
