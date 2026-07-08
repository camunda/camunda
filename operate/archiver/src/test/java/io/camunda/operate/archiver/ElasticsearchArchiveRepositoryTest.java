/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.archiver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.Metrics;
import io.camunda.operate.archiver.ArchiveByIdTaskSupplier.IdWithRouting;
import io.camunda.operate.property.ArchiverProperties;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.templates.BatchOperationTemplate;
import io.camunda.operate.schema.templates.DecisionInstanceTemplate;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.util.ElasticsearchUtil;
import io.micrometer.core.instrument.Timer;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.IndicesClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.common.document.DocumentField;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@ExtendWith(MockitoExtension.class)
public class ElasticsearchArchiveRepositoryTest {
  private static final List<Integer> PARTITION_IDS = List.of(1, 2);

  @Mock protected ThreadPoolTaskScheduler threadPoolTaskScheduler;
  @InjectMocks ElasticsearchArchiverRepository underTest;
  @Mock private BatchOperationTemplate batchOperationTemplate;
  @Mock private ListViewTemplate listViewTemplate;
  @Mock private DecisionInstanceTemplate decisionInstanceTemplate;
  @Mock private OperateProperties operateProperties;
  @Mock private Metrics metrics;
  @Mock private RestHighLevelClient esClient;
  @Mock private ObjectMapper objectMapper;

  @Mock private ArchiverProperties archiverProperties;

  @Test
  public void testSetIndexLifeCycle() throws ClassNotFoundException, IOException {
    final IndicesClient indicesClient = mock(IndicesClient.class);
    when(operateProperties.getArchiver()).thenReturn(archiverProperties);
    when(esClient.indices()).thenReturn(indicesClient);
    when(indicesClient.exists((GetIndexRequest) any(), any())).thenReturn(true);
    when(archiverProperties.isIlmEnabled()).thenReturn(true);
    underTest.setIndexLifeCycle("destinationIndexName");

    verify(archiverProperties).isIlmEnabled();
    assertThat(archiverProperties.isIlmEnabled()).isTrue();
    verify(esClient, times(2)).indices();
    verify(indicesClient).putSettings(any(), any());
    verify(indicesClient).exists((GetIndexRequest) any(), any());
  }

  @Test
  public void testSetIndexLifeCycleNoIndex() throws IOException {
    final IndicesClient indicesClient = mock(IndicesClient.class);
    when(operateProperties.getArchiver()).thenReturn(archiverProperties);
    when(archiverProperties.isIlmEnabled()).thenReturn(true);
    when(esClient.indices()).thenReturn(indicesClient);
    when(indicesClient.exists((GetIndexRequest) any(), any())).thenReturn(false);
    underTest.setIndexLifeCycle("destinationIndexName");

    verify(archiverProperties).isIlmEnabled();
    assertThat(archiverProperties.isIlmEnabled()).isTrue();
    verify(esClient).indices();
    verify(indicesClient, times(0)).putSettings(any(), any());
  }

  @Test
  public void testDeleteDocuments() {
    try (final MockedStatic<ElasticsearchUtil> mockedStatic = mockStatic(ElasticsearchUtil.class)) {
      try (final MockedStatic<Timer> mockedTimer = mockStatic(Timer.class)) {
        final Timer.Sample timer = mock(Timer.Sample.class);
        when(timer.stop(any())).thenReturn(1000L);
        mockedTimer.when(Timer::start).thenReturn(timer);
        final BulkByScrollResponse mockResponse = mock(BulkByScrollResponse.class);
        mockedStatic
            .when(() -> ElasticsearchUtil.deleteAsync(any(), any(), any()))
            .thenReturn(CompletableFuture.completedFuture(mockResponse));
        final CompletableFuture<Void> res = underTest.deleteDocuments("index", "id", List.of());
        res.join();
        assertThat(res).isCompleted();
      }
    }
  }

