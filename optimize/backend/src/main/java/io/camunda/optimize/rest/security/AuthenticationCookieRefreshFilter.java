/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security;

import io.camunda.optimize.service.security.AuthCookieService;
import io.camunda.optimize.service.security.SessionService;
import io.camunda.optimize.service.security.util.LocalDateUtil;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.CamundaPlatformCondition;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.core.HttpHeaders;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.GenericFilterBean;

@AllArgsConstructor
@Component
@Conditional(CamundaPlatformCondition.class)
@Order
public class AuthenticationCookieRefreshFilter extends GenericFilterBean {

  private final SessionService sessionService;
  private final ConfigurationService configurationService;
  private final AuthCookieService authCookieService;

  @Override
  public void doFilter(
      final ServletRequest request, final ServletResponse response, final FilterChain chain)
      throws IOException, ServletException {

    final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication instanceof PreAuthenticatedAuthenticationToken) {
      Optional.of((String) authentication.getCredentials())
          .filter(
              token ->
                  sessionService
                      .getExpiresAtLocalDateTime(token)
                      .map(
                          expiresAt -> {
                            final LocalDateTime now = LocalDateUtil.getCurrentLocalDateTime();
                            return expiresAt.isAfter(now)
                                // token reached last third of lifeTime => refresh
                                && Duration.between(now, expiresAt).toMinutes()
                                    <= (configurationService
                                            .getAuthConfiguration()
                                            .getTokenLifeTimeMinutes()
                                        / 3);
                          })
                      .orElse(false))
          .flatMap(sessionService::refreshAuthToken)
          .ifPresent(
              newToken ->
                  ((HttpServletResponse) response)
                      .setHeader(
                          HttpHeaders.SET_COOKIE,
                          authCookieService.createNewOptimizeAuthCookie(
                              newToken, request.getScheme())));
    }

    chain.doFilter(request, response);
  }
}
