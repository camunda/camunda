/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gatekeeper.spring.csrf;

import jakarta.servlet.http.HttpServletRequest;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * {@link RequestMatcher} that decides whether a request requires CSRF protection. Safe HTTP methods
 * (GET, HEAD, TRACE, OPTIONS) are always excluded, as are configured allowed paths and requests
 * originating from the Swagger UI.
 */
public final class CsrfProtectionRequestMatcher implements RequestMatcher {

  private static final Logger LOG = LoggerFactory.getLogger(CsrfProtectionRequestMatcher.class);

  // CSRF-safe methods
  private static final Pattern ALLOWED_METHODS = Pattern.compile("^(GET|HEAD|TRACE|OPTIONS)$");

  private final Pattern allowedPathsPattern;

  public CsrfProtectionRequestMatcher(final Set<String> allowedPaths) {
    this.allowedPathsPattern = allowedPathsToPattern(allowedPaths);
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

    if (referer != null && referer.matches(Pattern.quote(baseRequestUrl) + ".*/swagger-ui.*")) {
      return false;
    }

    // check if API call is coming from the browser
    return request.getSession(false) != null;
  }

  private Pattern allowedPathsToPattern(final Set<String> paths) {
    if (paths == null || paths.isEmpty()) {
      return Pattern.compile("^$");
    }
    final String patternAsString =
        paths.stream()
            .map(path -> path.replace("**", ".*")) // Replace wildcard with regex equivalent
            .collect(Collectors.joining("|", "^(", ")$")); // Combine paths into regex
    return Pattern.compile(patternAsString);
  }
}
