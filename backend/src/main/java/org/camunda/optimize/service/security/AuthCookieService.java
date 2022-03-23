/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.security.AuthConfiguration;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.NewCookie;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static org.camunda.optimize.rest.constants.RestConstants.AUTH_COOKIE_TOKEN_VALUE_PREFIX;
import static org.camunda.optimize.rest.constants.RestConstants.OPTIMIZE_AUTHORIZATION;
import static org.camunda.optimize.rest.constants.RestConstants.SAME_SITE_COOKIE_FLAG;
import static org.camunda.optimize.rest.constants.RestConstants.SAME_SITE_COOKIE_STRICT_VALUE;

@RequiredArgsConstructor
@Component
@Slf4j
public class AuthCookieService {

  private final ConfigurationService configurationService;

  public NewCookie createDeleteOptimizeAuthCookie(String requestScheme) {
    log.trace("Deleting Optimize authentication cookie.");
    return new NewCookie(
      OPTIMIZE_AUTHORIZATION,
      "",
      "/",
      null,
      "delete cookie",
      0,
      configurationService.getAuthConfiguration().getCookieConfiguration().resolveSecureFlagValue(requestScheme),
      true
    );
  }

  public String createNewOptimizeAuthCookie(final String securityToken, final String requestScheme) {
    log.trace("Creating Optimize authentication cookie.");
    NewCookie newCookie = new NewCookie(
      OPTIMIZE_AUTHORIZATION,
      AuthCookieService.createOptimizeAuthCookieValue(securityToken),
      "/" + configurationService.getAuthConfiguration().getCloudAuthConfiguration().getClusterId(),
      null,
      1,
      null,
      -1,
      getTokenIssuedAt(securityToken)
        .map(Date::toInstant)
        .map(issuedAt -> issuedAt.plus(getAuthConfiguration().getTokenLifeTimeMinutes(), ChronoUnit.MINUTES))
        .map(Date::from)
        .orElse(null),
      configurationService.getAuthConfiguration().getCookieConfiguration().resolveSecureFlagValue(requestScheme),
      true
    );

    String newCookieAsString = newCookie.toString();
    if (getAuthConfiguration().getCookieConfiguration().isSameSiteFlagEnabled()) {
      newCookieAsString = addSameSiteCookieFlag(newCookieAsString);
    }
    return newCookieAsString;
  }

  private AuthConfiguration getAuthConfiguration() {
    return configurationService.getAuthConfiguration();
  }

  private String addSameSiteCookieFlag(String newCookieAsString) {
    return newCookieAsString + String.format(";%s=%s", SAME_SITE_COOKIE_FLAG, SAME_SITE_COOKIE_STRICT_VALUE);
  }

  public static Optional<String> getToken(ContainerRequestContext requestContext) {
    return extractAuthorizationCookie(requestContext)
      .flatMap(AuthCookieService::extractTokenFromAuthorizationValue);
  }

  public static Optional<String> getToken(HttpServletRequest servletRequest) {
    return extractAuthorizationCookie(servletRequest)
      .flatMap(AuthCookieService::extractTokenFromAuthorizationValue);
  }

  private static Optional<Date> getTokenIssuedAt(String token) {
    return getTokenAttribute(token, DecodedJWT::getIssuedAt);
  }

  public static Optional<String> getTokenSubject(String token) {
    return getTokenAttribute(token, DecodedJWT::getSubject);
  }

  private static <T> Optional<T> getTokenAttribute(final String token,
                                                   final Function<DecodedJWT, T> getTokenAttributeFunction) {
    try {
      final DecodedJWT decoded = JWT.decode(token);
      return Optional.of(getTokenAttributeFunction.apply(decoded));
    } catch (Exception e) {
      log.debug("Could not decode security token to extract attribute!", e);
    }
    return Optional.empty();
  }


  private static Optional<String> extractAuthorizationCookie(ContainerRequestContext requestContext) {
    // load just issued token if set by previous filter
    String authorizationValue = (String) requestContext.getProperty(OPTIMIZE_AUTHORIZATION);
    if (authorizationValue == null) {
      for (Map.Entry<String, Cookie> c : requestContext.getCookies().entrySet()) {
        if (OPTIMIZE_AUTHORIZATION.equals(c.getKey())) {
          authorizationValue = c.getValue().getValue();
        }
      }
    }
    return Optional.ofNullable(authorizationValue);
  }

  private static Optional<String> extractAuthorizationCookie(HttpServletRequest servletRequest) {
    // load just issued token if set by previous filter
    String authorizationValue = (String) servletRequest.getAttribute(OPTIMIZE_AUTHORIZATION);
    if (authorizationValue == null && servletRequest.getCookies() != null) {
      for (javax.servlet.http.Cookie cookie : servletRequest.getCookies()) {
        if (OPTIMIZE_AUTHORIZATION.equals(cookie.getName())) {
          return Optional.of(cookie.getValue());
        }
      }
    }
    return Optional.ofNullable(authorizationValue);
  }

  private static Optional<String> extractTokenFromAuthorizationValue(final String authCookieValue) {
    return Optional.ofNullable(authCookieValue)
      .filter(value -> value.length() > AUTH_COOKIE_TOKEN_VALUE_PREFIX.length())
      .map(value -> value.substring(AUTH_COOKIE_TOKEN_VALUE_PREFIX.length()).trim())
      .filter(token -> !token.isEmpty());
  }

  public static String createOptimizeAuthCookieValue(final String securityToken) {
    return AUTH_COOKIE_TOKEN_VALUE_PREFIX + securityToken;
  }
}
