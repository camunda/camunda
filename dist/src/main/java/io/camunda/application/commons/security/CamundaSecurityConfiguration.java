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
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CamundaSecurityProperties.class)
public class CamundaSecurityConfiguration {

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
    final var multiTenancyEnabled = camundaSecurityProperties.getMultiTenancy().isEnabled();
    final var apiUnprotected = camundaSecurityProperties.getAuthentication().getUnprotectedApi();

    if (multiTenancyEnabled && apiUnprotected) {
      throw new IllegalStateException(
          "Multi-tenancy is enabled (%s=%b), but the API is unprotected (%s=%b). Please enable API protection if you want to make use of multi-tenancy."
              .formatted(
                  "camunda.security.multi-tenancy.enabled",
                  true,
                  "camunda.security.authentication.unprotected-api",
                  true));
    }
  }

  @ConfigurationProperties("camunda.security")
  public static final class CamundaSecurityProperties extends SecurityConfiguration {}
}
