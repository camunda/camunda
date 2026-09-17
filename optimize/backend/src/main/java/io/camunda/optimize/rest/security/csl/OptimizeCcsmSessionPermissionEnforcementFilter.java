/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.csl;

import static io.camunda.optimize.rest.security.csl.OptimizeApiRequests.isApiRequest;

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
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Enforces the Identity {@code write:*} (OPTIMIZE_PERMISSION) check for CCSM session logins. CSL
 * checks only the issuer, the signature and the expiry. Its {@code
 * OidcUserAuthenticationConverter#decodeAccessToken} also hides a validation error and then uses
 * the id_token claims, so a session keeps access after Identity removes the permission.
 *
 * <p>Bearer requests pass through, as they did on legacy CCSM.
 *
 * <p>An expired access token also passes through. Only CSL's webapp chain refreshes the token, so a
 * check on the API chain would deny each {@code /api/**} call for the rest of the session. The
 * check is only as fresh as the access token.
 *
 * <p>Only {@link NotAuthorizedException} invalidates the session, because only that is Identity's
 * verdict on the user. Other errors deny the request but keep the session, so an Identity outage
 * does not log out all users.
 *
 * <p>A denied webapp request gets a terminal 403 page, not a new login. The IdP session is still
 * valid, so a new login would issue a code again and the user would loop.
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
        // Fail closed: CSL would serve this request from the id_token claims instead.
        LOG.debug("Session holds no access token to verify; denying the request.");
        deny(request, response, "Session holds no access token that could be verified", null);
        return;
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
        deny(request, response, "Session's access token is not authorized to access Optimize", e);
        return;
      } catch (final IdentityException e) {
        // Not Identity's verdict on the user: the token is unusable, or Identity is unreachable.
        // Keep the session, or an Identity outage logs out all users.
        LOG.debug("Session's access token could not be verified; denying the request.", e);
        deny(request, response, "Session's access token could not be verified", e);
        return;
      } catch (final RuntimeException e) {
        // Fail closed, but keep the session: this says nothing about the user's permission.
        LOG.warn("Unexpected error verifying session's access token; denying the request.", e);
        deny(request, response, "Session's access token could not be verified", e);
        return;
      }
    }
    filterChain.doFilter(request, response);
  }

  /**
   * @throws InsufficientAuthenticationException for an API request, for the chain's {@code
   *     AuthenticationEntryPoint} to turn into a 401
   */
  private static void deny(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final String reason,
      final Throwable cause)
      throws IOException {
    if (isApiRequest(request)) {
      throw new InsufficientAuthenticationException(reason, cause);
    }
    // OptimizeErrorController renders every 403 as the CCSM "no authorization" page.
    response.sendError(HttpStatus.FORBIDDEN.value());
  }

  /** A bearer-only request never holds an {@code OAuth2AuthenticationToken}. */
  private static boolean isSessionAuthenticated() {
    return SecurityContextHolder.getContext().getAuthentication()
        instanceof OAuth2AuthenticationToken;
  }

  /** A token that does not decode counts as not expired, so that Identity decides on it. */
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
