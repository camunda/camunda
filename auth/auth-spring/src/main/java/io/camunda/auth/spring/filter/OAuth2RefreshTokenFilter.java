/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.spring.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Filter that handles OAuth2 refresh token flow for expiring access tokens. Monitors token
 * expiration with clock skew and logs out user if refresh fails.
 */
public class OAuth2RefreshTokenFilter extends OncePerRequestFilter {

  private static final Logger LOG = LoggerFactory.getLogger(OAuth2RefreshTokenFilter.class);
  private static final Duration DEFAULT_CLOCK_SKEW = Duration.ofSeconds(60);

  private final OAuth2AuthorizedClientRepository authorizedClientRepository;
  private final OAuth2AuthorizedClientManager authorizedClientManager;
  private final Duration clockSkew;
  private final Clock clock;

  public OAuth2RefreshTokenFilter(
      final OAuth2AuthorizedClientRepository authorizedClientRepository,
      final OAuth2AuthorizedClientManager authorizedClientManager) {
    this(
        authorizedClientRepository, authorizedClientManager, DEFAULT_CLOCK_SKEW, Clock.systemUTC());
  }

  public OAuth2RefreshTokenFilter(
      final OAuth2AuthorizedClientRepository authorizedClientRepository,
      final OAuth2AuthorizedClientManager authorizedClientManager,
      final Duration clockSkew,
      final Clock clock) {
    this.authorizedClientRepository = authorizedClientRepository;
    this.authorizedClientManager = authorizedClientManager;
    this.clockSkew = clockSkew;
    this.clock = clock;
  }

  @Override
  protected void doFilterInternal(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final FilterChain filterChain)
      throws ServletException, IOException {

    final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

    if (authentication instanceof final OAuth2AuthenticationToken oauthToken) {
      final String registrationId = oauthToken.getAuthorizedClientRegistrationId();
      final OAuth2AuthorizedClient authorizedClient =
          authorizedClientRepository.loadAuthorizedClient(registrationId, oauthToken, request);

      if (authorizedClient != null && isTokenExpired(authorizedClient.getAccessToken())) {
        LOG.debug("Access token expired for registration '{}', attempting refresh", registrationId);

        try {
          final var refreshRequest =
              org.springframework.security.oauth2.client.OAuth2AuthorizeRequest
                  .withClientRegistrationId(registrationId)
                  .principal(oauthToken)
                  .attribute(HttpServletRequest.class.getName(), request)
                  .attribute(HttpServletResponse.class.getName(), response)
                  .build();

          final OAuth2AuthorizedClient refreshedClient =
              authorizedClientManager.authorize(refreshRequest);

          if (refreshedClient != null) {
            authorizedClientRepository.saveAuthorizedClient(
                refreshedClient, oauthToken, request, response);
            LOG.debug("Access token refreshed for registration '{}'", registrationId);
          } else {
            LOG.warn(
                "Token refresh returned null for registration '{}', logging out", registrationId);
            logout(request, response, authentication);
            return;
          }
        } catch (final Exception e) {
          LOG.warn(
              "Token refresh failed for registration '{}': {}. Logging out.",
              registrationId,
              e.getMessage());
          logout(request, response, authentication);
          return;
        }
      }
    }

    filterChain.doFilter(request, response);
  }

  private boolean isTokenExpired(final OAuth2AccessToken accessToken) {
    if (accessToken == null || accessToken.getExpiresAt() == null) {
      return false;
    }
    return Instant.now(clock).isAfter(accessToken.getExpiresAt().minus(clockSkew));
  }

  private void logout(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final Authentication authentication) {
    final var logoutHandler = new SecurityContextLogoutHandler();
    logoutHandler.logout(request, response, authentication);
  }
}
