/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.shared.security;

import io.camunda.zeebe.gateway.rest.ConditionalOnRestGatewayEnabled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;
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

  private static final Logger LOGGER = LoggerFactory.getLogger(SecurityConfiguration.class);

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

  /**
   * Condition to check if the gateway is configured to use authentication, i.e. is not explicitly
   * set to {@code NONE}. It helps deal with the fact that the gateway can be embedded in the broker
   * or run standalone.
   */
  static class GatewaySecurityAuthenticationEnabledCondition implements Condition {

    @Override
    public boolean matches(final ConditionContext context, final AnnotatedTypeMetadata metadata) {
      final Environment env = context.getEnvironment();
      final boolean standaloneGatewayEnabled =
          "true".equals(env.getProperty("zeebe.gateway.enable"));
      final boolean standaloneGatewaySecurityNotNone =
          !"none".equals(env.getProperty("zeebe.gateway.security.authentication.mode"));
      final boolean embeddedGatewayEnabled =
          "true".equals(env.getProperty("zeebe.broker.gateway.enable"));
      final boolean embeddedGatewaySecurityNotNone =
          !"none".equals(env.getProperty("zeebe.broker.gateway.security.authentication.mode"));

      warnAboutAmbiguity(standaloneGatewayEnabled, embeddedGatewaySecurityNotNone, env);

      return (standaloneGatewayEnabled && standaloneGatewaySecurityNotNone)
          || (embeddedGatewayEnabled && embeddedGatewaySecurityNotNone);
    }

    private void warnAboutAmbiguity(
        final boolean standaloneGatewayEnabled,
        final boolean embeddedGatewayEnabled,
        final Environment env) {
      final String standaloneGatewaySecurityMode =
          env.getProperty("zeebe.gateway.security.authentication.mode");
      final String embeddedGatewaySecurityMode =
          env.getProperty("zeebe.broker.gateway.security.authentication.mode");

      if (standaloneGatewayEnabled && "identity".equals(embeddedGatewaySecurityMode)) {
        LOGGER.warn(
            "Standalone gateway is enabled but embedded gateway security mode is set to identity. "
                + "This configuration is ambiguous. Only the standalone gateway security mode will be used.");
      }

      if (embeddedGatewayEnabled && "identity".equals(standaloneGatewaySecurityMode)) {
        LOGGER.warn(
            "Embedded gateway is enabled but standalone gateway security mode is set to identity. "
                + "This configuration is ambiguous. Only the embedded gateway security mode will be used.");
      }
    }
  }
}
