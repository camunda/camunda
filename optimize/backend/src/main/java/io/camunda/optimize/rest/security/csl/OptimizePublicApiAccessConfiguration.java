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
import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.security.spring.security.SecurityHeadersCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

/**
 * Adds {@link OptimizePublicApiAccessFilter} to every CSL chain, for Self-Managed and for SaaS.
 * Both editions recognize an API client by CSL's classification of its token; SaaS also checks the
 * cluster id.
 */
@Configuration
@ConditionalOnProperty(
    name = "optimize.security.csl.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class OptimizePublicApiAccessConfiguration {

  @Bean
  @Conditional(CCSMCondition.class)
  public SecurityHeadersCustomizer ccsmPublicApiAccessFilterCustomizer(
      final CamundaAuthenticationProvider authenticationProvider) {
    return customizer(OptimizePublicApiAccessFilter.forSelfManaged(authenticationProvider));
  }

  /** The cluster id that the legacy SaaS public API decoder validated. */
  @Bean
  @Conditional(CCSaaSCondition.class)
  public SecurityHeadersCustomizer ccsaasPublicApiAccessFilterCustomizer(
      final CamundaAuthenticationProvider authenticationProvider,
      final ConfigurationService configurationService) {
    final var cloud = configurationService.getAuthConfiguration().getCloudAuthConfiguration();
    return customizer(
        OptimizePublicApiAccessFilter.forSaas(authenticationProvider, cloud.getClusterId()));
  }

  private static SecurityHeadersCustomizer customizer(final OptimizePublicApiAccessFilter filter) {
    return http -> http.addFilterAfter(filter, AuthorizationFilter.class);
  }
}
