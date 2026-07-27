/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config.controllers;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;

/**
 * In-memory {@link OAuth2AuthorizedClientRepository} test double for MockMvc OIDC tests.
 *
 * <p>CSL's default {@link HttpSessionOAuth2AuthorizedClientRepository} stores the authorized client
 * on the {@code HttpSession}. {@link OidcMockMvcTestHelper#oidcLogin} saves the client on the raw,
 * pre-dispatch {@code MockHttpServletRequest} handed to request post-processors — a different
 * object from the Spring-Session-backed session that CSL's {@code SessionRepositoryFilter} wrapper
 * resolves once the request is actually dispatched (ADR-0031), so the session-backed repository
 * never sees the saved client and {@code OAuth2RefreshTokenFilter} forces a logout. This repository
 * sidesteps that mismatch by keying storage on the client registration id and principal name
 * instead of the request/session.
 */
public final class InMemoryTestOAuth2AuthorizedClientRepository
    implements OAuth2AuthorizedClientRepository {

  private final Map<String, OAuth2AuthorizedClient> clients = new ConcurrentHashMap<>();

  @Override
  @SuppressWarnings("unchecked")
  public <T extends OAuth2AuthorizedClient> T loadAuthorizedClient(
      final String clientRegistrationId,
      final Authentication principal,
      final HttpServletRequest request) {
    return (T) clients.get(key(clientRegistrationId, principal.getName()));
  }

  @Override
  public void saveAuthorizedClient(
      final OAuth2AuthorizedClient authorizedClient,
      final Authentication principal,
      final HttpServletRequest request,
      final HttpServletResponse response) {
    clients.put(
        key(
            authorizedClient.getClientRegistration().getRegistrationId(),
            authorizedClient.getPrincipalName()),
        authorizedClient);
  }

  @Override
  public void removeAuthorizedClient(
      final String clientRegistrationId,
      final Authentication principal,
      final HttpServletRequest request,
      final HttpServletResponse response) {
    clients.remove(key(clientRegistrationId, principal.getName()));
  }

  private static String key(final String clientRegistrationId, final String principalName) {
    return clientRegistrationId + ":" + principalName;
  }
}
