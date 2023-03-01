/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest.security;

import lombok.AllArgsConstructor;
import org.camunda.optimize.service.security.AuthCookieService;
import org.camunda.optimize.service.security.SessionService;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.condition.CamundaPlatformCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.GenericFilterBean;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.HttpHeaders;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

@AllArgsConstructor
@Component
@Conditional(CamundaPlatformCondition.class)
@Order
public class AuthenticationCookieRefreshFilter extends GenericFilterBean {

  private final SessionService sessionService;
  private final ConfigurationService configurationService;
  private final AuthCookieService authCookieService;

  @Override
  public void doFilter(final ServletRequest request, final ServletResponse response, final FilterChain chain)
    throws IOException, ServletException {

    final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication instanceof PreAuthenticatedAuthenticationToken) {
      Optional.of((String) authentication.getCredentials())
        .filter(token -> sessionService.getExpiresAtLocalDateTime(token)
          .map(expiresAt -> {
            final LocalDateTime now = LocalDateUtil.getCurrentLocalDateTime();
            return expiresAt.isAfter(now)
              // token reached last third of lifeTime => refresh
              &&
              Duration.between(now, expiresAt).toMinutes()
                <= (configurationService.getAuthConfiguration().getTokenLifeTimeMinutes() / 3);
          })
          .orElse(false)
        )
        .flatMap(sessionService::refreshAuthToken)
        .ifPresent(newToken -> ((HttpServletResponse) response).setHeader(
          HttpHeaders.SET_COOKIE,
          authCookieService.createNewOptimizeAuthCookie(newToken, request.getScheme())
        ));
    }

    chain.doFilter(request, response);
  }

}
