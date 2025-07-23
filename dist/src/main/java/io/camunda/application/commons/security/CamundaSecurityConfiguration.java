/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.security;

import io.camunda.application.commons.security.CamundaSecurityConfiguration.CamundaSecurityProperties;
import io.camunda.security.configuration.MultiTenancyConfiguration;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.zeebe.util.VisibleForTesting;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CamundaSecurityProperties.class)
public class CamundaSecurityConfiguration {

  @VisibleForTesting
  public static final String UNPROTECTED_API_ENV_VAR =
      "CAMUNDA_SECURITY_AUTHENTICATION_UNPROTECTEDAPI";

  @VisibleForTesting
  public static final String UNPROTECTED_MCP_ENV_VAR =
      "CAMUNDA_SECURITY_AUTHENTICATION_UNPROTECTEDMCP";

  @VisibleForTesting
  public static final String AUTHORIZATION_CHECKS_ENV_VAR =
      "CAMUNDA_SECURITY_AUTHORIZATIONS_ENABLED";

  private final CamundaSecurityProperties camundaSecurityProperties;

  @Autowired
  public CamundaSecurityConfiguration(final CamundaSecurityProperties camundaSecurityProperties) {
    this.camundaSecurityProperties = camundaSecurityProperties;
  }

  @Bean
  public MultiTenancyConfiguration multiTenancyConfiguration(
      final SecurityConfiguration securityConfiguration) {
    return securityConfiguration.getMultiTenancy();
  }

  @PostConstruct
  public void validate() {
    final var multiTenancyEnabled = camundaSecurityProperties.getMultiTenancy().isChecksEnabled();
    final var apiUnprotected = camundaSecurityProperties.getAuthentication().getUnprotectedApi();
    final var mcpUnprotected = camundaSecurityProperties.getAuthentication().getUnprotectedMcp();

    if (multiTenancyEnabled && (apiUnprotected || mcpUnprotected)) {
      throw new IllegalStateException(
          "Multi-tenancy is enabled (%s=%b), but the API or MCP is unprotected (%s=%b). Please enable API and MCP protection if you want to make use of multi-tenancy."
              .formatted(
                  "camunda.security.multiTenancy.checksEnabled",
                  true,
                  "camunda.security.authentication.unprotected-api",
                  true));
    }
  }

  @ConfigurationProperties("camunda.security")
  public static final class CamundaSecurityProperties extends SecurityConfiguration {}
}
