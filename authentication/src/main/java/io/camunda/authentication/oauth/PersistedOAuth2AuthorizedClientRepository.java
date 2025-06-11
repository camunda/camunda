/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.oauth;

import io.camunda.search.clients.PersistentOAuth2AuthorizedClientsClient;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;

public class PersistedOAuth2AuthorizedClientRepository implements OAuth2AuthorizedClientRepository {

  private final PersistentOAuth2AuthorizedClientsClient authorizedClientsClient;

  @Override
  public <T extends OAuth2AuthorizedClient> T loadAuthorizedClient(
      final String clientRegistrationId, final Authentication principal,
      final HttpServletRequest request) {
    return null;
  }

  @Override
  public void saveAuthorizedClient(final OAuth2AuthorizedClient authorizedClient,
      final Authentication principal, final HttpServletRequest request,
      final HttpServletResponse response) {

  }

  @Override
  public void removeAuthorizedClient(final String clientRegistrationId,
      final Authentication principal, final HttpServletRequest request,
      final HttpServletResponse response) {

  }
}
