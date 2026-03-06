/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.ccsm;

import jakarta.annotation.Nonnull;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Servlet filter that enables JWT bearer-token authentication for non-public {@code /api/**}
 * endpoints in self-managed (CCSM) mode.
 *
 * <p>The filter is only registered when {@code api.jwtAuthForApiEnabled: true} is set. It runs
 * <em>before</em> {@link CCSMAuthenticationCookieFilter} in the security filter chain. The two
 * authentication paths are therefore mutually exclusive per request:
 *
 * <ol>
 *   <li>If the request carries a valid {@code Authorization: Bearer <token>} header, this filter
 *       authenticates the request and populates the {@link SecurityContextHolder}. The cookie
 *       filter downstream sees an already-authenticated context and skips its own logic.
 *   <li>If no {@code Authorization} header is present (or it is not a bearer token), this filter is
 *       a complete no-op and the cookie filter runs as usual.
 *   <li>If a bearer header is present but the token is invalid, this filter writes a {@code 401}
 *       immediately and stops the chain — no fall-through to cookie auth, preventing token-stuffing
 *       attacks.
 * </ol>
 *
 * <p>The same {@link JwtDecoder} that already validates tokens for the {@code /public/**} filter
 * chain is reused here, so issuer, audience, and expiry validation are identical.
 */
public class ApiBearerTokenAuthenticationFilter extends OncePerRequestFilter {

  private static final Logger LOG =
      LoggerFactory.getLogger(ApiBearerTokenAuthenticationFilter.class);

  private static final String BEARER_PREFIX = "Bearer ";

  private final JwtDecoder jwtDecoder;

  public ApiBearerTokenAuthenticationFilter(final JwtDecoder jwtDecoder) {
    this.jwtDecoder = jwtDecoder;
  }

  @Override
  protected void doFilterInternal(
      @Nonnull final HttpServletRequest request,
      @Nonnull final HttpServletResponse response,
      @Nonnull final FilterChain chain)
      throws ServletException, IOException {

    final String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

    // No bearer header → let the cookie filter handle it as normal.
    if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
      chain.doFilter(request, response);
      return;
    }

    // Already authenticated (e.g. by a prior filter in a nested chain) → skip.
    if (SecurityContextHolder.getContext().getAuthentication() != null
        && SecurityContextHolder.getContext().getAuthentication().isAuthenticated()) {
      chain.doFilter(request, response);
      return;
    }

    final String token = authHeader.substring(BEARER_PREFIX.length());

    try {
      final Jwt jwt = jwtDecoder.decode(token);
      final JwtAuthenticationToken authentication = new JwtAuthenticationToken(jwt);
      authentication.setAuthenticated(true);

      final var context = SecurityContextHolder.createEmptyContext();
      context.setAuthentication(authentication);
      SecurityContextHolder.setContext(context);

      LOG.debug(
          "Bearer JWT authentication succeeded for subject '{}' on '{} {}'",
          jwt.getSubject(),
          request.getMethod(),
          request.getRequestURI());

    } catch (final JwtException e) {
      LOG.debug(
          "Bearer JWT authentication failed on '{} {}': {}",
          request.getMethod(),
          request.getRequestURI(),
          e.getMessage());
      // A token was presented but it is invalid — reject immediately.
      // Do NOT fall through to cookie auth: that would allow a crafted invalid bearer
      // header to bypass the intention of the caller.
      SecurityContextHolder.clearContext();
      response.sendError(HttpStatus.UNAUTHORIZED.value(), "Invalid bearer token");
      return;
    }

    chain.doFilter(request, response);
  }
}
