/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.active;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.camunda.authentication.active.BlockedLogin.Marker;
import io.camunda.authentication.active.BlockedLogin.Variant;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

class BlockedLoginPageFilterTest {

  private final FilterChain chain = mock(FilterChain.class);
  private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/operate");
  private final MockHttpServletResponse response = new MockHttpServletResponse();
  private final BlockedLoginPageFilter filter = new BlockedLoginPageFilter();

  private void navigateWithMarker(final Marker marker) {
    request.addHeader("Accept", "text/html,application/xhtml+xml,*/*;q=0.8");
    request.addHeader("Sec-Fetch-Mode", "navigate");
    request.setCookies(new Cookie(BlockedLogin.COOKIE_NAME, marker.encode()));
  }

  private static Marker mismatch() {
    return new Marker(Variant.MISMATCH, "<b>jane</b>", "okta", "entra");
  }

  @Test
  void shouldShowPageForNavigationWithMarkerAndNoSession() throws Exception {
    // given
    navigateWithMarker(mismatch());

    // when
    filter.doFilter(request, response, chain);

    // then
    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(response.getContentType()).isEqualTo("text/html;charset=UTF-8");
    assertThat(response.getContentAsString())
        .contains(
            "This sign-in can&#39;t be used",
            "&lt;b&gt;jane&lt;/b&gt;",
            "okta",
            "entra",
            "href=\"/blocked-login/reset\"");
    assertThat(response.getHeader("Set-Cookie")).isNull();
    verify(chain, never()).doFilter(any(), any());
  }

  @Test
  void shouldShowDeactivatedVariantWithContextPath() throws Exception {
    // given
    request.setContextPath("/ctx");
    request.setRequestURI("/ctx/operate");
    navigateWithMarker(new Marker(Variant.DEACTIVATED, "jane", null, null));

    // when
    filter.doFilter(request, response, chain);

    // then
    assertThat(response.getContentAsString())
        .contains("Your account is deactivated", "href=\"/ctx/blocked-login/reset\"")
        .doesNotContain("Signed in with");
  }

  @Test
  void shouldPassThroughWhenThereIsASession() throws Exception {
    // given
    navigateWithMarker(mismatch());
    request.setSession(new MockHttpSession());

    // when
    filter.doFilter(request, response, chain);

    // then
    verify(chain).doFilter(request, response);
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  void shouldPassThroughWithoutMarker() throws Exception {
    // given
    request.addHeader("Accept", "text/html");

    // when
    filter.doFilter(request, response, chain);

    // then
    verify(chain).doFilter(request, response);
  }

  @Test
  void shouldPassThroughWhenMarkerCannotBeDecoded() throws Exception {
    // given
    request.addHeader("Accept", "text/html");
    request.setCookies(new Cookie(BlockedLogin.COOKIE_NAME, "tampered!.%%.x"));

    // when
    filter.doFilter(request, response, chain);

    // then
    verify(chain).doFilter(request, response);
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  void shouldPassThroughForXhr() throws Exception {
    // given
    navigateWithMarker(mismatch());
    request.removeHeader("Accept");
    request.addHeader("Accept", "application/json");

    // when
    filter.doFilter(request, response, chain);

    // then
    verify(chain).doFilter(request, response);
  }

  @ParameterizedTest
  @ValueSource(strings = {"/v2/topology", "/api/foo", "/operate/assets/index.js", "/styles.css"})
  void shouldPassThroughForApiAndAssets(final String path) throws Exception {
    // given
    request.setRequestURI(path);
    navigateWithMarker(mismatch());

    // when
    filter.doFilter(request, response, chain);

    // then
    verify(chain).doFilter(request, response);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/oauth2/authorization/okta",
        "/login/oauth2/code/okta",
        "/sso-callback",
        "/physical-tenants/t1/oauth2/authorization/okta"
      })
  void shouldPassThroughForSignInPaths(final String path) throws Exception {
    // given
    request.setRequestURI(path);
    navigateWithMarker(mismatch());

    // when
    filter.doFilter(request, response, chain);

    // then
    verify(chain).doFilter(request, response);
  }

  @Test
  void shouldClearMarkerAndRedirectOnReset() throws Exception {
    // given
    request.setRequestURI("/blocked-login/reset");
    navigateWithMarker(mismatch());

    // when
    filter.doFilter(request, response, chain);

    // then
    assertThat(response.getStatus()).isEqualTo(302);
    assertThat(response.getHeader("Location")).isEqualTo("/");
    assertThat(response.getHeader("Set-Cookie"))
        .startsWith("CAMUNDA_LOGIN_BLOCKED=;")
        .contains("Max-Age=0", "Path=/");
    verify(chain, never()).doFilter(any(), any());
  }

  @Test
  void shouldRedirectToContextRootOnReset() throws Exception {
    // given
    request.setContextPath("/ctx");
    request.setRequestURI("/ctx/blocked-login/reset");

    // when
    filter.doFilter(request, response, chain);

    // then
    assertThat(response.getHeader("Location")).isEqualTo("/ctx");
    assertThat(response.getHeader("Set-Cookie")).contains("Path=/ctx", "Max-Age=0");
  }
}
