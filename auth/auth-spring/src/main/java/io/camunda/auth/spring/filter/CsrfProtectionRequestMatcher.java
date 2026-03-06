/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.spring.filter;

import jakarta.servlet.http.HttpServletRequest;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * A {@link RequestMatcher} that determines which requests need CSRF protection. Exempts safe HTTP
 * methods (GET, HEAD, TRACE, OPTIONS), configured allowed paths, and Swagger UI requests.
 */
public class CsrfProtectionRequestMatcher implements RequestMatcher {

  private static final Logger LOG = LoggerFactory.getLogger(CsrfProtectionRequestMatcher.class);
  private static final Pattern ALLOWED_METHODS = Pattern.compile("^(GET|HEAD|TRACE|OPTIONS)$");

  private final Pattern allowedPathsPattern;

  public CsrfProtectionRequestMatcher(
      final Set<String> unprotectedPaths,
      final Set<String> unprotectedApiPaths,
      final String loginUrl,
      final String logoutUrl) {
    final Set<String> allowedPaths =
        Stream.of(
                unprotectedPaths.stream(),
                unprotectedApiPaths.stream(),
                Stream.of(loginUrl, logoutUrl))
            .flatMap(s -> s)
            .collect(Collectors.toSet());
    allowedPathsPattern = allowedPathsToPattern(allowedPaths);
    LOG.debug("CSRF protection configuration - allowed paths pattern: {}", allowedPathsPattern);
  }

  @Override
  public boolean matches(final HttpServletRequest request) {
    if (ALLOWED_METHODS.matcher(request.getMethod()).matches()) {
      return false;
    }

    if (allowedPathsPattern.matcher(request.getServletPath()).matches()) {
      return false;
    }

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
            .map(path -> path.replace("**", ".*"))
            .collect(Collectors.joining("|", "^(", ")$"));
    return Pattern.compile(patternAsString);
  }
}
