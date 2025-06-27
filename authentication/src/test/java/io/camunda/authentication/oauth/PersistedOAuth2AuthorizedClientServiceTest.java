/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.search.clients.PersistentOAuth2AuthorizedClientsClient;
import io.camunda.search.entities.PersistentOAuth2AuthorizedClientEntity;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mock.Strictness;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;

@ExtendWith(MockitoExtension.class)
class PersistedOAuth2AuthorizedClientServiceTest {

  public static final String BEARER_TOKEN_TYPE = "bearer";
  private static final String CLIENT_REGISTRATION_ID = "test-client";
  private static final String PRINCIPAL_NAME = "test-user";
  private static final String ACCESS_TOKEN_VALUE = "access-token-123";
  private static final String REFRESH_TOKEN_VALUE = "refresh-token-456";
  private static final String EXPECTED_RECORD_ID = CLIENT_REGISTRATION_ID + ":" + PRINCIPAL_NAME;

  @Mock(strictness = Strictness.LENIENT)
  private PersistentOAuth2AuthorizedClientsClient authorizedClientsClient;

  @Mock(strictness = Strictness.LENIENT)
  private ClientRegistrationRepository clientRegistrationRepository;

  @Mock(strictness = Strictness.LENIENT)
  private Authentication authentication;

  @Captor private ArgumentCaptor<PersistentOAuth2AuthorizedClientEntity> entityCaptor;

  private PersistedOAuth2AuthorizedClientService service;
  private ClientRegistration clientRegistration;
  private Instant accessTokenIssuedAt;
  private Instant accessTokenExpiresAt;
  private Instant refreshTokenIssuedAt;
  private Instant refreshTokenExpiresAt;

  @BeforeEach
  void setUp() {
    service =
        new PersistedOAuth2AuthorizedClientService(
            authorizedClientsClient, clientRegistrationRepository);

    accessTokenIssuedAt = Instant.now().minusSeconds(3600);
    accessTokenExpiresAt = Instant.now().plusSeconds(3600);
    refreshTokenIssuedAt = Instant.now().minusSeconds(3600);
    refreshTokenExpiresAt = Instant.now().plusSeconds(86400);

    clientRegistration =
        ClientRegistration.withRegistrationId(CLIENT_REGISTRATION_ID)
            .clientId("client-id")
            .clientSecret("client-secret")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("http://localhost:8080/sso-callback")
            .authorizationUri("http://localhost:18080/oauth/authorize")
            .tokenUri("http://localhost:18080/oauth/token")
            .build();

    when(authentication.getName()).thenReturn(PRINCIPAL_NAME);
  }

  @Test
  void shouldLoadAuthorizedClientSuccessfully() {
    // Given
    when(clientRegistrationRepository.findByRegistrationId(CLIENT_REGISTRATION_ID))
        .thenReturn(clientRegistration);

    final PersistentOAuth2AuthorizedClientEntity entity = createTestEntity();
    when(authorizedClientsClient.loadAuthorizedClient(CLIENT_REGISTRATION_ID, PRINCIPAL_NAME))
        .thenReturn(entity);

    // When
    final OAuth2AuthorizedClient result =
        service.loadAuthorizedClient(CLIENT_REGISTRATION_ID, PRINCIPAL_NAME);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getClientRegistration()).isEqualTo(clientRegistration);
    assertThat(result.getPrincipalName()).isEqualTo(PRINCIPAL_NAME);

    final OAuth2AccessToken accessToken = result.getAccessToken();
    assertThat(accessToken.getTokenValue()).isEqualTo(ACCESS_TOKEN_VALUE);
    assertThat(accessToken.getTokenType()).isEqualTo(OAuth2AccessToken.TokenType.BEARER);
    assertThat(accessToken.getIssuedAt()).isEqualTo(accessTokenIssuedAt);
    assertThat(accessToken.getExpiresAt()).isEqualTo(accessTokenExpiresAt);
    assertThat(accessToken.getScopes()).containsExactlyInAnyOrder("read", "write");

