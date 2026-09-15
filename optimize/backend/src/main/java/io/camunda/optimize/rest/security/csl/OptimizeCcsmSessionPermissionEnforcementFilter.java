/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.csl;

import com.auth0.jwt.JWT;
import com.auth0.jwt.exceptions.JWTDecodeException;
import io.camunda.identity.sdk.exception.IdentityException;
import io.camunda.optimize.rest.exceptions.NotAuthorizedException;
import io.camunda.optimize.service.security.CCSMTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.InsufficientAuthenticationException;
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
 * <p>Only {@link NotAuthorizedException}, Identity's verdict that this user may not use Optimize,
 * invalidates the session. {@link IdentityException} (an invalid or expired token, or Identity
 * being unreachable) and any other {@link RuntimeException} deny the request but leave the session
 * alone, so an Identity outage does not log every user out. Catching the unexpected at all mirrors
 * {@link OptimizeIdentityPermissionValidator#validate}: a security boundary must fail closed rather
 * than let it propagate as an uncaught 500.
 *
 * <p>Every denial is raised as an {@link InsufficientAuthenticationException} instead of writing a
 * status code directly. The filter sits behind {@code ExceptionTranslationFilter}, which hands the
 * exception to the chain's own {@code AuthenticationEntryPoint}, so each chain answers in its own
 * shape: the webapp chain redirects a browser navigation to the login, the API chain returns 401. A
 * bare 401 written here would leave a browser on an empty page.
 *
 * <p>An already expired access token is not verified at all, the request passes through. Only CSL's
 * webapp chain installs {@code OAuth2RefreshTokenFilter}, its API chain restores the session but
 * never refreshes the token. Verifying an expired token there would deny every {@code /api/**} call
 * once the access token's lifetime (minutes) is over, until a page load happens to hit the webapp
 * chain and refresh it. That is worse than not checking: without this filter such a session keeps
 * working, because CSL falls back to the id_token's claims. The permission check is therefore only
 * as fresh as the access token, and a revoked permission surfaces on the next refresh.
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
    final Optional<String> sessionAccessToken =
        ccsmTokenService.getSessionAccessToken(request).filter(token -> !isExpired(token));
    if (sessionAccessToken.isPresent()) {
      try {
        ccsmTokenService.verifyAccessToken(sessionAccessToken.get());
      } catch (final NotAuthorizedException e) {
        LOG.debug("Session's access token no longer authorized; invalidating session.", e);
        invalidateSession(request);
        throw new InsufficientAuthenticationException(
            "Session's access token is not authorized to access Optimize", e);
      } catch (final IdentityException e) {
        // Not a decision Identity made about this user: the token is unusable (invalid, expired
        // past what CSL's refresh could recover) or Identity is unreachable. Denying the request
        // is required, but destroying the session would log every user out on an Identity outage
        // and force a fresh login instead of letting the next request succeed.
        LOG.debug("Session's access token could not be verified; denying the request.", e);
        throw new InsufficientAuthenticationException(
            "Session's access token could not be verified", e);
      } catch (final RuntimeException e) {
        // Same fail-closed reasoning as OptimizeIdentityPermissionValidator#validate: an
        // unexpected error verifying the session's token must not propagate as an uncaught 500,
        // it must deny the request. Like the IdentityException case it says nothing about the
        // user's permission, so the session survives.
        LOG.warn("Unexpected error verifying session's access token; denying the request.", e);
        throw new InsufficientAuthenticationException(
            "Session's access token could not be verified", e);
      }
    }
    filterChain.doFilter(request, response);
  }

  /**
   * Whether the token is past its {@code exp} claim. A token that cannot be decoded is reported as
   * not expired, so the verification below still runs and Identity decides on it.
   */
  private static boolean isExpired(final String accessToken) {
    final Date expiresAt;
    try {
      expiresAt = JWT.decode(accessToken).getExpiresAt();
    } catch (final JWTDecodeException e) {
      LOG.debug("Session's access token is not a JWT, verifying it without an expiry check.", e);
      return false;
    }
    if (expiresAt == null) {
      return false;
    }
    final boolean expired = expiresAt.toInstant().isBefore(Instant.now());
    if (expired) {
      LOG.debug(
          "Session's access token expired at {}, skipping the permission check until it is "
              + "refreshed.",
          expiresAt);
    }
    return expired;
  }

  private void invalidateSession(final HttpServletRequest request) {
    SecurityContextHolder.clearContext();
    final HttpSession session = request.getSession(false);
    if (session != null) {
      session.invalidate();
    }
  }
}
