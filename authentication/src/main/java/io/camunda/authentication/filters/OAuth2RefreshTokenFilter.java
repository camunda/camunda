/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.filters;

import io.camunda.authentication.config.WebSecurityConfig;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.web.authentication.logout.CookieClearingLogoutHandler;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.web.filter.OncePerRequestFilter;

public class OAuth2RefreshTokenFilter extends OncePerRequestFilter {

  private static final Logger LOG = LoggerFactory.getLogger(OAuth2RefreshTokenFilter.class);

  private final Duration clockSkew = Duration.ofSeconds(60L);
  // Double clock skew is used to avoid falling into perpetual refresh loop
  private final Duration doubleClockSkew = clockSkew.multipliedBy(2);
  private final OAuth2AuthorizedClientRepository authorizedClientRepository;
  private final OAuth2AuthorizedClientManager authorizedClientManager;
  private final LogoutHandler logoutHandler;
  private final Supplier<SecurityContext> securityContextSupplier;

  public OAuth2RefreshTokenFilter(
      final OAuth2AuthorizedClientRepository authorizedClientRepository,
      final OAuth2AuthorizedClientManager authorizedClientManager) {
    this(
        authorizedClientRepository,
        authorizedClientManager,
        new CookieClearingLogoutHandler(WebSecurityConfig.SESSION_COOKIE),
        SecurityContextHolder::getContext);
  }

  // This constructor is for testing
  public OAuth2RefreshTokenFilter(
      final OAuth2AuthorizedClientRepository authorizedClientRepository,
      final OAuth2AuthorizedClientManager authorizedClientManager,
      final LogoutHandler logoutHandler,
      final Supplier<SecurityContext> securityContextSupplier) {
    this.authorizedClientRepository = authorizedClientRepository;
    this.authorizedClientManager = authorizedClientManager;
    this.logoutHandler = logoutHandler;
    this.securityContextSupplier = securityContextSupplier;
  }

  @Override
  protected void doFilterInternal(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final FilterChain filterChain)
      throws ServletException, IOException {

    final var authenticationToken = getAuthenticationToken();

    // Continue if it's not OAuth2 authentication
    if (authenticationToken == null) {
      LOG.debug("No OAuth2 authentication found in request");
      filterChain.doFilter(request, response);
      return;
    }

    final var authorizedClient = getAuthorizedClient(authenticationToken, request);
    if (authorizedClient == null) {
      logoutAndThrowAuthenticationException(
          request,
          response,
          authenticationToken,
          "unauthorized_client",
          "No client could be authorized");
    }

    if (!hasTokenExpired(authorizedClient)) {
      filterChain.doFilter(request, response);
      return;
    }

    if (!existsRefreshToken(authorizedClient)) {
      logoutAndThrowAuthenticationException(
          request,
          response,
          authenticationToken,
          "access_token_expired",
          "Access token expired, refresh token does not exist");
    }

    OAuth2AuthorizedClient refreshedAuthorizedClient = null;
    try {
      refreshedAuthorizedClient = refreshAccessToken(authenticationToken, authorizedClient);

      if (refreshedAuthorizedClient == null) {
        throw new OAuth2AuthenticationException("Failed to refresh access token");
      }

      // If the refreshed client is the same as the original one, it means
      // re-authorization is not supported for the client OR is not required,
      // e.g. a refresh token is not available OR the access token is not expired.
      // @see OAuth2AuthorizedClientManager#authorize(OAuth2AuthorizeRequest)
      if (refreshedAuthorizedClient == authorizedClient) {
        throw new OAuth2AuthenticationException(
            "Re-authorization is not supported or not required for the client");
      }

    } catch (final OAuth2AuthenticationException e) {
      logoutAndThrowAuthenticationException(
          request, response, authenticationToken, "refresh_token_failed", e);
    }

    if (refreshedAuthorizedClient == null || hasTokenExpired(refreshedAuthorizedClient)) {
      logoutAndThrowAuthenticationException(
          request, response, authenticationToken, "access_token_expired", "Access token expired");
    }

    filterChain.doFilter(request, response);
  }

  protected OAuth2AuthenticationToken getAuthenticationToken() {
    final var authentication = securityContextSupplier.get().getAuthentication();
    return authentication instanceof final OAuth2AuthenticationToken oauth2AuthenticationToken
        ? oauth2AuthenticationToken
        : null;
  }

  protected OAuth2AuthorizedClient getAuthorizedClient(
      final OAuth2AuthenticationToken authenticationToken, final HttpServletRequest request) {
    final var clientRegistrationId = authenticationToken.getAuthorizedClientRegistrationId();
    return authorizedClientRepository.loadAuthorizedClient(
        clientRegistrationId, authenticationToken, request);
  }

  protected boolean hasTokenExpired(final OAuth2AuthorizedClient authorizedClient) {
    final var tokenExpired =
        Optional.of(authorizedClient)
            .map(OAuth2AuthorizedClient::getAccessToken)
            .map(this::isExpired)
            .orElse(false);

    if (tokenExpired) {
      LOG.debug("Token expired for {}", authorizedClient.getPrincipalName());
    }

    return tokenExpired;
  }

  protected boolean isExpired(final OAuth2AccessToken token) {
    if (token.getExpiresAt() == null) {
      return false; // Token does not expire
    }

    if (applyClockSkewToExpirationCheck(token)) {
      return Instant.now().plus(clockSkew).isAfter(token.getExpiresAt());
    }

    return Instant.now().isAfter(token.getExpiresAt());
  }

  private boolean applyClockSkewToExpirationCheck(final OAuth2AccessToken token) {
    if (token.getIssuedAt() == null || token.getExpiresAt() == null) {
      return false;
    }

    // If token issuance duration is too short, we do not apply clock skew
    // to avoid falling into perpetual refresh loop
    final var tokenDuration = Duration.between(token.getIssuedAt(), token.getExpiresAt()).abs();
    return tokenDuration.minus(doubleClockSkew).isPositive();
  }

  protected boolean existsRefreshToken(final OAuth2AuthorizedClient authorizedClient) {
    return authorizedClient.getRefreshToken() != null;
  }

  protected OAuth2AuthorizedClient refreshAccessToken(
      final OAuth2AuthenticationToken authenticationToken,
      final OAuth2AuthorizedClient authorizedClient) {
    final var authorizeRequest =
        OAuth2AuthorizeRequest.withAuthorizedClient(authorizedClient)
            .principal(authenticationToken)
            .build();
    return authorizedClientManager.authorize(authorizeRequest);
  }

  private void logoutAndThrowAuthenticationException(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final OAuth2AuthenticationToken authenticationToken,
      final String reasonCode,
      final String reasonMessage) {
    forceLogout(authenticationToken, request, response);
    throw new OAuth2AuthenticationException(new OAuth2Error(reasonCode), reasonMessage);
  }

  private void logoutAndThrowAuthenticationException(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final OAuth2AuthenticationToken authenticationToken,
      final String reasonCode,
      final Throwable e) {
    forceLogout(authenticationToken, request, response);
    throw new OAuth2AuthenticationException(new OAuth2Error(reasonCode), e);
  }

  private void forceLogout(
      final OAuth2AuthenticationToken authenticationToken,
      final HttpServletRequest request,
      final HttpServletResponse response) {
    authenticationToken.setAuthenticated(false);
    logoutHandler.logout(request, response, authenticationToken);
  }
}
