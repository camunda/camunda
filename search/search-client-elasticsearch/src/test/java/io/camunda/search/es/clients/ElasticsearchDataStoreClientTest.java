/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.es.clients;

import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.Result;
import co.elastic.clients.elasticsearch.core.GetRequest;
import co.elastic.clients.elasticsearch.core.GetResponse;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import co.elastic.clients.elasticsearch.core.search.TotalHitsRelation;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.TransportOptions;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.camunda.search.clients.core.SearchGetRequest;
import io.camunda.search.clients.core.SearchIndexRequest;
import io.camunda.search.clients.core.SearchQueryRequest;
import io.camunda.search.clients.core.SearchQueryResponse;
import io.camunda.search.clients.core.SearchWriteResponse;
import io.camunda.search.es.transformers.ElasticsearchTransformers;
import java.io.IOException;
import java.util.ArrayList;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.ArgumentCaptor;

public class ElasticsearchDataStoreClientTest {

  @RegisterExtension
  static final WireMockExtension WIRE_MOCK =
      WireMockExtension.newInstance()
          .options(WireMockConfiguration.wireMockConfig().dynamicPort())
          .build();

  private static final long TEST_HITS = 789;
  private static final TotalHitsRelation HITS_RELATION = TotalHitsRelation.Eq;
  private static final long CAPPED_HITS = 10_000;
  private static final TotalHitsRelation CAPPED_HITS_RELATION = TotalHitsRelation.Gte;
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
    final var searchResponse = createDefaultSearchResponse(TEST_HITS, HITS_RELATION);
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
    final var searchResponse = createDefaultSearchResponse(TEST_HITS, HITS_RELATION);
    when(client.search(searchRequestCaptor.capture(), eq(TestDocument.class)))
        .thenReturn(searchResponse);

    final SearchQueryRequest request =
        SearchQueryRequest.of(b -> b.index("operate-list-view-8.3.0_").size(1));

    // when
    final var response = searchClient.search(request, TestDocument.class);

    // then
    assertThat(response).isNotNull();
    assertThat(response.totalHits()).isEqualTo(TEST_HITS);
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
    final var mockTransport = mock(ElasticsearchTransport.class);
    final var mockOptions = mock(TransportOptions.class);
    when(client._transport()).thenReturn(mockTransport);
    when(client._transportOptions()).thenReturn(mockOptions);
    when(mockTransport.performRequest(indexRequestCaptor.capture(), any(), any()))
        .thenReturn(createDefaultIndexResponse());

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
    final var mockTransport = mock(ElasticsearchTransport.class);
    final var mockOptions = mock(TransportOptions.class);
    when(client._transport()).thenReturn(mockTransport);
    when(client._transportOptions()).thenReturn(mockOptions);
    when(mockTransport.performRequest(any(), any(), any()))
        .thenReturn(createDefaultIndexResponse());

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

  @Test
  public void shouldHaveMoreTotalItemsFalse() throws IOException {
    // given
    final var searchRequestCaptor = ArgumentCaptor.forClass(SearchRequest.class);
    final var searchResponse = createDefaultSearchResponse(TEST_HITS, HITS_RELATION);
    when(client.search(searchRequestCaptor.capture(), eq(TestDocument.class)))
        .thenReturn(searchResponse);

    final SearchQueryRequest request =
        SearchQueryRequest.of(b -> b.index("operate-list-view-8.3.0_").size(1));

    // when
    final SearchQueryResponse<TestDocument> response =
        searchClient.search(request, TestDocument.class);

    // then
    assertThat(response).isNotNull();
    assertThat(response.hasMoreTotalItems()).isFalse();
  }

  @Test
  public void shouldHaveMoreTotalItemsTrue() throws IOException {
    // given
    final var searchResponse = createDefaultSearchResponse(CAPPED_HITS, CAPPED_HITS_RELATION);
    when(client.search((SearchRequest) any(), eq(TestDocument.class))).thenReturn(searchResponse);

    final SearchQueryRequest request =
        SearchQueryRequest.of(b -> b.index("operate-list-view-8.3.0_").size(1));

    // when
    final SearchQueryResponse<TestDocument> response =
        searchClient.search(request, TestDocument.class);

    // then
    assertThat(response).isNotNull();
    assertThat(response.hasMoreTotalItems()).isTrue();
  }

