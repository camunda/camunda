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

  // The login endpoint requires a valid CSRF token unconditionally, even on a browser that holds
  // no session yet: without this, a cross-site POST /login is indistinguishable from a legitimate
  // one on a browser that already has an authenticated session, and silently replaces the victim's
  // session with an attacker-controlled one (camunda/security-testing-findings#281). The generic
  // "protect once a session exists" rule below is not enough here, because the attack's whole
  // premise is that the victim's browser already has a session by the time the forged request
  // lands. Checked ahead of allowedPathsPattern/isSwaggerReferer so neither can exempt it.
  private final Pattern enforcedPathsPattern;

  public CsrfProtectionRequestMatcher() {
    allowedPathsPattern = getAllowedPathsPattern();
    enforcedPathsPattern = getEnforcedPathsPattern();
  }

  @Override
  public boolean matches(final HttpServletRequest request) {
    if (matchesPattern(ALLOWED_METHODS, request.getMethod())) {
      return false;
    }

    if (matchesPattern(enforcedPathsPattern, request.getServletPath())) {
      return true;
    }

    // short-circuit check for the following conditions
    if (matchesPattern(allowedPathsPattern, request.getServletPath())
        || isSwaggerReferer(request)) {
      return false;
    }

    // check if API call is coming from the browser
    return request.getSession(false) != null;
  }

  private boolean matchesPattern(final Pattern pattern, final String str) {
    return pattern.matcher(str).matches();
  }

  private boolean isSwaggerReferer(final HttpServletRequest request) {
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
    return referer != null && referer.matches(Pattern.quote(baseRequestUrl) + ".*/swagger-ui.*");
  }

  private Pattern getAllowedPathsPattern() {
    final Set<String> paths = new HashSet<>();
    paths.addAll(WebSecurityConfig.UNPROTECTED_PATHS);
    paths.addAll(WebSecurityConfig.UNPROTECTED_API_PATHS);
    paths.add(WebSecurityConfig.LOGOUT_URL);
    final Pattern compiledPattern = compilePathsPattern(paths);
    LOG.debug("CSRF protection configuration - allowed paths pattern: {}", compiledPattern);
    return compiledPattern;
  }

  private Pattern getEnforcedPathsPattern() {
    final Pattern compiledPattern = compilePathsPattern(Set.of(WebSecurityConfig.LOGIN_URL));
    LOG.debug("CSRF protection configuration - enforced paths pattern: {}", compiledPattern);
    return compiledPattern;
  }

  private Pattern compilePathsPattern(final Set<String> paths) {
    final String patternAsString =
        paths.stream()
            .map(path -> path.replace("**", ".*")) // Replace wildcard with regex equivalent
            .collect(Collectors.joining("|", "^(", ")$")); // Combine paths into regex
    return Pattern.compile(patternAsString);
  }
}
