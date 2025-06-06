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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.AbstractOAuth2Token;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.web.authentication.logout.CookieClearingLogoutHandler;
import org.springframework.web.filter.OncePerRequestFilter;

public class OAuth2RefreshTokenFilter extends OncePerRequestFilter {

  private final Duration clockSkew = Duration.ofSeconds(60L);
  private final OAuth2AuthorizedClientService authorizedClientService;
  private final OAuth2AuthorizedClientManager authorizedClientManager;
  private final CookieClearingLogoutHandler logoutHandler;

  public OAuth2RefreshTokenFilter(
      final OAuth2AuthorizedClientService authorizedClientService,
      final OAuth2AuthorizedClientManager authorizedClientManager) {
    this.authorizedClientService = authorizedClientService;
    this.authorizedClientManager = authorizedClientManager;
    logoutHandler = new CookieClearingLogoutHandler(WebSecurityConfig.SESSION_COOKIE);
  }

  @Override
  protected void doFilterInternal(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final FilterChain filterChain)
      throws ServletException, IOException {

    final var authenticationToken = getAuthenticationToken();
    if (authenticationToken == null) {
      filterChain.doFilter(request, response);
      return;
    }

    final var authorizedClient = getAuthorizedClient(authenticationToken);
    if (authorizedClient == null) {
      authenticationToken.setAuthenticated(false);
      logoutHandler.logout(request, response, authenticationToken);
      throw new OAuth2AuthorizationException(
          new OAuth2Error("unauthorized_client"), "No client could be authorized");
    }

    if (!hasTokenExpired(authorizedClient)) {
      filterChain.doFilter(request, response);
      return;
    }

    if (!existsRefreshToken(authorizedClient)) {
      authenticationToken.setAuthenticated(false);
      logoutHandler.logout(request, response, authenticationToken);
      throw new OAuth2AuthenticationException(
          new OAuth2Error("access_token_expired"),
          "Access token expired, refresh token does not exist");
    }

    final OAuth2AuthorizedClient refreshedAuthorizedClient;
    try {
      refreshedAuthorizedClient = refreshAccessToken(authenticationToken, authorizedClient);
    } catch (final OAuth2AuthorizationException e) {
      authenticationToken.setAuthenticated(false);
      logoutHandler.logout(request, response, authenticationToken);
      throw new OAuth2AuthenticationException(new OAuth2Error("refresh_token_failed"), e);
    }

    if (refreshedAuthorizedClient == null || hasTokenExpired(refreshedAuthorizedClient)) {
      authenticationToken.setAuthenticated(false);
      logoutHandler.logout(request, response, authenticationToken);
      throw new OAuth2AuthenticationException(
          new OAuth2Error("access_token_expired"), "Access token expired");
    }

    filterChain.doFilter(request, response);
  }

  protected OAuth2AuthenticationToken getAuthenticationToken() {
    final var authentication = SecurityContextHolder.getContext().getAuthentication();
    return authentication instanceof final OAuth2AuthenticationToken oauth2AuthenticationToken
        ? oauth2AuthenticationToken
        : null;
  }

  protected OAuth2AuthorizedClient getAuthorizedClient(
      final OAuth2AuthenticationToken authenticationToken) {
    final var clientRegistrationId = authenticationToken.getAuthorizedClientRegistrationId();
    final var principalName = authenticationToken.getName();
    return authorizedClientService.loadAuthorizedClient(clientRegistrationId, principalName);
  }

  protected boolean hasTokenExpired(final OAuth2AuthorizedClient authorizedClient) {
    return Optional.of(authorizedClient)
        .map(OAuth2AuthorizedClient::getAccessToken)
        .map(AbstractOAuth2Token::getExpiresAt)
        .map(this::isExpired)
        .orElse(false);
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
    // TODO: do we need to pass any attributes?
    final var authorizeRequest =
        OAuth2AuthorizeRequest.withAuthorizedClient(authorizedClient)
            .principal(authenticationToken)
            .build();
    return authorizedClientManager.authorize(authorizeRequest);
  }
}
