/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.csl;

import io.camunda.optimize.service.security.CCSMTokenService;
import io.camunda.optimize.service.util.configuration.condition.CCSMCondition;
import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.security.spring.security.SecurityHeadersCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

/**
 * Adds {@link OptimizeBearerPermissionFilter} to every CSL chain for CCSM, so the Optimize
 * permission is enforced against a role-less user's bearer token across the whole {@code /api/**}
 * surface. Independent of, and composes with, the session-path {@code
 * OptimizeComponentAccessFilter}: the two act on disjoint authentication types (login session vs.
 * bearer token) and neither depends on the other's beans.
 */
@Configuration
@Conditional(CCSMCondition.class)
@ConditionalOnProperty(
    name = "optimize.security.csl.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class OptimizeBearerPermissionConfiguration {

  @Bean
  public SecurityHeadersCustomizer bearerPermissionFilterCustomizer(
      final CamundaAuthenticationProvider authenticationProvider,
      final CCSMTokenService tokenService) {
    final var filter = new OptimizeBearerPermissionFilter(authenticationProvider, tokenService);
    return http -> http.addFilterAfter(filter, AuthorizationFilter.class);
  }
}
