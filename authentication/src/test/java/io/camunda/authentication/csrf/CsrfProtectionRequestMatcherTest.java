/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.csrf;

import static com.google.common.net.HttpHeaders.AUTHORIZATION;
import static com.google.common.net.HttpHeaders.REFERER;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.camunda.authentication.config.WebSecurityConfig;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;

class CsrfProtectionRequestMatcherTest {

  private CsrfProtectionRequestMatcher matcher;

  @BeforeEach
  void beforeEach() {
    matcher = new CsrfProtectionRequestMatcher();
  }

  @ParameterizedTest
  @ValueSource(strings = {"GET", "HEAD", "TRACE", "OPTIONS"})
  void safeMethodsDoNotMatchForCsrf(final String method) {
    final var request = prepareMockRequest();
    request.setMethod(method);
    assertFalse(matcher.matches(request));
  }

  @ParameterizedTest
  @ValueSource(strings = {"POST", "PUT", "PATCH", "DELETE"})
  void whenUnprotectedApiIsOffAndApiCalledFromBrowser(final String method) {
    final var request = prepareMockRequest();
    request.getSession(true);
    request.setMethod(method);
    assertTrue(matcher.matches(request));
  }

  @ParameterizedTest
  @ValueSource(strings = {"POST", "PUT", "PATCH", "DELETE"})
  void whenUnprotectedApiIsOffAndApiCalledFromNonBrowser(final String method) {
    final var request = prepareMockRequest();
    request.setMethod(method);
    assertFalse(matcher.matches(request));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/v2/operate/processes",
        "/v2/process-instances/search",
        "/v1/flownode-instances/search",
        "/api/process-instances/123/sequence-flows"
      })
  void whenUnprotectedApiIsOnAndApiCalledFromBrowser(final String protectedUrls) {
    final var unprotectedApiMatcher = new CsrfProtectionRequestMatcher();
    final var request = prepareMockRequest();
    request.getSession(true);
    request.setServletPath(protectedUrls);
    assertTrue(unprotectedApiMatcher.matches(request));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/v2/operate/processes",
        "/v2/process-instances/search",
        "/v1/flownode-instances/search",
        "/api/process-instances/123/sequence-flows"
      })
  void whenUnprotectedApiIsOnAndApiCalledFromNonBrowser(final String protectedUrls) {
    final var request = prepareMockRequest();
    request.setServletPath(protectedUrls);
    assertFalse(matcher.matches(request));
  }

  @Test
  void swaggerReferrerDoesNotMatchForCsrf() {
    final var request = prepareMockRequest();
    request.addHeader(REFERER, "http://localhost/swagger-ui/index.html");
    request.getSession(true);
    assertFalse(matcher.matches(request));
  }

  @Test
  void arbitraryReferrerMatchesForCsrf() {
    final var request = prepareMockRequest();
    request.addHeader(REFERER, "http://localhost/fake-swagger/index.html");
    request.getSession(true);
    assertTrue(matcher.matches(request));
  }

  @Test
  void bearerAuthorizedDoesNotMatchForCsrf() {
    final var request = prepareMockRequest();
    request.addHeader(AUTHORIZATION, "Bearer some-token");
    assertFalse(matcher.matches(request));
  }

  @Test
  void basicAuthorizedDoesNotMatchForCsrf() {
    final var request = prepareMockRequest();
    request.addHeader(AUTHORIZATION, "Basic some-creds");
    assertFalse(matcher.matches(request));
  }

  @ParameterizedTest
  @MethodSource("unprotectedPaths")
  void unprotectedPathsDoNotMatchForCsrf(final String path) {
    final var request = prepareMockRequest();
    request.setServletPath(path.replace("**", "/some/path").replace("*", "/some-path"));
    assertFalse(matcher.matches(request));
  }

  @ParameterizedTest
  @MethodSource("protectedPaths")
  void protectedPathsMatchForCsrfFromBrowser(final String path) {
    final var request = prepareMockRequest();
    request.getSession(true);
    request.setServletPath(path.replace("**", "/some/path").replace("*", "/some-path"));
    assertTrue(matcher.matches(request));
  }

  @ParameterizedTest
  @MethodSource("protectedPaths")
  void protectedPathsDontMatchForCsrfFromBrowser(final String path) {
    final var request = prepareMockRequest();
    request.setServletPath(path.replace("**", "/some/path").replace("*", "/some-path"));
    assertFalse(matcher.matches(request));
  }

  private static Stream<Arguments> unprotectedPaths() {
    final Set<String> allowedPaths = new HashSet<>();
    allowedPaths.addAll(WebSecurityConfig.UNPROTECTED_PATHS);
    allowedPaths.addAll(WebSecurityConfig.UNPROTECTED_API_PATHS);
    allowedPaths.add(WebSecurityConfig.LOGIN_URL);
    allowedPaths.add(WebSecurityConfig.LOGOUT_URL);
    return Stream.of(allowedPaths.stream().map(Arguments::of).toArray(Arguments[]::new));
  }

  private static Stream<Arguments> protectedPaths() {
    final Set<String> protectedPaths = new HashSet<>(WebSecurityConfig.API_PATHS);
    return Stream.of(protectedPaths.stream().map(Arguments::of).toArray(Arguments[]::new));
  }

  private MockHttpServletRequest prepareMockRequest() {
    final MockHttpServletRequest request = new MockHttpServletRequest();
    request.setMethod("POST"); // POST by default will apply CSRF
    request.setServletPath("/v2/operate/processes");
    return request;
  }
}
