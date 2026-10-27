/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.ccsm;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.netmikey.logunit.api.LogCapturer;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.event.Level;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

class OidcRedirectDiagnosticsFilterTest {

  private static final String CALLBACK_PATH = "/api/authentication/callback";

  @RegisterExtension
  final LogCapturer logs =
      LogCapturer.create().captureForType(OidcRedirectDiagnosticsFilter.class, Level.DEBUG);

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

    // when
    filter.doFilter(request, response, noopChain());

    // then
    assertThat(response.getStatus()).isEqualTo(200);
    assertNoWarnLogged();
  }

  @Test
  void shouldPassThroughCallbackWithoutError() throws Exception {
    // given — callback with code and valid session, no forwarded headers
    final var request = new MockHttpServletRequest("GET", CALLBACK_PATH);
    request.addParameter("code", "auth-code-value");
    request.addParameter("state", "state-value");
    request.setSession(new MockHttpSession());
    final var response = new MockHttpServletResponse();

    // when
    filter.doFilter(request, response, noopChain());

    // then — chain completed, no diagnostic WARN
    assertThat(response.getStatus()).isEqualTo(200);
    assertNoWarnLogged();
  }

  @Test
  void shouldWarnOnCallbackWithCodeButNoSession() throws Exception {
    // given — callback arrives with an authorization code but no HTTP session
    final var request = new MockHttpServletRequest("GET", CALLBACK_PATH);
    request.addParameter("code", "auth-code-value");
    // no session — getSession(false) returns null
    final var response = new MockHttpServletResponse();

    // when
    filter.doFilter(request, response, noopChain());

    // then — request completed and WARN emitted about the missing session
    assertThat(response.getStatus()).isEqualTo(200);
    logs.assertContains(
        e -> e.getLevel() == Level.WARN && e.getMessage().contains("no valid HTTP session"),
        "expected WARN about missing HTTP session on callback");
  }

  @Test
  void shouldNotWarnOnCallbackWithoutCode() throws Exception {
    // given — callback without a code param (e.g. error response from IdP)
    final var request = new MockHttpServletRequest("GET", CALLBACK_PATH);
    request.addParameter("error", "access_denied");
    final var response = new MockHttpServletResponse();

    // when
    filter.doFilter(request, response, noopChain());

    // then — no session WARN because there is no authorization code to exchange
    assertThat(response.getStatus()).isEqualTo(200);
    assertNoWarnLogged();
  }

  @Test
  void shouldWarnWhenForwardedHeadersDifferFromTomcatUrl() throws Exception {
    // given — reverse proxy sets X-Forwarded-Proto/Host, but Tomcat uses its own scheme/host
    final var request = new MockHttpServletRequest("GET", CALLBACK_PATH);
    request.setScheme("http");
    request.setServerName("internal-host");
    request.setServerPort(8090);
    request.setRequestURI(CALLBACK_PATH);
    request.addHeader("X-Forwarded-Proto", "https");
    request.addHeader("X-Forwarded-Host", "optimize.example.com");
    final var response = new MockHttpServletResponse();

    // when
    filter.doFilter(request, response, noopChain());

    // then — WARN naming both the raw Tomcat URL and the expected external URL
    logs.assertContains(
        e -> e.getLevel() == Level.WARN && e.getMessage().contains("mismatch"),
        "expected WARN about callback URL mismatch");
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

    // when
    filter.doFilter(request, response, noopChain());

    // then — DEBUG entry logged but no WARN
    assertThat(response.getStatus()).isEqualTo(200);
    assertNoWarnLogged();
    logs.assertContains(
        e -> e.getLevel() == Level.DEBUG && e.getMessage().contains("OIDC callback received"),
        "expected DEBUG entry for callback");
  }

  @Test
  void shouldWarnOnIdentityRedirectWithMismatchedRedirectUri() throws Exception {
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

    // when
    filter.doFilter(request, response, chain);

    // then — redirect went through AND WARN naming the mismatched redirect_uri was emitted
    assertThat(response.getStatus()).isEqualTo(302);
    logs.assertContains(
        e -> e.getLevel() == Level.WARN && e.getMessage().contains("redirect_uri"),
        "expected WARN about mismatched redirect_uri in Identity authorize redirect");
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

  // --- URL construction via doFilter: assert no WARN when URLs match ---

  @Test
  void shouldUseFirstValueFromCommaListInXForwardedPort() throws Exception {
    // given — proxy sends X-Forwarded-Port as a comma-separated list; first value "443" is default
    final var request = new MockHttpServletRequest("GET", CALLBACK_PATH);
    request.setRequestURI(CALLBACK_PATH);
    request.addHeader("X-Forwarded-Proto", "https");
    request.addHeader("X-Forwarded-Host", "optimize.example.com");
    request.addHeader("X-Forwarded-Port", "443, 8443");
    request.setScheme("https");
    request.setServerName("optimize.example.com");
    request.setServerPort(443);
    final var response = new MockHttpServletResponse();

    // when
    filter.doFilter(request, response, noopChain());

    // then — first port value "443" is the HTTPS default; both URLs agree, no WARN
    assertThat(response.getStatus()).isEqualTo(200);
    assertNoWarnLogged();
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

    // when
    filter.doFilter(request, response, noopChain());

    // then — port 8443 extracted from host; Tomcat URL also has :8443; no WARN
    assertThat(response.getStatus()).isEqualTo(200);
    assertNoWarnLogged();
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

    // when
    filter.doFilter(request, response, noopChain());

    // then — port 443 suppressed from both sides; URLs agree, no WARN
    assertThat(response.getStatus()).isEqualTo(200);
    assertNoWarnLogged();
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

    // when
    filter.doFilter(request, response, noopChain());

    // then — X-Forwarded-Port=8443 overrides embedded 9999; Tomcat :8443 matches; no WARN
    assertThat(response.getStatus()).isEqualTo(200);
    assertNoWarnLogged();
  }

  // --- helpers ---

  private void assertNoWarnLogged() {
    logs.assertDoesNotContain(
        e -> e.getLevel() == Level.WARN, "unexpected WARN logged by diagnostics filter");
  }

  private static FilterChain noopChain() {
    return (req, res) -> {};
  }
}
