/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.shared.security;

import io.camunda.zeebe.gateway.rest.ConditionalOnRestGatewayEnabled;
import io.camunda.zeebe.gateway.rest.TenantAttributeHolder;
import io.camunda.zeebe.shared.management.ConditionalOnManagementContext;
import org.springframework.boot.actuate.autoconfigure.web.ManagementContextConfiguration;
import org.springframework.boot.actuate.autoconfigure.web.ManagementContextType;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.WebFilterExchange;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import reactor.core.publisher.Mono;

@Profile("identity-auth")
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
@Configuration(proxyBeanMethods = false)
public final class SecurityConfiguration {

  @Bean
  @ConditionalOnRestGatewayEnabled
  public SecurityWebFilterChain restGatewaySecurity(
      final ServerHttpSecurity http,
      final IdentityAuthenticationManager authManager,
      final PreAuthTokenConverter converter,
      final ProblemAuthFailureHandler authFailureHandler) {
    final var authFilter = new AuthenticationWebFilter(authManager);
    authFilter.setServerAuthenticationConverter(converter);
    authFilter.setAuthenticationFailureHandler(authFailureHandler);
    authFilter.setAuthenticationSuccessHandler(SecurityConfiguration::injectTenantIds);

    return configureSecurity(http)
        .authenticationManager(authManager)
        .addFilterAt(authFilter, SecurityWebFiltersOrder.AUTHENTICATION)
        .exceptionHandling(
            spec ->
                spec.authenticationEntryPoint(authFailureHandler)
                    .accessDeniedHandler(authFailureHandler))
        .authorizeExchange(spec -> spec.anyExchange().authenticated())
        .build();
  }

  private static Mono<Void> injectTenantIds(
      final WebFilterExchange webFilterExchange, final Authentication authentication) {
    final var exchange = webFilterExchange.getExchange();
    if (authentication instanceof final IdentityAuthentication identity) {
      TenantAttributeHolder.withTenantIds(exchange, identity.tenantIds());
    }

    return webFilterExchange.getChain().filter(exchange);
  }

  private static ServerHttpSecurity configureSecurity(final ServerHttpSecurity http) {
    return http.csrf(CsrfSpec::disable)
        .cors(CorsSpec::disable)
        .logout(LogoutSpec::disable)
        .formLogin(FormLoginSpec::disable)
        .httpBasic(HttpBasicSpec::disable)
        .anonymous(AnonymousSpec::disable);
  }

  @Profile("identity-auth")
  @ConditionalOnManagementContext
  @EnableWebFluxSecurity
  @EnableReactiveMethodSecurity
  @ManagementContextConfiguration(value = ManagementContextType.ANY, proxyBeanMethods = false)
  public static final class ManagementSecurityConfiguration {
    @Bean
    public SecurityWebFilterChain managementSecurity(final ServerHttpSecurity http) {
      return configureSecurity(http)
          .authorizeExchange(spec -> spec.anyExchange().permitAll())
          .build();
    }
  }
}
