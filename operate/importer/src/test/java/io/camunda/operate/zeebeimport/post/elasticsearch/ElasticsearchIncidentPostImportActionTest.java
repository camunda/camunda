/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.zeebeimport.post.elasticsearch;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.templates.PostImporterQueueTemplate;
import io.camunda.operate.zeebeimport.post.AdditionalData;
import org.apache.lucene.search.TotalHits;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.admin.indices.refresh.RefreshResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.IndicesClient;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

public class ElasticsearchIncidentPostImportActionTest {

  private static final String QUEUE_ALIAS = "operate-post-importer-queue-alias";
  private static final String QUEUE_WRITE_INDEX = "operate-post-importer-queue-8.7.0_";

  private RestHighLevelClient esClient;
  private IndicesClient indicesClient;
  private PostImporterQueueTemplate postImporterQueueTemplate;
  private ElasticsearchIncidentPostImportAction action;

  @Before
  public void setUp() {
    esClient = mock(RestHighLevelClient.class);
    indicesClient = mock(IndicesClient.class);
    postImporterQueueTemplate = mock(PostImporterQueueTemplate.class);

    when(esClient.indices()).thenReturn(indicesClient);
    when(postImporterQueueTemplate.getAlias()).thenReturn(QUEUE_ALIAS);
    when(postImporterQueueTemplate.getFullQualifiedName()).thenReturn(QUEUE_WRITE_INDEX);

    action = new ElasticsearchIncidentPostImportAction(1);
    ReflectionTestUtils.setField(action, "esClient", esClient);
    ReflectionTestUtils.setField(action, "postImporterQueueTemplate", postImporterQueueTemplate);
    ReflectionTestUtils.setField(action, "operateProperties", new OperateProperties());
  }

  @Test
  public void shouldRefreshPostImporterQueueIndexBeforeReadingBatch() throws Exception {
    // given
    final RefreshResponse refreshResponse = refreshResponse(0);
    final SearchResponse searchResponse = emptySearchResponse();
    when(indicesClient.refresh(any(RefreshRequest.class), any(RequestOptions.class)))
        .thenReturn(refreshResponse);
    when(esClient.search(any(SearchRequest.class), any(RequestOptions.class)))
        .thenReturn(searchResponse);

    // when
    action.getPendingIncidents(new AdditionalData(), 0L);

    // then - the queue write index is refreshed before the batch search runs, so a lagging shard
    // exposes its writes and no pending entry is skipped by the forward-only cursor
    final InOrder inOrder = Mockito.inOrder(indicesClient, esClient);
    inOrder.verify(indicesClient).refresh(any(RefreshRequest.class), any(RequestOptions.class));
    inOrder.verify(esClient).search(any(SearchRequest.class), any(RequestOptions.class));
  }

  @Test
  public void shouldFailBatchWithoutSearchingWhenRefreshReportsShardFailures() throws Exception {
    // given
    final RefreshResponse refreshResponse = refreshResponse(1);
    when(indicesClient.refresh(any(RefreshRequest.class), any(RequestOptions.class)))
        .thenReturn(refreshResponse);

    // when / then - a partial refresh must abort the batch (and let it retry) rather than search a
    // potentially stale, partially-refreshed index and advance the cursor past unseen entries
    assertThrows(
        OperateRuntimeException.class, () -> action.getPendingIncidents(new AdditionalData(), 0L));
    verify(esClient, never()).search(any(SearchRequest.class), any(RequestOptions.class));
  }

  @Test
  public void shouldDisablePartialResultsOnPendingBatchSearch() throws Exception {
    // given
    final RefreshResponse refreshResponse = refreshResponse(0);
    final SearchResponse searchResponse = emptySearchResponse();
    when(indicesClient.refresh(any(RefreshRequest.class), any(RequestOptions.class)))
        .thenReturn(refreshResponse);
    final ArgumentCaptor<SearchRequest> searchCaptor = ArgumentCaptor.forClass(SearchRequest.class);
    when(esClient.search(searchCaptor.capture(), any(RequestOptions.class)))
        .thenReturn(searchResponse);

    // when
    action.getPendingIncidents(new AdditionalData(), 0L);

    // then - an unavailable shard must fail the search (triggering a retry) instead of silently
    // returning partial hits and letting the cursor skip the entries on the missing shard
    assertFalse(searchCaptor.getValue().allowPartialSearchResults());
  }

  private RefreshResponse refreshResponse(final int failedShards) {
    final RefreshResponse response = mock(RefreshResponse.class);
    when(response.getFailedShards()).thenReturn(failedShards);
    return response;
  }

  private SearchResponse emptySearchResponse() {
    final SearchResponse response = mock(SearchResponse.class);
    final SearchHits hits =
        new SearchHits(new SearchHit[0], new TotalHits(0, TotalHits.Relation.EQUAL_TO), Float.NaN);
    when(response.getHits()).thenReturn(hits);
    return response;
  }
}