  @Test
  public void testDeleteDocumentsError() {
    try (final MockedStatic<ElasticsearchUtil> mockedStatic = mockStatic(ElasticsearchUtil.class)) {
      try (final MockedStatic<Timer> mockedTimer = mockStatic(Timer.class)) {
        final Timer.Sample timer = mock(Timer.Sample.class);
        mockedTimer.when(Timer::start).thenReturn(timer);
        final CompletableFuture<BulkByScrollResponse> failedFuture = new CompletableFuture<>();
        final String errorMsg = "test error";
        failedFuture.completeExceptionally(new Exception(errorMsg));
        mockedStatic
            .when(() -> ElasticsearchUtil.deleteAsync(any(), any(), any()))
            .thenReturn(failedFuture);
        final CompletableFuture<Void> res = underTest.deleteDocuments("index", "id", List.of());
        try {
          res.join();
        } catch (final Exception e) {
          assertThat(e.getMessage()).contains(errorMsg);
        }
      }
    }
  }

  @Test
  public void testReindexDocuments() {
    try (final MockedStatic<ElasticsearchUtil> mockedStatic = mockStatic(ElasticsearchUtil.class)) {
      try (final MockedStatic<Timer> mockedTimer = mockStatic(Timer.class)) {
        final Timer.Sample timer = mock(Timer.Sample.class);
        when(timer.stop(any())).thenReturn(1000L);
        mockedTimer.when(Timer::start).thenReturn(timer);
        final BulkByScrollResponse mockResponse = mock(BulkByScrollResponse.class);
        mockedStatic
            .when(() -> ElasticsearchUtil.reindexAsync(any(), any(), any()))
            .thenReturn(CompletableFuture.completedFuture(mockResponse));
        final CompletableFuture<Void> res =
            underTest.reindexDocuments("sourceIndex", "destinationIndex", "id", List.of());
        res.join();
        assertThat(res).isCompleted();
      }
    }
  }

