/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.security;

import static io.camunda.operate.webapp.security.OperateURIs.LOGIN_RESOURCE;
import static io.camunda.operate.webapp.security.OperateURIs.LOGOUT_RESOURCE;

import io.camunda.operate.exceptions.OperateRuntimeException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.regex.Pattern;
import org.springframework.security.web.util.matcher.RequestMatcher;

public class CsrfRequireMatcher implements RequestMatcher {
  private static final Pattern ALLOWED_METHODS = Pattern.compile("^(GET|HEAD|TRACE|OPTIONS)$");

  private static final Pattern ALLOWED_PATHS = Pattern.compile(LOGOUT_RESOURCE);

  @Override
  public boolean matches(final HttpServletRequest request) {
    // If request is from Allowed Methods or Logout
    if (ALLOWED_METHODS.matcher(request.getMethod()).matches()) {
      return false;
    }
    // The login endpoint requires a valid CSRF token unconditionally: without it, a cross-site
    // POST to it silently replaces the session of a browser that is already logged in with one of
    // the attacker's choosing (camunda/security-testing-findings#281). Checked ahead of the
    // Swagger and Bearer exemptions below so neither can exempt it.
    if (LOGIN_RESOURCE.equals(request.getServletPath())) {
      return true;
    }
    if (ALLOWED_PATHS.matcher(request.getServletPath()).matches()) {
      return false;
    }

    final String referer = request.getHeader("Referer");

    // If request is from Swagger UI
    final String baseRequestUrl;
    try {
      final URL requestUrl = URI.create(request.getRequestURL().toString()).toURL();
      baseRequestUrl =
          requestUrl.getProtocol()
              + "://"
              + requestUrl.getHost()
              + (requestUrl.getPort() > 0 ? ":" + requestUrl.getPort() : "");
    } catch (final MalformedURLException e) {
      throw new OperateRuntimeException(e);
    }

    if (referer != null && referer.matches(baseRequestUrl + "/swagger-ui.*")) {
      return false;
    }

    // If is authenticated from as API user using Bearer Token
    final String authorizationHeader = request.getHeader("Authorization");
    final boolean isAuthorizationHeaderPresent =
        authorizationHeader != null && authorizationHeader.startsWith("Bearer ");

    return !isAuthorizationHeaderPresent;
  }
}
