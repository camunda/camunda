/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.exchange.obo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.auth.domain.model.TokenExchangeRequest;
import io.camunda.auth.domain.model.TokenExchangeResponse;
import io.camunda.auth.domain.model.TokenType;
import io.camunda.auth.domain.service.TokenExchangeService;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OnBehalfOfServiceTest {

  @Mock private TokenExchangeService tokenExchangeService;

  private OnBehalfOfService onBehalfOfService;

  @BeforeEach
  void setUp() {
    onBehalfOfService = new OnBehalfOfService(tokenExchangeService);
  }

  @Test
  void shouldDelegateToTokenExchangeService() {
    // given
    final TokenExchangeResponse response = createResponse();
    when(tokenExchangeService.exchange(any(TokenExchangeRequest.class))).thenReturn(response);

    // when
    onBehalfOfService.getOnBehalfOfToken("subject-token", "zeebe-api", Set.of("read"));

    // then
    verify(tokenExchangeService).exchange(any(TokenExchangeRequest.class));
  }

  @Test
  void shouldReturnAccessToken() {
    // given
    final TokenExchangeResponse response = createResponse();
    when(tokenExchangeService.exchange(any(TokenExchangeRequest.class))).thenReturn(response);

    // when
    final String accessToken =
        onBehalfOfService.getOnBehalfOfToken("subject-token", "zeebe-api", Set.of("read"));

    // then
    assertThat(accessToken).isEqualTo("exchanged-access-token");
  }

  private TokenExchangeResponse createResponse() {
    return TokenExchangeResponse.builder()
        .accessToken("exchanged-access-token")
        .issuedTokenType(TokenType.ACCESS_TOKEN)
        .tokenType("Bearer")
        .expiresIn(3600)
        .scope(Set.of("read"))
        .build();
  }
}
