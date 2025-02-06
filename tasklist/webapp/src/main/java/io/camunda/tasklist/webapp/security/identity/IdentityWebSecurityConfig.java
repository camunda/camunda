/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.security.identity;

import static io.camunda.tasklist.webapp.security.TasklistProfileService.IDENTITY_AUTH_PROFILE;
import static io.camunda.tasklist.webapp.security.TasklistURIs.*;
import static io.camunda.webapps.util.HttpUtils.REQUESTED_URL;
import static io.camunda.webapps.util.HttpUtils.getRequestedUrl;
import static org.apache.commons.lang3.StringUtils.contains;

import io.camunda.tasklist.webapp.security.BaseWebConfigurer;
import io.camunda.tasklist.webapp.security.oauth.IdentityOAuth2WebConfigurer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.handler.HandlerMappingIntrospector;

@Profile(IDENTITY_AUTH_PROFILE)
@EnableWebSecurity
@Component("webSecurityConfig")
public class IdentityWebSecurityConfig extends BaseWebConfigurer {

  protected final Logger logger = LoggerFactory.getLogger(getClass());

  @Autowired protected IdentityOAuth2WebConfigurer oAuth2WebConfigurer;

  @Override
  protected void applyOAuth2Settings(final HttpSecurity http) throws Exception {
    oAuth2WebConfigurer.configure(http);
  }

  @Override
  protected void applySecurityFilterSettings(
      final HttpSecurity http, final HandlerMappingIntrospector introspector) throws Exception {
    if (tasklistProperties.isCsrfPreventionEnabled()) {
      logger.info("CSRF Protection Enabled");
      configureCSRF(http);
    } else {
      http.csrf((csrf) -> csrf.disable());
    }
    http.authorizeRequests(
            (authorize) -> {
              authorize
                  .requestMatchers(getAuthWhitelist(introspector))
                  .permitAll()
                  .requestMatchers(
                      AntPathRequestMatcher.antMatcher(ALL_REST_VERSION_API),
                      AntPathRequestMatcher.antMatcher(ERROR_URL))
                  .authenticated()
                  .requestMatchers(AntPathRequestMatcher.antMatcher(ROOT_URL))
                  .authenticated();
            })
        .exceptionHandling(
            (handling) -> {
              handling.authenticationEntryPoint(this::authenticationEntry);
            });
  }

  protected void authenticationEntry(
      final HttpServletRequest req, final HttpServletResponse res, final AuthenticationException ex)
      throws IOException {
    final String requestedUrl = getRequestedUrl(req);

    if (contains(requestedUrl.toLowerCase(), REST_V1_API)) {
      req.getSession().invalidate();
      sendJSONErrorMessage(res, ex.getMessage());
    } else {
      logger.debug("Try to access protected resource {}. Save it for later redirect", requestedUrl);
      req.getSession().setAttribute(REQUESTED_URL, requestedUrl);
      res.sendRedirect(req.getContextPath() + LOGIN_RESOURCE);
    }
  }
}
