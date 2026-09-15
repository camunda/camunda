/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.csl;

import io.camunda.identity.sdk.exception.IdentityException;
import io.camunda.optimize.rest.exceptions.NotAuthorizedException;
import io.camunda.optimize.service.security.CCSMTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Closes the gap CSL's {@code OidcUserAuthenticationConverter#decodeAccessToken} leaves open for
 * session-authenticated CCSM users: that method catches {@code JwtException} (a supertype of {@link
 * org.springframework.security.oauth2.jwt.JwtValidationException}, which {@link
 * OptimizeIdentityPermissionValidator} throws) and returns {@code null} on a decode failure. Its
 * caller then falls back to building a {@code CamundaAuthentication} from the id_token's claims
 * instead of denying the request — so a session user whose access token starts failing the Identity
 * {@code write:*} check (revoked permission, revoked token, etc.) keeps a working session
 * indefinitely. The legacy {@code CCSMAuthenticationCookieFilter} closed exactly this gap by
 * deleting the auth cookies outright on {@link NotAuthorizedException}; this filter mirrors that
 * "hard kill" behaviour for CSL's session-cookie model.
 *
 * <p>Runs once per request, after CSL's session/authentication filters have populated the {@link
 * SecurityContextHolder} (see {@link OptimizeCcsmSecurityConfiguration} for how it is wired into
 * the webapp and API chains). Scoping to the session-authenticated case is delegated to {@link
 * CCSMTokenService#getSessionAccessToken(HttpServletRequest)} itself, which only resolves a token
 * when the current {@code SecurityContext} holds an {@code OAuth2AuthenticationToken} (CSL's
 * session-login authentication type) — a bearer-only request never has one, so it naturally
 * resolves no token here and falls through unchanged. That request is already independently decoded
 * and gated by {@link OptimizeIdentityPermissionValidator} on every call, so skipping it here
 * avoids a redundant re-check (safe either way, since {@link
 * CCSMTokenService#verifyAccessToken(String)} is idempotent, but pure overhead if repeated).
 *
 * <p>Retrieves the session's stored access token directly from {@link
 * CCSMTokenService#getSessionAccessToken(HttpServletRequest)} rather than trying to recover the
 * exact token value CSL's {@code OidcUserAuthenticationConverter} decoded, because CSL exposes no
 * such extension point to a downstream filter — this is the pragmatic fallback the task called for:
 * independently extract and re-verify the same token the converter would have used.
 *
 * <p>Catches {@link NotAuthorizedException} (permission denied), {@link IdentityException} (covers
 * {@code TokenVerificationException} for an invalid/expired token — unlike {@link
 * CCSMTokenService#verifyToken(String)}, {@link CCSMTokenService#verifyAccessToken(String)} does
 * not wrap that case into {@link NotAuthorizedException} — plus Identity being unreachable), and
 * finally any other {@link RuntimeException}, mirroring {@link
 * OptimizeIdentityPermissionValidator#validate}: a security boundary must fail closed on the
 * unexpected rather than let it propagate as an uncaught 500, which would be worse than the 401
 * this filter exists to produce.
 */
public class OptimizeCcsmSessionPermissionEnforcementFilter extends OncePerRequestFilter {

  private static final Logger LOG =
      LoggerFactory.getLogger(OptimizeCcsmSessionPermissionEnforcementFilter.class);

  private final CCSMTokenService ccsmTokenService;

  public OptimizeCcsmSessionPermissionEnforcementFilter(final CCSMTokenService ccsmTokenService) {
    this.ccsmTokenService = ccsmTokenService;
  }

  @Override
  protected void doFilterInternal(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final FilterChain filterChain)
      throws ServletException, IOException {
    final Optional<String> sessionAccessToken = ccsmTokenService.getSessionAccessToken(request);
    if (sessionAccessToken.isPresent()) {
      try {
        ccsmTokenService.verifyAccessToken(sessionAccessToken.get());
      } catch (final NotAuthorizedException | IdentityException e) {
        LOG.debug("Session's access token no longer authorized; invalidating session.", e);
        invalidateSession(request);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        return;
      } catch (final RuntimeException e) {
        // Same fail-closed reasoning as OptimizeIdentityPermissionValidator#validate: an
        // unexpected error verifying the session's token must not propagate as an uncaught 500,
        // it must deny the request the same as a confirmed rejection.
        LOG.warn("Unexpected error verifying session's access token; invalidating session.", e);
        invalidateSession(request);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        return;
      }
    }
    filterChain.doFilter(request, response);
  }

  private void invalidateSession(final HttpServletRequest request) {
    SecurityContextHolder.clearContext();
    final HttpSession session = request.getSession(false);
    if (session != null) {
      session.invalidate();
    }
  }
}
