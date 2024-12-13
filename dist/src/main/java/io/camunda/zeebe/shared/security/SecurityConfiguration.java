/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.shared.security;

import io.camunda.authentication.tenant.TenantAttributeHolder;
import io.camunda.zeebe.gateway.rest.ConditionalOnRestGatewayEnabled;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.NoneNestedConditions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ConfigurationCondition.ConfigurationPhase;
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
  @Conditional(GatewaySecurityAuthenticationEnabledCondition.class)
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
      TenantAttributeHolder.setTenantIds(identity.tenantIds());
    }
  }

  private HttpSecurity configureSecurity(final HttpSecurity http) throws Exception {
    return http.csrf(CsrfConfigurer::disable)
        .cors(CorsConfigurer::disable)
        .logout(LogoutConfigurer::disable)
        .formLogin(FormLoginConfigurer::disable)
        .httpBasic(HttpBasicConfigurer::disable)
        .anonymous(AnonymousConfigurer::disable);
  }

  /**
   * Condition to check if the gateway is configured to use authentication, i.e. is not explicitly
   * set to {@code NONE}. It helps deal with the fact that the gateway can be embedded in the broker
   * or run standalone.
   */
  static class GatewaySecurityAuthenticationEnabledCondition extends NoneNestedConditions {

    public GatewaySecurityAuthenticationEnabledCondition() {
      super(ConfigurationPhase.REGISTER_BEAN);
    }

    @ConditionalOnProperty(
        prefix = "zeebe.gateway",
        value = "security.authentication.mode",
        havingValue = "none")
    static class StandaloneGatewayCondition {}

    @ConditionalOnProperty(
        prefix = "zeebe.broker.gateway",
        value = "security.authentication.mode",
        havingValue = "none")
    static class EmbeddedGatewayCondition {}
  }
}
