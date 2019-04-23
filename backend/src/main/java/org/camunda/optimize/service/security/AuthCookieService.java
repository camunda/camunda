/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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


@Component
public class AuthCookieService {
  private static final Logger logger = LoggerFactory.getLogger(AuthCookieService.class);

  public static final String AUTH_COOKIE_TOKEN_VALUE_PREFIX = "Bearer ";
  public static String OPTIMIZE_AUTHORIZATION = "X-Optimize-Authorization";
  public static String SAME_SITE_COOKIE_FLAG = "SameSite";
  public static String SAME_SITE_COOKIE_STRICT_VALUE = "Strict";

  private final ConfigurationService configurationService;

  @Autowired
  AuthCookieService(final ConfigurationService configurationService) {
    this.configurationService = configurationService;
  }

  public NewCookie createDeleteOptimizeAuthCookie() {
    logger.trace("Deleting Optimize authentication cookie.");
    return new NewCookie(
      OPTIMIZE_AUTHORIZATION,
      "",
      "/",
      null,
      "delete cookie",
      0,
      configurationService.isHttpDisabled(),
      true
    );
  }

  public String createNewOptimizeAuthCookie(final String securityToken) {
    logger.trace("Creating Optimize authentication cookie.");
    NewCookie newCookie = new NewCookie(
      OPTIMIZE_AUTHORIZATION,
      AuthCookieService.createOptimizeAuthCookieValue(securityToken),
      "/",
      null,
      1,
      null,
      -1,
      getTokenIssuedAt(securityToken)
        .map(Date::toInstant)
        .map(issuedAt -> issuedAt.plus(configurationService.getTokenLifeTimeMinutes(), ChronoUnit.MINUTES))
        .map(Date::from)
        .orElse(null),
      configurationService.isHttpDisabled(),
      true
    );
    
    String newCookieAsString = newCookie.toString();
    if (configurationService.getSameSiteCookieFlagEnabled()) {
      newCookieAsString = addSameSiteCookieFlag(newCookieAsString);
    }
    return newCookieAsString;
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
      logger.debug("Could not decode security token to extract attribute!", e);
    }
    return Optional.empty();
  }


  private static Optional<String> extractAuthorizationCookie(ContainerRequestContext requestContext) {
    // load just issued token if set by previous filter
    String authorizationHeader = (String) requestContext.getProperty(OPTIMIZE_AUTHORIZATION);
    if (authorizationHeader == null) {
      /* Check cookies for optimize authorization header*/
      for (Map.Entry<String, Cookie> c : requestContext.getCookies().entrySet()) {
        if (OPTIMIZE_AUTHORIZATION.equals(c.getKey())) {
          authorizationHeader = c.getValue().getValue();
        }
      }
    }
    return Optional.ofNullable(authorizationHeader);
  }

  private static Optional<String> extractAuthorizationCookie(HttpServletRequest servletRequest) {
    if (servletRequest.getCookies() != null) {
      for (javax.servlet.http.Cookie cookie : servletRequest.getCookies()) {
        if (OPTIMIZE_AUTHORIZATION.equals(cookie.getName())) {
          return Optional.of(cookie.getValue());
        }
      }
    }
    return Optional.empty();
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
