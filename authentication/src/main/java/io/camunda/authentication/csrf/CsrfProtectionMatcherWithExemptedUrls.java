/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.csrf;

import io.camunda.zeebe.util.VersionUtil;
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

public class CsrfProtectionMatcherWithExemptedUrls implements RequestMatcher {

  private static final Logger LOG =
      LoggerFactory.getLogger(CsrfProtectionMatcherWithExemptedUrls.class);

  // CSRF-safe methods
  private static final Pattern ALLOWED_METHODS = Pattern.compile("^(GET|HEAD|TRACE|OPTIONS)$");

  // Camunda Client
  private static final String ALLOWED_AGENT = "camunda-client-java/" + VersionUtil.getVersion();

  private final Pattern allowedPathsPattern;

  public CsrfProtectionMatcherWithExemptedUrls(final Set<String>... allowedPathsSets) {
    final Set<String> allowedPaths = new HashSet<>();
    for (final Set<String> allowedPathSet : allowedPathsSets) {
      allowedPaths.addAll(allowedPathSet);
    }
    allowedPathsPattern = allowedPathsToPattern(allowedPaths);
  }

  @Override
  public boolean matches(final HttpServletRequest request) {
    // These methods are CSRF-safe
    if (ALLOWED_METHODS.matcher(request.getMethod()).matches()) {
      return false;
    }

    // Camunda Client is allowed to bypass CSRF
    if (ALLOWED_AGENT.equals(request.getHeader(HttpHeaders.USER_AGENT))) {
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

    // If is authenticated from as API user using Bearer Token
    final String authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
    final boolean isAuthorizationHeaderPresent =
        authorizationHeader != null
            && (authorizationHeader.startsWith("Bearer ")
                || authorizationHeader.startsWith("Basic "));

    return !isAuthorizationHeaderPresent;
  }

  private Pattern allowedPathsToPattern(final Set<String> paths) {
    final String patternAsString =
        paths.stream()
            .map(path -> path.replace("**", ".*")) // Replace wildcard with regex equivalent
            .collect(Collectors.joining("|", "^(", ")$")); // Combine paths into regex
    LOG.info("About to compile the following pattern: " + patternAsString);
    return Pattern.compile(patternAsString);
  }
}
