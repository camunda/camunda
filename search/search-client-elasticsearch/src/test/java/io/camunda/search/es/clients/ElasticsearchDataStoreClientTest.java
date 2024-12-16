/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.es.clients;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Result;
import co.elastic.clients.elasticsearch.core.GetRequest;
import co.elastic.clients.elasticsearch.core.GetResponse;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import co.elastic.clients.elasticsearch.core.search.TotalHitsRelation;
import io.camunda.search.clients.core.SearchGetRequest;
import io.camunda.search.clients.core.SearchIndexRequest;
import io.camunda.search.clients.core.SearchQueryRequest;
import io.camunda.search.clients.core.SearchWriteResponse;
import io.camunda.search.es.transformers.ElasticsearchTransformers;
import java.io.IOException;
import java.util.ArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

public class ElasticsearchDataStoreClientTest {

  private ElasticsearchClient client;
  private ElasticsearchSearchClient searchClient;

  @BeforeEach
  public void before() {
    client = mock(ElasticsearchClient.class);
    searchClient = new ElasticsearchSearchClient(client, new ElasticsearchTransformers());
  }

  @Test
  public void shouldTransformSearchRequest() throws IOException {
    // given
    final var searchRequestCaptor = ArgumentCaptor.forClass(SearchRequest.class);
    final var searchResponse = createDefaultSearchResponse();
    when(client.search(searchRequestCaptor.capture(), eq(TestDocument.class)))
        .thenReturn(searchResponse);

    final SearchQueryRequest request =
        SearchQueryRequest.of(b -> b.index("operate-list-view-8.3.0_").size(1));

    // when
    searchClient.search(request, TestDocument.class);

    // then
    assertThat(searchRequestCaptor.getValue().index()).contains("operate-list-view-8.3.0_");
  }

  @Test
  public void shouldTransformSearchResponse() throws IOException {
    // given
    final var searchRequestCaptor = ArgumentCaptor.forClass(SearchRequest.class);
    final var searchResponse = createDefaultSearchResponse();
    when(client.search(searchRequestCaptor.capture(), eq(TestDocument.class)))
        .thenReturn(searchResponse);

    final SearchQueryRequest request =
        SearchQueryRequest.of(b -> b.index("operate-list-view-8.3.0_").size(1));

    // when
    final var response = searchClient.search(request, TestDocument.class);

    // then
    assertThat(response).isNotNull();
    assertThat(response.totalHits()).isEqualTo(789);
  }

  @Test
  public void shouldTransformGetRequest() throws IOException {
    // given
    final var getRequestCaptor = ArgumentCaptor.forClass(GetRequest.class);
    final var getResponse = createDefaultGetResponse();
    when(client.get(getRequestCaptor.capture(), eq(TestDocument.class))).thenReturn(getResponse);

    final var searchGetRequest =
        SearchGetRequest.of(b -> b.id("foo").index("bar").routing("foobar"));

    // when
    searchClient.get(searchGetRequest, TestDocument.class);

    // then
    assertThat(getRequestCaptor.getValue().id()).isEqualTo("foo");
    assertThat(getRequestCaptor.getValue().index()).isEqualTo("bar");
    assertThat(getRequestCaptor.getValue().routing()).isEqualTo("foobar");
  }

  @Test
  public void shouldTransformGetResponse() throws IOException {
    // given
    final var getRequestCaptor = ArgumentCaptor.forClass(GetRequest.class);
    final var getResponse = createDefaultGetResponse();
    when(client.get(getRequestCaptor.capture(), eq(TestDocument.class))).thenReturn(getResponse);

    final var searchGetRequest =
        SearchGetRequest.of(b -> b.id("foo").index("bar").routing("foobar"));

    // when
    final var response = searchClient.get(searchGetRequest, TestDocument.class);

    // then
    assertThat(response).isNotNull();
    assertThat(response.id()).isEqualTo("foo");
    assertThat(response.index()).isEqualTo("bar");
    assertThat(response.found()).isTrue();
    assertThat(response.source().id()).isEqualTo("123");
  }

  @Test
  public void shouldTransformIndexRequest() throws IOException {
    // given
    final var indexRequestCaptor = ArgumentCaptor.forClass(IndexRequest.class);
    final var indexResponse = createDefaultIndexResponse();
    when(client.index(indexRequestCaptor.capture())).thenReturn(indexResponse);

    final var doc = new TestDocument("test");
    final var searchIndexRequest =
        SearchIndexRequest.of(b -> b.id("foo").index("bar").routing("foobar").document(doc));

    // when
    searchClient.index(searchIndexRequest);

    // then
    assertThat(indexRequestCaptor.getValue().id()).isEqualTo("foo");
    assertThat(indexRequestCaptor.getValue().index()).isEqualTo("bar");
    assertThat(indexRequestCaptor.getValue().routing()).isEqualTo("foobar");
    assertThat(indexRequestCaptor.getValue().document()).isEqualTo(doc);
  }

  @Test
  public void shouldTransformIndexResponse() throws IOException {
    // given
    final var indexRequestCaptor = ArgumentCaptor.forClass(IndexRequest.class);
    final var indexResponse = createDefaultIndexResponse();
    when(client.index(indexRequestCaptor.capture())).thenReturn(indexResponse);

    final var doc = new TestDocument("test");
    final var searchIndexRequest =
        SearchIndexRequest.of(b -> b.id("foo").index("bar").routing("foobar").document(doc));

    // when
    final var response = searchClient.index(searchIndexRequest);

    // then
    assertThat(response).isNotNull();
    assertThat(response.id()).isEqualTo("foo");
    assertThat(response.index()).isEqualTo("bar");
    assertThat(response.result()).isEqualTo(SearchWriteResponse.Result.CREATED);
  }

  private SearchResponse<TestDocument> createDefaultSearchResponse() {
    return SearchResponse.of(
        (f) ->
            f.took(122)
                .hits(
                    HitsMetadata.of(
                        (m) ->
                            m.hits(new ArrayList<>())
                                .total((t) -> t.value(789).relation(TotalHitsRelation.Eq))))
                .shards((s) -> s.failed(0).successful(100).total(100))
                .timedOut(false));
  }

  private GetResponse<TestDocument> createDefaultGetResponse() {
    return GetResponse.of(
        b -> b.id("foo").index("bar").found(true).source(new TestDocument("123")));
  }

  private IndexResponse createDefaultIndexResponse() {
    return IndexResponse.of(
        b ->
            b.id("foo")
                .index("bar")
                .result(Result.Created)
                .primaryTerm(1L)
                .seqNo(1L)
                .version(1L)
                .shards(s -> s.total(1).successful(1).failed(0)));
  }

  record TestDocument(String id) {}
}
