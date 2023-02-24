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
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.NewCookie;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static org.camunda.optimize.rest.constants.RestConstants.AUTH_COOKIE_TOKEN_VALUE_PREFIX;
import static org.camunda.optimize.rest.constants.RestConstants.OPTIMIZE_AUTHORIZATION;
import static org.camunda.optimize.rest.constants.RestConstants.OPTIMIZE_REFRESH_TOKEN;
import static org.camunda.optimize.rest.constants.RestConstants.OPTIMIZE_SERVICE_TOKEN;
import static org.camunda.optimize.rest.constants.RestConstants.SAME_SITE_COOKIE_FLAG;
import static org.camunda.optimize.rest.constants.RestConstants.SAME_SITE_COOKIE_STRICT_VALUE;

@RequiredArgsConstructor
@Component
@Slf4j
public class AuthCookieService {

  private final ConfigurationService configurationService;

  public NewCookie createDeleteOptimizeAuthCookie(final String requestScheme) {
    return createDeleteOptimizeAuthCookie(isSecureScheme(requestScheme));
  }

  public NewCookie createDeleteOptimizeAuthCookie(final boolean secure) {
    log.trace("Deleting Optimize authentication cookie.");
    return new NewCookie(
      OPTIMIZE_AUTHORIZATION, "", getCookiePath(), null, "delete cookie", 0, secure, true
    );
  }

  public NewCookie createDeleteOptimizeRefreshCookie(final boolean secure) {
    log.trace("Deleting Optimize refresh cookie.");
    return new NewCookie(
      OPTIMIZE_REFRESH_TOKEN, "", getCookiePath(), null, "delete cookie", 0, secure, true
    );
  }

  public String createNewOptimizeAuthCookie(final String optimizeAuthCookieToken, final String requestScheme) {
    return createNewOptimizeAuthCookie(
      optimizeAuthCookieToken,
      getOptimizeAuthCookieTokenExpiryDate(optimizeAuthCookieToken).orElse(null),
      requestScheme
    );
  }

  public String createNewOptimizeAuthCookie(final String optimizeAuthCookieToken,
                                            final Instant expiresAt,
                                            final String requestScheme) {
    log.trace("Creating Optimize authentication cookie.");

    return createCookie(
      OPTIMIZE_AUTHORIZATION,
      AuthCookieService.createOptimizeAuthCookieValue(optimizeAuthCookieToken),
      requestScheme,
      convertInstantToDate(expiresAt)
    );
  }

  public String createNewOptimizeRefreshCookie(final String cookieValue,
                                               final Instant expiresAt,
                                               final String requestScheme) {
    log.trace("Creating Optimize authentication cookie.");

    return createCookie(
      OPTIMIZE_REFRESH_TOKEN,
      AuthCookieService.createOptimizeAuthCookieValue(cookieValue),
      requestScheme,
      convertInstantToDate(expiresAt)
    );
  }

  public String createOptimizeServiceTokenCookie(final OAuth2AccessToken accessToken,
                                                 final Instant expiresAt,
                                                 final String requestScheme) {
    log.trace("Creating Optimize service token cookie.");
    return createCookie(
      OPTIMIZE_SERVICE_TOKEN,
      accessToken.getTokenValue(),
      requestScheme,
      convertInstantToDate(expiresAt)
    );
  }

  public Optional<Instant> getOptimizeAuthCookieTokenExpiryDate(final String optimizeAuthCookieToken) {
    return getTokenIssuedAt(optimizeAuthCookieToken)
      .map(Date::toInstant)
      .map(issuedAt -> issuedAt.plus(getAuthConfiguration().getTokenLifeTimeMinutes(), ChronoUnit.MINUTES));
  }

  public static Optional<String> getAuthCookieToken(ContainerRequestContext requestContext) {
    return extractCookieValue(requestContext)
      .flatMap(AuthCookieService::extractTokenFromAuthorizationValue);
  }

  public static Optional<String> getAuthCookieToken(HttpServletRequest servletRequest) {
    return extractCookieValue(servletRequest, OPTIMIZE_AUTHORIZATION)
      .flatMap(AuthCookieService::extractTokenFromAuthorizationValue);
  }

  public static Optional<String> getServiceAccessToken(HttpServletRequest servletRequest) {
    return extractCookieValue(servletRequest, OPTIMIZE_SERVICE_TOKEN);
  }

  public static Optional<String> getTokenSubject(String token) {
    return getTokenAttribute(token, DecodedJWT::getSubject);
  }

  public static String createOptimizeAuthCookieValue(final String tokenValue) {
    return AUTH_COOKIE_TOKEN_VALUE_PREFIX + tokenValue;
  }

  private Date convertInstantToDate(final Instant expiresAt) {
    return Optional.ofNullable(expiresAt).map(Date::from).orElse(null);
  }

  private String createCookie(final String cookieName, final String cookieValue, final String requestScheme,
                              final Date expiryDate) {
    NewCookie newCookie = new NewCookie(
      cookieName, cookieValue, getCookiePath(), null, 1, null, -1, expiryDate, isSecureScheme(requestScheme), true
    );

    String newCookieAsString = newCookie.toString();
    if (getAuthConfiguration().getCookieConfiguration().isSameSiteFlagEnabled()) {
      newCookieAsString = addSameSiteCookieFlag(newCookieAsString);
    }
    return newCookieAsString;
  }

  private String getCookiePath() {
    return "/" + configurationService.getAuthConfiguration().getCloudAuthConfiguration().getClusterId();
  }

  private boolean isSecureScheme(final String requestScheme) {
    return configurationService.getAuthConfiguration().getCookieConfiguration().resolveSecureFlagValue(requestScheme);
  }

  private AuthConfiguration getAuthConfiguration() {
    return configurationService.getAuthConfiguration();
  }

  private String addSameSiteCookieFlag(String newCookieAsString) {
    return newCookieAsString + String.format(";%s=%s", SAME_SITE_COOKIE_FLAG, SAME_SITE_COOKIE_STRICT_VALUE);
  }

  private static Optional<Date> getTokenIssuedAt(String token) {
    return getTokenAttribute(token, DecodedJWT::getIssuedAt);
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

  private static Optional<String> extractCookieValue(final ContainerRequestContext requestContext) {
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

  private static Optional<String> extractCookieValue(final HttpServletRequest servletRequest, final String cookieName) {
    // load just issued token if set by previous filter
    String authorizationValue = (String) servletRequest.getAttribute(cookieName);
    if (authorizationValue == null && servletRequest.getCookies() != null) {
      for (javax.servlet.http.Cookie cookie : servletRequest.getCookies()) {
        if (cookieName.equals(cookie.getName())) {
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
}
