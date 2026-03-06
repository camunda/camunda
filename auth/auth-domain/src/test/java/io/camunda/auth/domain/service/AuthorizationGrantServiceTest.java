/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.auth.domain.exception.AuthorizationGrantException;
import io.camunda.auth.domain.model.ClientCredentialsGrantRequest;
import io.camunda.auth.domain.model.ClientCredentialsGrantResponse;
import io.camunda.auth.domain.model.TokenExchangeGrantRequest;
import io.camunda.auth.domain.model.TokenExchangeGrantResponse;
import io.camunda.auth.domain.model.TokenMetadata;
import io.camunda.auth.domain.model.TokenType;
import io.camunda.auth.domain.port.outbound.AuthorizationGrantClient;
import io.camunda.auth.domain.port.outbound.TokenStorePort;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthorizationGrantServiceTest {

  @Mock private AuthorizationGrantClient mockClient;
  @Mock private TokenStorePort mockTokenStore;
  @Mock private DelegationChainValidator mockChainValidator;

  private AuthorizationGrantService service;

  @BeforeEach
  void setUp() {
    service = new AuthorizationGrantService(mockClient, mockTokenStore, mockChainValidator);
  }

  @Test
  void shouldAuthorizeTokenExchangeSuccessfully() {
    // given
    final TokenExchangeGrantRequest request = createTokenExchangeRequest();
    final TokenExchangeGrantResponse expectedResponse = createTokenExchangeResponse();
    when(mockClient.authorize(request)).thenReturn(expectedResponse);

    // when
    final var response = service.authorize(request);

    // then
    assertThat(response).isEqualTo(expectedResponse);
    verify(mockClient).authorize(request);
    verify(mockTokenStore).store(any(TokenMetadata.class));
  }

  @Test
  void shouldStoreFailedAuditRecordOnException() {
    // given
    final TokenExchangeGrantRequest request = createTokenExchangeRequest();
    when(mockClient.authorize(request))
        .thenThrow(new AuthorizationGrantException.InvalidGrant("Token expired"));

    // when/then
    assertThatThrownBy(() -> service.authorize(request))
        .isInstanceOf(AuthorizationGrantException.InvalidGrant.class);
    verify(mockTokenStore).store(any(TokenMetadata.class));
  }

  @Test
  void shouldWorkWithoutStore() {
    // given
    final AuthorizationGrantService simpleService = new AuthorizationGrantService(mockClient);
    final TokenExchangeGrantRequest request = createTokenExchangeRequest();
    final TokenExchangeGrantResponse expectedResponse = createTokenExchangeResponse();
    when(mockClient.authorize(request)).thenReturn(expectedResponse);

    // when
    final var response = simpleService.authorize(request);

    // then
    assertThat(response).isEqualTo(expectedResponse);
  }

  @Test
  void shouldNotValidateDelegationChainForClientCredentials() {
    // given
    final ClientCredentialsGrantRequest request =
        ClientCredentialsGrantRequest.builder()
            .audience("zeebe-api")
            .scopes(Set.of("read"))
            .build();
    final ClientCredentialsGrantResponse expectedResponse =
        ClientCredentialsGrantResponse.builder()
            .accessToken("cc-token")
            .tokenType("Bearer")
            .expiresIn(3600)
            .scope(Set.of("read"))
            .build();
    when(mockClient.authorize(request)).thenReturn(expectedResponse);

    // when
    final var response = service.authorize(request);

    // then
    assertThat(response).isEqualTo(expectedResponse);
    verifyNoInteractions(mockChainValidator);
  }

  @Test
  void shouldValidateDelegationChainForTokenExchangeWithActorToken() {
    // given
    final TokenExchangeGrantRequest request =
        TokenExchangeGrantRequest.builder()
            .subjectToken("subject-token-value")
            .subjectTokenType(TokenType.ACCESS_TOKEN)
            .actorToken("actor-token-value")
            .actorTokenType(TokenType.ACCESS_TOKEN)
            .audience("zeebe-api")
            .scopes(Set.of("read"))
            .build();
    final TokenExchangeGrantResponse expectedResponse = createTokenExchangeResponse();
    when(mockClient.authorize(request)).thenReturn(expectedResponse);

    // when
    final var response = service.authorize(request);

    // then
    assertThat(response).isEqualTo(expectedResponse);
    verify(mockChainValidator).validate("subject-token-value");
  }

  private TokenExchangeGrantRequest createTokenExchangeRequest() {
    return TokenExchangeGrantRequest.builder()
        .subjectToken("subject-token-value")
        .subjectTokenType(TokenType.ACCESS_TOKEN)
        .audience("zeebe-api")
        .scopes(Set.of("read", "write"))
        .build();
  }

  private TokenExchangeGrantResponse createTokenExchangeResponse() {
    return TokenExchangeGrantResponse.builder()
        .accessToken("exchanged-token-value")
        .issuedTokenType(TokenType.ACCESS_TOKEN)
        .tokenType("Bearer")
        .expiresIn(3600)
        .scope(Set.of("read", "write"))
        .build();
  }
}
