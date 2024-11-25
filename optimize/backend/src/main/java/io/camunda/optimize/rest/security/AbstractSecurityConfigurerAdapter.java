/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security;

import static io.camunda.optimize.tomcat.OptimizeResourceConstants.REST_API_PATH;

import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.security.AuthCookieService;
import io.camunda.optimize.service.security.SessionService;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

public abstract class AbstractSecurityConfigurerAdapter {

  protected static final String DEEP_SUB_PATH_ANY = "/**";
  protected static final String PUBLIC_API_PATH = createApiPath("/public/**");

  protected final ConfigurationService configurationService;
  protected final CustomPreAuthenticatedAuthenticationProvider
      preAuthenticatedAuthenticationProvider;
  protected final SessionService sessionService;
  protected final AuthCookieService authCookieService;

  public AbstractSecurityConfigurerAdapter(
      final ConfigurationService configurationService,
      final CustomPreAuthenticatedAuthenticationProvider preAuthenticatedAuthenticationProvider,
      final SessionService sessionService,
      final AuthCookieService authCookieService) {
    this.configurationService = configurationService;
    this.preAuthenticatedAuthenticationProvider = preAuthenticatedAuthenticationProvider;
    this.sessionService = sessionService;
    this.authCookieService = authCookieService;
  }

  protected SecurityFilterChain applyPublicApiOptions(final HttpSecurity http) {
    try {
      return configureGenericSecurityOptions(http)
          // everything requires authentication
          .authorizeHttpRequests(httpRequests -> httpRequests.anyRequest().authenticated())
          .oauth2ResourceServer(
              oauth2resourceServer ->
                  oauth2resourceServer.jwt(
                      jwtConfigurer -> jwtConfigurer.decoder(publicApiJwtDecoder())))
          .build();
    } catch (final Exception e) {
      throw new OptimizeRuntimeException(e);
    }
  }

  protected HttpSecurity configureGenericSecurityOptions(final HttpSecurity http) {
    try {
      return http
          // csrf is not used but the same-site property of the auth cookie, see
          // AuthCookieService#createNewOptimizeAuthCookie
          .csrf(AbstractHttpConfigurer::disable)
          .httpBasic(AbstractHttpConfigurer::disable)
          // disable frame options so embed links work, it's not a risk disabling this globally as
          // clickjacking
          // is prevented by the same-site flag being set to `strict` on the authentication cookie
          .headers(headers -> headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::disable))
          // spring session management is not needed as we have stateless session handling using a
          // JWT
          // token stored as cookie
          .sessionManagement(
              sessionMgmt -> sessionMgmt.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
    } catch (final Exception e) {
      throw new OptimizeRuntimeException(e);
    }
  }

  protected abstract JwtDecoder publicApiJwtDecoder();

  protected static String createApiPath(final String... subPath) {
    return REST_API_PATH + String.join("", subPath);
  }
}
