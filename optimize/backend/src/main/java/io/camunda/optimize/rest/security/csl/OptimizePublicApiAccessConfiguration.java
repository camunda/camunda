/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.csl;

import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.CCSMCondition;
import io.camunda.optimize.service.util.configuration.condition.CCSaaSCondition;
import io.camunda.security.spring.security.SecurityHeadersCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

/**
 * Adds {@link OptimizePublicApiAccessFilter} to every CSL chain, for Self-Managed and for SaaS,
 * with the public API audience of each edition.
 */
@Configuration
@ConditionalOnProperty(
    name = "optimize.security.csl.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class OptimizePublicApiAccessConfiguration {

  /** The audience that the legacy CCSM public API decoder validated. */
  @Bean
  @Conditional(CCSMCondition.class)
  public SecurityHeadersCustomizer ccsmPublicApiAccessFilterCustomizer(
      final ConfigurationService configurationService) {
    return customizer(configurationService.getOptimizeApiConfiguration().getAudience());
  }

  /** The audience that the legacy SaaS public API decoder validated. */
  @Bean
  @Conditional(CCSaaSCondition.class)
  public SecurityHeadersCustomizer ccsaasPublicApiAccessFilterCustomizer(
      final ConfigurationService configurationService) {
    return customizer(
        configurationService.getAuthConfiguration().getCloudAuthConfiguration().getAudience());
  }

  private static SecurityHeadersCustomizer customizer(final String publicApiAudience) {
    final var filter = new OptimizePublicApiAccessFilter(publicApiAudience);
    return http -> http.addFilterAfter(filter, AuthorizationFilter.class);
  }
}
