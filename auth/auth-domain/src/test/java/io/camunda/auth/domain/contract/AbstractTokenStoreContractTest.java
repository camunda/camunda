/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.domain.contract;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.auth.domain.model.TokenMetadata;
import io.camunda.auth.domain.model.TokenMetadata.ExchangeStatus;
import io.camunda.auth.domain.port.outbound.TokenStorePort;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Contract test base for {@link TokenStorePort} implementations. Both RDBMS and ES adapters must
 * extend this class and pass all tests.
 */
public abstract class AbstractTokenStoreContractTest {

  protected abstract TokenStorePort createStore();

  @Test
  void shouldStoreAndRetrieveByExchangeId() {
    // given
    final TokenStorePort store = createStore();
    final String exchangeId = UUID.randomUUID().toString();
    final TokenMetadata metadata = createMetadata(exchangeId, "user-1", "actor-1");

    // when
    store.store(metadata);
    final var result = store.findByExchangeId(exchangeId);

    // then
    assertThat(result).isPresent();
    assertThat(result.get().exchangeId()).isEqualTo(exchangeId);
    assertThat(result.get().subjectPrincipalId()).isEqualTo("user-1");
    assertThat(result.get().exchangeStatus()).isEqualTo(ExchangeStatus.SUCCESS);
  }

  @Test
  void shouldReturnEmptyForNonexistentId() {
    // given
    final TokenStorePort store = createStore();

    // when
    final var result = store.findByExchangeId("nonexistent-id");

    // then
    assertThat(result).isEmpty();
  }

  @Test
  void shouldFindBySubjectPrincipalIdWithTimeRange() {
    // given
    final TokenStorePort store = createStore();
    final Instant now = Instant.now();
    final TokenMetadata m1 =
        createMetadataWithTime(UUID.randomUUID().toString(), "user-1", now.minusSeconds(60));
    final TokenMetadata m2 =
        createMetadataWithTime(UUID.randomUUID().toString(), "user-1", now.minusSeconds(30));
    final TokenMetadata m3 =
        createMetadataWithTime(UUID.randomUUID().toString(), "user-2", now.minusSeconds(30));

    store.store(m1);
    store.store(m2);
    store.store(m3);

    // when
    final var results =
        store.findBySubjectPrincipalId("user-1", now.minusSeconds(120), now.plusSeconds(10));

    // then
    assertThat(results).hasSize(2);
    assertThat(results).allMatch(m -> "user-1".equals(m.subjectPrincipalId()));
  }

  protected TokenMetadata createMetadata(
      final String exchangeId, final String subjectId, final String actorId) {
    return createMetadataWithTime(exchangeId, subjectId, Instant.now());
  }

  protected TokenMetadata createMetadataWithTime(
      final String exchangeId, final String subjectId, final Instant exchangeTime) {
    return TokenMetadata.builder()
        .exchangeId(exchangeId)
        .subjectPrincipalId(subjectId)
        .actorPrincipalId("actor-1")
        .targetAudience("zeebe-api")
        .grantedScopes(Set.of("read", "write"))
        .exchangeTime(exchangeTime)
        .expiryTime(exchangeTime.plusSeconds(300))
        .exchangeStatus(ExchangeStatus.SUCCESS)
        .build();
  }
}
