/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.shared.security;

import io.camunda.zeebe.gateway.rest.TenantAttributeHolder;
import io.camunda.zeebe.shared.management.ConditionalOnNonManagementContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity.AnonymousSpec;
import org.springframework.security.config.web.server.ServerHttpSecurity.CorsSpec;
import org.springframework.security.config.web.server.ServerHttpSecurity.CsrfSpec;
import org.springframework.security.config.web.server.ServerHttpSecurity.FormLoginSpec;
import org.springframework.security.config.web.server.ServerHttpSecurity.HttpBasicSpec;
import org.springframework.security.config.web.server.ServerHttpSecurity.LogoutSpec;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Configuration(proxyBeanMethods = false)
@Profile("identity-auth")
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
@ConditionalOnNonManagementContext
public final class RestGatewaySecurity {

  @Bean
  public SecurityWebFilterChain identityAuthentication(
      final ServerHttpSecurity http,
      final IdentityAuthenticationManager authManager,
      final PreAuthTokenConverter converter,
      final ProblemAuthFailureHandler authFailureHandler) {
    final var authFilter = new AuthenticationWebFilter(authManager);
    authFilter.setServerAuthenticationConverter(converter);
    authFilter.setAuthenticationFailureHandler(authFailureHandler);

    return http.csrf(CsrfSpec::disable)
        .cors(CorsSpec::disable)
        .logout(LogoutSpec::disable)
        .formLogin(FormLoginSpec::disable)
        .httpBasic(HttpBasicSpec::disable)
        .authenticationManager(authManager)
        .anonymous(AnonymousSpec::disable)
        .addFilterAt(authFilter, SecurityWebFiltersOrder.AUTHENTICATION)
        .addFilterAfter(this::injectTenantIdsToContext, SecurityWebFiltersOrder.AUTHORIZATION)
        .exceptionHandling(spec -> spec.accessDeniedHandler(authFailureHandler))
        .authorizeExchange(spec -> spec.anyExchange().authenticated())
        .build();
  }

  private Mono<Void> injectTenantIdsToContext(
      final ServerWebExchange exchange, final WebFilterChain chain) {
    return exchange
        .getPrincipal()
        .cast(IdentityAuthentication.class)
        .map(IdentityAuthentication::tenantIds)
        .map(tenantIds -> TenantAttributeHolder.withTenantIds(exchange, tenantIds))
        .flatMap(chain::filter);
  }
}
