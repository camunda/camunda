/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.test.utils;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ShardFailure;
import co.elastic.clients.elasticsearch.indices.ElasticsearchIndicesClient;
import co.elastic.clients.elasticsearch.indices.RefreshResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.ShardSearchFailure;
import org.opensearch.client.opensearch.indices.OpenSearchIndicesClient;

class SearchClientAdapterTest {

  // ─────────────────────────────────────────────────────────────────────────
  // Elasticsearch tests
  // ─────────────────────────────────────────────────────────────────────────

  @Nested
  class WithElasticsearchClient {

    @Mock private ElasticsearchClient elsClient;
    @Mock private ElasticsearchIndicesClient elsIndicesClient;
    @Mock private RefreshResponse elsRefreshResponse;
    @Mock private co.elastic.clients.elasticsearch._types.ShardStatistics elsShards;

    private SearchClientAdapter adapter;

    @BeforeEach
    void setUp() {
      MockitoAnnotations.openMocks(this);
      when(elsClient.indices()).thenReturn(elsIndicesClient);
      final var objectMapper = mock(ObjectMapper.class);
      adapter = new SearchClientAdapter(elsClient, objectMapper);
    }

    // ── refresh() ────────────────────────────────────────────────────────

    @Test
    void refreshNoPrefixCallsRefreshWithoutIndex() throws IOException {
      when(elsIndicesClient.refresh()).thenReturn(elsRefreshResponse);
      when(elsRefreshResponse.shards()).thenReturn(elsShards);
      when(elsShards.failures()).thenReturn(List.of());

      adapter.refresh();

      verify(elsIndicesClient).refresh();
    }

    // ── refresh(String prefix) ────────────────────────────────────────────

    @Test
    void refreshWithPrefixCallsRefreshWithWildcard() throws IOException {
      when(elsIndicesClient.refresh(any(Function.class))).thenReturn(elsRefreshResponse);
      when(elsRefreshResponse.shards()).thenReturn(elsShards);
      when(elsShards.failures()).thenReturn(List.of());

      adapter.refresh("my-prefix");

      verify(elsIndicesClient).refresh(any(Function.class));
    }

    @Test
    void refreshWithNullPrefixDelegatesToNoArgRefresh() throws IOException {
      when(elsIndicesClient.refresh()).thenReturn(elsRefreshResponse);
      when(elsRefreshResponse.shards()).thenReturn(elsShards);
      when(elsShards.failures()).thenReturn(List.of());

      adapter.refresh((String) null);

      verify(elsIndicesClient).refresh();
    }

    @Test
    void refreshWithEmptyPrefixDelegatesToNoArgRefresh() throws IOException {
      when(elsIndicesClient.refresh()).thenReturn(elsRefreshResponse);
      when(elsRefreshResponse.shards()).thenReturn(elsShards);
      when(elsShards.failures()).thenReturn(List.of());

      adapter.refresh("");

      verify(elsIndicesClient).refresh();
    }

    @Test
    void refreshNullShardsThrowsRuntimeException() throws IOException {
      when(elsIndicesClient.refresh()).thenReturn(elsRefreshResponse);
      when(elsRefreshResponse.shards()).thenReturn(null);

      assertThatThrownBy(() -> adapter.refresh())
          .isInstanceOf(RuntimeException.class)
          .hasMessage("No shards stats returned");
    }

    @Test
    void refreshWithShardFailuresThrowsRuntimeException() throws IOException {
      final var failure = mock(co.elastic.clients.elasticsearch._types.ShardFailure.class);

      when(elsIndicesClient.refresh()).thenReturn(elsRefreshResponse);
      when(elsRefreshResponse.shards()).thenReturn(elsShards);
      when(elsShards.failures()).thenReturn(List.of(failure));

      assertThatThrownBy(() -> adapter.refresh())
          .isInstanceOf(RuntimeException.class)
          .hasMessageStartingWith("Index refresh failed with shard failures: ");
    }

    @Test
    void refreshWithPrefixNullShardsThrowsRuntimeException() throws IOException {
      when(elsIndicesClient.refresh(any(Function.class))).thenReturn(elsRefreshResponse);
      when(elsRefreshResponse.shards()).thenReturn(null);

      assertThatThrownBy(() -> adapter.refresh("my-prefix"))
          .isInstanceOf(RuntimeException.class)
          .hasMessage("No shards stats returned");
    }

