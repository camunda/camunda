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
import io.camunda.security.api.context.CamundaAuthenticationProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Enforces the Optimize Identity permission on a CCSM bearer request, but only for a token whose
 * {@link CamundaAuthenticationProvider#getCamundaAuthentication()} classifies it as a user rather
 * than an M2M client. An M2M client token is left exactly as the audience check on the surrounding
 * chain already authorized it — legacy CCSM behavior this filter must not change.
 *
 * <p>Classification is read from CSL's own {@code CamundaAuthentication} rather than re-derived
 * here, so this filter always agrees with every other CSL consumer (login-session {@code /me},
 * membership resolution) on whether a given bearer token belongs to a user or a client — both are
 * backed by the same {@code LazyTokenClaimsConverter} and configured claim names.
 *
 * <p>Runs on every CSL chain (see {@link OptimizeBearerPermissionConfiguration}), so it covers the
 * whole {@code /api/**} surface including {@code /api/public/**} and {@code
 * /api/ingestion/variable}, which previously had no Identity check of any kind.
 */
public final class OptimizeBearerPermissionFilter extends OncePerRequestFilter {

  private static final Logger LOG = LoggerFactory.getLogger(OptimizeBearerPermissionFilter.class);

  private final CamundaAuthenticationProvider authenticationProvider;
  private final CCSMTokenService tokenService;
  private final AtomicBoolean warnedAboutFailOpen = new AtomicBoolean(false);

  public OptimizeBearerPermissionFilter(
      final CamundaAuthenticationProvider authenticationProvider,
      final CCSMTokenService tokenService) {
    this.authenticationProvider = authenticationProvider;
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

    if (isM2mClient()) {
      filterChain.doFilter(request, response);
      return;
    }

    try {
      tokenService.verifyAccessToken(jwtAuthentication.getToken().getTokenValue());
    } catch (final NotAuthorizedException e) {
      LOG.debug("Denying bearer request at {}: {}", request.getRequestURI(), e.getMessage());
      response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
      return;
    } catch (final TokenVerificationException e) {
      // The bearer chain already verified signature and audience before this filter runs; a fresh
      // permission lookup failing here (e.g. Identity temporarily unreachable) is not evidence the
      // user lacks the permission, so it must not be treated as a denial. Mirrors
      // OptimizeCcsmComponentAccessPolicy's identical leniency on this exception.
      if (warnedAboutFailOpen.compareAndSet(false, true)) {
        LOG.warn(
            "Access token could not be freshly verified, letting the request through without the"
                + " Optimize permission check: {}. This means the check is not currently being"
                + " enforced on bearer requests; further occurrences are logged at DEBUG.",
            e.getMessage());
      } else {
        LOG.debug(
            "Access token could not be freshly verified, letting the request through: {}",
            e.getMessage());
      }
    }

    filterChain.doFilter(request, response);
  }

  // false only when CSL's own classification clearly resolves the bearer token to an M2M client;
  // true for a user, and true (fail closed) when the token can't be classified at all — the same
  // conversion failure that would otherwise surface as an unauthenticated request elsewhere.
  private boolean isM2mClient() {
    try {
      final var camundaAuthentication = authenticationProvider.getCamundaAuthentication();
      return camundaAuthentication != null && camundaAuthentication.authenticatedClientId() != null;
    } catch (final RuntimeException e) {
      LOG.debug("Could not classify the bearer token's subject, assuming a user", e);
      return false;
    }
  }
}
