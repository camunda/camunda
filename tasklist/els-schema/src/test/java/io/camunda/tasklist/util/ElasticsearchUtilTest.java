/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.tasklist.exceptions.PersistenceException;
import java.io.IOException;
import org.apache.lucene.search.TotalHits;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.ClearScrollRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchScrollRequest;
import org.elasticsearch.action.support.WriteRequest.RefreshPolicy;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.xcontent.XContentType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ElasticsearchUtilTest {

  @Captor private ArgumentCaptor<BulkRequest> bulkRequestCaptor;

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void testProcessBulkRequestRequestNotDividedWhenSizeIsLessThanOrEqualToMaxSize(
      final boolean exactSize) throws Exception {
    // given
    final RestHighLevelClient esClient = mock(RestHighLevelClient.class);
    final BulkResponse bulkResponse = mock(BulkResponse.class);
    when(bulkResponse.getItems()).thenReturn(new org.elasticsearch.action.bulk.BulkItemResponse[0]);
    when(esClient.bulk(any(BulkRequest.class), eq(RequestOptions.DEFAULT)))
        .thenReturn(bulkResponse);

    final BulkRequest bulkRequest = new BulkRequest();
    bulkRequest.add(
        new IndexRequest("test-index").id("1").source("{\"field\":\"value\"}", XContentType.JSON));

    final long maxBulkRequestSize =
        exactSize ? bulkRequest.estimatedSizeInBytes() : 1024 * 1024 * 90; // exact or 90 MB

    // when
    ElasticsearchUtil.processBulkRequest(
        esClient, bulkRequest, RefreshPolicy.NONE, maxBulkRequestSize);

    // then
    verify(esClient, times(1)).bulk(bulkRequestCaptor.capture(), eq(RequestOptions.DEFAULT));
    assertThat(bulkRequestCaptor.getAllValues()).hasSize(1);
    final BulkRequest capturedRequest = bulkRequestCaptor.getValue();
    assertThat(capturedRequest.requests()).hasSize(1);
  }

  @Test
  void testProcessBulkRequestRequestIsDividedWhenSizeIsGreaterThanMaxSize() throws Exception {
    // given
    final RestHighLevelClient esClient = mock(RestHighLevelClient.class);
    final BulkResponse bulkResponse = mock(BulkResponse.class);
    when(bulkResponse.getItems()).thenReturn(new org.elasticsearch.action.bulk.BulkItemResponse[0]);
    when(esClient.bulk(any(BulkRequest.class), eq(RequestOptions.DEFAULT)))
        .thenReturn(bulkResponse);

    final BulkRequest bulkRequest = new BulkRequest();
    // Add multiple requests to exceed the max size
    for (int i = 0; i < 10; i++) {
      bulkRequest.add(
          new IndexRequest("test-index")
              .id(String.valueOf(i))
              .source("{\"field\":\"value" + i + "\"}", XContentType.JSON));
    }

    // Set max size to be small enough to force division
    final long maxBulkRequestSize = bulkRequest.estimatedSizeInBytes() / 3;

    // when
    ElasticsearchUtil.processBulkRequest(
        esClient, bulkRequest, RefreshPolicy.NONE, maxBulkRequestSize);

    // then - should be called multiple times (at least 2)
    verify(esClient, atLeast(2)).bulk(bulkRequestCaptor.capture(), eq(RequestOptions.DEFAULT));
    assertThat(bulkRequestCaptor.getAllValues()).hasSizeGreaterThanOrEqualTo(2);

    // Verify all requests were processed
    int totalRequestsProcessed = 0;
    for (final BulkRequest capturedRequest : bulkRequestCaptor.getAllValues()) {
      totalRequestsProcessed += capturedRequest.requests().size();
    }
    assertThat(totalRequestsProcessed).isEqualTo(10);
  }

  @Test
  void testProcessBulkRequestThrowsExceptionWhenSingleRequestExceedsMaxSize() throws Exception {
    // given
    final RestHighLevelClient esClient = mock(RestHighLevelClient.class);

    final BulkRequest bulkRequest = new BulkRequest();
    // Create a large document
    final StringBuilder largeContent = new StringBuilder("{\"field\":\"");
    for (int i = 0; i < 100000; i++) {
      largeContent.append("x");
    }
    largeContent.append("\"}");

    bulkRequest.add(
        new IndexRequest("test-index").id("1").source(largeContent.toString(), XContentType.JSON));

    // Set max size to be smaller than the single request
    final long maxBulkRequestSize = 1024; // 1 KB

    // when/then
    assertThatThrownBy(
            () ->
                ElasticsearchUtil.processBulkRequest(
                    esClient, bulkRequest, RefreshPolicy.NONE, maxBulkRequestSize))
        .isInstanceOf(PersistenceException.class)
        .hasMessageContaining("greater than max allowed");
  }

  @Test
  void shouldClearScrollWhenProcessorThrowsInScrollWith() throws Exception {
    final RestHighLevelClient esClient = mock(RestHighLevelClient.class);
    final SearchResponse searchResponse = mock(SearchResponse.class);
    final SearchHit hit = mock(SearchHit.class);
    final SearchHits hits =
        new SearchHits(new SearchHit[] {hit}, new TotalHits(1, TotalHits.Relation.EQUAL_TO), 1.0f);
    when(searchResponse.getHits()).thenReturn(hits);
    when(searchResponse.getScrollId()).thenReturn("scroll-id-1");
    when(esClient.search(any(SearchRequest.class), any())).thenReturn(searchResponse);

    final SearchRequest request = new SearchRequest("some-alias");
    assertThatThrownBy(
            () ->
                ElasticsearchUtil.scrollWith(
                    request,
                    esClient,
                    h -> {
                      throw new RuntimeException("processor failure");
                    },
                    null,
                    null))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("processor failure");

    final ArgumentCaptor<ClearScrollRequest> clearCaptor =
        ArgumentCaptor.forClass(ClearScrollRequest.class);
    verify(esClient).clearScroll(clearCaptor.capture(), any());
    assertThat(clearCaptor.getValue().getScrollIds()).containsExactly("scroll-id-1");
  }

  @Test
  void shouldClearScrollWhenScrollCallThrowsInScrollWith() throws Exception {
    final RestHighLevelClient esClient = mock(RestHighLevelClient.class);
    final SearchResponse searchResponse = mock(SearchResponse.class);
    final SearchHit hit = mock(SearchHit.class);
    final SearchHits hits =
        new SearchHits(new SearchHit[] {hit}, new TotalHits(1, TotalHits.Relation.EQUAL_TO), 1.0f);
    when(searchResponse.getHits()).thenReturn(hits);
    when(searchResponse.getScrollId()).thenReturn("scroll-id-2");
    when(esClient.search(any(SearchRequest.class), any())).thenReturn(searchResponse);
    when(esClient.scroll(any(SearchScrollRequest.class), any()))
        .thenThrow(new IOException("network boom"));

    final SearchRequest request = new SearchRequest("some-alias");
    assertThatThrownBy(() -> ElasticsearchUtil.scrollWith(request, esClient, h -> {}, null, null))
        .isInstanceOf(IOException.class)
        .hasMessage("network boom");

    final ArgumentCaptor<ClearScrollRequest> clearCaptor =
        ArgumentCaptor.forClass(ClearScrollRequest.class);
    verify(esClient).clearScroll(clearCaptor.capture(), any());
    assertThat(clearCaptor.getValue().getScrollIds()).containsExactly("scroll-id-2");
  }

  @Test
  void testProcessBulkRequestHandlesIOException() throws Exception {
    // given
    final RestHighLevelClient esClient = mock(RestHighLevelClient.class);
    when(esClient.bulk(any(BulkRequest.class), eq(RequestOptions.DEFAULT)))
        .thenThrow(new IOException("Connection error"));

    final BulkRequest bulkRequest = new BulkRequest();
    bulkRequest.add(
        new IndexRequest("test-index").id("1").source("{\"field\":\"value\"}", XContentType.JSON));

    final long maxBulkRequestSize = 1024 * 1024 * 90; // 90 MB

    // when/then
    assertThatThrownBy(
            () ->
                ElasticsearchUtil.processBulkRequest(
                    esClient, bulkRequest, RefreshPolicy.NONE, maxBulkRequestSize))
        .isInstanceOf(PersistenceException.class)
        .hasMessageContaining("Error when processing bulk request against Elasticsearch");
  }
}