    final OAuth2RefreshToken refreshToken = result.getRefreshToken();
    assertThat(refreshToken).isNotNull();
    assertThat(refreshToken.getTokenValue()).isEqualTo(REFRESH_TOKEN_VALUE);
    assertThat(refreshToken.getIssuedAt()).isEqualTo(refreshTokenIssuedAt);
    assertThat(refreshToken.getExpiresAt()).isEqualTo(refreshTokenExpiresAt);
  }

  @Test
  void shouldReturnNullWhenClientRegistrationNotFound() {
    // Given
    when(clientRegistrationRepository.findByRegistrationId(CLIENT_REGISTRATION_ID))
        .thenReturn(null);

    // When
    final OAuth2AuthorizedClient result =
        service.loadAuthorizedClient(CLIENT_REGISTRATION_ID, PRINCIPAL_NAME);

    // Then
    assertThat(result).isNull();
    verify(authorizedClientsClient, never()).loadAuthorizedClient(any(), any());
  }

  @Test
  void shouldReturnNullWhenClientDataIsNull() {
    // Given
    when(clientRegistrationRepository.findByRegistrationId(CLIENT_REGISTRATION_ID))
        .thenReturn(clientRegistration);
    when(authorizedClientsClient.loadAuthorizedClient(CLIENT_REGISTRATION_ID, PRINCIPAL_NAME))
        .thenReturn(null);

    // When
    final OAuth2AuthorizedClient result =
        service.loadAuthorizedClient(CLIENT_REGISTRATION_ID, PRINCIPAL_NAME);

    // Then
    assertThat(result).isNull();
  }

  @Test
  void shouldHandleAuthorizedClientWithoutRefreshToken() {
    // Given
    when(clientRegistrationRepository.findByRegistrationId(CLIENT_REGISTRATION_ID))
        .thenReturn(clientRegistration);

    final PersistentOAuth2AuthorizedClientEntity entityWithoutRefreshToken =
        createTestEntityWithoutRefreshToken();
    when(authorizedClientsClient.loadAuthorizedClient(CLIENT_REGISTRATION_ID, PRINCIPAL_NAME))
        .thenReturn(entityWithoutRefreshToken);

    // When
    final OAuth2AuthorizedClient result =
        service.loadAuthorizedClient(CLIENT_REGISTRATION_ID, PRINCIPAL_NAME);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getAccessToken()).isNotNull();
    assertThat(result.getRefreshToken()).isNull();
  }

  @Test
  void shouldSaveAuthorizedClientSuccessfully() {
    // Given
    final OAuth2AuthorizedClient authorizedClient = createOAuth2AuthorizedClient();

    // When
    service.saveAuthorizedClient(authorizedClient, authentication);

    // Then
    verify(authorizedClientsClient)
        .saveAuthorizedClient(entityCaptor.capture(), eq(PRINCIPAL_NAME));

    final PersistentOAuth2AuthorizedClientEntity savedEntity = entityCaptor.getValue();
    assertThat(savedEntity.id()).isEqualTo(EXPECTED_RECORD_ID);
    assertThat(savedEntity.clientRegistrationId()).isEqualTo(CLIENT_REGISTRATION_ID);
    assertThat(savedEntity.principalName()).isEqualTo(PRINCIPAL_NAME);
    assertThat(savedEntity.accessTokenValue()).isEqualTo(ACCESS_TOKEN_VALUE);
    assertThat(savedEntity.accessTokenType()).isEqualTo("Bearer");
    assertThat(savedEntity.accessTokenIssuedAt())
        .isEqualTo(accessTokenIssuedAt.atOffset(ZoneOffset.UTC));
    assertThat(savedEntity.accessTokenExpiresAt())
        .isEqualTo(accessTokenExpiresAt.atOffset(ZoneOffset.UTC));
    assertThat(savedEntity.accessTokenScopes()).containsExactlyInAnyOrder("read", "write");
    assertThat(savedEntity.refreshTokenValue()).isEqualTo(REFRESH_TOKEN_VALUE);
    assertThat(savedEntity.refreshTokenIssuedAt())
        .isEqualTo(refreshTokenIssuedAt.atOffset(ZoneOffset.UTC));
    assertThat(savedEntity.refreshTokenExpiresAt())
        .isEqualTo(refreshTokenExpiresAt.atOffset(ZoneOffset.UTC));
  }

  @Test
  void shouldSaveAuthorizedClientWithoutRefreshToken() {
    // Given
    final OAuth2AuthorizedClient authorizedClient =
        createOAuth2AuthorizedClientWithoutRefreshToken();

    // When
    service.saveAuthorizedClient(authorizedClient, authentication);

    // Then
    verify(authorizedClientsClient)
        .saveAuthorizedClient(entityCaptor.capture(), eq(PRINCIPAL_NAME));

    final PersistentOAuth2AuthorizedClientEntity savedEntity = entityCaptor.getValue();
    assertThat(savedEntity.refreshTokenValue()).isNull();
    assertThat(savedEntity.refreshTokenIssuedAt()).isNull();
    assertThat(savedEntity.refreshTokenExpiresAt()).isNull();
  }

  @Test
  void shouldHandleRefreshTokenWithoutExpiration() {
    // Given
    final OAuth2RefreshToken refreshTokenWithoutExpiration =
        new OAuth2RefreshToken(REFRESH_TOKEN_VALUE, refreshTokenIssuedAt, null);

    final OAuth2AuthorizedClient authorizedClient =
        new OAuth2AuthorizedClient(
            clientRegistration, PRINCIPAL_NAME, createAccessToken(), refreshTokenWithoutExpiration);

    // When
    service.saveAuthorizedClient(authorizedClient, authentication);

    // Then
    verify(authorizedClientsClient)
        .saveAuthorizedClient(entityCaptor.capture(), eq(PRINCIPAL_NAME));

    final PersistentOAuth2AuthorizedClientEntity savedEntity = entityCaptor.getValue();
    assertThat(savedEntity.refreshTokenValue()).isEqualTo(REFRESH_TOKEN_VALUE);
    assertThat(savedEntity.refreshTokenIssuedAt())
        .isEqualTo(refreshTokenIssuedAt.atOffset(ZoneOffset.UTC));
    assertThat(savedEntity.refreshTokenExpiresAt()).isNull();
  }

  @Test
  void shouldRemoveAuthorizedClientSuccessfully() {
    // When
    service.removeAuthorizedClient(CLIENT_REGISTRATION_ID, PRINCIPAL_NAME);

    // Then
    verify(authorizedClientsClient).removeAuthorizedClient(CLIENT_REGISTRATION_ID, PRINCIPAL_NAME);
  }

  @Test
  void shouldHandleCaseInsensitiveTokenType() {
    // Given
    when(clientRegistrationRepository.findByRegistrationId(CLIENT_REGISTRATION_ID))
        .thenReturn(clientRegistration);

    final PersistentOAuth2AuthorizedClientEntity entityWithUppercaseTokenType =
        new PersistentOAuth2AuthorizedClientEntity(
            EXPECTED_RECORD_ID,
            CLIENT_REGISTRATION_ID,
            PRINCIPAL_NAME,
            ACCESS_TOKEN_VALUE,
            "BEARER", // Uppercase
            accessTokenIssuedAt.atOffset(ZoneOffset.UTC),
            accessTokenExpiresAt.atOffset(ZoneOffset.UTC),
            Set.of("read", "write"),
            REFRESH_TOKEN_VALUE,
            refreshTokenIssuedAt.atOffset(ZoneOffset.UTC),
            refreshTokenExpiresAt.atOffset(ZoneOffset.UTC));

    when(authorizedClientsClient.loadAuthorizedClient(CLIENT_REGISTRATION_ID, PRINCIPAL_NAME))
        .thenReturn(entityWithUppercaseTokenType);

    // When
    final OAuth2AuthorizedClient result =
        service.loadAuthorizedClient(CLIENT_REGISTRATION_ID, PRINCIPAL_NAME);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getAccessToken().getTokenType())
        .isEqualTo(OAuth2AccessToken.TokenType.BEARER);
  }

  @Test
  void shouldHandleNonBearerTokenType() {
    // Given
    when(clientRegistrationRepository.findByRegistrationId(CLIENT_REGISTRATION_ID))
        .thenReturn(clientRegistration);

    final PersistentOAuth2AuthorizedClientEntity entityWithCustomTokenType =
        new PersistentOAuth2AuthorizedClientEntity(
            EXPECTED_RECORD_ID,
            CLIENT_REGISTRATION_ID,
            PRINCIPAL_NAME,
            ACCESS_TOKEN_VALUE,
            "DPoP", // Non-bearer type
            accessTokenIssuedAt.atOffset(ZoneOffset.UTC),
            accessTokenExpiresAt.atOffset(ZoneOffset.UTC),
            Set.of("read", "write"),
            REFRESH_TOKEN_VALUE,
            refreshTokenIssuedAt.atOffset(ZoneOffset.UTC),
            refreshTokenExpiresAt.atOffset(ZoneOffset.UTC));

    when(authorizedClientsClient.loadAuthorizedClient(CLIENT_REGISTRATION_ID, PRINCIPAL_NAME))
        .thenReturn(entityWithCustomTokenType);

    // When & Then
    assertThrows(
        RuntimeException.class,
        () -> service.loadAuthorizedClient(CLIENT_REGISTRATION_ID, PRINCIPAL_NAME));
  }

  private PersistentOAuth2AuthorizedClientEntity createTestEntity() {
    return new PersistentOAuth2AuthorizedClientEntity(
        EXPECTED_RECORD_ID,
        CLIENT_REGISTRATION_ID,
        PRINCIPAL_NAME,
        ACCESS_TOKEN_VALUE,
        BEARER_TOKEN_TYPE,
        accessTokenIssuedAt.atOffset(ZoneOffset.UTC),
        accessTokenExpiresAt.atOffset(ZoneOffset.UTC),
        Set.of("read", "write"),
        REFRESH_TOKEN_VALUE,
        refreshTokenIssuedAt.atOffset(ZoneOffset.UTC),
        refreshTokenExpiresAt.atOffset(ZoneOffset.UTC));
  }

  private PersistentOAuth2AuthorizedClientEntity createTestEntityWithoutRefreshToken() {
    return new PersistentOAuth2AuthorizedClientEntity(
        EXPECTED_RECORD_ID,
        CLIENT_REGISTRATION_ID,
        PRINCIPAL_NAME,
        ACCESS_TOKEN_VALUE,
        BEARER_TOKEN_TYPE,
        accessTokenIssuedAt.atOffset(ZoneOffset.UTC),
        accessTokenExpiresAt.atOffset(ZoneOffset.UTC),
        Set.of("read", "write"),
        null, // No refresh token
        null,
        null);
  }

  private OAuth2AuthorizedClient createOAuth2AuthorizedClient() {
    return new OAuth2AuthorizedClient(
        clientRegistration, PRINCIPAL_NAME, createAccessToken(), createRefreshToken());
  }

  private OAuth2AuthorizedClient createOAuth2AuthorizedClientWithoutRefreshToken() {
    return new OAuth2AuthorizedClient(
        clientRegistration, PRINCIPAL_NAME, createAccessToken(), null); // No refresh token
  }

  private OAuth2AccessToken createAccessToken() {
    return new OAuth2AccessToken(
        OAuth2AccessToken.TokenType.BEARER,
        ACCESS_TOKEN_VALUE,
        accessTokenIssuedAt,
        accessTokenExpiresAt,
        Set.of("read", "write"));
  }

  private OAuth2RefreshToken createRefreshToken() {
    return new OAuth2RefreshToken(REFRESH_TOKEN_VALUE, refreshTokenIssuedAt, refreshTokenExpiresAt);
  }
}
