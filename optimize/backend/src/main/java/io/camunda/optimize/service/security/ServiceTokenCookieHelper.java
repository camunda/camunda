/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.security;

import static io.camunda.optimize.rest.constants.RestConstants.OPTIMIZE_REFRESH_TOKEN;
import static io.camunda.optimize.rest.constants.RestConstants.OPTIMIZE_SERVICE_TOKEN;
import static io.camunda.optimize.rest.constants.RestConstants.SAME_SITE_COOKIE_FLAG;
import static io.camunda.optimize.rest.constants.RestConstants.SAME_SITE_COOKIE_STRICT_VALUE;

import io.camunda.auth.domain.model.CookieDescriptor;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.security.AuthConfiguration;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.stereotype.Component;

@Component
public class ServiceTokenCookieHelper {

  private static final Logger LOG = LoggerFactory.getLogger(ServiceTokenCookieHelper.class);
  private static final int MAX_AUTH_COOKIE_CHUNKS = 5;

  private final ConfigurationService configurationService;
  private final CookieDescriptor cookieDescriptor;

  public ServiceTokenCookieHelper(
      final ConfigurationService configurationService, final CookieDescriptor cookieDescriptor) {
    this.configurationService = configurationService;
    this.cookieDescriptor = cookieDescriptor;
  }

  public List<Cookie> createServiceTokenCookies(
      final OAuth2AccessToken accessToken, final Instant expiresAt, final String requestScheme) {
    LOG.trace("Creating Optimize service token cookie(s).");
    final String tokenValue = accessToken.getTokenValue();
    final int maxCookieLength = getAuthConfiguration().getCookieConfiguration().getMaxSize();
    final int numberOfCookies = (int) Math.ceil((double) tokenValue.length() / maxCookieLength);
    final List<Cookie> cookies = new ArrayList<>();
    for (int i = 0; i < numberOfCookies; i++) {
      cookies.add(
          createCookie(
              getServiceCookieNameWithSuffix(i),
              extractChunk(i, numberOfCookies, maxCookieLength, tokenValue),
              expiresAt,
              requestScheme,
              false));
    }
    return cookies;
  }

  public static Optional<String> getServiceAccessToken(final HttpServletRequest servletRequest) {
    boolean serviceTokenExtracted = false;
    int serviceTokenSuffixToExtract = 0;
    final StringBuilder serviceAccessToken = new StringBuilder();
    while (!serviceTokenExtracted) {
      final String serviceCookieName = getServiceCookieNameWithSuffix(serviceTokenSuffixToExtract);
      String authorizationValue = null;
      if (servletRequest.getCookies() != null) {
        for (final Cookie cookie : servletRequest.getCookies()) {
          if (serviceCookieName.equals(cookie.getName())) {
            authorizationValue = cookie.getValue();
          }
        }
      }
      if (authorizationValue != null) {
        serviceAccessToken.append(authorizationValue);
        serviceTokenSuffixToExtract += 1;
      } else {
        serviceTokenExtracted = true;
      }
    }
    if (serviceAccessToken.length() != 0) {
      return Optional.of(serviceAccessToken.toString().trim());
    }
    return Optional.empty();
  }

  public Cookie createRefreshCookie(
      final String refreshToken, final Instant expiresAt, final String requestScheme) {
    return createCookie(OPTIMIZE_REFRESH_TOKEN, refreshToken, expiresAt, requestScheme, false);
  }

  public List<Cookie> createDeleteAuthCookies(final boolean secure) {
    final List<Cookie> cookies = new ArrayList<>();
    final String cookiePath = getCookiePath();
    for (int i = 0; i < MAX_AUTH_COOKIE_CHUNKS; i++) {
      final Cookie cookie = new Cookie(cookieDescriptor.namePrefix() + "_" + i, "");
      cookie.setPath(cookiePath);
      cookie.setMaxAge(0);
      cookie.setSecure(secure);
      cookie.setHttpOnly(true);
      cookies.add(cookie);
    }
    return cookies;
  }

  public Cookie createDeleteRefreshCookie(final boolean secure) {
    final Cookie cookie = new Cookie(OPTIMIZE_REFRESH_TOKEN, "");
    cookie.setPath(getCookiePath());
    cookie.setMaxAge(0);
    cookie.setSecure(secure);
    cookie.setHttpOnly(true);
    return cookie;
  }

  private Cookie createCookie(
      final String cookieName,
      final String cookieValue,
      final Instant expiresAt,
      final String requestScheme,
      final boolean isDelete) {
    final Cookie cookie = new Cookie(cookieName, cookieValue);
    cookie.setPath(getCookiePath());
    cookie.setHttpOnly(true);
    cookie.setSecure(isSecureScheme(requestScheme));
    if (getAuthConfiguration().getCookieConfiguration().isSameSiteFlagEnabled()) {
      cookie.setAttribute(SAME_SITE_COOKIE_FLAG, SAME_SITE_COOKIE_STRICT_VALUE);
    }
    if (expiresAt == null) {
      cookie.setMaxAge(-1);
    } else {
      cookie.setMaxAge(isDelete ? 0 : (int) Duration.between(Instant.now(), expiresAt).toSeconds());
    }
    return cookie;
  }

  private String getCookiePath() {
    return "/"
        + configurationService.getAuthConfiguration().getCloudAuthConfiguration().getClusterId();
  }

  private boolean isSecureScheme(final String requestScheme) {
    return configurationService
        .getAuthConfiguration()
        .getCookieConfiguration()
        .resolveSecureFlagValue(requestScheme);
  }

  private AuthConfiguration getAuthConfiguration() {
    return configurationService.getAuthConfiguration();
  }

  private static String getServiceCookieNameWithSuffix(final int suffix) {
    return OPTIMIZE_SERVICE_TOKEN + "_" + suffix;
  }

  private static String extractChunk(
      final int i, final int numberOfCookies, final int maxCookieLength, final String value) {
    return i == (numberOfCookies - 1)
        ? value.substring((i * maxCookieLength))
        : value.substring((i * maxCookieLength), ((i * maxCookieLength) + maxCookieLength));
  }
}
