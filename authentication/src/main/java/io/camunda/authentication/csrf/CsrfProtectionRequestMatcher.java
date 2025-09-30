/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.csrf;

import io.camunda.authentication.config.WebSecurityConfig;
import jakarta.servlet.http.HttpServletRequest;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.web.util.matcher.RequestMatcher;

public class CsrfProtectionRequestMatcher implements RequestMatcher {

  private static final Logger LOG = LoggerFactory.getLogger(CsrfProtectionRequestMatcher.class);

  // CSRF-safe methods
  private static final Pattern ALLOWED_METHODS = Pattern.compile("^(GET|HEAD|TRACE|OPTIONS)$");

  private final Pattern allowedPathsPattern;

  public CsrfProtectionRequestMatcher() {
    final Set<String> allowedPaths = new HashSet<>();
    allowedPaths.addAll(WebSecurityConfig.UNPROTECTED_PATHS);
    allowedPaths.addAll(WebSecurityConfig.UNPROTECTED_API_PATHS);
    allowedPaths.add(WebSecurityConfig.LOGIN_URL);
    allowedPaths.add(WebSecurityConfig.LOGOUT_URL);
    allowedPathsPattern = allowedPathsToPattern(allowedPaths);
    LOG.debug("CSRF protection configuration - allowed paths pattern: {}", allowedPathsPattern);
  }

  @Override
  public boolean matches(final HttpServletRequest request) {
    // These methods are CSRF-safe
    if (ALLOWED_METHODS.matcher(request.getMethod()).matches()) {
      return false;
    }

    if (allowedPathsPattern.matcher(request.getServletPath()).matches()) {
      return false;
    }

    // If request coming from Swagger UI
    final String referer = request.getHeader(HttpHeaders.REFERER);
    final String baseRequestUrl;
    try {
      final URL requestUrl = URI.create(request.getRequestURL().toString()).toURL();
      baseRequestUrl =
          requestUrl.getProtocol()
              + "://"
              + requestUrl.getHost()
              + (requestUrl.getPort() > 0 ? ":" + requestUrl.getPort() : "");
    } catch (final MalformedURLException e) {
      throw new RuntimeException(e);
    }

    if (referer != null && referer.matches(baseRequestUrl + "/swagger-ui.*")) {
      return false;
    }

    return apiCallComingFromBrowser(request);
  }

  private boolean apiCallComingFromBrowser(final HttpServletRequest request) {
    return request.getSession(false) != null;
  }

  private Pattern allowedPathsToPattern(final Set<String> paths) {
    final String patternAsString =
        paths.stream()
            .map(path -> path.replace("**", ".*")) // Replace wildcard with regex equivalent
            .collect(Collectors.joining("|", "^(", ")$")); // Combine paths into regex
    return Pattern.compile(patternAsString);
  }
}