  @Test
  void shouldUseAliasAwareEndpointWhenIndexing() throws IOException, ElasticsearchException {
    // given
    final var mockTransport = mock(ElasticsearchTransport.class);
    final var mockOptions = mock(TransportOptions.class);
    when(client._transport()).thenReturn(mockTransport);
    when(client._transportOptions()).thenReturn(mockOptions);
    when(mockTransport.performRequest(any(), any(), any()))
        .thenReturn(createDefaultIndexResponse());

    final var doc = new TestDocument("test");
    final var searchIndexRequest =
        SearchIndexRequest.of(b -> b.id("foo").index("bar").document(doc));

    // when
    searchClient.index(searchIndexRequest);

    // then - the custom endpoint (not IndexRequest._ENDPOINT) must be passed to the transport
    verify(mockTransport)
        .performRequest(any(), same(AliasAwareIndexResponseDeserializer.ENDPOINT), any());
  }

  @Test
  void shouldIndexWithUnderscorePrefixedShardFailureFields() {
    // given - ES 8.16.6 server returns _shard/_index/_node aliases in failures (issue #45809)
    WIRE_MOCK.stubFor(
        put(urlPathEqualTo("/my-index/_doc/doc1"))
            .willReturn(
                ok().withHeader("X-Elastic-Product", "Elasticsearch")
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {
                          "_id": "doc1",
                          "_index": "my-index",
                          "result": "created",
                          "_primary_term": 1,
                          "_seq_no": 0,
                          "_version": 1,
                          "_shards": {
                            "total": 2,
                            "successful": 1,
                            "failed": 1,
                            "failures": [
                              {
                                "_shard": 1,
                                "_index": "my-index",
                                "_node": "node1",
                                "reason": {"type": "i_o_exception", "reason": "disk full"},
                                "status": "INTERNAL_SERVER_ERROR"
                              }
                            ]
                          }
                        }
                        """)));

    final var request =
        SearchIndexRequest.of(b -> b.id("doc1").index("my-index").document("payload"));

    // when / then - must not throw MissingRequiredPropertyException for unrecognized _shard alias
    assertThatNoException()
        .isThrownBy(
            () -> {
              try (final ElasticsearchSearchClient elasticsearchSearchClient =
                  buildWireMockSearchClient()) {
                final var response = elasticsearchSearchClient.index(request);
                assertThat(response.id()).isEqualTo("doc1");
                assertThat(response.index()).isEqualTo("my-index");
              }
            });
  }

  @Test
  void shouldIndexWithCanonicalShardFields() {
    // given - canonical (non-underscore) field names still deserialize correctly
    WIRE_MOCK.stubFor(
        put(urlPathEqualTo("/my-index/_doc/doc2"))
            .willReturn(
                ok().withHeader("X-Elastic-Product", "Elasticsearch")
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {
                          "_id": "doc2",
                          "_index": "my-index",
                          "result": "updated",
                          "_primary_term": 1,
                          "_seq_no": 0,
                          "_version": 1,
                          "_shards": {
                            "total": 2,
                            "successful": 1,
                            "failed": 1,
                            "failures": [
                              {
                                "shard": 1,
                                "index": "my-index",
                                "node": "node1",
                                "reason": {"type": "i_o_exception", "reason": "disk full"},
                                "status": "INTERNAL_SERVER_ERROR"
                              }
                            ]
                          }
                        }
                        """)));

    final var request =
        SearchIndexRequest.of(b -> b.id("doc2").index("my-index").document("payload"));

    assertThatNoException()
        .isThrownBy(
            () -> {
              try (final ElasticsearchSearchClient elasticsearchSearchClient =
                  buildWireMockSearchClient()) {
                final var response = elasticsearchSearchClient.index(request);
                assertThat(response.id()).isEqualTo("doc2");
                assertThat(response.result()).isEqualTo(SearchWriteResponse.Result.UPDATED);
              }
            });
  }

  private ElasticsearchSearchClient buildWireMockSearchClient() {
    final var restClient =
        RestClient.builder(new HttpHost("localhost", WIRE_MOCK.getPort())).build();
    final var transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
    return new ElasticsearchSearchClient(
        new ElasticsearchClient(transport), new ElasticsearchTransformers());
  }

  private SearchResponse<TestDocument> createDefaultSearchResponse(
      final long totalHits, final TotalHitsRelation totalHitsRelation) {
    return SearchResponse.of(
        (f) ->
            f.took(122)
                .hits(
                    HitsMetadata.of(
                        (m) ->
                            m.hits(new ArrayList<>())
                                .total((t) -> t.value(totalHits).relation(totalHitsRelation))))
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
