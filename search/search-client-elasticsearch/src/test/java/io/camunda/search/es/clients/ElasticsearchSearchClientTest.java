/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.es.clients;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.ScrollResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import io.camunda.search.clients.core.SearchQueryRequest;
import io.camunda.search.es.transformers.ElasticsearchTransformers;
import io.camunda.search.exception.CamundaSearchException;
import java.io.IOException;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ElasticsearchSearchClientTest {

  private static final String SCROLL_ID = "scrollId123";
  private ElasticsearchClient client;
  private ElasticsearchSearchClient searchClient;
  private SearchQueryRequest searchRequest;
  private SearchResponse<Object> searchResponse;
  private ScrollResponse<Object> scrollResponse;
  private ScrollResponse<Object> emptyScrollResponse;

  @BeforeEach
  void setUp() {
    client = mock(ElasticsearchClient.class);
    searchClient = new ElasticsearchSearchClient(client, new ElasticsearchTransformers());
    searchRequest = mock(SearchQueryRequest.class);
    when(searchRequest.size()).thenReturn(null);
    searchResponse =
        SearchResponse.of(
            f ->
                f.scrollId(SCROLL_ID)
                    .hits(
                        h -> h.hits(Hit.of(hit -> hit.id("id").index("idx").source(new Object()))))
                    .shards((s) -> s.failed(0).successful(1).total(1))
                    .took(1L)
                    .timedOut(false));
    scrollResponse =
        ScrollResponse.of(
            f ->
                f.scrollId(SCROLL_ID)
                    .hits(
                        h -> h.hits(Hit.of(hit -> hit.id("id").index("idx").source(new Object()))))
                    .shards((s) -> s.failed(0).successful(1).total(1))
                    .took(1L)
                    .timedOut(false));
    emptyScrollResponse =
        ScrollResponse.of(
            f ->
                f.scrollId(SCROLL_ID)
                    .hits(h -> h.hits(List.of()))
                    .shards((s) -> s.failed(0).successful(0).total(0))
                    .took(1L)
                    .timedOut(false));
  }

  @Test
  void findAllShouldReturnResultsWhenSearchIsSuccessful() throws IOException {
    // given
    final var searchRequestCaptor = ArgumentCaptor.forClass(SearchRequest.class);
    when(client.search(searchRequestCaptor.capture(), any())).thenReturn(searchResponse);
    when(client.scroll(any(Function.class), any()))
        .thenReturn(scrollResponse)
        .thenReturn(emptyScrollResponse);

    // when
    final List<Object> result = searchClient.findAll(searchRequest, Object.class);

    // then
    assertThat(result).hasSize(2);
    assertThat(searchRequestCaptor.getValue().scroll().time()).isEqualTo("1m");
    verify(client).clearScroll(any(Function.class));
  }

  @Test
  void findAllShouldHandleIOException() throws IOException {
    // given
    when(client.search(any(SearchRequest.class), any())).thenThrow(IOException.class);

    // when & Assert
    assertThrows(
        CamundaSearchException.class, () -> searchClient.findAll(searchRequest, Object.class));
    verify(client, never()).scroll(any(Function.class), any());
    verify(client, never()).clearScroll(any(Function.class));
  }

  @Test
  void findAllShouldClearScrollOnException() throws IOException {
    // given
    when(client.search(any(SearchRequest.class), any())).thenReturn(searchResponse);
    when(client.scroll(any(Function.class), any())).thenThrow(IOException.class);

    // when & Assert
    assertThrows(
        CamundaSearchException.class, () -> searchClient.findAll(searchRequest, Object.class));
    verify(client).clearScroll(any(Function.class));
  }
}
