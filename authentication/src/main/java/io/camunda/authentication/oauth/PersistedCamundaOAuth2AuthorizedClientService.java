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
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.util.Assert;

/**
 * Elasticsearch-based implementation of {@link OAuth2AuthorizedClientService} that persists
 * authorized clients directly to Elasticsearch.
 *
 * <p>This implementation does not use in-memory caching and performs direct operations against
 * Elasticsearch for each request.
 */
public class PersistedCamundaOAuth2AuthorizedClientService
    implements OAuth2AuthorizedClientService {

  private final ClientRegistrationRepository clientRegistrationRepository;
  private final OAuth2AuthorizedClientRepository oAuth2AuthorizedClientRepository;

  //  private final RestHighLevelClient elasticsearchClient;
  //  private final ObjectMapper objectMapper;
  //  private final String indexName;

  /**
   * Constructs a new ElasticsearchOAuth2AuthorizedClientService.
   *
   * @param clientRegistrationRepository the client registration repository
   */
  public PersistedCamundaOAuth2AuthorizedClientService(
      final ClientRegistrationRepository clientRegistrationRepository,
      final OAuth2AuthorizedClientRepository oAuth2AuthorizedClientRepository) {
    Assert.notNull(clientRegistrationRepository, "clientRegistrationRepository cannot be null");
    this.clientRegistrationRepository = clientRegistrationRepository;
    this.oAuth2AuthorizedClientRepository = oAuth2AuthorizedClientRepository;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T extends OAuth2AuthorizedClient> T loadAuthorizedClient(
      final String clientRegistrationId, final String principalName) {
    Assert.hasText(clientRegistrationId, "clientRegistrationId cannot be null or empty");
    Assert.hasText(principalName, "principalName cannot be null or empty");

    System.out.println("loadAuthorizedClient");

    final ClientRegistration clientRegistration =
        clientRegistrationRepository.findByRegistrationId(clientRegistrationId);
    if (clientRegistration == null) {
      return null;
    }
    final PersistentOAuth2AuthorizedClientEntity clientData =
        oAuth2AuthorizedClientRepository.loadAuthorizedClient(clientRegistrationId, principalName);
    return (T) convertToOAuth2AuthorizedClient(clientData, clientRegistration);

    //    try {
    //      final String documentId = generateDocumentId(clientRegistrationId, principalName);
    //      final GetRequest getRequest = new GetRequest(indexName, documentId);
    //      final GetResponse getResponse = elasticsearchClient.get(getRequest,
    // RequestOptions.DEFAULT);
    //
    //      if (!getResponse.isExists()) {
    //        return null;
    //      }
    //
    //      final ClientRegistration clientRegistration =
    // clientRegistrationRepository.findByRegistrationId(clientRegistrationId);
    //      if (clientRegistration == null) {
    //        return null;
    //      }
    //
    ////      final Map<String, Object> sourceMap = getResponse.getSourceAsMap();
    ////      final PersistentOAuth2AuthorizedClientEntity clientData =
    // objectMapper.convertValue(sourceMap, PersistentOAuth2AuthorizedClientEntity.class);
    //      final PersistentOAuth2AuthorizedClientEntity clientData =
    // authorizedClientsClient.loadAuthorizedClient(clientRegistrationId, principalName);

    //      return (T) convertToOAuth2AuthorizedClient(clientData, clientRegistration);
    //    } catch (final IOException e) {
    //      throw new RuntimeException("Failed to load authorized client from Elasticsearch", e);
    //    }
  }

  @Override
  public void saveAuthorizedClient(
      final OAuth2AuthorizedClient authorizedClient, final Authentication principal) {
    Assert.notNull(authorizedClient, "authorizedClient cannot be null");
    Assert.notNull(principal, "principal cannot be null");

    System.out.println("saveAuthorizedClient");

    final PersistentOAuth2AuthorizedClientEntity clientData =
        convertToClientData(authorizedClient, principal.getName());
    authorizedClientsClient.saveAuthorizedClient(clientData, principal.getName());

    //    try {
    //      final String documentId = generateDocumentId(
    //          authorizedClient.getClientRegistration().getRegistrationId(),
    //          principal.getName()
    //      );
    //
    //      final PersistentOAuth2AuthorizedClientEntity clientData =
    // convertToClientData(authorizedClient, principal.getName());
    //
    //      final IndexRequest indexRequest = new IndexRequest(indexName)
    //          .id(documentId)
    //          .source(objectMapper.writeValueAsString(clientData), XContentType.JSON);
    //
    //      elasticsearchClient.index(indexRequest, RequestOptions.DEFAULT);
    //    } catch (final IOException e) {
    //      throw new RuntimeException("Failed to save authorized client to Elasticsearch", e);
    //    }
  }

  @Override
  public void removeAuthorizedClient(
      final String clientRegistrationId, final String principalName) {
    Assert.hasText(clientRegistrationId, "clientRegistrationId cannot be null or empty");
    Assert.hasText(principalName, "principalName cannot be null or empty");

    System.out.println("removeAuthorizedClient");

    authorizedClientsClient.removeAuthorizedClient(clientRegistrationId, principalName);

    //    try {
    //      final String documentId = generateDocumentId(clientRegistrationId, principalName);
    //      final DeleteRequest deleteRequest = new DeleteRequest(indexName, documentId);
    //      elasticsearchClient.delete(deleteRequest, RequestOptions.DEFAULT);
    //    } catch (final IOException e) {
    //      throw new RuntimeException("Failed to remove authorized client from Elasticsearch", e);
    //    }
  }

  //  private String generateDocumentId(final String clientRegistrationId, final String
  // principalName) {
  //    return clientRegistrationId + ":" + principalName;
  //  }

  private PersistentOAuth2AuthorizedClientEntity convertToClientData(
      final OAuth2AuthorizedClient authorizedClient, final String principalName) {
    final OAuth2AccessToken accessToken = authorizedClient.getAccessToken();
    final OAuth2RefreshToken refreshToken = authorizedClient.getRefreshToken();

    return new PersistentOAuth2AuthorizedClientEntity(
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

    OAuth2AccessToken.TokenType tokenType = OAuth2AccessToken.TokenType.BEARER;
    if ("bearer".equalsIgnoreCase(clientData.accessTokenType())) {
      tokenType = OAuth2AccessToken.TokenType.BEARER;
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
}
