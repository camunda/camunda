/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.identity.authentication.config;

import static org.springframework.security.config.Customizer.withDefaults;

import io.camunda.identity.authentication.handler.CamundaAuthenticationFailureHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;

@Configuration
@Profile({"identity-basic-auth", "identity-oidc-auth"})
@EnableWebSecurity
@EnableMethodSecurity
public class WebSecurityConfig {
  public static final String[] UNAUTHENTICATED_PATHS =
      new String[] {"/login**", "/logout**", "/error**", "/actuator**"};
  private static final Logger LOG = LoggerFactory.getLogger(WebSecurityConfig.class);

  @Bean
  public SecurityFilterChain securityFilterChain(final HttpSecurity httpSecurity) {
    try {
      return httpSecurity.build();
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Bean
  @Primary
  @Profile("identity-basic-auth")
  public HttpSecurity localHttpSecurity(
      final HttpSecurity httpSecurity, final CamundaAuthenticationFailureHandler authFailureHandler)
      throws Exception {
    LOG.info("Configuring basic auth login");
    return baseHttpSecurity(httpSecurity, authFailureHandler)
        .httpBasic(withDefaults())
        .logout((logout) -> logout.logoutSuccessUrl("/"));
  }

  @Bean
  @Primary
  @Profile("identity-oidc-auth")
  public HttpSecurity oidcHttpSecurity(
      final HttpSecurity httpSecurity,
      final ClientRegistrationRepository clientRegistrationRepository,
      final CamundaAuthenticationFailureHandler authFailureHandler)
      throws Exception {
    LOG.info("Configuring OIDC login");
    return baseHttpSecurity(httpSecurity, authFailureHandler)
        .oauth2Login(withDefaults())
        .logout(
            logout ->
                logout.logoutSuccessHandler(oidcLogoutSuccessHandler(clientRegistrationRepository)))
        .oidcLogout((logout) -> logout.backChannel(withDefaults()));
  }

  private HttpSecurity baseHttpSecurity(
      final HttpSecurity httpSecurity,
      final CamundaAuthenticationFailureHandler authFailureHandler) {
    try {
      return httpSecurity
          .authorizeHttpRequests(
              (authorizeHttpRequests) ->
                  authorizeHttpRequests
                      .requestMatchers(UNAUTHENTICATED_PATHS)
                      .permitAll()
                      .anyRequest()
                      .authenticated())
          .headers(
              (headers) ->
                  headers.httpStrictTransportSecurity(
                      (httpStrictTransportSecurity) ->
                          httpStrictTransportSecurity
                              .includeSubDomains(true)
                              .maxAgeInSeconds(63072000)
                              .preload(true)))
          .exceptionHandling(
              (exceptionHandling) -> exceptionHandling.accessDeniedHandler(authFailureHandler))
          .csrf(AbstractHttpConfigurer::disable)
          .cors(AbstractHttpConfigurer::disable)
          .formLogin(AbstractHttpConfigurer::disable)
          .anonymous(AbstractHttpConfigurer::disable);
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }
  }

  private LogoutSuccessHandler oidcLogoutSuccessHandler(
      final ClientRegistrationRepository clientRegistrationRepository) {
    final OidcClientInitiatedLogoutSuccessHandler oidcLogoutSuccessHandler =
        new OidcClientInitiatedLogoutSuccessHandler(clientRegistrationRepository);

    oidcLogoutSuccessHandler.setPostLogoutRedirectUri("{baseUrl}");

    return oidcLogoutSuccessHandler;
  }
}
