/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.spring.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * Comprehensive integration-style test for {@link CsrfProtectionRequestMatcher}. Extends coverage
 * beyond the existing unit test to cover all bypass scenarios: HTTP methods, unprotected paths, API
 * paths, login/logout, Swagger UI, browser vs non-browser detection.
 */
class CsrfProtectionFlowTest {

  private static final Set<String> UNPROTECTED_PATHS =
      Set.of("/actuator/**", "/error", "/public/**");
  private static final Set<String> UNPROTECTED_API_PATHS =
      Set.of("/v2/license", "/api/public/**", "/v1/health");
  private static final String LOGIN_URL = "/login";
  private static final String LOGOUT_URL = "/logout";

  private final CsrfProtectionRequestMatcher matcher =
      new CsrfProtectionRequestMatcher(
          UNPROTECTED_PATHS, UNPROTECTED_API_PATHS, LOGIN_URL, LOGOUT_URL);

  // -- Safe HTTP methods --

  @ParameterizedTest(name = "safe method {0} should not require CSRF")
  @ValueSource(strings = {"GET", "HEAD", "TRACE", "OPTIONS"})
  void safeHttpMethodsShouldNotRequireCsrf(final String method) {
    // given
    final var request = createRequest(method, "/some-protected-path");
    request.getSession(true); // even with session, safe methods are exempt

    // when/then
    assertThat(matcher.matches(request)).isFalse();
  }

  // -- Unsafe HTTP methods requiring CSRF --

  @ParameterizedTest(name = "unsafe method {0} with session should require CSRF")
  @ValueSource(strings = {"POST", "PUT", "DELETE", "PATCH"})
  void unsafeHttpMethodsWithSessionShouldRequireCsrf(final String method) {
    // given
    final var request = createRequest(method, "/protected-resource");
    request.getSession(true);

    // when/then
    assertThat(matcher.matches(request)).isTrue();
  }

  // -- Unprotected paths bypass CSRF --

  @ParameterizedTest(name = "unprotected path {0} should bypass CSRF")
  @MethodSource("unprotectedPathProvider")
  void unprotectedPathsShouldBypassCsrf(final String path) {
    // given
    final var request = createRequest("POST", path);
    request.getSession(true);

    // when/then
    assertThat(matcher.matches(request)).isFalse();
  }

  static Stream<Arguments> unprotectedPathProvider() {
    return Stream.of(
        // Unprotected paths
        Arguments.of("/actuator/health"),
        Arguments.of("/actuator/prometheus"),
        Arguments.of("/error"),
        Arguments.of("/public/something"),
        // Unprotected API paths
        Arguments.of("/v2/license"),
        Arguments.of("/api/public/status"),
        Arguments.of("/v1/health"),
        // Login/logout URLs
        Arguments.of("/login"),
        Arguments.of("/logout"));
  }

  // -- Login/logout URL bypass --

  @Test
  void postToLoginUrlShouldBypassCsrf() {
    // given
    final var request = createRequest("POST", "/login");
    request.getSession(true);

    // when/then
    assertThat(matcher.matches(request)).isFalse();
  }

  @Test
  void postToLogoutUrlShouldBypassCsrf() {
    // given
    final var request = createRequest("POST", "/logout");
    request.getSession(true);

    // when/then
    assertThat(matcher.matches(request)).isFalse();
  }

  // -- Swagger UI referer bypass --

  @Test
  void postWithSwaggerUiRefererShouldBypassCsrf() {
    // given
    final var request = createRequest("POST", "/v3/api-docs");
    request.addHeader("Referer", "http://localhost/swagger-ui/index.html");
    request.getSession(true);

    // when/then
    assertThat(matcher.matches(request)).isFalse();
  }

  @Test
  void postWithSwaggerUiRefererOnCustomPortShouldBypassCsrf() {
    // given
    final var request = new MockHttpServletRequest();
    request.setMethod("POST");
    request.setServerName("localhost");
    request.setServerPort(8080);
    request.setRequestURI("/v3/api-docs");
    request.setServletPath("/v3/api-docs");
    request.addHeader("Referer", "http://localhost:8080/swagger-ui/index.html");
    request.getSession(true);

    // when/then
    assertThat(matcher.matches(request)).isFalse();
  }

  @Test
  void postWithNonSwaggerRefererShouldRequireCsrf() {
    // given
    final var request = createRequest("POST", "/some-path");
    request.addHeader("Referer", "http://localhost/some-other-page");
    request.getSession(true);

    // when/then
    assertThat(matcher.matches(request)).isTrue();
  }

  @Test
  void postWithRefererFromDifferentHostShouldRequireCsrf() {
    // given — Swagger referer from a different host should NOT match
    final var request = createRequest("POST", "/v3/api-docs");
    request.addHeader("Referer", "http://evil.com/swagger-ui/index.html");
    request.getSession(true);

    // when/then
    assertThat(matcher.matches(request)).isTrue();
  }

  // -- Browser vs non-browser detection --

  @Test
  void nonBrowserApiCallShouldBypassCsrf() {
    // given — no session means non-browser API call
    final var request = createRequest("POST", "/api/data");
    // no session created

    // when/then
    assertThat(matcher.matches(request)).isFalse();
  }

  @Test
  void browserSessionRequestShouldRequireCsrf() {
    // given — existing session indicates browser
    final var request = createRequest("POST", "/api/data");
    request.getSession(true);

    // when/then
    assertThat(matcher.matches(request)).isTrue();
  }

  @Test
  void deleteWithSessionShouldRequireCsrf() {
    // given
    final var request = createRequest("DELETE", "/api/resource/123");
    request.getSession(true);

    // when/then
    assertThat(matcher.matches(request)).isTrue();
  }

  @Test
  void deleteWithoutSessionShouldBypassCsrf() {
    // given
    final var request = createRequest("DELETE", "/api/resource/123");

    // when/then
    assertThat(matcher.matches(request)).isFalse();
  }

  // -- Edge cases --

  @Test
  void putToUnprotectedPathShouldBypassCsrf() {
    // given — PUT to unprotected path should be exempt regardless of session
    final var request = createRequest("PUT", "/actuator/shutdown");
    request.getSession(true);

    // when/then
    assertThat(matcher.matches(request)).isFalse();
  }

  @Test
  void shouldHandleWildcardPathMatching() {
    // given — path matching /public/** wildcard
    final var request = createRequest("POST", "/public/deeply/nested/path");
    request.getSession(true);

    // when/then
    assertThat(matcher.matches(request)).isFalse();
  }

  @Test
  void shouldRequireCsrfForNonMatchingPath() {
    // given — path that doesn't match any unprotected pattern
    final var request = createRequest("POST", "/api/private/resource");
    request.getSession(true);

    // when/then
    assertThat(matcher.matches(request)).isTrue();
  }

  // -- Helper methods --

  private MockHttpServletRequest createRequest(final String method, final String path) {
    final var request = new MockHttpServletRequest();
    request.setMethod(method);
    request.setRequestURI(path);
    request.setServletPath(path);
    return request;
  }
}
