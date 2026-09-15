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
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Enforces the Identity {@code write:*} (OPTIMIZE_PERMISSION) check for session-authenticated CCSM
 * users by re-verifying the session's stored access token, and invalidates the session once
 * Identity revokes the permission. CSL does not cover this path: {@code
 * OidcUserAuthenticationConverter#decodeAccessToken} catches the {@code JwtException} {@link
 * OptimizeIdentityPermissionValidator} throws and its caller falls back to the id_token's claims,
 * so such a session keeps working indefinitely.
 *
 * <p>Applies only to session logins, which {@link
 * CCSMTokenService#getSessionAccessToken(HttpServletRequest)} scopes by resolving a token just for
 * an {@code OAuth2AuthenticationToken} context. A bearer request holds none and passes through,
 * {@link OptimizeIdentityPermissionValidator} gates it on every call anyway.
 *
 * <p>A session login that resolves no token at all, for example because a failed refresh removed
 * the authorized client, is denied: there is nothing to verify and CSL's id_token fallback would
 * serve the request unchecked.
 *
 * <p>An expired access token passes through unverified. Only CSL's webapp chain installs {@code
 * OAuth2RefreshTokenFilter}, so verifying it on the API chain would deny every {@code /api/**} call
 * for the rest of the session. The permission check is therefore only as fresh as the access token,
 * and a revoked permission surfaces on the next refresh.
 *
 * <p>Only {@link NotAuthorizedException}, Identity's verdict on this user, invalidates the session.
 * {@link IdentityException} (an invalid token or Identity being unreachable) and any other {@link
 * RuntimeException} deny the request but keep the session, so an Identity outage does not log
 * everybody out. Denials are raised as {@link InsufficientAuthenticationException} so each chain's
 * own {@code AuthenticationEntryPoint} shapes the response: a login redirect on the webapp chain, a
 * 401 on the API chain.
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
    if (sessionAccessToken.isEmpty()) {
      if (isSessionAuthenticated()) {
        // A session login without a stored access token: there is nothing to verify, and CSL's
        // id_token fallback would serve the request unchecked. Fail closed instead of treating it
        // like a bearer-only request.
        LOG.debug("Session holds no access token to verify; denying the request.");
        throw new InsufficientAuthenticationException(
            "Session holds no access token that could be verified");
      }
      filterChain.doFilter(request, response);
      return;
    }
    if (!isExpired(sessionAccessToken.get())) {
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
   * Whether CSL's session login authenticated this request, meaning the {@code SecurityContext}
   * holds an {@code OAuth2AuthenticationToken}. A bearer-only request never holds one.
   */
  private static boolean isSessionAuthenticated() {
    return SecurityContextHolder.getContext().getAuthentication()
        instanceof OAuth2AuthenticationToken;
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
