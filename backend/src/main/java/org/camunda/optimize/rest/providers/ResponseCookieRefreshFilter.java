/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest.providers;

import lombok.AllArgsConstructor;
import org.camunda.optimize.service.security.AuthCookieService;
import org.camunda.optimize.service.security.SessionService;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.stereotype.Component;

import javax.annotation.Priority;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.ext.Provider;
import java.time.Duration;
import java.time.LocalDateTime;

@AllArgsConstructor
@Secured
@Provider
@Priority(Priorities.AUTHENTICATION)
@Component
public class ResponseCookieRefreshFilter implements ContainerResponseFilter {

  private final SessionService sessionService;
  private final ConfigurationService configurationService;
  private final AuthCookieService authCookieService;

  @Override
  public void filter(final ContainerRequestContext requestContext, final ContainerResponseContext responseContext) {
    AuthCookieService.getToken(requestContext)
      .filter(token -> sessionService.getExpiresAtLocalDateTime(token)
        .map(expiresAt -> {
          final LocalDateTime now = LocalDateUtil.getCurrentLocalDateTime();
          return expiresAt.isAfter(now)
            // token reached last third of lifeTime => refresh
            && Duration.between(now, expiresAt).toMinutes() <= (configurationService.getTokenLifeTimeMinutes() / 3);
        })
        .orElse(false)
      )
      .flatMap(sessionService::refreshAuthToken)
      .ifPresent(newToken -> responseContext.getHeaders().add(
        HttpHeaders.SET_COOKIE,
        authCookieService.createNewOptimizeAuthCookie(newToken)
      ));
  }
}
