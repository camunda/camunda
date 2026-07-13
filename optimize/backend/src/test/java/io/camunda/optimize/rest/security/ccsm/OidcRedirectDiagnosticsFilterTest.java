/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.ccsm;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

class OidcRedirectDiagnosticsFilterTest {

  private static final String CALLBACK_PATH = "/api/authentication/callback";

  private OidcRedirectDiagnosticsFilter filter;

  @BeforeEach
  void setUp() {
    filter = new OidcRedirectDiagnosticsFilter(CALLBACK_PATH);
  }

  @Test
  void shouldPassThroughUnrelatedRequests() throws Exception {
    // given
    final var request = new MockHttpServletRequest("GET", "/api/dashboard");
    final var response = new MockHttpServletResponse();
    final var chain = noopChain();

    // when
    filter.doFilter(request, response, chain);

    // then — no exception, chain completed
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  void shouldPassThroughCallbackWithoutError() throws Exception {
    // given — callback with code and valid session
    final var request = new MockHttpServletRequest("GET", CALLBACK_PATH);
    request.addParameter("code", "auth-code-value");
    request.addParameter("state", "state-value");
    request.setSession(new MockHttpSession());
    final var response = new MockHttpServletResponse();

    // when — no exception
    filter.doFilter(request, response, noopChain());
  }

  @Test
  void shouldWarnOnCallbackWithCodeButNoSession() throws Exception {
    // given — callback arrives with an authorization code but no HTTP session
    final var request = new MockHttpServletRequest("GET", CALLBACK_PATH);
    request.addParameter("code", "auth-code-value");
    // no session — getSession(false) returns null
    final var response = new MockHttpServletResponse();

    // when — no exception (logs a WARN)
    filter.doFilter(request, response, noopChain());

    // then — request completed, downstream chain was called
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  void shouldNotWarnOnCallbackWithoutCode() throws Exception {
    // given — callback without a code param (e.g. error response from IdP)
    final var request = new MockHttpServletRequest("GET", CALLBACK_PATH);
    request.addParameter("error", "access_denied");
    final var response = new MockHttpServletResponse();

    // when — no exception
    filter.doFilter(request, response, noopChain());
  }

  @Test
  void shouldLogMismatchWhenForwardedHeadersDifferFromTomcatUrl() throws Exception {
    // given — reverse proxy sets X-Forwarded-Proto/Host, but Tomcat uses its own scheme/host
    final var request = new MockHttpServletRequest("GET", CALLBACK_PATH);
    request.setScheme("http");
    request.setServerName("internal-host");
    request.setServerPort(8090);
    request.setRequestURI(CALLBACK_PATH);
    // Simulate a plain StringBuffer for getRequestURL (MockHttpServletRequest builds it from
    // scheme/host/port)
    request.addHeader("X-Forwarded-Proto", "https");
    request.addHeader("X-Forwarded-Host", "optimize.example.com");
    final var response = new MockHttpServletResponse();

    // when — no exception; a WARN is logged because URLs differ
    filter.doFilter(request, response, noopChain());
  }

  @Test
  void shouldNotWarnWhenForwardedHeadersMatchTomcatUrl() throws Exception {
    // given — Tomcat and forwarded headers agree
    final var request = new MockHttpServletRequest("GET", CALLBACK_PATH);
    request.setScheme("https");
    request.setServerName("optimize.example.com");
    request.setServerPort(443);
    request.setRequestURI(CALLBACK_PATH);
    request.addHeader("X-Forwarded-Proto", "https");
    request.addHeader("X-Forwarded-Host", "optimize.example.com");
    final var response = new MockHttpServletResponse();

    // when — no exception, no WARN (URLs match)
    filter.doFilter(request, response, noopChain());
  }

  @Test
  void shouldLogIdentityRedirectDiagnosticsOnMismatch() throws Exception {
    // given — chain redirects to Identity with a redirect_uri that doesn't match forwarded headers
    final var request = new MockHttpServletRequest("GET", "/api/dashboard");
    request.addHeader("X-Forwarded-Proto", "https");
    request.addHeader("X-Forwarded-Host", "optimize.example.com");
    request.setScheme("http");
    request.setServerName("internal-host");
    final var response = new MockHttpServletResponse();

    final FilterChain chain =
        (req, res) -> {
          ((MockHttpServletResponse) res)
              .setHeader(
                  "Location",
                  "https://idp.example.com/authorize?redirect_uri=http%3A%2F%2Finternal-host%2Fapi%2Fauthentication%2Fcallback&response_type=code");
          ((MockHttpServletResponse) res).setStatus(302);
        };

    // when — no exception; a WARN is logged about the redirect_uri mismatch
    filter.doFilter(request, response, chain);

    assertThat(response.getStatus()).isEqualTo(302);
  }

  // --- splitHostPort ---

  @Test
  void shouldSplitHostWithNumericPort() {
    final String[] parts = OidcRedirectDiagnosticsFilter.splitHostPort("example.com:8443");
    assertThat(parts[0]).isEqualTo("example.com");
    assertThat(parts[1]).isEqualTo("8443");
  }

  @Test
  void shouldReturnNullPortWhenNoPort() {
    final String[] parts = OidcRedirectDiagnosticsFilter.splitHostPort("example.com");
    assertThat(parts[0]).isEqualTo("example.com");
    assertThat(parts[1]).isNull();
  }

  @Test
  void shouldNotMisidentifyColonInIPv6AsPort() {
    // IPv6 without port — last "colon" segment is not all digits
    final String[] parts = OidcRedirectDiagnosticsFilter.splitHostPort("[::1]");
    assertThat(parts[0]).isEqualTo("[::1]");
    assertThat(parts[1]).isNull();
  }

  @Test
  void shouldSplitBracketedIPv6WithPort() {
    final String[] parts = OidcRedirectDiagnosticsFilter.splitHostPort("[::1]:8080");
    assertThat(parts[0]).isEqualTo("[::1]");
    assertThat(parts[1]).isEqualTo("8080");
  }

  // --- buildExpectedCallbackUrl via doFilter ---

  @Test
  void shouldUseFirstValueFromCommaListInXForwardedPort() throws Exception {
    // given — proxy sends X-Forwarded-Port as a comma-separated list
    final var request = new MockHttpServletRequest("GET", CALLBACK_PATH);
    request.setRequestURI(CALLBACK_PATH);
    request.addHeader("X-Forwarded-Proto", "https");
    request.addHeader("X-Forwarded-Host", "optimize.example.com");
    request.addHeader("X-Forwarded-Port", "443, 8443");
    request.setScheme("https");
    request.setServerName("optimize.example.com");
    request.setServerPort(443);
    final var response = new MockHttpServletResponse();

    // when — no exception; URL derived from first port value "443" matches (default, suppressed),
    // so Tomcat URL and expected URL both resolve to https://optimize.example.com/..., no WARN
    filter.doFilter(request, response, noopChain());
  }

  @Test
  void shouldExtractPortEmbeddedInXForwardedHost() throws Exception {
    // given — X-Forwarded-Host includes a non-default port
    final var request = new MockHttpServletRequest("GET", CALLBACK_PATH);
    request.setRequestURI(CALLBACK_PATH);
    request.addHeader("X-Forwarded-Proto", "https");
    request.addHeader("X-Forwarded-Host", "optimize.example.com:8443");
    // no X-Forwarded-Port header
    request.setScheme("https");
    request.setServerName("optimize.example.com");
    request.setServerPort(8443);
    final var response = new MockHttpServletResponse();

    // when — no exception; expected URL includes :8443, Tomcat URL also includes :8443 → no WARN
    filter.doFilter(request, response, noopChain());
  }

  @Test
  void shouldSuppressDefaultPortEmbeddedInXForwardedHost() throws Exception {
    // given — X-Forwarded-Host includes the default HTTPS port
    final var request = new MockHttpServletRequest("GET", CALLBACK_PATH);
    request.setRequestURI(CALLBACK_PATH);
    request.addHeader("X-Forwarded-Proto", "https");
    request.addHeader("X-Forwarded-Host", "optimize.example.com:443");
    request.setScheme("https");
    request.setServerName("optimize.example.com");
    request.setServerPort(443);
    final var response = new MockHttpServletResponse();

    // when — default port must be suppressed so no double-colon URL is built
    filter.doFilter(request, response, noopChain());
  }

  @Test
  void shouldPreferXForwardedPortOverPortEmbeddedInHost() throws Exception {
    // given — both X-Forwarded-Host:port and X-Forwarded-Port are present; Port header wins
    final var request = new MockHttpServletRequest("GET", CALLBACK_PATH);
    request.setRequestURI(CALLBACK_PATH);
    request.addHeader("X-Forwarded-Proto", "https");
    request.addHeader("X-Forwarded-Host", "optimize.example.com:9999");
    request.addHeader("X-Forwarded-Port", "8443");
    request.setScheme("https");
    request.setServerName("optimize.example.com");
    request.setServerPort(8443);
    final var response = new MockHttpServletResponse();

    // when — no exception; X-Forwarded-Port=8443 overrides embedded 9999
    filter.doFilter(request, response, noopChain());
  }

  private static FilterChain noopChain() {
    return (req, res) -> {};
  }
}
