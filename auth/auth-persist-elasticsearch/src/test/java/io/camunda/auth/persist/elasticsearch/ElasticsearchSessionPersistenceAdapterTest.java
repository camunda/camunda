/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.persist.elasticsearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.ErrorResponse;
import co.elastic.clients.elasticsearch.core.DeleteByQueryResponse;
import co.elastic.clients.elasticsearch.core.DeleteResponse;
import co.elastic.clients.elasticsearch.core.GetResponse;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import io.camunda.auth.domain.model.SessionData;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ElasticsearchSessionPersistenceAdapterTest {

  @Mock private ElasticsearchClient client;

  @InjectMocks private ElasticsearchSessionPersistenceAdapter adapter;

  @Test
  @SuppressWarnings("unchecked")
  void shouldFindSessionById() throws IOException {
    // given
    final var sessionData = createSessionData("session-1");
    final var document = ElasticsearchSessionDocument.fromDomain(sessionData);
    final GetResponse<ElasticsearchSessionDocument> response = mock(GetResponse.class);
    when(response.found()).thenReturn(true);
    when(response.source()).thenReturn(document);
    when(client.get(any(Function.class), eq(ElasticsearchSessionDocument.class)))
        .thenReturn(response);

    // when
    final var result = adapter.findById("session-1");

    // then
    assertThat(result).isNotNull();
    assertThat(result.id()).isEqualTo("session-1");
    assertThat(result.maxInactiveIntervalInSeconds()).isEqualTo(1800L);
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldReturnNullWhenSessionNotFound() throws IOException {
    // given
    final GetResponse<ElasticsearchSessionDocument> response = mock(GetResponse.class);
    when(response.found()).thenReturn(false);
    when(client.get(any(Function.class), eq(ElasticsearchSessionDocument.class)))
        .thenReturn(response);

    // when
    final var result = adapter.findById("nonexistent-id");

    // then
    assertThat(result).isNull();
  }

  @Test
  void shouldSaveSession() throws IOException {
    // given
    final var sessionData = createSessionData("session-1");
    when(client.index(any(Function.class))).thenReturn(mock(IndexResponse.class));

    // when
    adapter.save(sessionData);

    // then
    verify(client).index(any(Function.class));
  }

  @Test
  void shouldDeleteSessionById() throws IOException {
    // given
    when(client.delete(any(Function.class))).thenReturn(mock(DeleteResponse.class));

    // when
    adapter.deleteById("session-1");

    // then
    verify(client).delete(any(Function.class));
  }

  @Test
  void shouldHandleDeleteElasticsearchException() throws IOException {
    // given
    final var errorResponse =
        new ErrorResponse.Builder()
            .status(404)
            .error(e -> e.type("not_found_exception").reason("test"))
            .build();
    final var exception = new ElasticsearchException("test-endpoint", errorResponse);
    when(client.delete(any(Function.class))).thenThrow(exception);

    // when/then
    assertThatNoException().isThrownBy(() -> adapter.deleteById("session-1"));
  }

  @Test
  void shouldDeleteExpiredSessions() throws IOException {
    // given
    when(client.deleteByQuery(any(Function.class))).thenReturn(mock(DeleteByQueryResponse.class));

    // when
    adapter.deleteExpired();

    // then
    verify(client).deleteByQuery(any(Function.class));
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldFindAllSessions() throws IOException {
    // given
    final var sessionData = createSessionData("session-1");
    final var document = ElasticsearchSessionDocument.fromDomain(sessionData);
    final Hit<ElasticsearchSessionDocument> hit = mock(Hit.class);
    when(hit.source()).thenReturn(document);
    final HitsMetadata<ElasticsearchSessionDocument> hitsMetadata = mock(HitsMetadata.class);
    when(hitsMetadata.hits()).thenReturn(List.of(hit));
    final SearchResponse<ElasticsearchSessionDocument> searchResponse = mock(SearchResponse.class);
    when(searchResponse.hits()).thenReturn(hitsMetadata);
    when(client.search(any(Function.class), eq(ElasticsearchSessionDocument.class)))
        .thenReturn(searchResponse);

    // when
    final var result = adapter.findAll();

    // then
    assertThat(result).hasSize(1);
    assertThat(result.get(0).id()).isEqualTo("session-1");
  }

  private SessionData createSessionData(final String sessionId) {
    return new SessionData(
        sessionId, System.currentTimeMillis(), System.currentTimeMillis(), 1800L, Map.of());
  }
}
