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
import static org.mockito.Mockito.when;

import io.camunda.auth.domain.exception.TokenExchangeException;
import io.camunda.auth.domain.model.TokenExchangeRequest;
import io.camunda.auth.domain.model.TokenExchangeResponse;
import io.camunda.auth.domain.model.TokenMetadata;
import io.camunda.auth.domain.model.TokenType;
import io.camunda.auth.domain.port.outbound.TokenExchangeClient;
import io.camunda.auth.domain.port.outbound.TokenStorePort;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TokenExchangeServiceTest {

  @Mock private TokenExchangeClient mockClient;
  @Mock private TokenStorePort mockTokenStore;
  @Mock private DelegationChainValidator mockChainValidator;

  private TokenExchangeService service;

  @BeforeEach
  void setUp() {
    service = new TokenExchangeService(mockClient, mockTokenStore, mockChainValidator);
  }

  @Test
  void shouldExchangeTokenSuccessfully() {
    // given
    final TokenExchangeRequest request = createRequest();
    final TokenExchangeResponse expectedResponse = createResponse();
    when(mockClient.exchange(request)).thenReturn(expectedResponse);

    // when
    final TokenExchangeResponse response = service.exchange(request);

    // then
    assertThat(response).isEqualTo(expectedResponse);
    verify(mockClient).exchange(request);
    verify(mockTokenStore).store(any(TokenMetadata.class));
  }

  @Test
  void shouldStoreFailedAuditRecordOnException() {
    // given
    final TokenExchangeRequest request = createRequest();
    when(mockClient.exchange(request))
        .thenThrow(new TokenExchangeException.InvalidGrant("Token expired"));

    // when/then
    assertThatThrownBy(() -> service.exchange(request))
        .isInstanceOf(TokenExchangeException.InvalidGrant.class);
    verify(mockTokenStore).store(any(TokenMetadata.class));
  }

  @Test
  void shouldWorkWithoutStore() {
    // given
    final TokenExchangeService simpleService = new TokenExchangeService(mockClient);
    final TokenExchangeRequest request = createRequest();
    final TokenExchangeResponse expectedResponse = createResponse();
    when(mockClient.exchange(request)).thenReturn(expectedResponse);

    // when
    final TokenExchangeResponse response = simpleService.exchange(request);

    // then
    assertThat(response).isEqualTo(expectedResponse);
  }

  private TokenExchangeRequest createRequest() {
    return TokenExchangeRequest.builder()
        .subjectToken("subject-token-value")
        .subjectTokenType(TokenType.ACCESS_TOKEN)
        .audience("zeebe-api")
        .scopes(Set.of("read", "write"))
        .build();
  }

  private TokenExchangeResponse createResponse() {
    return TokenExchangeResponse.builder()
        .accessToken("exchanged-token-value")
        .issuedTokenType(TokenType.ACCESS_TOKEN)
        .tokenType("Bearer")
        .expiresIn(3600)
        .scope(Set.of("read", "write"))
        .build();
  }
}
