/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.persist.elasticsearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.ErrorResponse;
import co.elastic.clients.elasticsearch.core.GetResponse;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import io.camunda.auth.domain.model.TokenMetadata;
import io.camunda.auth.domain.model.TokenMetadata.ExchangeStatus;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ElasticsearchTokenStoreAdapterTest {

  @Mock private ElasticsearchClient client;

  @InjectMocks private ElasticsearchTokenStoreAdapter adapter;

  @Test
  void shouldStoreTokenMetadata() throws IOException {
    // given
    final var metadata = createTokenMetadata("exchange-1");
    when(client.index(any(Function.class))).thenReturn(mock(IndexResponse.class));

    // when
    adapter.store(metadata);

    // then
    verify(client).index(any(Function.class));
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldFindByExchangeId() throws IOException {
    // given
    final var metadata = createTokenMetadata("exchange-1");
    final var document = ElasticsearchTokenDocument.fromDomain(metadata);
    final GetResponse<ElasticsearchTokenDocument> response = mock(GetResponse.class);
    when(response.found()).thenReturn(true);
    when(response.source()).thenReturn(document);
    when(client.get(any(Function.class), eq(ElasticsearchTokenDocument.class)))
        .thenReturn(response);

    // when
    final var result = adapter.findByExchangeId("exchange-1");

    // then
    assertThat(result).isPresent();
    assertThat(result.get().exchangeId()).isEqualTo("exchange-1");
    assertThat(result.get().subjectPrincipalId()).isEqualTo("user-1");
    assertThat(result.get().actorPrincipalId()).isEqualTo("actor-1");
    assertThat(result.get().targetAudience()).isEqualTo("zeebe-api");
    assertThat(result.get().grantedScopes()).containsExactlyInAnyOrder("read", "write");
    assertThat(result.get().exchangeStatus()).isEqualTo(ExchangeStatus.SUCCESS);
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldReturnEmptyForNonexistentId() throws IOException {
    // given
    final GetResponse<ElasticsearchTokenDocument> response = mock(GetResponse.class);
    when(response.found()).thenReturn(false);
    when(client.get(any(Function.class), eq(ElasticsearchTokenDocument.class)))
        .thenReturn(response);

    // when
    final var result = adapter.findByExchangeId("nonexistent-id");

    // then
    assertThat(result).isEmpty();
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldFindBySubjectPrincipalId() throws IOException {
    // given
    final var metadata = createTokenMetadata("exchange-1");
    final var document = ElasticsearchTokenDocument.fromDomain(metadata);
    final Hit<ElasticsearchTokenDocument> hit = mock(Hit.class);
    when(hit.source()).thenReturn(document);
    final HitsMetadata<ElasticsearchTokenDocument> hitsMetadata = mock(HitsMetadata.class);
    when(hitsMetadata.hits()).thenReturn(List.of(hit));
    final SearchResponse<ElasticsearchTokenDocument> searchResponse = mock(SearchResponse.class);
    when(searchResponse.hits()).thenReturn(hitsMetadata);
    when(client.search(any(Function.class), eq(ElasticsearchTokenDocument.class)))
        .thenReturn(searchResponse);

    // when
    final var from = Instant.now().minusSeconds(3600);
    final var to = Instant.now();
    final var result = adapter.findBySubjectPrincipalId("user-1", from, to);

    // then
    assertThat(result).hasSize(1);
    assertThat(result.get(0).exchangeId()).isEqualTo("exchange-1");
    assertThat(result.get(0).subjectPrincipalId()).isEqualTo("user-1");
  }

  @Test
  void shouldThrowRuntimeExceptionOnStoreFailure() throws IOException {
    // given
    final var metadata = createTokenMetadata("exchange-1");
    final var errorResponse =
        new ErrorResponse.Builder()
            .status(500)
            .error(e -> e.type("index_exception").reason("test failure"))
            .build();
    final var exception = new ElasticsearchException("test-endpoint", errorResponse);
    when(client.index(any(Function.class))).thenThrow(exception);

    // when/then
    assertThatThrownBy(() -> adapter.store(metadata))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("exchange-1")
        .hasCause(exception);
  }

  private TokenMetadata createTokenMetadata(final String exchangeId) {
    return TokenMetadata.builder()
        .exchangeId(exchangeId)
        .subjectPrincipalId("user-1")
        .actorPrincipalId("actor-1")
        .targetAudience("zeebe-api")
        .grantedScopes(Set.of("read", "write"))
        .exchangeTime(Instant.now())
        .expiryTime(Instant.now().plusSeconds(300))
        .exchangeStatus(ExchangeStatus.SUCCESS)
        .build();
  }
}
