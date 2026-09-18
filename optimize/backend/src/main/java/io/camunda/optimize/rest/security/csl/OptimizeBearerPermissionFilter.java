/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.csl;

import io.camunda.identity.sdk.authentication.exception.TokenVerificationException;
import io.camunda.optimize.rest.exceptions.NotAuthorizedException;
import io.camunda.optimize.service.security.CCSMTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Enforces the Optimize Identity permission on a CCSM bearer request, but only for a token that
 * {@link OidcBearerPrincipalClassifier} classifies as a user. An M2M client token is left exactly
 * as the audience check on the surrounding chain already authorized it — legacy CCSM behavior this
 * filter must not change.
 *
 * <p>Runs on every CSL chain (see {@link OptimizeBearerPermissionConfiguration}), so it covers the
 * whole {@code /api/**} surface including {@code /api/public/**} and {@code
 * /api/ingestion/variable}, which previously had no Identity check of any kind. See
 * camunda/camunda#63372.
 */
public final class OptimizeBearerPermissionFilter extends OncePerRequestFilter {

  private static final Logger LOG = LoggerFactory.getLogger(OptimizeBearerPermissionFilter.class);

  private final OidcBearerPrincipalClassifier classifier;
  private final CCSMTokenService tokenService;

  public OptimizeBearerPermissionFilter(
      final OidcBearerPrincipalClassifier classifier, final CCSMTokenService tokenService) {
    this.classifier = classifier;
    this.tokenService = tokenService;
  }

  @Override
  protected void doFilterInternal(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final FilterChain filterChain)
      throws ServletException, IOException {
    final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (!(authentication instanceof final JwtAuthenticationToken jwtAuthentication)) {
      // Not a bearer request (e.g. an OIDC login session, or unauthenticated); the session-path
      // component-access check and/or the chain's own rules apply instead.
      filterChain.doFilter(request, response);
      return;
    }

    final var jwt = jwtAuthentication.getToken();
    if (!classifier.requiresOptimizePermissionCheck(jwt.getClaims())) {
      filterChain.doFilter(request, response);
      return;
    }

    try {
      tokenService.verifyAccessToken(jwt.getTokenValue());
    } catch (final NotAuthorizedException e) {
      LOG.debug("Denying bearer request at {}: {}", request.getRequestURI(), e.getMessage());
      response.sendError(HttpServletResponse.SC_UNAUTHORIZED, e.getMessage());
      return;
    } catch (final TokenVerificationException e) {
      // The bearer chain already verified signature and audience before this filter runs; a fresh
      // permission lookup failing here (e.g. Identity temporarily unreachable) is not evidence the
      // user lacks the permission, so it must not be treated as a denial. Mirrors
      // OptimizeCcsmComponentAccessPolicy's identical leniency on this exception.
      LOG.debug(
          "Access token could not be freshly verified, letting the request through: {}",
          e.getMessage());
    }

    filterChain.doFilter(request, response);
  }
}
