/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.zeebeimport.post.opensearch;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.operate.entities.IncidentEntity;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.schema.templates.PostImporterQueueTemplate;
import io.camunda.operate.store.opensearch.client.sync.OpenSearchDocumentOperations;
import io.camunda.operate.store.opensearch.client.sync.OpenSearchIndexOperations;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.zeebeimport.post.AdditionalData;
import jakarta.json.stream.JsonGenerator;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.opensearch.client.json.JsonpSerializable;
import org.opensearch.client.json.jsonb.JsonbJsonpMapper;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.TotalHitsRelation;
import org.springframework.test.util.ReflectionTestUtils;

public class OpensearchIncidentPostImportActionTest {

  private static final String QUEUE_ALIAS = "operate-post-importer-queue-alias";
  private static final String QUEUE_WRITE_INDEX = "operate-post-importer-queue-8.7.0_";
  private static final String LIST_VIEW_ALIAS = "operate-list-view-alias";
  private static final String LIST_VIEW_WRITE_INDEX = "operate-list-view-8.3.0_";

  private RichOpenSearchClient richOpenSearchClient;
  private OpenSearchIndexOperations indexOperations;
  private OpenSearchDocumentOperations documentOperations;
  private PostImporterQueueTemplate postImporterQueueTemplate;
  private ListViewTemplate listViewTemplate;
  private OpensearchIncidentPostImportAction action;

  @Before
  public void setUp() {
    richOpenSearchClient = mock(RichOpenSearchClient.class);
    indexOperations = mock(OpenSearchIndexOperations.class);
    documentOperations = mock(OpenSearchDocumentOperations.class);
    postImporterQueueTemplate = mock(PostImporterQueueTemplate.class);
    listViewTemplate = mock(ListViewTemplate.class);

    when(richOpenSearchClient.index()).thenReturn(indexOperations);
    when(richOpenSearchClient.doc()).thenReturn(documentOperations);
    when(postImporterQueueTemplate.getAlias()).thenReturn(QUEUE_ALIAS);
    when(postImporterQueueTemplate.getFullQualifiedName()).thenReturn(QUEUE_WRITE_INDEX);
    when(listViewTemplate.getAlias()).thenReturn(LIST_VIEW_ALIAS);
    when(listViewTemplate.getFullQualifiedName()).thenReturn(LIST_VIEW_WRITE_INDEX);

    action = new OpensearchIncidentPostImportAction(1);
    ReflectionTestUtils.setField(action, "richOpenSearchClient", richOpenSearchClient);
    ReflectionTestUtils.setField(action, "postImporterQueueTemplate", postImporterQueueTemplate);
    ReflectionTestUtils.setField(action, "listViewTemplate", listViewTemplate);
    ReflectionTestUtils.setField(action, "operateProperties", new OperateProperties());
  }

  @Test
  public void shouldOnlyMatchProcessInstanceDocumentsWhenLookingUpTreePaths() throws Exception {
    // given
    final ArgumentCaptor<SearchRequest.Builder> searchCaptor =
        ArgumentCaptor.forClass(SearchRequest.Builder.class);
    final IncidentEntity incident = new IncidentEntity().setProcessInstanceKey(123L);
    final AdditionalData data = new AdditionalData();

    // when
    ReflectionTestUtils.invokeMethod(action, "queryData", List.of(incident), data);

    // then - the list-view index holds both processInstance and activity documents behind the
    // same _id space; without this constraint the lookup can match an unrelated activity document
    verify(documentOperations).scrollWith(searchCaptor.capture(), any(), any());
    final String query = json(searchCaptor.getValue().build().query());
    assertTrue(query.contains("joinRelation"));
    assertTrue(query.contains("processInstance"));
  }

  @Test
  public void shouldRefreshPostImporterQueueIndexBeforeReadingBatch() {
    // given
    doReturn(emptySearchResponse()).when(documentOperations).search(any(), any());

    // when
    action.getPendingIncidents(new AdditionalData(), 0L);

    // then - the queue write index is refreshed before the batch search runs, so a lagging shard
    // exposes its writes and no pending entry is skipped by the forward-only cursor
    final InOrder inOrder = Mockito.inOrder(indexOperations, documentOperations);
    inOrder.verify(indexOperations).refreshWithFailOnPartial(QUEUE_WRITE_INDEX);
    inOrder.verify(documentOperations).search(any(), any());
  }

  @Test
  public void shouldFailBatchWithoutSearchingWhenRefreshFails() {
    // given
    doThrow(new OperateRuntimeException("refresh failed"))
        .when(indexOperations)
        .refreshWithFailOnPartial(anyString());

    // when / then - a failed refresh must abort the batch (and let it retry) rather than search a
    // potentially stale, partially-refreshed index and advance the cursor past unseen entries
    assertThrows(
        OperateRuntimeException.class, () -> action.getPendingIncidents(new AdditionalData(), 0L));
    verify(documentOperations, never()).search(any(), any());
  }

  @Test
  public void shouldDisablePartialResultsOnPendingBatchSearch() {
    // given
    final ArgumentCaptor<SearchRequest.Builder> searchCaptor =
        ArgumentCaptor.forClass(SearchRequest.Builder.class);
    doReturn(emptySearchResponse()).when(documentOperations).search(searchCaptor.capture(), any());

    // when
    action.getPendingIncidents(new AdditionalData(), 0L);

    // then - an unavailable shard must fail the search (triggering a retry) instead of silently
    // returning partial hits and letting the cursor skip the entries on the missing shard
    assertFalse(searchCaptor.getValue().build().allowPartialSearchResults());
  }

  private SearchResponse<Object> emptySearchResponse() {
    return new SearchResponse.Builder<Object>()
        .took(1)
        .timedOut(false)
        .shards(s -> s.total(1).successful(1).failed(0))
        .hits(h -> h.total(t -> t.value(0).relation(TotalHitsRelation.Eq)).hits(List.of()))
        .build();
  }

  private String json(final JsonpSerializable serializable) {
    final ByteArrayOutputStream baos = new ByteArrayOutputStream();
    final JsonbJsonpMapper mapper = new JsonbJsonpMapper();
    try (JsonGenerator generator = mapper.jsonProvider().createGenerator(baos)) {
      serializable.serialize(generator, mapper);
    }
    return baos.toString(StandardCharsets.UTF_8);
  }
}
