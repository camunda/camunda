/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.starter;

import io.camunda.auth.spring.converter.OidcTokenAuthenticationConverter;
import io.camunda.auth.spring.filter.OAuth2RefreshTokenFilter;
import io.camunda.auth.spring.handler.AuthFailureHandler;
import io.camunda.auth.starter.config.CamundaAuthProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

/**
 * Auto-configuration providing generic SecurityFilterChain beans for unprotected paths, API
 * protection with JWT, and catch-all deny chain. Gated on {@code camunda.auth.method=oidc}
 * (default).
 */
@AutoConfiguration(after = CamundaOidcAutoConfiguration.class)
@ConditionalOnProperty(name = "camunda.auth.method", havingValue = "oidc")
@EnableWebSecurity
public class CamundaSecurityFilterChainAutoConfiguration {

  private static final Logger LOG =
      LoggerFactory.getLogger(CamundaSecurityFilterChainAutoConfiguration.class);

  @Bean
  @Order(0)
  @ConditionalOnMissingBean(name = "unprotectedPathsSecurityFilterChain")
  public SecurityFilterChain unprotectedPathsSecurityFilterChain(
      final HttpSecurity http, final CamundaAuthProperties properties) throws Exception {
    final var unprotectedPaths = properties.getSecurity().getUnprotectedPaths();
    LOG.debug("Configuring unprotected paths: {}", unprotectedPaths);
    return http.securityMatcher(unprotectedPaths.toArray(String[]::new))
        .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
        .csrf(csrf -> csrf.disable())
        .cors(cors -> cors.disable())
        .formLogin(form -> form.disable())
        .anonymous(anon -> anon.disable())
        .build();
  }

  @Bean
  @Order(0)
  @ConditionalOnMissingBean(name = "unprotectedApiSecurityFilterChain")
  @ConditionalOnProperty(name = "camunda.auth.unprotected-api", havingValue = "true")
  public SecurityFilterChain unprotectedApiSecurityFilterChain(
      final HttpSecurity http, final CamundaAuthProperties properties) throws Exception {
    final var apiPaths = properties.getSecurity().getApiPaths();
    LOG.warn(
        "API paths are UNPROTECTED. This is intended for development only. API paths: {}",
        apiPaths);
    return http.securityMatcher(apiPaths.toArray(String[]::new))
        .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
        .csrf(csrf -> csrf.disable())
        .build();
  }

  @Bean
  @Order(1)
  @ConditionalOnMissingBean(name = "oidcApiSecurityFilterChain")
  @ConditionalOnProperty(
      name = "camunda.auth.unprotected-api",
      havingValue = "false",
      matchIfMissing = true)
  public SecurityFilterChain oidcApiSecurityFilterChain(
      final HttpSecurity http,
      final CamundaAuthProperties properties,
      final JwtDecoder jwtDecoder,
      final OidcTokenAuthenticationConverter oidcTokenAuthenticationConverter,
      final AuthFailureHandler authFailureHandler,
      final OAuth2RefreshTokenFilter oAuth2RefreshTokenFilter)
      throws Exception {
    final var apiPaths = properties.getSecurity().getApiPaths();
    final var unprotectedApiPaths = properties.getSecurity().getUnprotectedApiPaths();

    return http.securityMatcher(apiPaths.toArray(String[]::new))
        .authorizeHttpRequests(
            auth -> {
              if (!unprotectedApiPaths.isEmpty()) {
                auth.requestMatchers(unprotectedApiPaths.toArray(String[]::new)).permitAll();
              }
              auth.anyRequest().authenticated();
            })
        .oauth2ResourceServer(
            rs ->
                rs.jwt(jwt -> jwt.decoder(jwtDecoder))
                    .authenticationEntryPoint(authFailureHandler)
                    .accessDeniedHandler(authFailureHandler))
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.NEVER))
        .addFilterAfter(oAuth2RefreshTokenFilter, BasicAuthenticationFilter.class)
        .csrf(csrf -> csrf.disable())
        .requestCache(cache -> cache.disable())
        .build();
  }

  @Bean
  @Order(2)
  @ConditionalOnMissingBean(name = "protectedUnhandledPathsSecurityFilterChain")
  public SecurityFilterChain protectedUnhandledPathsSecurityFilterChain(final HttpSecurity http)
      throws Exception {
    return http.securityMatcher("/**")
        .authorizeHttpRequests(auth -> auth.anyRequest().denyAll())
        .exceptionHandling(
            eh -> eh.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.NOT_FOUND)))
        .csrf(csrf -> csrf.disable())
        .build();
  }
}
