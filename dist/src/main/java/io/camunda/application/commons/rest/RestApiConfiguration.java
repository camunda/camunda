/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.rest;

import io.camunda.authentication.CachingCamundaAuthenticationProvider;
import io.camunda.authentication.ConditionalOnUnprotectedApi;
import io.camunda.authentication.DefaultCamundaAuthenticationProvider;
import io.camunda.authentication.converter.CamundaAuthenticationDelegatingConverter;
import io.camunda.authentication.converter.UnprotectedCamundaAuthenticationConverter;
import io.camunda.authentication.holder.CamundaAuthenticationDelegatingHolder;
import io.camunda.authentication.holder.HttpSessionBasedAuthenticationHolder;
import io.camunda.authentication.holder.RequestContextBasedAuthenticationHolder;
import io.camunda.security.auth.CamundaAuthenticationConverter;
import io.camunda.security.auth.CamundaAuthenticationHolder;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.zeebe.gateway.rest.ConditionalOnRestGatewayEnabled;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackages = {"io.camunda.zeebe.gateway.rest", "io.camunda.service.validation"})
@ConditionalOnRestGatewayEnabled
public class RestApiConfiguration {

  @Bean
  @ConditionalOnUnprotectedApi
  public CamundaAuthenticationConverter<Authentication> unprotectedAuthenticationConverter() {
    return new UnprotectedCamundaAuthenticationConverter();
  }

  @Bean
  public CamundaAuthenticationHolder requestContextBasedAuthenticationHolder(
      final HttpServletRequest request) {
    return new RequestContextBasedAuthenticationHolder(request);
  }

  @Bean
  public CamundaAuthenticationHolder httpSessionBasedAuthenticationHolder(
      final HttpServletRequest request, final SecurityConfiguration securityConfiguration) {
    return new HttpSessionBasedAuthenticationHolder(
        request, securityConfiguration.getAuthentication());
  }

  @Bean
  public CamundaAuthenticationProvider camundaAuthenticationProvider(
      final List<CamundaAuthenticationHolder> holders,
      final List<CamundaAuthenticationConverter<Authentication>> converters,
      @Value("${camunda.security.authentication.oidc.camunda-auth-cache.enabled:true}")
          final boolean cacheEnabled,
      @Value("${camunda.security.authentication.oidc.camunda-auth-cache.max-size:10000}")
          final long cacheMaxSize,
      @Value("${camunda.security.authentication.oidc.camunda-auth-cache.max-ttl:PT5M}")
          final Duration cacheMaxTtl) {
    final CamundaAuthenticationProvider underlying =
        new DefaultCamundaAuthenticationProvider(
            new CamundaAuthenticationDelegatingHolder(holders),
            new CamundaAuthenticationDelegatingConverter(converters));
    // Per-token cross-request cache for the built CamundaAuthentication. The default in-request
    // RequestContextBasedAuthenticationHolder dedups within one request; this wrapper additionally
    // dedups across requests for the same bearer token, eliminating the 3-4 secondary-storage
    // queries that DefaultMembershipService issues per build (mappingRules, groups, roles,
    // tenants). Only JwtAuthenticationToken-authenticated requests are cached; session-based and
    // basic-auth flows fall through to the delegate. See {@link
    // CachingCamundaAuthenticationProvider}.
    if (cacheEnabled) {
      return new CachingCamundaAuthenticationProvider(underlying, cacheMaxSize, cacheMaxTtl);
    }
    return underlying;
  }
}
