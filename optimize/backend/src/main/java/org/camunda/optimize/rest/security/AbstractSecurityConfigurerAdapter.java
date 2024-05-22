/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest.security;

import static org.camunda.optimize.jetty.OptimizeResourceConstants.REST_API_PATH;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.camunda.optimize.service.security.AuthCookieService;
import org.camunda.optimize.service.security.SessionService;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

@RequiredArgsConstructor
public abstract class AbstractSecurityConfigurerAdapter {

  protected static final String PUBLIC_API_PATH = createApiPath("/public/**");

  protected final ConfigurationService configurationService;
  protected final CustomPreAuthenticatedAuthenticationProvider
      preAuthenticatedAuthenticationProvider;
  protected final SessionService sessionService;
  protected final AuthCookieService authCookieService;

  @SneakyThrows
  protected SecurityFilterChain applyPublicApiOptions(HttpSecurity http) {
    return configureGenericSecurityOptions(http)
        // everything requires authentication
        .authorizeHttpRequests(httpRequests -> httpRequests.anyRequest().authenticated())
        .oauth2ResourceServer(
            oauth2resourceServer ->
                oauth2resourceServer.jwt(
                    jwtConfigurer -> jwtConfigurer.decoder(publicApiJwtDecoder())))
        .build();
  }

  @SneakyThrows
  protected HttpSecurity configureGenericSecurityOptions(HttpSecurity http) {
    return http
        // csrf is not used but the same-site property of the auth cookie, see
        // AuthCookieService#createNewOptimizeAuthCookie
        .csrf(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        // disable frame options so embed links work, it's not a risk disabling this globally as
        // clickjacking
        // is prevented by the same-site flag being set to `strict` on the authentication cookie
        .headers(headers -> headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::disable))
        // spring session management is not needed as we have stateless session handling using a JWT
        // token stored as cookie
        .sessionManagement(
            sessionMgmt -> sessionMgmt.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
  }

  protected abstract JwtDecoder publicApiJwtDecoder();

  protected static String createApiPath(final String... subPath) {
    return REST_API_PATH + String.join("", subPath);
  }
}
