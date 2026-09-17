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
 * Enforces the Identity {@code write:*} (OPTIMIZE_PERMISSION) check for session-authenticated CCSM
 * users by re-verifying the session's stored access token, and invalidates the session once
 * Identity revokes the permission. CSL does not cover this path: it validates issuer, signature and
 * expiry only, and {@code OidcUserAuthenticationConverter#decodeAccessToken} even swallows a
 * validation failure and falls back to the id_token's claims, so such a session keeps working
 * indefinitely.
 *
 * <p>Applies only to session logins, which {@link
 * CCSMTokenService#getSessionAccessToken(HttpServletRequest)} scopes by resolving a token just for
 * an {@code OAuth2AuthenticationToken} context. A bearer request holds none and passes through, the
 * same as on legacy CCSM, which authenticated API tokens on signature and audience alone.
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
 * everybody out.
 *
 * <p>An API request is denied by raising {@link InsufficientAuthenticationException}, which the
 * chain's {@link OptimizeOidcAuthenticationEntryPoint} answers with a 401. A webapp request is
 * denied with a terminal 403 instead, rendered by {@code OptimizeErrorController}. Raising the
 * exception there would restart the OIDC login, and since the IdP's own session is still valid it
 * re-issues a code immediately and the user loops between Optimize and the IdP forever. The 403
 * page is also what the CCSM stack without CSL answers with.
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
        // Not a decision Identity made about this user: the token is unusable (invalid, expired
        // past what CSL's refresh could recover) or Identity is unreachable. Denying the request
        // is required, but destroying the session would log every user out on an Identity outage
        // and force a fresh login instead of letting the next request succeed.
        LOG.debug("Session's access token could not be verified; denying the request.", e);
        deny(request, response, "Session's access token could not be verified", e);
        return;
      } catch (final RuntimeException e) {
        // Fail closed: an unexpected error verifying the session's token must not propagate as an
        // uncaught 500, it must deny the request. Like the IdentityException case it says nothing
        // about the user's permission, so the session survives.
        LOG.warn("Unexpected error verifying session's access token; denying the request.", e);
        deny(request, response, "Session's access token could not be verified", e);
        return;
      }
    }
    filterChain.doFilter(request, response);
  }

  /**
   * Ends the request as denied: 401 on the API surface, a terminal 403 page on the webapp surface.
   *
   * @throws InsufficientAuthenticationException for an API request, for the chain's {@code
   *     AuthenticationEntryPoint} to shape
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
    // The message belongs to OptimizeErrorController, which renders every 403 as the CCSM
    // "no authorization to access Optimize" page.
    response.sendError(HttpStatus.FORBIDDEN.value());
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
