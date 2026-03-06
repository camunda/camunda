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

import io.camunda.auth.domain.exception.AuthorizationGrantException;
import io.camunda.auth.domain.model.AuthorizationGrantRequest;
import io.camunda.auth.domain.model.AuthorizationGrantResponse;
import io.camunda.auth.domain.port.outbound.AuthorizationGrantClient;
import org.junit.jupiter.api.Test;

/**
 * Contract test base for {@link AuthorizationGrantClient} implementations. Every authorization
 * grant client must extend this class and pass all contract tests.
 */
public abstract class AbstractAuthorizationGrantClientContractTest {

  protected abstract AuthorizationGrantClient createClient();

  protected abstract AuthorizationGrantRequest createValidRequest();

  protected abstract AuthorizationGrantRequest createInvalidRequest();

  @Test
  void shouldReturnNonNullResponse() {
    // given
    final AuthorizationGrantClient client = createClient();
    final AuthorizationGrantRequest request = createValidRequest();

    // when
    final AuthorizationGrantResponse response = client.authorize(request);

    // then
    assertThat(response).isNotNull();
    assertThat(response.accessToken()).isNotBlank();
    assertThat(response.expiresIn()).isPositive();
  }

  @Test
  void shouldReturnScopesInResponse() {
    // given
    final AuthorizationGrantClient client = createClient();
    final AuthorizationGrantRequest request = createValidRequest();

    // when
    final AuthorizationGrantResponse response = client.authorize(request);

    // then
    assertThat(response.scope()).isNotNull();
  }

  @Test
  void shouldThrowOnInvalidRequest() {
    // given
    final AuthorizationGrantClient client = createClient();
    final AuthorizationGrantRequest request = createInvalidRequest();

    // when/then
    assertThatThrownBy(() -> client.authorize(request))
        .isInstanceOf(AuthorizationGrantException.class);
  }
}
