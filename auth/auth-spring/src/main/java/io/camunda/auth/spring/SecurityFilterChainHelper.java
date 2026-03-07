/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.spring;

import io.camunda.auth.spring.filter.CsrfProtectionRequestMatcher;
import java.util.Set;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter;

/** Shared configuration methods for SecurityFilterChain beans to avoid duplication. */
public final class SecurityFilterChainHelper {

  private SecurityFilterChainHelper() {}

  public static void configureCsrf(
      final HttpSecurity http,
      final String csrfTokenName,
      final Set<String> unprotectedPaths,
      final Set<String> unprotectedApiPaths,
      final String loginUrl,
      final String logoutUrl)
      throws Exception {
    final var csrfMatcher =
        new CsrfProtectionRequestMatcher(
            unprotectedPaths, unprotectedApiPaths, loginUrl, logoutUrl);

    final CookieCsrfTokenRepository csrfTokenRepository =
        CookieCsrfTokenRepository.withHttpOnlyFalse();
    csrfTokenRepository.setCookieName(csrfTokenName);
    csrfTokenRepository.setHeaderName(csrfTokenName);

    final CsrfTokenRequestAttributeHandler requestHandler = new CsrfTokenRequestAttributeHandler();

    http.csrf(
        csrf ->
            csrf.csrfTokenRepository(csrfTokenRepository)
                .csrfTokenRequestHandler(requestHandler)
                .requireCsrfProtectionMatcher(csrfMatcher));
  }

  public static void setupSecureHeaders(final HttpSecurity http) throws Exception {
    http.headers(
        headers ->
            headers
                .httpStrictTransportSecurity(
                    hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(63072000))
                .xssProtection(
                    xss ->
                        xss.headerValue(XXssProtectionHeaderWriter.HeaderValue.ENABLED_MODE_BLOCK))
                .contentTypeOptions(cto -> {})
                .frameOptions(frame -> frame.sameOrigin()));
  }
}
