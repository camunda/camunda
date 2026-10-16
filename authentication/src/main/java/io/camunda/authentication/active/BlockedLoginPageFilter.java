/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.active;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Shows the blocked-login page to a browser that was just signed out by {@link InactiveUserFilter}.
 *
 * <p>After the block ended the session, the next navigation is unauthenticated and Spring
 * Security's chain would redirect it to the identity provider, so the user would never learn why
 * they were signed out. The marker cookie set at block time lets this filter answer that navigation
 * with the page instead. The cookie is kept until the user follows the "Sign in again" link to
 * {@link BlockedLogin#RESET_PATH}, which clears it, or until it expires.
 *
 * <p>Must be registered with an order before Spring Security's filter chain proxy, so that the
 * reset path never reaches the chain and needs no authentication.
 */
public final class BlockedLoginPageFilter extends OncePerRequestFilter {

  private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();
  private static final List<String> SIGN_IN_PATTERNS =
      List.of("/oauth2/**", "/login/oauth2/**", "/sso-callback", "/sso-callback/**");

  @Override
  protected void doFilterInternal(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final FilterChain filterChain)
      throws ServletException, IOException {
    final var path = BlockedLogin.pathWithinContext(request);
    if (BlockedLogin.RESET_PATH.equals(path)) {
      BlockedLogin.clearMarker(request, response);
      response.setStatus(HttpServletResponse.SC_FOUND);
      response.setHeader("Location", BlockedLogin.contextRoot(request));
      response.setHeader("Cache-Control", "no-store");
      return;
    }
    if (BlockedLogin.hasMarker(request)
        && !isSignIn(path)
        && !BlockedLogin.isStaticResource(request)
        && BlockedLogin.isBrowserNavigation(request)
        && request.getSession(false) == null) {
      final var marker = BlockedLogin.readMarker(request);
      if (marker != null) {
        BlockedLogin.writePage(request, response, marker);
        return;
      }
    }
    filterChain.doFilter(request, response);
  }

  private static boolean isSignIn(final String path) {
    final var unprefixed = BlockedLogin.withoutTenantPrefix(path);
    return SIGN_IN_PATTERNS.stream()
        .anyMatch(
            pattern ->
                PATH_MATCHER.match(pattern, path) || PATH_MATCHER.match(pattern, unprefixed));
  }
}