  @Test
  public void testReindexDocumentsError() {
    try (final MockedStatic<ElasticsearchUtil> mockedStatic = mockStatic(ElasticsearchUtil.class)) {
      try (final MockedStatic<Timer> mockedTimer = mockStatic(Timer.class)) {
        final Timer.Sample timer = mock(Timer.Sample.class);
        mockedTimer.when(Timer::start).thenReturn(timer);
        final CompletableFuture<Void> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new Exception("test error"));
        mockedStatic
            .when(() -> ElasticsearchUtil.reindexAsync(any(), any(), any()))
            .thenReturn(failedFuture);
        final CompletableFuture<Void> res =
            underTest.reindexDocuments("sourceIndex", "destinationIndex", "id", List.of());
        try {
          res.join();
        } catch (final Exception e) {
          assertThat(e.getMessage()).isEqualTo("java.lang.Exception: test error");
        }
      }
    }
  }

  @Test
  void shouldDisallowPartialSearchResultsWhenGettingArchiveDocIdsBatch() {
    // given
    final SearchResponse searchResponse = mock(SearchResponse.class);
    final var hits = mock(org.elasticsearch.search.SearchHits.class);
    when(searchResponse.getHits()).thenReturn(hits);
    when(hits.getHits()).thenReturn(new org.elasticsearch.search.SearchHit[0]);

    final ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);

    try (final MockedStatic<Timer> mockedTimer = mockStatic(Timer.class)) {
      final Timer.Sample timerSample = mock(Timer.Sample.class);
      mockedTimer.when(Timer::start).thenReturn(timerSample);

      try (final MockedStatic<ElasticsearchUtil> mockedStatic =
          mockStatic(ElasticsearchUtil.class)) {
        mockedStatic
            .when(() -> ElasticsearchUtil.searchAsync(captor.capture(), any(), any()))
            .thenReturn(CompletableFuture.completedFuture(searchResponse));

        // when
        underTest
            .getArchiveDocIdsBatch(
                "source-index",
                Map.of("key", List.of("1")),
                Map.of(),
                Map.of(),
                10,
                List.of(),
                Runnable::run)
            .join();
      }
    }

    // then
    assertThat(captor.getValue().allowPartialSearchResults()).isFalse();
  }

  @Test
  public void testGetBatchOperationsNextBatchEmptyHits() {
    setBatchOperationMocks();

    try (final MockedStatic<ElasticsearchUtil> elasticsearchUtilMockedStatic =
        mockStatic(ElasticsearchUtil.class)) {
      final SearchResponse searchResponse = mock(SearchResponse.class);
      final SearchHits searchHits = mock(SearchHits.class);
      when(searchResponse.getHits()).thenReturn(searchHits);
      when(searchHits.getHits()).thenReturn(new SearchHit[0]);

      try (final MockedConstruction<SearchRequest> mockedConstruction =
          mockConstruction(
              SearchRequest.class,
              (mock, context) -> {
                when(mock.source(any())).thenReturn(mock);
                when(mock.requestCache(false)).thenReturn(mock);
              })) {
        try (final MockedStatic<Timer> mockedTimer = mockStatic(Timer.class)) {
          final Timer.Sample timer = mock(Timer.Sample.class);
          when(timer.stop(any())).thenReturn(1000L);
          mockedTimer.when(Timer::start).thenReturn(timer);
          elasticsearchUtilMockedStatic
              .when(() -> ElasticsearchUtil.searchAsync(any(), any(), any()))
              .thenReturn(CompletableFuture.completedFuture(searchResponse));

          final CompletableFuture<ArchiveBatch> res = underTest.getBatchOperationNextBatch();
          assertThat(res).isCompleted();
          assertThat(res.join()).isNull();
        }
      }
    }
  }

  @Test
  public void testGetStandaloneDecisionInstancesNextBatchEmptyHits() {
    setDecisionInstanceMocks();

    try (final MockedStatic<ElasticsearchUtil> elasticsearchUtilMockedStatic =
        mockStatic(ElasticsearchUtil.class)) {
      elasticsearchUtilMockedStatic
          .when(
              () ->
                  ElasticsearchUtil.joinWithAnd(
                      any(),
                      eq(
                          QueryBuilders.termsQuery(
                              DecisionInstanceTemplate.PARTITION_ID, PARTITION_IDS)),
                      eq(
                          QueryBuilders.termQuery(
                              DecisionInstanceTemplate.PROCESS_INSTANCE_KEY, -1))))
          .thenCallRealMethod();

      final SearchResponse searchResponse = mock(SearchResponse.class);
      final SearchHits searchHits = mock(SearchHits.class);
      when(searchResponse.getHits()).thenReturn(searchHits);
      when(searchHits.getHits()).thenReturn(new SearchHit[0]);

      try (final MockedConstruction<SearchRequest> mockedConstruction =
          mockConstruction(
              SearchRequest.class,
              (mock, context) -> {
                when(mock.source(any())).thenReturn(mock);
                when(mock.requestCache(false)).thenReturn(mock);
              })) {
        try (final MockedStatic<Timer> mockedTimer = mockStatic(Timer.class)) {
          final Timer.Sample timer = mock(Timer.Sample.class);
          when(timer.stop(any())).thenReturn(1000L);
          mockedTimer.when(Timer::start).thenReturn(timer);
          elasticsearchUtilMockedStatic
              .when(() -> ElasticsearchUtil.searchAsync(any(), any(), any()))
              .thenReturn(CompletableFuture.completedFuture(searchResponse));

          final CompletableFuture<ArchiveBatch> res =
              underTest.getStandaloneDecisionNextBatch(PARTITION_IDS);
          assertThat(res).isCompleted();
          assertThat(res.join()).isNull();
        }
      }
    }
  }

  @Test
  public void testGetBatchOperationsNextBatchPacksEarliestBucketOnly() {
    setBatchOperationMocks();

    try (final MockedStatic<ElasticsearchUtil> elasticsearchUtilMockedStatic =
        mockStatic(ElasticsearchUtil.class)) {
      final SearchResponse searchResponse = mock(SearchResponse.class);
      final SearchHits searchHits = mock(SearchHits.class);
      when(searchResponse.getHits()).thenReturn(searchHits);

      final SearchHit hitA = mock(SearchHit.class);
      final SearchHit hitB = mock(SearchHit.class);
      final SearchHit hitC = mock(SearchHit.class);
      when(hitA.getId()).thenReturn("a");
      when(hitB.getId()).thenReturn("b");
      // hitC.getId() is NOT stubbed — takeWhile stops before hitC so getId() is never called
      final DocumentField fieldA = mock(DocumentField.class);
      final DocumentField fieldB = mock(DocumentField.class);
      final DocumentField fieldC = mock(DocumentField.class);
      when(fieldA.getValue()).thenReturn("2024-01-01");
      when(fieldB.getValue()).thenReturn("2024-01-01");
      when(fieldC.getValue()).thenReturn("2024-01-02");
      when(hitA.field(BatchOperationTemplate.END_DATE)).thenReturn(fieldA);
      when(hitB.field(BatchOperationTemplate.END_DATE)).thenReturn(fieldB);
      when(hitC.field(BatchOperationTemplate.END_DATE)).thenReturn(fieldC);
      when(searchHits.getHits()).thenReturn(new SearchHit[] {hitA, hitB, hitC});

      try (final MockedConstruction<SearchRequest> mockedConstruction =
          mockConstruction(
              SearchRequest.class,
              (mock, context) -> {
                when(mock.source(any())).thenReturn(mock);
                when(mock.requestCache(false)).thenReturn(mock);
              })) {
        try (final MockedStatic<Timer> mockedTimer = mockStatic(Timer.class)) {
          final Timer.Sample timer = mock(Timer.Sample.class);
          when(timer.stop(any())).thenReturn(1000L);
          mockedTimer.when(Timer::start).thenReturn(timer);
          elasticsearchUtilMockedStatic
              .when(() -> ElasticsearchUtil.searchAsync(any(), any(), any()))
              .thenReturn(CompletableFuture.completedFuture(searchResponse));

          final ArchiveBatch batch = underTest.getBatchOperationNextBatch().join();
          assertThat(batch).isNotNull();
          assertThat(batch.getFinishDate()).isEqualTo("2024-01-01");
          assertThat(batch.getIds()).isEqualTo(List.of("a", "b"));
        }
      }
    }
  }

  @Test
  public void testGetStandaloneDecisionInstancesNextBatchPacksEarliestBucketOnly() {
    setDecisionInstanceMocks();

    try (final MockedStatic<ElasticsearchUtil> elasticsearchUtilMockedStatic =
        mockStatic(ElasticsearchUtil.class)) {
      elasticsearchUtilMockedStatic
          .when(
              () ->
                  ElasticsearchUtil.joinWithAnd(
                      any(),
                      eq(
                          QueryBuilders.termsQuery(
                              DecisionInstanceTemplate.PARTITION_ID, PARTITION_IDS)),
                      eq(
                          QueryBuilders.termQuery(
                              DecisionInstanceTemplate.PROCESS_INSTANCE_KEY, -1))))
          .thenCallRealMethod();

      final SearchResponse searchResponse = mock(SearchResponse.class);
      final SearchHits searchHits = mock(SearchHits.class);
      when(searchResponse.getHits()).thenReturn(searchHits);

      final SearchHit hitA = mock(SearchHit.class);
      final SearchHit hitB = mock(SearchHit.class);
      final SearchHit hitC = mock(SearchHit.class);
      when(hitA.getId()).thenReturn("a");
      when(hitB.getId()).thenReturn("b");
      // hitC.getId() is NOT stubbed — takeWhile stops before hitC so getId() is never called
      final DocumentField fieldA = mock(DocumentField.class);
      final DocumentField fieldB = mock(DocumentField.class);
      final DocumentField fieldC = mock(DocumentField.class);
      when(fieldA.getValue()).thenReturn("2024-01-01");
      when(fieldB.getValue()).thenReturn("2024-01-01");
      when(fieldC.getValue()).thenReturn("2024-01-02");
      when(hitA.field(DecisionInstanceTemplate.EVALUATION_DATE)).thenReturn(fieldA);
      when(hitB.field(DecisionInstanceTemplate.EVALUATION_DATE)).thenReturn(fieldB);
      when(hitC.field(DecisionInstanceTemplate.EVALUATION_DATE)).thenReturn(fieldC);
      when(searchHits.getHits()).thenReturn(new SearchHit[] {hitA, hitB, hitC});

      try (final MockedConstruction<SearchRequest> mockedConstruction =
          mockConstruction(
              SearchRequest.class,
              (mock, context) -> {
                when(mock.source(any())).thenReturn(mock);
                when(mock.requestCache(false)).thenReturn(mock);
              })) {
        try (final MockedStatic<Timer> mockedTimer = mockStatic(Timer.class)) {
          final Timer.Sample timer = mock(Timer.Sample.class);
          when(timer.stop(any())).thenReturn(1000L);
          mockedTimer.when(Timer::start).thenReturn(timer);
          elasticsearchUtilMockedStatic
              .when(() -> ElasticsearchUtil.searchAsync(any(), any(), any()))
              .thenReturn(CompletableFuture.completedFuture(searchResponse));

          final ArchiveBatch batch = underTest.getStandaloneDecisionNextBatch(PARTITION_IDS).join();
          assertThat(batch).isNotNull();
          assertThat(batch.getFinishDate()).isEqualTo("2024-01-01");
          assertThat(batch.getIds()).isEqualTo(List.of("a", "b"));
        }
      }
    }
  }

  @Test
  void shouldPassRoutingToBulkDeleteRequest() {
    // given
    final var docs =
        List.of(
            new IdWithRouting("child-doc", "99"), // child doc routed by PI key
            new IdWithRouting("pi-doc", null)); // PI doc has no custom routing

    final ArgumentCaptor<BulkRequest> bulkCaptor = ArgumentCaptor.forClass(BulkRequest.class);

    try (final MockedStatic<Timer> mockedTimer = mockStatic(Timer.class)) {
      final Timer.Sample timer = mock(Timer.Sample.class);
      mockedTimer.when(Timer::start).thenReturn(timer);

      final BulkResponse bulkResponse = mock(BulkResponse.class);
      when(bulkResponse.hasFailures()).thenReturn(false);
      when(bulkResponse.getItems()).thenReturn(new BulkItemResponse[0]);

      doAnswer(
              invocation -> {
                final ActionListener<BulkResponse> listener = invocation.getArgument(2);
                listener.onResponse(bulkResponse);
                return null;
              })
          .when(esClient)
          .bulkAsync(bulkCaptor.capture(), any(), any());

      // when
      underTest.deleteDocumentsById("source-index", docs, Runnable::run).join();
    }

    // then — routing is forwarded exactly as supplied
    final var requests = bulkCaptor.getValue().requests();
    assertThat(requests.get(0).id()).isEqualTo("child-doc");
    assertThat(requests.get(0).routing()).isEqualTo("99");
    assertThat(requests.get(1).id()).isEqualTo("pi-doc");
    assertThat(requests.get(1).routing()).isNull();
  }

  @Test
  public void testGetProcessInstancesNextBatchEmptyHits() {
    setProcessInstancesMocks();

    try (final MockedStatic<ElasticsearchUtil> elasticsearchUtilMockedStatic =
        mockStatic(ElasticsearchUtil.class)) {
      elasticsearchUtilMockedStatic
          .when(
              () ->
                  ElasticsearchUtil.joinWithAnd(
                      any(),
                      eq(
                          QueryBuilders.termQuery(
                              ListViewTemplate.JOIN_RELATION,
                              ListViewTemplate.PROCESS_INSTANCE_JOIN_RELATION)),
                      eq(QueryBuilders.termsQuery(ListViewTemplate.PARTITION_ID, PARTITION_IDS))))
          .thenCallRealMethod();

      final SearchResponse searchResponse = mock(SearchResponse.class);
      final SearchHits searchHits = mock(SearchHits.class);
      when(searchResponse.getHits()).thenReturn(searchHits);
      when(searchHits.getHits()).thenReturn(new SearchHit[0]);

      try (final MockedConstruction<SearchRequest> mockedConstruction =
          mockConstruction(
              SearchRequest.class,
              (mock, context) -> {
                when(mock.source(any())).thenReturn(mock);
                when(mock.requestCache(false)).thenReturn(mock);
              })) {
        try (final MockedStatic<Timer> mockedTimer = mockStatic(Timer.class)) {
          final Timer.Sample timer = mock(Timer.Sample.class);
          when(timer.stop(any())).thenReturn(1000L);
          mockedTimer.when(Timer::start).thenReturn(timer);
          elasticsearchUtilMockedStatic
              .when(() -> ElasticsearchUtil.searchAsync(any(), any(), any()))
              .thenReturn(CompletableFuture.completedFuture(searchResponse));

          final CompletableFuture<ArchiveBatch> res =
              underTest.getProcessInstancesNextBatch(PARTITION_IDS);
          res.join();
          assertThat(res).isCompleted();
          assertThat(res.join()).isNull();
        }
      }
    }
  }

  @Test
  public void testGetProcessInstancesNextBatchPacksEarliestBucketOnly() {
    setProcessInstancesMocks();

    try (final MockedStatic<ElasticsearchUtil> elasticsearchUtilMockedStatic =
        mockStatic(ElasticsearchUtil.class)) {
      elasticsearchUtilMockedStatic
          .when(
              () ->
                  ElasticsearchUtil.joinWithAnd(
                      any(),
                      eq(
                          QueryBuilders.termQuery(
                              ListViewTemplate.JOIN_RELATION,
                              ListViewTemplate.PROCESS_INSTANCE_JOIN_RELATION)),
                      eq(QueryBuilders.termsQuery(ListViewTemplate.PARTITION_ID, PARTITION_IDS))))
          .thenCallRealMethod();

      final SearchResponse searchResponse = mock(SearchResponse.class);
      final SearchHits searchHits = mock(SearchHits.class);
      when(searchResponse.getHits()).thenReturn(searchHits);

      final SearchHit hitA = mock(SearchHit.class);
      final SearchHit hitB = mock(SearchHit.class);
      final SearchHit hitC = mock(SearchHit.class);
      when(hitA.getId()).thenReturn("a");
      when(hitB.getId()).thenReturn("b");
      // hitC.getId() is NOT stubbed — takeWhile stops before hitC so getId() is never called
      final DocumentField fieldA = mock(DocumentField.class);
      final DocumentField fieldB = mock(DocumentField.class);
      final DocumentField fieldC = mock(DocumentField.class);
      when(fieldA.getValue()).thenReturn("2024-01-01");
      when(fieldB.getValue()).thenReturn("2024-01-01");
      when(fieldC.getValue()).thenReturn("2024-01-02");
      when(hitA.field(ListViewTemplate.END_DATE)).thenReturn(fieldA);
      when(hitB.field(ListViewTemplate.END_DATE)).thenReturn(fieldB);
      when(hitC.field(ListViewTemplate.END_DATE)).thenReturn(fieldC);
      when(searchHits.getHits()).thenReturn(new SearchHit[] {hitA, hitB, hitC});

      try (final MockedConstruction<SearchRequest> mockedConstruction =
          mockConstruction(
              SearchRequest.class,
              (mock, context) -> {
                when(mock.source(any())).thenReturn(mock);
                when(mock.requestCache(false)).thenReturn(mock);
              })) {
        try (final MockedStatic<Timer> mockedTimer = mockStatic(Timer.class)) {
          final Timer.Sample timer = mock(Timer.Sample.class);
          when(timer.stop(any())).thenReturn(1000L);
          mockedTimer.when(Timer::start).thenReturn(timer);
          elasticsearchUtilMockedStatic
              .when(() -> ElasticsearchUtil.searchAsync(any(), any(), any()))
              .thenReturn(CompletableFuture.completedFuture(searchResponse));

          final CompletableFuture<ArchiveBatch> res =
              underTest.getProcessInstancesNextBatch(PARTITION_IDS);
          final ArchiveBatch batch = res.join();
          assertThat(batch).isNotNull();
          assertThat(batch.getFinishDate()).isEqualTo("2024-01-01");
          assertThat(batch.getIds()).isEqualTo(List.of("a", "b"));
        }
      }
    }
  }

  private void setupMocks() {
    when(operateProperties.getArchiver()).thenReturn(archiverProperties);
    when(archiverProperties.getRolloverInterval()).thenReturn("1d");
    when(archiverProperties.getElsRolloverDateFormat()).thenReturn("date");
    when(archiverProperties.getRolloverBatchSize()).thenReturn(100);
    when(archiverProperties.getArchivingTimepoint()).thenReturn("now-1s");
  }

  private void setProcessInstancesMocks() {
    setupMocks();
    when(listViewTemplate.getFullQualifiedName()).thenReturn("qualifiedName");
  }

  private void setBatchOperationMocks() {
    setupMocks();
    when(batchOperationTemplate.getFullQualifiedName()).thenReturn("qualifiedName");
  }

  private void setDecisionInstanceMocks() {
    setupMocks();
    when(decisionInstanceTemplate.getFullQualifiedName()).thenReturn("decisionsQualifiedName");
  }
}
