/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.starter;

import io.camunda.auth.domain.model.AuthenticationMethod;
import io.camunda.auth.domain.spi.BasicAuthMembershipResolver;
import io.camunda.auth.spring.SecurityFilterChainCustomizer;
import io.camunda.auth.spring.SecurityFilterChainHelper;
import io.camunda.auth.spring.converter.UsernamePasswordAuthenticationTokenConverter;
import io.camunda.auth.spring.handler.AuthFailureHandler;
import io.camunda.auth.starter.condition.ConditionalOnAuthenticationMethod;
import io.camunda.auth.starter.condition.ConditionalOnProtectedApi;
import io.camunda.auth.starter.config.CamundaAuthProperties;
import java.util.Collections;
import java.util.List;
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
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

/**
 * Auto-configuration for basic authentication filter chains. Requires secondary storage (database)
 * to be available.
 */
@AutoConfiguration(after = CamundaAuthAutoConfiguration.class)
@ConditionalOnAuthenticationMethod(AuthenticationMethod.BASIC)
@ConditionalOnProperty(
    name = "camunda.auth.basic.secondary-storage-available",
    havingValue = "true")
@EnableWebSecurity
public class CamundaBasicAuthAutoConfiguration {

  private static final Logger LOG =
      LoggerFactory.getLogger(CamundaBasicAuthAutoConfiguration.class);

  @Bean
  @ConditionalOnMissingBean(UsernamePasswordAuthenticationTokenConverter.class)
  public UsernamePasswordAuthenticationTokenConverter usernamePasswordAuthenticationTokenConverter(
      final BasicAuthMembershipResolver basicAuthMembershipResolver) {
    return new UsernamePasswordAuthenticationTokenConverter(basicAuthMembershipResolver);
  }

  @Bean
  @Order(0)
  @ConditionalOnMissingBean(name = "basicUnprotectedPathsSecurityFilterChain")
  public SecurityFilterChain basicUnprotectedPathsSecurityFilterChain(
      final HttpSecurity http, final CamundaAuthProperties properties) throws Exception {
    final var unprotectedPaths = properties.getSecurity().getUnprotectedPaths();
    LOG.debug("Configuring unprotected paths for basic auth: {}", unprotectedPaths);
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
  @ConditionalOnMissingBean(name = "basicUnprotectedApiSecurityFilterChain")
  @ConditionalOnProperty(name = "camunda.auth.unprotected-api", havingValue = "true")
  public SecurityFilterChain basicUnprotectedApiSecurityFilterChain(
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
  @ConditionalOnMissingBean(name = "basicApiSecurityFilterChain")
  @ConditionalOnProtectedApi
  public SecurityFilterChain basicApiSecurityFilterChain(
      final HttpSecurity http,
      final CamundaAuthProperties properties,
      final AuthFailureHandler authFailureHandler)
      throws Exception {
    final var apiPaths = properties.getSecurity().getApiPaths();
    final var unprotectedApiPaths = properties.getSecurity().getUnprotectedApiPaths();

    LOG.debug("Configuring basic auth API security for paths: {}", apiPaths);

    return http.securityMatcher(apiPaths.toArray(String[]::new))
        .authorizeHttpRequests(
            auth -> {
              if (!unprotectedApiPaths.isEmpty()) {
                auth.requestMatchers(unprotectedApiPaths.toArray(String[]::new)).permitAll();
              }
              auth.anyRequest().authenticated();
            })
        .httpBasic(basic -> basic.authenticationEntryPoint(authFailureHandler))
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(
            eh ->
                eh.authenticationEntryPoint(authFailureHandler)
                    .accessDeniedHandler(authFailureHandler))
        .csrf(csrf -> csrf.disable())
        .requestCache(cache -> cache.disable())
        .build();
  }

  @Bean
  @Order(1)
  @ConditionalOnMissingBean(name = "basicWebappSecurityFilterChain")
  @ConditionalOnProperty(name = "camunda.auth.security.webapp-enabled", havingValue = "true")
  public SecurityFilterChain basicWebappSecurityFilterChain(
      final HttpSecurity http,
      final CamundaAuthProperties properties,
      final AuthFailureHandler authFailureHandler,
      final List<SecurityFilterChainCustomizer> customizers)
      throws Exception {
    final var securityProps = properties.getSecurity();
    final var webappPaths = securityProps.getWebappPaths();
    final var sessionCookie = securityProps.getSessionCookie();

    LOG.debug("Configuring basic auth webapp security for paths: {}", webappPaths);

    http.securityMatcher(webappPaths.toArray(String[]::new));

    // Form login
    http.formLogin(
        form ->
            form.loginPage("/login")
                .failureHandler(authFailureHandler)
                .successHandler((request, response, authentication) -> response.sendRedirect("/")));

    // Logout
    http.logout(logout -> logout.logoutSuccessUrl("/login").deleteCookies(sessionCookie));

    // CSRF
    if (securityProps.isCsrfEnabled()) {
      SecurityFilterChainHelper.configureCsrf(
          http,
          securityProps.getCsrfTokenName(),
          securityProps.getUnprotectedPaths(),
          securityProps.getUnprotectedApiPaths(),
          "/login",
          "/logout");
    } else {
      http.csrf(csrf -> csrf.disable());
    }

    // Secure headers
    SecurityFilterChainHelper.setupSecureHeaders(http);

    // Authorization
    http.authorizeHttpRequests(auth -> auth.anyRequest().authenticated());

    // Exception handling
    http.exceptionHandling(
        eh ->
            eh.authenticationEntryPoint(authFailureHandler)
                .accessDeniedHandler(authFailureHandler));

    // Apply customizers
    for (final SecurityFilterChainCustomizer customizer :
        customizers != null
            ? customizers
            : Collections.<SecurityFilterChainCustomizer>emptyList()) {
      customizer.customize(http);
    }

    return http.build();
  }

  @Bean
  @Order(2)
  @ConditionalOnMissingBean(name = "basicProtectedUnhandledPathsSecurityFilterChain")
  public SecurityFilterChain basicProtectedUnhandledPathsSecurityFilterChain(
      final HttpSecurity http) throws Exception {
    return http.securityMatcher("/**")
        .authorizeHttpRequests(auth -> auth.anyRequest().denyAll())
        .exceptionHandling(
            eh -> eh.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.NOT_FOUND)))
        .csrf(csrf -> csrf.disable())
        .build();
  }
}
