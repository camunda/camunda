/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.domain.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.auth.domain.exception.TokenExchangeException;
import io.camunda.auth.domain.model.TokenExchangeRequest;
import io.camunda.auth.domain.model.TokenExchangeResponse;
import io.camunda.auth.domain.model.TokenType;
import io.camunda.auth.domain.port.outbound.TokenExchangeClient;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Contract test base for {@link TokenExchangeClient} implementations. Every token exchange client
 * must extend this class and pass all contract tests.
 */
public abstract class AbstractTokenExchangeClientContractTest {

  protected abstract TokenExchangeClient createClient();

  protected abstract TokenExchangeRequest createValidRequest();

  protected abstract String createExpiredToken();

  @Test
  void shouldReturnNonNullResponse() {
    // given
    final TokenExchangeClient client = createClient();
    final TokenExchangeRequest request = createValidRequest();

    // when
    final TokenExchangeResponse response = client.exchange(request);

    // then
    assertThat(response).isNotNull();
    assertThat(response.accessToken()).isNotBlank();
    assertThat(response.issuedTokenType()).isNotNull();
    assertThat(response.expiresIn()).isPositive();
  }

  @Test
  void shouldReturnScopesInResponse() {
    // given
    final TokenExchangeClient client = createClient();
    final TokenExchangeRequest request = createValidRequest();

    // when
    final TokenExchangeResponse response = client.exchange(request);

    // then
    assertThat(response.scope()).isNotNull();
  }

  @Test
  void shouldThrowOnExpiredSubjectToken() {
    // given
    final TokenExchangeClient client = createClient();
    final String expiredToken = createExpiredToken();
    final TokenExchangeRequest request =
        TokenExchangeRequest.builder()
            .subjectToken(expiredToken)
            .subjectTokenType(TokenType.ACCESS_TOKEN)
            .audience("target-service")
            .scopes(Set.of("read"))
            .build();

    // when/then
    assertThatThrownBy(() -> client.exchange(request)).isInstanceOf(TokenExchangeException.class);
  }
}
