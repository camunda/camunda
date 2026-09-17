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
import io.camunda.security.spring.security.SecurityHeadersCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

/**
 * Adds to CSL the Identity {@code write:*} (OPTIMIZE_PERMISSION) gate on CCSM session logins, which
 * CSL does not cover. Bearer tokens on {@code /api/**} stay ungated, as they were on legacy CCSM.
 *
 * <p>{@code optimize.security.csl.enabled=false} selects the legacy stack instead.
 */
@Configuration
@Conditional(CCSMCondition.class)
@ConditionalOnProperty(
    name = "optimize.security.csl.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class OptimizeCcsmSecurityConfiguration {

  /**
   * {@link SecurityHeadersCustomizer} carries the filter because it is the only CSL extension point
   * that receives the real {@link HttpSecurity} builder, and every chain builder applies it. The
   * filter lands on all CSL chains, including the unprotected one, where it is inert for want of a
   * session.
   *
   * <p>It goes after {@link AuthorizationFilter} because CSL's {@code OAuth2RefreshTokenFilter}
   * anchors there too and claims the position first, so the webapp chain refreshes an expired token
   * before the filter reads it.
   */
  @Bean
  public SecurityHeadersCustomizer ccsmSessionPermissionEnforcementFilterInstaller(
      final CCSMTokenService ccsmTokenService) {
    final OptimizeCcsmSessionPermissionEnforcementFilter filter =
        new OptimizeCcsmSessionPermissionEnforcementFilter(ccsmTokenService);
    return httpSecurity -> httpSecurity.addFilterAfter(filter, AuthorizationFilter.class);
  }
}
