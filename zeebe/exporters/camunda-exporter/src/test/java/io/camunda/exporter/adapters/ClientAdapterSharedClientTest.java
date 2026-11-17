/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.adapters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.transport.ElasticsearchTransport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.OpenSearchTransport;

/** Covers wrapping a client owned by someone else, e.g. the application's shared client bean. */
final class ClientAdapterSharedClientTest {

  @Test
  void shouldNotCloseSharedElasticsearchClient() throws Exception {
    // given
    final var transport = mock(ElasticsearchTransport.class);
    when(transport.jsonpMapper())
        .thenReturn(new co.elastic.clients.json.jackson.JacksonJsonpMapper());
    final var adapter = ClientAdapter.of(new ElasticsearchClient(transport));

    // when
    adapter.close();

    // then
    verify(transport, never()).close();
  }

  @Test
  void shouldNotCloseSharedOpensearchClient() throws Exception {
    // given
    final var transport = mock(OpenSearchTransport.class);
    when(transport.jsonpMapper())
        .thenReturn(new org.opensearch.client.json.jackson.JacksonJsonpMapper());
    final var adapter = ClientAdapter.of(new OpenSearchClient(transport));

    // when
    adapter.close();

    // then
    verify(transport, never()).close();
  }

  @Test
  void shouldReuseTheObjectMapperOfTheSharedElasticsearchClient() {
    // given
    final var objectMapper = new ObjectMapper();
    final var transport = mock(ElasticsearchTransport.class);
    when(transport.jsonpMapper())
        .thenReturn(new co.elastic.clients.json.jackson.JacksonJsonpMapper(objectMapper));

    // when
    final var adapter = ClientAdapter.of(new ElasticsearchClient(transport));

    // then
    assertThat(adapter.objectMapper()).isSameAs(objectMapper);
  }

  @Test
  void shouldReuseTheObjectMapperOfTheSharedOpensearchClient() {
    // given
    final var objectMapper = new ObjectMapper();
    final var transport = mock(OpenSearchTransport.class);
    when(transport.jsonpMapper())
        .thenReturn(new org.opensearch.client.json.jackson.JacksonJsonpMapper(objectMapper));

    // when
    final var adapter = ClientAdapter.of(new OpenSearchClient(transport));

    // then
    assertThat(adapter.objectMapper()).isSameAs(objectMapper);
  }

  @Test
  void shouldRejectElasticsearchClientWithoutJacksonMapper() {
    // given
    final var transport = mock(ElasticsearchTransport.class);
    when(transport.jsonpMapper()).thenReturn(mock(co.elastic.clients.json.JsonpMapper.class));

    // when / then
    assertThatThrownBy(() -> ClientAdapter.of(new ElasticsearchClient(transport)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("JacksonJsonpMapper");
  }

  @Test
  void shouldRejectOpensearchClientWithoutJacksonMapper() {
    // given
    final var transport = mock(OpenSearchTransport.class);
    when(transport.jsonpMapper()).thenReturn(mock(org.opensearch.client.json.JsonpMapper.class));

    // when / then
    assertThatThrownBy(() -> ClientAdapter.of(new OpenSearchClient(transport)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("JacksonJsonpMapper");
  }
}
