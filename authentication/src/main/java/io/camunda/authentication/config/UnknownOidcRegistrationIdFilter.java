/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import io.camunda.security.spring.oidc.LazyClientRegistrationRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Answers {@code /oauth2/authorization/{registrationId}} with {@code 404} when no client
 * registration with that id exists.
 *
 * <p>Without this filter, the authorization request resolver throws for an unknown id, and Spring
 * Security's {@code OAuth2AuthorizationRequestRedirectFilter} logs every such request at ERROR with
 * a stack trace and answers {@code 500}. Scanners probe this path with arbitrary ids, so this is a
 * client error, not a server error.
 */
@NullMarked
public final class UnknownOidcRegistrationIdFilter extends OncePerRequestFilter {

  private static final Logger LOG = LoggerFactory.getLogger(UnknownOidcRegistrationIdFilter.class);
  private static final String REGISTRATION_ID = "registrationId";

  private final RequestMatcher matcher =
      PathPatternRequestMatcher.withDefaults()
          .matcher("/oauth2/authorization/{" + REGISTRATION_ID + "}");
  private final ObjectProvider<ClientRegistrationRepository> clientRegistrationRepository;

  public UnknownOidcRegistrationIdFilter(
      final ObjectProvider<ClientRegistrationRepository> clientRegistrationRepository) {
    this.clientRegistrationRepository = clientRegistrationRepository;
  }

  @Override
  protected void doFilterInternal(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final FilterChain filterChain)
      throws ServletException, IOException {
    final var registrationId = registrationId(request);
    final var repository = clientRegistrationRepository.getIfAvailable();
    if (registrationId != null && repository != null && !isKnown(repository, registrationId)) {
      LOG.debug(
          "Rejecting OAuth2 authorization request for unknown client registration '{}'",
          registrationId);
      response.sendError(HttpStatus.NOT_FOUND.value());
      return;
    }
    filterChain.doFilter(request, response);
  }

  private @Nullable String registrationId(final HttpServletRequest request) {
    final var result = matcher.matcher(request);
    return result.isMatch() ? result.getVariables().get(REGISTRATION_ID) : null;
  }

  private static boolean isKnown(
      final ClientRegistrationRepository repository, final String registrationId) {
    // The lazy repository resolves the provider's discovery document on lookup; checking the
    // configured ids keeps this filter free of network calls.
    if (repository instanceof final LazyClientRegistrationRepository lazy) {
      return lazy.registrationIds().contains(registrationId);
    }
    return repository.findByRegistrationId(registrationId) != null;
  }
}
