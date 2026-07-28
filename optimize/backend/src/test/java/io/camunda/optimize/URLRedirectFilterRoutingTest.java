/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.optimize.tomcat.URLRedirectFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Verifies which request URIs the SPA-routing filter rewrites to {@code /#} for each {@code
 * optimize.security.csl.enabled} state. In CSL mode the auth endpoints must reach the security
 * chain unrewritten, otherwise OIDC login initiation bounces to the SPA home. See <a
 * href="https://github.com/camunda/camunda-security-library/blob/main/docs/adr/0038-optimize-reuses-stateful-oidc-webapp-chain.md">ADR-0038</a>.
 */
@ExtendWith(MockitoExtension.class)
class URLRedirectFilterRoutingTest {

  private static final String REDIRECT_TARGET = "/#";

  @Mock private HttpServletRequest request;
  @Mock private HttpServletResponse response;
  @Mock private FilterChain filterChain;

  @ParameterizedTest
  @ValueSource(strings = {"/oauth2/authorization/optimize", "/logout"})
  void shouldRewriteCslAuthEndpointsWhenCslDisabled(final String requestUri) throws Exception {
    givenRequestTo(requestUri);

    filterFor(false).doFilter(request, response, filterChain);

    thenRedirectedToSpa();
  }

  @ParameterizedTest
  @ValueSource(strings = {"/oauth2/authorization/optimize", "/logout"})
  void shouldPassCslAuthEndpointsThroughWhenCslEnabled(final String requestUri) throws Exception {
    givenRequestTo(requestUri);

    filterFor(true).doFilter(request, response, filterChain);

    thenPassedThrough();
  }

  /**
   * Paths the legacy stack already allows. They must keep passing through in CSL mode: {@code
   * /login} and {@code /external/**} back the login picker and the public share links, and the CSL
   * OIDC callback is {@code /api/authentication/callback} on CCSM / {@code /sso-callback} on
   * CCSaaS.
   */
  @ParameterizedTest
  @ValueSource(
      strings = {
        "/login",
        "/external/share-id",
        "/external/api/shared-report",
        "/sso-callback",
        "/api/authentication/callback",
        "/actuator/health"
      })
  void shouldPassSharedAllowedPathsThroughInBothModes(final String requestUri) throws Exception {
    givenRequestTo(requestUri);

    filterFor(false).doFilter(request, response, filterChain);
    filterFor(true).doFilter(request, response, filterChain);

    verify(filterChain, times(2)).doFilter(request, response);
    verify(response, never()).sendRedirect(any());
  }

  @ParameterizedTest
  @ValueSource(strings = {"/dashboard/some-id", "/collection", "/unknown"})
  void shouldRewriteUnknownSpaRoutesInBothModes(final String requestUri) throws Exception {
    givenRequestTo(requestUri);

    filterFor(false).doFilter(request, response, filterChain);
    filterFor(true).doFilter(request, response, filterChain);

    verify(response, times(2)).sendRedirect(REDIRECT_TARGET);
    verifyNoInteractions(filterChain);
  }

  private void givenRequestTo(final String requestUri) {
    when(request.getContextPath()).thenReturn("");
    when(request.getRequestURI()).thenReturn(requestUri);
  }

  private URLRedirectFilter filterFor(final boolean cslEnabled) {
    return new URLRedirectFilter(
        OptimizeTomcatConfig.buildRedirectExclusionRegex("", cslEnabled), REDIRECT_TARGET);
  }

  private void thenPassedThrough() throws Exception {
    verify(filterChain).doFilter(request, response);
    verify(response, never()).sendRedirect(any());
  }

  private void thenRedirectedToSpa() throws Exception {
    verify(response).sendRedirect(REDIRECT_TARGET);
    verifyNoInteractions(filterChain);
  }
}
