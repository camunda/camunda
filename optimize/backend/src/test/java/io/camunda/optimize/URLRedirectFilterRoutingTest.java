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
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Verifies which request URIs the SPA-routing filter rewrites to {@code /#} for each {@code
 * optimize.security.csl.enabled} state. In CSL mode the auth endpoints must reach the security
 * chain unrewritten, otherwise OIDC login initiation bounces to the SPA home. See <a
 * href="https://github.com/camunda/camunda-security-library/blob/main/docs/adr/0038-optimize-reuses-stateful-oidc-webapp-chain.md">ADR-0038</a>.
 *
 * <p>Every case runs twice, once without a servlet context path and once with one, because CCSaaS
 * derives the context path from the cluster id. The filter strips the context path before matching,
 * so the outcome has to be the same either way.
 */
@ExtendWith(MockitoExtension.class)
class URLRedirectFilterRoutingTest {

  private static final String SPA_HOME = "/#";
  private static final List<String> CONTEXT_PATHS = List.of("", "/cluster-id");

  @Mock private HttpServletRequest request;
  @Mock private HttpServletResponse response;
  @Mock private FilterChain filterChain;

  /** CSL's own endpoints: login initiation and logout. */
  private static Stream<Arguments> cslAuthEndpoints() {
    return perContextPath("/oauth2/authorization/optimize", "/logout");
  }

  /**
   * Paths the legacy stack already allows, which must keep passing through in CSL mode: {@code
   * /login} and {@code /external/**} back the login picker and the public share links, and the CSL
   * OIDC callback is {@code /api/authentication/callback} on CCSM / {@code /sso-callback} on
   * CCSaaS.
   */
  private static Stream<Arguments> pathsAllowedInBothModes() {
    return perContextPath(
        "/login",
        "/external/share-id",
        "/external/api/shared-report",
        "/sso-callback",
        "/api/authentication/callback",
        "/actuator/health");
  }

  private static Stream<Arguments> unknownSpaRoutes() {
    return perContextPath("/dashboard/some-id", "/collection", "/unknown");
  }

  private static Stream<Arguments> perContextPath(final String... paths) {
    return CONTEXT_PATHS.stream()
        .flatMap(contextPath -> Arrays.stream(paths).map(path -> Arguments.of(contextPath, path)));
  }

  @ParameterizedTest(name = "contextPath=''{0}'' path={1}")
  @MethodSource("cslAuthEndpoints")
  void shouldRewriteCslAuthEndpointsWhenCslDisabled(final String contextPath, final String path)
      throws Exception {
    givenRequestTo(contextPath, path);

    filterFor(contextPath, false).doFilter(request, response, filterChain);

    verify(response).sendRedirect(contextPath + SPA_HOME);
    verifyNoInteractions(filterChain);
  }

  @ParameterizedTest(name = "contextPath=''{0}'' path={1}")
  @MethodSource("cslAuthEndpoints")
  void shouldPassCslAuthEndpointsThroughWhenCslEnabled(final String contextPath, final String path)
      throws Exception {
    givenRequestTo(contextPath, path);

    filterFor(contextPath, true).doFilter(request, response, filterChain);

    verify(filterChain).doFilter(request, response);
    verify(response, never()).sendRedirect(any());
  }

  @ParameterizedTest(name = "contextPath=''{0}'' path={1}")
  @MethodSource("pathsAllowedInBothModes")
  void shouldPassAlreadyAllowedPathsThroughInBothModes(final String contextPath, final String path)
      throws Exception {
    givenRequestTo(contextPath, path);

    filterFor(contextPath, false).doFilter(request, response, filterChain);
    filterFor(contextPath, true).doFilter(request, response, filterChain);

    verify(filterChain, times(2)).doFilter(request, response);
    verify(response, never()).sendRedirect(any());
  }

  @ParameterizedTest(name = "contextPath=''{0}'' path={1}")
  @MethodSource("unknownSpaRoutes")
  void shouldRewriteUnknownSpaRoutesInBothModes(final String contextPath, final String path)
      throws Exception {
    givenRequestTo(contextPath, path);

    filterFor(contextPath, false).doFilter(request, response, filterChain);
    filterFor(contextPath, true).doFilter(request, response, filterChain);

    verify(response, times(2)).sendRedirect(contextPath + SPA_HOME);
    verifyNoInteractions(filterChain);
  }

  private void givenRequestTo(final String contextPath, final String path) {
    when(request.getContextPath()).thenReturn(contextPath);
    when(request.getRequestURI()).thenReturn(contextPath + path);
  }

  private URLRedirectFilter filterFor(final String contextPath, final boolean cslEnabled) {
    return new URLRedirectFilter(
        OptimizeTomcatConfig.buildRedirectExclusionRegex(contextPath, cslEnabled),
        contextPath + SPA_HOME);
  }
}
