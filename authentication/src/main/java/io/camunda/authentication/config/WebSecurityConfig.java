/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import static org.springframework.security.config.Customizer.withDefaults;

import io.camunda.authentication.CamundaUserDetailsService;
import io.camunda.authentication.handler.AuthFailureHandler;
import io.camunda.authentication.handler.CustomMethodSecurityExpressionHandler;
import io.camunda.service.AuthorizationServices;
import io.camunda.service.UserServices;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@Profile("auth-basic|auth-oidc")
public class WebSecurityConfig {
  public static final String[] UNAUTHENTICATED_PATHS =
      new String[] {"/login**", "/logout**", "/error**", "/actuator**"};
  private static final Logger LOG = LoggerFactory.getLogger(WebSecurityConfig.class);

  @Bean
  @ConditionalOnMissingBean(MethodSecurityExpressionHandler.class)
  public MethodSecurityExpressionHandler methodSecurityExpressionHandler(
      final AuthorizationServices<AuthorizationRecord> authorizationServices) {
    return new CustomMethodSecurityExpressionHandler(authorizationServices);
  }

  @Bean
  @Profile("auth-basic")
  public CamundaUserDetailsService camundaUserDetailsService(final UserServices userServices) {
    return new CamundaUserDetailsService(userServices);
  }

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
  @Profile("auth-oidc")
  public HttpSecurity oidcHttpSecurity(
      final HttpSecurity httpSecurity,
      final AuthFailureHandler authFailureHandler,
      final ClientRegistrationRepository clientRegistrationRepository)
      throws Exception {
    return baseHttpSecurity(httpSecurity, authFailureHandler)
        .oauth2ResourceServer(
            oauth2 ->
                oauth2.jwt(
                    jwtConfigurer ->
                        jwtConfigurer.jwkSetUri(
                            clientRegistrationRepository
                                .findByRegistrationId("oidcclient")
                                .getProviderDetails()
                                .getJwkSetUri())))
        .oauth2Login(oauthLoginConfigurer -> {})
        .oidcLogout(httpSecurityOidcLogoutConfigurer -> {})
        .logout((logout) -> logout.logoutSuccessUrl("/"));
  }

  @Bean
  @Primary
  @Profile("auth-basic")
  public HttpSecurity localHttpSecurity(
      final HttpSecurity httpSecurity, final AuthFailureHandler authFailureHandler)
      throws Exception {
    LOG.info("Configuring basic auth login");
    return baseHttpSecurity(httpSecurity, authFailureHandler)
        .httpBasic(withDefaults())
        .logout((logout) -> logout.logoutSuccessUrl("/"));
  }

  private HttpSecurity baseHttpSecurity(
      final HttpSecurity httpSecurity, final AuthFailureHandler authFailureHandler) {
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
}
