/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.shared.security;

import static io.camunda.authentication.config.AuthenticationProperties.API_UNPROTECTED;

import io.camunda.zeebe.gateway.rest.ConditionalOnRestGatewayEnabled;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AnonymousConfigurer;
import org.springframework.security.config.annotation.web.configurers.CorsConfigurer;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.config.annotation.web.configurers.FormLoginConfigurer;
import org.springframework.security.config.annotation.web.configurers.HttpBasicConfigurer;
import org.springframework.security.config.annotation.web.configurers.LogoutConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFilter;
import org.springframework.security.web.servletapi.SecurityContextHolderAwareRequestFilter;

@Profile("identity-auth")
@EnableWebSecurity
@EnableMethodSecurity
@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {

  @Bean
  @Order(Ordered.HIGHEST_PRECEDENCE)
  public SecurityFilterChain managementSecurity(final HttpSecurity http) throws Exception {
    http.securityMatchers(
        (matchers) -> {
          matchers
              // all actuator endpoints
              .requestMatchers(EndpointRequest.toAnyEndpoint())
              // endpoints defined in BrokerHealthRoutes
              .requestMatchers("/ready", "/health", "/startup")
              // allows forwarding the failure when request failed
              // for example when an endpoint could not be found
              .requestMatchers("/error");
        });

    return configureSecurity(http)
        .authorizeHttpRequests(spec -> spec.anyRequest().permitAll())
        .build();
  }

  @Bean
  @ConditionalOnRestGatewayEnabled
  @ConditionalOnProperty(value = API_UNPROTECTED, havingValue = "false")
  public SecurityFilterChain restGatewaySecurity(
      final HttpSecurity http,
      final IdentityAuthenticationManager authManager,
      final PreAuthTokenConverter converter,
      final ProblemAuthFailureHandler authFailureHandler)
      throws Exception {
    final var authFilter = new AuthenticationFilter(authManager, converter);
    authFilter.setFailureHandler(authFailureHandler);

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

  private HttpSecurity configureSecurity(final HttpSecurity http) throws Exception {
    return http.csrf(CsrfConfigurer::disable)
        .cors(CorsConfigurer::disable)
        .logout(LogoutConfigurer::disable)
        .formLogin(FormLoginConfigurer::disable)
        .httpBasic(HttpBasicConfigurer::disable)
        .anonymous(AnonymousConfigurer::disable);
  }
}
