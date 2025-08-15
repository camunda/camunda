/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.config;

import io.camunda.security.ConditionalOnSaaSConfigured;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApiDescriptionConfig {

  @Bean
  @ConditionalOnSaaSConfigured
  public ApiDescription saasApiDescription() {
    return new ApiDescription(
        """
        API for communicating with a Camunda 8 SaaS cluster.

        **Authentication:**
        - **OIDC Authentication**: Log into `/operate/login` to get proper credentials
        - **Bearer Token**: Use the Authorize button in Swagger UI to provide a Bearer token""");
  }

  @Bean
  @ConditionalOnMissingBean(ApiDescription.class)
  public ApiDescription selfManagedApiDescription() {
    return new ApiDescription(
        """
        API for communicating with a Camunda 8 Self-Managed cluster.

        **Authentication Options:**
        - **Basic Authentication**: Use username/password credentials
        - **OIDC Authentication**: Both OAuth2 flow and Bearer token supported
          - Use Swagger UI's Authorize flow, or
          - Log into `/operate/login` to get proper credentials""");
  }

  public static class ApiDescription {
    private final String description;

    public ApiDescription(final String description) {
      this.description = description;
    }

    public String getDescription() {
      return description;
    }
  }
}
