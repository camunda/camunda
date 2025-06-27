/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.oauth;

import io.camunda.search.clients.PersistentOAuth2AuthorizedClientsClient;
import io.camunda.search.entities.PersistentOAuth2AuthorizedClientEntity;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;

public class PersistedOAuth2AuthorizedClientService implements OAuth2AuthorizedClientService {

  private static final Logger LOG =
      LoggerFactory.getLogger(PersistedOAuth2AuthorizedClientService.class);

  private final PersistentOAuth2AuthorizedClientsClient authorizedClientsClient;
  private final ClientRegistrationRepository clientRegistrationRepository;

  public PersistedOAuth2AuthorizedClientService(
      final PersistentOAuth2AuthorizedClientsClient authorizedClientsClient,
      final ClientRegistrationRepository clientRegistrationRepository) {
    this.authorizedClientsClient = authorizedClientsClient;
    this.clientRegistrationRepository = clientRegistrationRepository;
  }

  @Override
  public <T extends OAuth2AuthorizedClient> T loadAuthorizedClient(
      final String clientRegistrationId, final String principalName) {
    LOG.debug(
        "Loading persistent authorized client for clientRegistrationId {}", clientRegistrationId);
    final ClientRegistration clientRegistration =
        clientRegistrationRepository.findByRegistrationId(clientRegistrationId);
    if (clientRegistration == null) {
      return null;
    }
    final PersistentOAuth2AuthorizedClientEntity clientData =
        authorizedClientsClient.loadAuthorizedClient(clientRegistrationId, principalName);
    return (T) convertToOAuth2AuthorizedClient(clientData, clientRegistration);
  }

  @Override
  public void saveAuthorizedClient(
      final OAuth2AuthorizedClient authorizedClient, final Authentication principal) {
    LOG.debug(
        "Saving authorized client for clientRegistrationId {}",
        authorizedClient.getClientRegistration().getRegistrationId());
    final PersistentOAuth2AuthorizedClientEntity clientData =
        convertToClientData(authorizedClient, principal.getName());
    authorizedClientsClient.saveAuthorizedClient(clientData, principal.getName());
  }

  @Override
  public void removeAuthorizedClient(
      final String clientRegistrationId, final String principalName) {
    LOG.debug("Removing authorized client for clientRegistrationId {}", clientRegistrationId);
    authorizedClientsClient.removeAuthorizedClient(clientRegistrationId, principalName);
  }

  private PersistentOAuth2AuthorizedClientEntity convertToClientData(
      final OAuth2AuthorizedClient authorizedClient, final String principalName) {
    final OAuth2AccessToken accessToken = authorizedClient.getAccessToken();
    final OAuth2RefreshToken refreshToken = authorizedClient.getRefreshToken();

    return new PersistentOAuth2AuthorizedClientEntity(
        getAuthorizedClientRecordId(
            authorizedClient.getClientRegistration().getRegistrationId(), principalName),
        authorizedClient.getClientRegistration().getRegistrationId(),
        principalName,
        accessToken.getTokenValue(),
        accessToken.getTokenType().getValue(),
        accessToken.getIssuedAt().atOffset(ZoneOffset.UTC),
        accessToken.getExpiresAt().atOffset(ZoneOffset.UTC),
        accessToken.getScopes(),
        refreshToken != null ? refreshToken.getTokenValue() : null,
        refreshToken != null ? refreshToken.getIssuedAt().atOffset(ZoneOffset.UTC) : null,
        refreshToken != null
            ? Optional.ofNullable(refreshToken.getExpiresAt())
                .map(rtea -> rtea.atOffset(ZoneOffset.UTC))
                .orElse(null)
            : null);
  }

  private OAuth2AuthorizedClient convertToOAuth2AuthorizedClient(
      final PersistentOAuth2AuthorizedClientEntity clientData,
      final ClientRegistration clientRegistration) {
    if (clientData == null) {
      return null;
    }

    final OAuth2AccessToken.TokenType tokenType;
    if (OAuth2AccessToken.TokenType.BEARER
        .getValue()
        .equalsIgnoreCase(clientData.accessTokenType())) {
      tokenType = OAuth2AccessToken.TokenType.BEARER;
    } else {
      throw new RuntimeException("Unsupported token type: " + clientData.accessTokenType());
    }

    final OAuth2AccessToken accessToken =
        new OAuth2AccessToken(
            tokenType,
            clientData.accessTokenValue(),
            clientData.accessTokenIssuedAt().toInstant(),
            clientData.accessTokenExpiresAt().toInstant(),
            clientData.accessTokenScopes());

    OAuth2RefreshToken refreshToken = null;
    if (clientData.refreshTokenValue() != null) {
      refreshToken =
          new OAuth2RefreshToken(
              clientData.refreshTokenValue(),
              clientData.refreshTokenIssuedAt().toInstant(),
              Optional.ofNullable(clientData.refreshTokenExpiresAt())
                  .map(OffsetDateTime::toInstant)
                  .orElse(null));
    }

    return new OAuth2AuthorizedClient(
        clientRegistration, clientData.principalName(), accessToken, refreshToken);
  }

  private String getAuthorizedClientRecordId(
      final String clientRegistrationId, final String principalName) {
    return clientRegistrationId + ":" + principalName;
  }
}
