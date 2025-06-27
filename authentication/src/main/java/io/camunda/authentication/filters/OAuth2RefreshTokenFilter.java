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
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.AbstractOAuth2Token;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.web.authentication.logout.CookieClearingLogoutHandler;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.web.filter.OncePerRequestFilter;

public class OAuth2RefreshTokenFilter extends OncePerRequestFilter {

  private static final Logger LOG = LoggerFactory.getLogger(OAuth2RefreshTokenFilter.class);

  private final Duration clockSkew = Duration.ofSeconds(60L);
  private final OAuth2AuthorizedClientService authorizedClientService;
  private final OAuth2AuthorizedClientManager authorizedClientManager;
  private final LogoutHandler logoutHandler;
  private final Supplier<SecurityContext> securityContextSupplier;

  public OAuth2RefreshTokenFilter(
      final OAuth2AuthorizedClientService authorizedClientService,
      final OAuth2AuthorizedClientManager authorizedClientManager) {
    this(
        authorizedClientService,
        authorizedClientManager,
        new CookieClearingLogoutHandler(WebSecurityConfig.SESSION_COOKIE),
        SecurityContextHolder::getContext);
  }

  // This constructor is for testing
  public OAuth2RefreshTokenFilter(
      final OAuth2AuthorizedClientService authorizedClientService,
      final OAuth2AuthorizedClientManager authorizedClientManager,
      final LogoutHandler logoutHandler,
      final Supplier<SecurityContext> securityContextSupplier) {
    this.authorizedClientService = authorizedClientService;
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
      logoutAndThrowAuthorizationException(
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
      logoutAndThrowAuthorizationException(
          request,
          response,
          authenticationToken,
          "access_token_expired",
          "Access token expired, refresh token does not exist");
    }

    OAuth2AuthorizedClient refreshedAuthorizedClient = null;
    try {
      // TODO: debug only REMOVE after testing
      LOG.warn("Requesting access token for URL {}", request.getRequestURI());
      refreshedAuthorizedClient = refreshAccessToken(authenticationToken, authorizedClient);
      // TODO: debug only REMOVE after testing
      LOG.warn(
          "Access token refreshed for user {}",
          refreshedAuthorizedClient.getAccessToken().getTokenValue());
    } catch (final OAuth2AuthorizationException e) {
      logoutAndThrowAuthorizationException(
          request, response, authenticationToken, "refresh_token_failed", e.getMessage());
    }

    if (refreshedAuthorizedClient == null || hasTokenExpired(refreshedAuthorizedClient)) {
      logoutAndThrowAuthorizationException(
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
    final var authentication = securityContextSupplier.get().getAuthentication();
    return authorizedClientService.loadAuthorizedClient(
        clientRegistrationId, authentication.getName());
  }

  protected boolean hasTokenExpired(final OAuth2AuthorizedClient authorizedClient) {
    final var tokenExpired =
        Optional.of(authorizedClient)
            .map(OAuth2AuthorizedClient::getAccessToken)
            .map(AbstractOAuth2Token::getExpiresAt)
            .map(this::isExpired)
            .orElse(false);

    if (tokenExpired) {
      LOG.debug("Token expired for {}", authorizedClient.getPrincipalName());
    }

    return tokenExpired;
  }

  protected boolean isExpired(final Instant expiresAt) {
    return Instant.now().plus(clockSkew).isAfter(expiresAt);
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

  private void logoutAndThrowAuthorizationException(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final OAuth2AuthenticationToken authenticationToken,
      final String reasonCode,
      final String reasonMessage) {
    authenticationToken.setAuthenticated(false);
    logoutHandler.logout(request, response, authenticationToken);
    throw new OAuth2AuthorizationException(new OAuth2Error(reasonCode), reasonMessage);
  }
}