    @Test
    void refreshWithPrefixWithShardFailuresThrowsRuntimeException() throws IOException {
      final var failure = mock(ShardFailure.class);

      when(elsIndicesClient.refresh(any(Function.class))).thenReturn(elsRefreshResponse);
      when(elsRefreshResponse.shards()).thenReturn(elsShards);
      when(elsShards.failures()).thenReturn(List.of(failure));

      assertThatThrownBy(() -> adapter.refresh("my-prefix"))
          .isInstanceOf(RuntimeException.class)
          .hasMessageStartingWith("Index refresh failed with shard failures: ");
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // OpenSearch tests
  // ─────────────────────────────────────────────────────────────────────────

  @Nested
  class WithOpenSearchClient {

    @Mock private OpenSearchClient osClient;
    @Mock private OpenSearchIndicesClient osIndicesClient;
    @Mock private org.opensearch.client.opensearch.indices.RefreshResponse osRefreshResponse;
    @Mock private org.opensearch.client.opensearch._types.ShardStatistics osShards;

    private SearchClientAdapter adapter;

    @BeforeEach
    void setUp() throws IOException {
      MockitoAnnotations.openMocks(this);
      when(osClient.indices()).thenReturn(osIndicesClient);
      final var objectMapper = mock(ObjectMapper.class);
      adapter = new SearchClientAdapter(osClient, objectMapper);
    }

    // ── refresh() ────────────────────────────────────────────────────────

    @Test
    void refreshNoPrefixCallsRefreshWithoutIndex() throws IOException {
      when(osIndicesClient.refresh()).thenReturn(osRefreshResponse);
      when(osRefreshResponse.shards()).thenReturn(osShards);
      when(osShards.failures()).thenReturn(List.of());

      adapter.refresh();

      verify(osIndicesClient).refresh();
    }

    // ── refresh(String prefix) ────────────────────────────────────────────

    @Test
    void refreshWithPrefixCallsRefreshWithWildcard() throws IOException {
      when(osIndicesClient.refresh(any(Function.class))).thenReturn(osRefreshResponse);
      when(osRefreshResponse.shards()).thenReturn(osShards);
      when(osShards.failures()).thenReturn(List.of());

      adapter.refresh("my-prefix");

      verify(osIndicesClient).refresh(any(Function.class));
    }

    @Test
    void refreshWithNullPrefixDelegatesToNoArgRefresh() throws IOException {
      when(osIndicesClient.refresh()).thenReturn(osRefreshResponse);
      when(osRefreshResponse.shards()).thenReturn(osShards);
      when(osShards.failures()).thenReturn(List.of());

      adapter.refresh((String) null);

      verify(osIndicesClient).refresh();
    }

    @Test
    void refreshWithEmptyPrefixDelegatesToNoArgRefresh() throws IOException {
      when(osIndicesClient.refresh()).thenReturn(osRefreshResponse);
      when(osRefreshResponse.shards()).thenReturn(osShards);
      when(osShards.failures()).thenReturn(List.of());

      adapter.refresh("");

      verify(osIndicesClient).refresh();
    }

    @Test
    void refreshWithShardFailuresThrowsRuntimeException() throws IOException {
      final var failure = mock(ShardSearchFailure.class);

      when(osIndicesClient.refresh()).thenReturn(osRefreshResponse);
      when(osRefreshResponse.shards()).thenReturn(osShards);
      when(osShards.failures()).thenReturn(List.of(failure));

      assertThatThrownBy(() -> adapter.refresh())
          .isInstanceOf(RuntimeException.class)
          .hasMessageStartingWith("Index refresh failed with shard failures: ");
    }

    @Test
    void refreshWithPrefixWithShardFailuresThrowsRuntimeException() throws IOException {
      final var failure = mock(ShardSearchFailure.class);

      when(osIndicesClient.refresh(any(Function.class))).thenReturn(osRefreshResponse);
      when(osRefreshResponse.shards()).thenReturn(osShards);
      when(osShards.failures()).thenReturn(List.of(failure));

      assertThatThrownBy(() -> adapter.refresh("my-prefix"))
          .isInstanceOf(RuntimeException.class)
          .hasMessageStartingWith("Index refresh failed with shard failures: ");
    }
  }
}
