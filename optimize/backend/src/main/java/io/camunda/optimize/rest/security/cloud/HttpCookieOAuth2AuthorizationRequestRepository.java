/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.cloud;

import io.camunda.optimize.service.util.configuration.ConfigurationService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Optional;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

/**
 * Stores pending authorization requests in a cookie instead of a local session.
 *
 * <p>Originates from https://stackoverflow.com/q/49095383.
 */
public class HttpCookieOAuth2AuthorizationRequestRepository
    implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

  private static final String REQUEST_COOKIE_NAME = "oauth2_auth_request";
  private static final int REQUEST_COOKIE_MAX_AGE = 180;

  private final ConfigurationService configurationService;
  private final AuthorizationRequestCookieValueMapper authorizationRequestCookieValueMapper;

  public HttpCookieOAuth2AuthorizationRequestRepository(
      final ConfigurationService configurationService,
      final AuthorizationRequestCookieValueMapper authorizationRequestCookieValueMapper) {
    this.configurationService = configurationService;
    this.authorizationRequestCookieValueMapper = authorizationRequestCookieValueMapper;
  }

  @Override
  public OAuth2AuthorizationRequest loadAuthorizationRequest(final HttpServletRequest request) {
    return getAuthorizationRequestCookie(request)
        .map(Cookie::getValue)
        .map(authorizationRequestCookieValueMapper::deserialize)
        .orElse(null);
  }

  @Override
  public void saveAuthorizationRequest(
      final OAuth2AuthorizationRequest authorizationRequest,
      final HttpServletRequest request,
      final HttpServletResponse response) {
    if (authorizationRequest == null) {
      deleteCookie(request, response);
      return;
    }

    addCookie(
        response,
        authorizationRequestCookieValueMapper.serialize(authorizationRequest),
        configurationService
            .getAuthConfiguration()
            .getCookieConfiguration()
            .resolveSecureFlagValue(request.getScheme()));
  }

  @Override
  public OAuth2AuthorizationRequest removeAuthorizationRequest(
      final HttpServletRequest request, final HttpServletResponse response) {
    final OAuth2AuthorizationRequest authorizationRequest = loadAuthorizationRequest(request);
    deleteCookie(request, response);
    return authorizationRequest;
  }

  private static void addCookie(
      final HttpServletResponse response, final String value, final boolean secure) {
    final Cookie cookie = new Cookie(REQUEST_COOKIE_NAME, value);
    cookie.setPath("/");
    cookie.setHttpOnly(true);
    cookie.setMaxAge(REQUEST_COOKIE_MAX_AGE);
    cookie.setSecure(secure);
    response.addCookie(cookie);
  }

  private static void deleteCookie(
      final HttpServletRequest request, final HttpServletResponse response) {
    final Cookie[] cookies = request.getCookies();
    if (cookies != null && cookies.length > 0) {
      for (final Cookie cookie : cookies) {
        if (cookie.getName().equals(REQUEST_COOKIE_NAME)) {
          cookie.setValue("");
          cookie.setPath("/");
          cookie.setMaxAge(0);
          response.addCookie(cookie);
        }
      }
    }
  }

  private static Optional<Cookie> getAuthorizationRequestCookie(final HttpServletRequest request) {
    final Cookie[] cookies = request.getCookies();
    if (cookies != null && cookies.length > 0) {
      for (final Cookie cookie : cookies) {
        if (cookie.getName().equals(REQUEST_COOKIE_NAME)) {
          return Optional.of(cookie);
        }
      }
    }

    return Optional.empty();
  }
}
