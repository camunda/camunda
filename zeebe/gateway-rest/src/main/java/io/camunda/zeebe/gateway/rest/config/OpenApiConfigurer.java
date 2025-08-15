/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.config;

import io.swagger.v3.oas.models.OpenAPI;

/**
 * Interface for configuring OpenAPI security schemes and descriptions based on deployment type
 * (SaaS vs Self-Managed).
 */
public interface OpenApiConfigurer {

  /**
   * Configures the OpenAPI security schemes for the specific deployment type.
   *
   * @param openApi the OpenAPI object to configure
   */
  void configureSecurity(OpenAPI openApi);

  /**
   * Gets the API description for the specific deployment type.
   *
   * @return the API description text
   */
  String getApiDescription();
}
