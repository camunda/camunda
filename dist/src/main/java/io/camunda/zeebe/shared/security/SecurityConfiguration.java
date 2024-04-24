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
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.boot.actuate.autoconfigure.web.ManagementContextConfiguration;
import org.springframework.boot.actuate.autoconfigure.web.ManagementContextType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AnonymousConfigurer;
import org.springframework.security.config.annotation.web.configurers.CorsConfigurer;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.config.annotation.web.configurers.FormLoginConfigurer;
import org.springframework.security.config.annotation.web.configurers.HttpBasicConfigurer;
import org.springframework.security.config.annotation.web.configurers.LogoutConfigurer;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFilter;
import org.springframework.security.web.servletapi.SecurityContextHolderAwareRequestFilter;

@Profile("identity-auth")
@EnableWebSecurity
@EnableMethodSecurity
@Configuration(proxyBeanMethods = false)
public final class SecurityConfiguration {

  @Bean
  @ConditionalOnRestGatewayEnabled
  public SecurityFilterChain restGatewaySecurity(
      final HttpSecurity http,
      final IdentityAuthenticationManager authManager,
      final PreAuthTokenConverter converter,
      final ProblemAuthFailureHandler authFailureHandler)
      throws Exception {
    final var authFilter = new AuthenticationFilter(authManager, converter);
    authFilter.setFailureHandler(authFailureHandler);
    authFilter.setSuccessHandler(SecurityConfiguration::injectTenantIds);

    return configureSecurity(http)
        .authenticationManager(authManager)
        .addFilterBefore(authFilter, SecurityContextHolderAwareRequestFilter.class)
        .exceptionHandling(
            spec ->
                spec.authenticationEntryPoint(authFailureHandler)
                    .accessDeniedHandler(authFailureHandler))
        .authorizeHttpRequests(spec -> spec.anyRequest().authenticated())
        .build();
  }

  private static void injectTenantIds(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final Authentication authentication)
      throws IOException, ServletException {
    if (authentication instanceof final IdentityAuthentication identity) {
      TenantAttributeHolder.withTenantIds(identity.tenantIds());
    }
  }

  private static HttpSecurity configureSecurity(final HttpSecurity http) throws Exception {
    return http.csrf(CsrfConfigurer::disable)
        .cors(CorsConfigurer::disable)
        .logout(LogoutConfigurer::disable)
        .formLogin(FormLoginConfigurer::disable)
        .httpBasic(HttpBasicConfigurer::disable)
        .anonymous(AnonymousConfigurer::disable);
  }

  @Profile("identity-auth")
  @ConditionalOnManagementContext
  @EnableWebSecurity
  @EnableMethodSecurity
  @ManagementContextConfiguration(value = ManagementContextType.ANY, proxyBeanMethods = false)
  public static final class ManagementSecurityConfiguration {
    @Bean
    public SecurityFilterChain managementSecurity(final HttpSecurity http) throws Exception {
      return configureSecurity(http)
          .authorizeHttpRequests(spec -> spec.anyRequest().permitAll())
          .build();
    }
  }
}
