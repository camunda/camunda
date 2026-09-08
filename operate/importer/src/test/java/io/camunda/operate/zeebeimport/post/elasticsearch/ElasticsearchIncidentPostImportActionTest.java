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
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.operate.entities.IncidentEntity;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.schema.templates.PostImporterQueueTemplate;
import io.camunda.operate.zeebeimport.post.AdditionalData;
import java.util.List;
import java.util.Map;
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
  private static final String LIST_VIEW_ALIAS = "operate-list-view-alias";
  private static final String LIST_VIEW_WRITE_INDEX = "operate-list-view-8.3.0_";

  private RestHighLevelClient esClient;
  private IndicesClient indicesClient;
  private PostImporterQueueTemplate postImporterQueueTemplate;
  private ListViewTemplate listViewTemplate;
  private ElasticsearchIncidentPostImportAction action;

  @Before
  public void setUp() {
    esClient = mock(RestHighLevelClient.class);
    indicesClient = mock(IndicesClient.class);
    postImporterQueueTemplate = mock(PostImporterQueueTemplate.class);
    listViewTemplate = mock(ListViewTemplate.class);

    when(esClient.indices()).thenReturn(indicesClient);
    when(postImporterQueueTemplate.getAlias()).thenReturn(QUEUE_ALIAS);
    when(postImporterQueueTemplate.getFullQualifiedName()).thenReturn(QUEUE_WRITE_INDEX);
    when(listViewTemplate.getAlias()).thenReturn(LIST_VIEW_ALIAS);
    when(listViewTemplate.getFullQualifiedName()).thenReturn(LIST_VIEW_WRITE_INDEX);

    action = new ElasticsearchIncidentPostImportAction(1);
    ReflectionTestUtils.setField(action, "esClient", esClient);
    ReflectionTestUtils.setField(action, "postImporterQueueTemplate", postImporterQueueTemplate);
    ReflectionTestUtils.setField(action, "listViewTemplate", listViewTemplate);
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

  @Test
  public void shouldOnlyMatchProcessInstanceDocumentsWhenLookingUpTreePaths() throws Exception {
    // given
    final ArgumentCaptor<SearchRequest> searchCaptor = ArgumentCaptor.forClass(SearchRequest.class);
    final SearchResponse searchResponse = emptySearchResponse();
    when(esClient.search(searchCaptor.capture(), any(RequestOptions.class)))
        .thenReturn(searchResponse);
    final IncidentEntity incident = new IncidentEntity().setProcessInstanceKey(123L);
    final AdditionalData data = new AdditionalData();

    // when
    ReflectionTestUtils.invokeMethod(action, "queryData", List.of(incident), data);

    // then - the list-view index holds both processInstance and activity documents behind the
    // same _id space; without this constraint the lookup can match an unrelated activity document
    final String query = searchCaptor.getValue().source().query().toString();
    assertTrue(query.contains("\"joinRelation\""));
    assertTrue(query.contains("\"processInstance\""));
  }

  @Test
  public void shouldNotFailWhenListViewLookupMatchesDocumentWithoutTreePath() throws Exception {
    // given - simulates the list-view index unexpectedly returning a document with no treePath
    // (e.g. an activity document slipping past the joinRelation filter above)
    final SearchHit hitWithoutTreePath = mock(SearchHit.class);
    when(hitWithoutTreePath.getId()).thenReturn("123");
    when(hitWithoutTreePath.getIndex()).thenReturn("operate-list-view-8.3.0_");
    when(hitWithoutTreePath.getSourceAsMap()).thenReturn(Map.of());
    final SearchResponse withHit = mock(SearchResponse.class);
    when(withHit.getHits())
        .thenReturn(
            new SearchHits(
                new SearchHit[] {hitWithoutTreePath},
                new TotalHits(1, TotalHits.Relation.EQUAL_TO),
                Float.NaN));
    final SearchResponse emptyFollowUp = emptySearchResponse();
    when(esClient.search(any(SearchRequest.class), any(RequestOptions.class))).thenReturn(withHit);
    when(esClient.scroll(any(), any(RequestOptions.class))).thenReturn(emptyFollowUp);
    final IncidentEntity incident = new IncidentEntity().setProcessInstanceKey(123L);
    final AdditionalData data = new AdditionalData();

    // when - must not throw NullPointerException (Collectors.toMap on a null value)
    ReflectionTestUtils.invokeMethod(action, "queryData", List.of(incident), data);

    // then - the document without a treePath is skipped rather than corrupting the batch
    assertTrue(data.getProcessInstanceTreePaths().isEmpty());
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
