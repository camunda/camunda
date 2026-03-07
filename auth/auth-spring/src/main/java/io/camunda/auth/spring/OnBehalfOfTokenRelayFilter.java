/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.spring;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Spring Security filter that performs OBO token exchange for downstream service calls using Spring
 * Security's built-in {@link OAuth2AuthorizedClientManager}. This filter composes additively with
 * existing filter chains.
 *
 * <p>When enabled, it stores the exchanged OBO token as a request attribute so downstream
 * components can retrieve it.
 */
public class OnBehalfOfTokenRelayFilter extends OncePerRequestFilter {

  private static final Logger LOG = LoggerFactory.getLogger(OnBehalfOfTokenRelayFilter.class);

  public static final String OBO_TOKEN_ATTRIBUTE = "camunda.auth.obo.token";

  private final OAuth2AuthorizedClientManager authorizedClientManager;
  private final String clientRegistrationId;

  public OnBehalfOfTokenRelayFilter(
      final OAuth2AuthorizedClientManager authorizedClientManager,
      final String clientRegistrationId) {
    this.authorizedClientManager = authorizedClientManager;
    this.clientRegistrationId = clientRegistrationId;
  }

  @Override
  protected void doFilterInternal(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final FilterChain filterChain)
      throws ServletException, IOException {

    final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

    if (authentication instanceof JwtAuthenticationToken jwtAuth) {
      try {
        final OAuth2AuthorizeRequest authorizeRequest =
            OAuth2AuthorizeRequest.withClientRegistrationId(clientRegistrationId)
                .principal(authentication)
                .build();

        final OAuth2AuthorizedClient authorizedClient =
            authorizedClientManager.authorize(authorizeRequest);

        if (authorizedClient != null && authorizedClient.getAccessToken() != null) {
          request.setAttribute(
              OBO_TOKEN_ATTRIBUTE, authorizedClient.getAccessToken().getTokenValue());
          LOG.debug("OBO token exchange successful for registration={}", clientRegistrationId);
        }
      } catch (final Exception e) {
        LOG.warn(
            "OBO token exchange failed for registration={}: {}",
            clientRegistrationId,
            e.getMessage());
      }
    }

    filterChain.doFilter(request, response);
  }

  @Override
  protected boolean shouldNotFilter(final HttpServletRequest request) {
    final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    return !(authentication instanceof JwtAuthenticationToken);
  }
}
