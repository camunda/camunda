/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest.cloud;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.security.AuthCookieService;
import org.camunda.optimize.service.util.configuration.condition.CCSaaSCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;

@Component
@Slf4j
@Conditional(CCSaaSCondition.class)
@RequiredArgsConstructor
public class AccountsUserAccessTokenProvider {

  private Optional<String> retrieveServiceTokenFromFramework() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null) {
      if (authentication instanceof JwtAuthenticationToken) {
        return Optional.ofNullable(((JwtAuthenticationToken) authentication).getToken().getTokenValue());
      } else {
        log.info("Could not retrieve Jwt Token. Provided token has type " + authentication.getClass().getTypeName());
        return Optional.empty();
      }
    }
    return Optional.empty();
  }

  public Optional<String> getCurrentUsersAccessToken() {
    Optional<String> accessToken = Optional.ofNullable(RequestContextHolder.getRequestAttributes())
      .filter(ServletRequestAttributes.class::isInstance)
      .map(ServletRequestAttributes.class::cast)
      .map(ServletRequestAttributes::getRequest)
      .flatMap(AuthCookieService::getServiceAccessToken);
    // In case we don't have a cookie to extract the service token from, we try to retrieve it directly from the
    // framework
    if (accessToken.isEmpty()) {
      accessToken = retrieveServiceTokenFromFramework();
    }
    return accessToken;
  }
}
