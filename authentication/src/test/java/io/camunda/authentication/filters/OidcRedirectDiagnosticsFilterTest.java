/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.filters;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

public class OidcRedirectDiagnosticsFilterTest {

  private static final String CALLBACK_PATH = "/sso-callback";

  @Test
  void shouldComputeExternalBaseUrlFromForwardedHeaders() {
    // given - a request behind a reverse proxy terminating TLS on a non-default port
    final MockHttpServletRequest request =
        new MockHttpServletRequest("GET", "/oauth2/authorization/oidc");
    request.setServerName("internal-host");
    request.addHeader("X-Forwarded-Proto", "https");
    request.addHeader("X-Forwarded-Host", "auth.example.com:8443");

    // when
    final String baseUrl = OidcRedirectDiagnosticsFilter.computeExternalBaseUrl(request);

    // then
    assertThat(baseUrl).isEqualTo("https://auth.example.com:8443");
  }

  @Test
  void shouldComputeExternalBaseUrlFromBracketedIpv6ForwardedHost() {
    // given - the reverse proxy reports a bracketed IPv6 host + port. A naive ':' split would
    // corrupt the IPv6 literal; the authority must be preserved verbatim.
    final MockHttpServletRequest request =
        new MockHttpServletRequest("GET", "/oauth2/authorization/oidc");
    request.setServerName("internal-host");
    request.addHeader("X-Forwarded-Proto", "https");
    request.addHeader("X-Forwarded-Host", "[2001:db8::1]:8443");

    // when
    final String baseUrl = OidcRedirectDiagnosticsFilter.computeExternalBaseUrl(request);

    // then
    assertThat(baseUrl).isEqualTo("https://[2001:db8::1]:8443");
  }

  @Test
  void shouldHonourForwardedPrefixInExternalBaseUrl() {
    // given - the app is served under a path prefix by the proxy
    final MockHttpServletRequest request =
        new MockHttpServletRequest("GET", "/oauth2/authorization/oidc");
    request.setServerName("internal-host");
    request.addHeader("X-Forwarded-Proto", "https");
    request.addHeader("X-Forwarded-Host", "auth.example.com");
    request.addHeader("X-Forwarded-Prefix", "/camunda/");

    // when
    final String baseUrl = OidcRedirectDiagnosticsFilter.computeExternalBaseUrl(request);

    // then - trailing slash on the prefix is stripped
    assertThat(baseUrl).isEqualTo("https://auth.example.com/camunda");
  }

  @Test
  void shouldExtractRedirectUriFromLocationHeader() {
    // given
    final String location =
        "https://idp.example.com/authorize?client_id=x&redirect_uri=https://app.example.com/sso-callback";

    // when
    final String redirectUri = OidcRedirectDiagnosticsFilter.extractRedirectUri(location);

    // then
    assertThat(redirectUri).isEqualTo("https://app.example.com/sso-callback");
  }

  @Test
  void shouldReturnNullRedirectUriWhenLocationHasNoRedirectUriParam() {
    assertThat(
            OidcRedirectDiagnosticsFilter.extractRedirectUri("https://idp.example.com/authorize"))
        .isNull();
    assertThat(OidcRedirectDiagnosticsFilter.extractRedirectUri(null)).isNull();
    assertThat(OidcRedirectDiagnosticsFilter.extractRedirectUri("")).isNull();
  }

  @Test
  void shouldDetectAuthorizationRequests() {
    assertThat(OidcRedirectDiagnosticsFilter.isAuthorizationRequest("/oauth2/authorization/oidc"))
        .isTrue();
    assertThat(OidcRedirectDiagnosticsFilter.isAuthorizationRequest("/sso-callback")).isFalse();
    assertThat(OidcRedirectDiagnosticsFilter.isAuthorizationRequest(null)).isFalse();
  }

  @Test
  void shouldFlagCallbackWithCodeAndNoSessionAsLostSession() {
    // given - a callback carrying an authorization code but no HTTP session
    final MockHttpServletRequest request = new MockHttpServletRequest("GET", CALLBACK_PATH);
    request.setParameter("code", "auth-code-123");

    // when / then
    assertThat(OidcRedirectDiagnosticsFilter.indicatesLostSession(request, CALLBACK_PATH)).isTrue();
  }

  @Test
  void shouldNotFlagCallbackWhenSessionIsPresent() {
    // given - a callback carrying a code and a valid session
    final MockHttpServletRequest request = new MockHttpServletRequest("GET", CALLBACK_PATH);
    request.setParameter("code", "auth-code-123");
    request.getSession(true);

    // when / then
    assertThat(OidcRedirectDiagnosticsFilter.indicatesLostSession(request, CALLBACK_PATH))
        .isFalse();
  }

  @Test
  void shouldNotFlagNonCallbackRequestAsLostSession() {
    // given - an authorization request (not the callback) with no session
    final MockHttpServletRequest request =
        new MockHttpServletRequest("GET", "/oauth2/authorization/oidc");
    request.setParameter("code", "auth-code-123");

    // when / then
    assertThat(OidcRedirectDiagnosticsFilter.indicatesLostSession(request, CALLBACK_PATH))
        .isFalse();
  }
}
