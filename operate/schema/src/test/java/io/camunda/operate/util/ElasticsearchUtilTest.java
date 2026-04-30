/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.ClearScrollRequest;
import co.elastic.clients.elasticsearch.core.ScrollResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import co.elastic.clients.elasticsearch.core.search.ResponseBody;
import co.elastic.clients.elasticsearch.core.search.TotalHits;
import co.elastic.clients.elasticsearch.core.search.TotalHitsRelation;
import io.camunda.operate.store.ScrollException;
import java.io.IOException;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ElasticsearchUtilTest {

  @Mock private ElasticsearchClient esClient;

  @Test
  void shouldClearScrollWhenStreamIsClosedNormally() throws IOException {
    // given
    final String scrollId = "test-scroll-id";
    final SearchRequest.Builder searchRequestBuilder = new SearchRequest.Builder();

    final SearchResponse<TestEntity> searchResponse = mockSearchResponse(scrollId, List.of());

    when(esClient.search(any(SearchRequest.class), eq(TestEntity.class)))
        .thenReturn(searchResponse);

    // when - stream is used in try-with-resources and closed normally
    try (Stream<ResponseBody<TestEntity>> stream =
        ElasticsearchUtil.scrollAllStream(esClient, searchRequestBuilder, TestEntity.class)) {
      // consume the stream
      stream.count();
    }

    // then - clearScroll should have been called
    final ArgumentCaptor<ClearScrollRequest> captor =
        ArgumentCaptor.forClass(ClearScrollRequest.class);
    verify(esClient, times(1)).clearScroll(captor.capture());
    assertThat(captor.getValue().scrollId()).contains(scrollId);
  }

  @Test
  void shouldClearScrollWhenStreamIsTerminatedEarly() throws IOException {
    // given
    final String scrollId = "test-scroll-id";
    final SearchRequest.Builder searchRequestBuilder = new SearchRequest.Builder();

    final SearchResponse<TestEntity> searchResponse =
        mockSearchResponse(scrollId, List.of(mockHit("1")));
    final ScrollResponse<TestEntity> scrollResponse =
        mockScrollResponse(scrollId, List.of(mockHit("2")));

    when(esClient.search(any(SearchRequest.class), eq(TestEntity.class)))
        .thenReturn(searchResponse);
    when(esClient.scroll(any(Function.class), eq(TestEntity.class))).thenReturn(scrollResponse);

    // when - stream is terminated early with findFirst()
    try (Stream<ResponseBody<TestEntity>> stream =
        ElasticsearchUtil.scrollAllStream(esClient, searchRequestBuilder, TestEntity.class)) {
      stream.findFirst(); // this terminates the stream early
    }

    // then - clearScroll should have been called
    final ArgumentCaptor<ClearScrollRequest> captor =
        ArgumentCaptor.forClass(ClearScrollRequest.class);
    verify(esClient, times(1)).clearScroll(captor.capture());
    assertThat(captor.getValue().scrollId()).contains(scrollId);
  }

  @Test
  void shouldClearScrollWhenExceptionOccursDuringStreamProcessing() throws IOException {
    // given
    final String scrollId = "test-scroll-id";
    final SearchRequest.Builder searchRequestBuilder = new SearchRequest.Builder();

    final SearchResponse<TestEntity> searchResponse =
        mockSearchResponse(scrollId, List.of(mockHit("1")));

    when(esClient.search(any(SearchRequest.class), eq(TestEntity.class)))
        .thenReturn(searchResponse);
    when(esClient.scroll(any(Function.class), eq(TestEntity.class)))
        .thenThrow(new IOException("Network error"));

    // when/then - exception during stream processing
    assertThatThrownBy(
            () -> {
              try (Stream<ResponseBody<TestEntity>> stream =
                  ElasticsearchUtil.scrollAllStream(
                      esClient, searchRequestBuilder, TestEntity.class)) {
                // force evaluation of the stream which will trigger the scroll and exception
                stream.count();
              }
            })
        .isInstanceOf(ScrollException.class)
        .hasMessageContaining("Error during scroll");

    // then - clearScroll should have been called from both the catch block and onClose
    final ArgumentCaptor<ClearScrollRequest> captor =
        ArgumentCaptor.forClass(ClearScrollRequest.class);
    verify(esClient, times(2)).clearScroll(captor.capture());
    assertThat(captor.getAllValues()).allSatisfy(req -> assertThat(req.scrollId()).isNotEmpty());
  }

  @Test
  void shouldClearScrollWhenStreamIsPartiallyConsumed() throws IOException {
    // given
    final String scrollId = "test-scroll-id";
    final SearchRequest.Builder searchRequestBuilder = new SearchRequest.Builder();

    final SearchResponse<TestEntity> searchResponse =
        mockSearchResponse(scrollId, List.of(mockHit("1")));
    final ScrollResponse<TestEntity> scrollResponse =
        mockScrollResponse(scrollId, List.of(mockHit("2")));

    when(esClient.search(any(SearchRequest.class), eq(TestEntity.class)))
        .thenReturn(searchResponse);
    when(esClient.scroll(any(Function.class), eq(TestEntity.class))).thenReturn(scrollResponse);

    // when - stream is partially consumed then closed
    try (Stream<ResponseBody<TestEntity>> stream =
        ElasticsearchUtil.scrollAllStream(esClient, searchRequestBuilder, TestEntity.class)) {
      stream.limit(1).count(); // only consume first element
    }

    // then - clearScroll should have been called
    final ArgumentCaptor<ClearScrollRequest> captor =
        ArgumentCaptor.forClass(ClearScrollRequest.class);
    verify(esClient, times(1)).clearScroll(captor.capture());
    assertThat(captor.getValue().scrollId()).contains(scrollId);
  }

  @Test
  void shouldClearScrollWhenDownstreamRuntimeExceptionOccurs() throws IOException {
    // given
    final String scrollId = "test-scroll-id";
    final SearchRequest.Builder searchRequestBuilder = new SearchRequest.Builder();

    final SearchResponse<TestEntity> searchResponse =
        mockSearchResponse(scrollId, List.of(mockHit("1")));
    final ScrollResponse<TestEntity> scrollResponse =
        mockScrollResponse(scrollId, List.of(mockHit("2")));

    when(esClient.search(any(SearchRequest.class), eq(TestEntity.class)))
        .thenReturn(searchResponse);
    when(esClient.scroll(any(Function.class), eq(TestEntity.class))).thenReturn(scrollResponse);

    // when - RuntimeException is thrown from downstream consumer after first page
    assertThatThrownBy(
            () -> {
              try (Stream<ResponseBody<TestEntity>> stream =
                  ElasticsearchUtil.scrollAllStream(
                      esClient, searchRequestBuilder, TestEntity.class)) {
                stream
                    .peek(
                        res -> {
                          // throw after processing first response to simulate callback error
                          if (!res.hits().hits().isEmpty()) {
                            throw new RuntimeException("Callback processing error");
                          }
                        })
                    .forEach(r -> {}); // force stream evaluation
              }
            })
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Callback processing error");

    // then - clearScroll should have been called from onClose
    final ArgumentCaptor<ClearScrollRequest> captor =
        ArgumentCaptor.forClass(ClearScrollRequest.class);
    verify(esClient, times(1)).clearScroll(captor.capture());
    assertThat(captor.getValue().scrollId()).contains(scrollId);
  }

  @Test
  void shouldClearScrollWhenStreamIsNeverConsumed() throws IOException {
    // given
    final String scrollId = "test-scroll-id";
    final SearchRequest.Builder searchRequestBuilder = new SearchRequest.Builder();

    final SearchResponse<TestEntity> searchResponse = mockSearchResponse(scrollId, List.of());

    when(esClient.search(any(SearchRequest.class), eq(TestEntity.class)))
        .thenReturn(searchResponse);

    // when - stream is created but never consumed (this would be a caller bug)
    try (Stream<ResponseBody<TestEntity>> stream =
        ElasticsearchUtil.scrollAllStream(esClient, searchRequestBuilder, TestEntity.class)) {
      // stream created but not consumed
    }

    // then - clearScroll should still be called from onClose
    final ArgumentCaptor<ClearScrollRequest> captor =
        ArgumentCaptor.forClass(ClearScrollRequest.class);
    verify(esClient, times(1)).clearScroll(captor.capture());
    assertThat(captor.getValue().scrollId()).contains(scrollId);
  }

  private SearchResponse<TestEntity> mockSearchResponse(
      final String scrollId, final List<Hit<TestEntity>> hits) {
    final SearchResponse<TestEntity> response = mock(SearchResponse.class);
    when(response.scrollId()).thenReturn(scrollId);

    final HitsMetadata<TestEntity> hitsMetadata = mock(HitsMetadata.class);
    when(hitsMetadata.hits()).thenReturn(hits);
    when(response.hits()).thenReturn(hitsMetadata);

    final TotalHits totalHits = mock(TotalHits.class);
    when(totalHits.value()).thenReturn((long) hits.size());
    when(totalHits.relation()).thenReturn(TotalHitsRelation.Eq);
    when(hitsMetadata.total()).thenReturn(totalHits);

    return response;
  }

  private ScrollResponse<TestEntity> mockScrollResponse(
      final String scrollId, final List<Hit<TestEntity>> hits) {
    final ScrollResponse<TestEntity> response = mock(ScrollResponse.class);
    when(response.scrollId()).thenReturn(scrollId);

    final HitsMetadata<TestEntity> hitsMetadata = mock(HitsMetadata.class);
    when(hitsMetadata.hits()).thenReturn(hits);
    when(response.hits()).thenReturn(hitsMetadata);

    return response;
  }

  private Hit<TestEntity> mockHit(final String id) {
    final Hit<TestEntity> hit = mock(Hit.class);
    when(hit.id()).thenReturn(id);
    when(hit.source()).thenReturn(new TestEntity(id));
    return hit;
  }

  // Simple test entity class
  static class TestEntity {
    private String id;

    public TestEntity(final String id) {
      this.id = id;
    }

    public String getId() {
      return id;
    }
  }
}
