/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.oauth;

import io.camunda.search.clients.PersistentOAuth2AuthorizedClientsClient;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;

public class PersistedCamundaOAuth2AuthorizedClientService
    implements OAuth2AuthorizedClientService {

  private final PersistentOAuth2AuthorizedClientsClient authorizedClientsClient;
  private final PersistedCamundaOAuth2AuthorizedClientMapper authorizedClientMapper;

  public PersistedCamundaOAuth2AuthorizedClientService(
      final PersistentOAuth2AuthorizedClientsClient authorizedClientsClient,
      final PersistedCamundaOAuth2AuthorizedClientMapper authorizedClientMapper) {
    this.authorizedClientsClient = authorizedClientsClient;
    this.authorizedClientMapper = authorizedClientMapper;
  }

  @Override
  public <T extends OAuth2AuthorizedClient> T loadAuthorizedClient(
      final String clientRegistrationId, final String principalName) {
    final var storedClientEntity =
        authorizedClientsClient.loadAuthorizedClient(clientRegistrationId, principalName);
    return authorizedClientMapper.toOAuth2AuthorizedClient(storedClientEntity);
  }

  @Override
  public void saveAuthorizedClient(
      final OAuth2AuthorizedClient authorizedClient, final Authentication principal) {
    final var clientEntityToStore =
        authorizedClientMapper.toPersistentOAuth2AuthorizedClientEntity(authorizedClient);
    authorizedClientsClient.saveAuthorizedClient(clientEntityToStore, principal.getName());
  }

  @Override
  public void removeAuthorizedClient(
      final String clientRegistrationId, final String principalName) {
    authorizedClientsClient.removeAuthorizedClient(clientRegistrationId, principalName);
  }
}
