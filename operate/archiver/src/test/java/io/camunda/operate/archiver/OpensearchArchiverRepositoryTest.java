/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.archiver;

import static io.camunda.operate.schema.SchemaManager.OPERATE_DELETE_ARCHIVED_INDICES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.operate.Metrics;
import io.camunda.operate.archiver.ArchiveByIdTaskSupplier.IdWithRouting;
import io.camunda.operate.exceptions.ArchiverException;
import io.camunda.operate.property.ArchiverProperties;
import io.camunda.operate.property.OperateOpensearchProperties;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.templates.BatchOperationTemplate;
import io.camunda.operate.schema.templates.DecisionInstanceTemplate;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.store.opensearch.client.sync.OpenSearchISMOperations;
import io.camunda.operate.store.opensearch.client.sync.OpenSearchIndexOperations;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.store.opensearch.dsl.QueryDSL;
import io.camunda.operate.store.opensearch.dsl.RequestDSL;
import io.camunda.operate.util.Either;
import io.camunda.operate.util.OpensearchUtil;
import io.micrometer.core.instrument.Timer;
import jakarta.json.JsonArray;
import jakarta.json.JsonValue;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch._types.SortOptions;
import org.opensearch.client.opensearch._types.aggregations.Aggregate;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.aggregations.Buckets;
import org.opensearch.client.opensearch._types.aggregations.LongTermsAggregate;
import org.opensearch.client.opensearch._types.aggregations.LongTermsBucket;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.BulkResponse;
import org.opensearch.client.opensearch.core.DeleteByQueryResponse;
import org.opensearch.client.opensearch.core.ReindexResponse;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.core.search.HitsMetadata;
import org.opensearch.client.opensearch.core.search.SourceConfig;

@ExtendWith(MockitoExtension.class)
public class OpensearchArchiverRepositoryTest {
  private static final List<Integer> PARTITION_IDS = List.of(1, 2);

  @Mock protected RichOpenSearchClient richOpenSearchClient;
  @Mock protected OpenSearchAsyncClient openSearchAsyncClient;
  @InjectMocks OpensearchArchiverRepository underTest;
  @Mock OperateOpensearchProperties operateOpensearchProperties;
  @Mock private BatchOperationTemplate batchOperationTemplate;
  @Mock private ListViewTemplate processInstanceTemplate;
  @Mock private DecisionInstanceTemplate decisionInstanceTemplate;
  @Mock private OperateProperties operateProperties;
  @Mock private Metrics metrics;
  private ArchiverProperties archiverProperties;
  private OpenSearchIndexOperations openSearchIndexOperations;

  @BeforeEach
  public void setUp() {
    archiverProperties = mock(ArchiverProperties.class);
    openSearchIndexOperations = mock(OpenSearchIndexOperations.class);
  }

  @Test
  public void testSetIndexLifeCycle() {
    final OpenSearchISMOperations openSearchISMOperations = mock(OpenSearchISMOperations.class);
    when(operateProperties.getArchiver()).thenReturn(archiverProperties);
    when(richOpenSearchClient.index()).thenReturn(openSearchIndexOperations);
    when(archiverProperties.isIlmEnabled()).thenReturn(true);
    when(openSearchIndexOperations.indexExists(anyString())).thenReturn(true);
    when(richOpenSearchClient.ism()).thenReturn(openSearchISMOperations);
    when(openSearchISMOperations.addPolicyToIndex(
            "destinationIndexName", OPERATE_DELETE_ARCHIVED_INDICES))
        .thenReturn(null);
    underTest.setIndexLifeCycle("destinationIndexName");
    verify(archiverProperties).isIlmEnabled();
    assertThat(archiverProperties.isIlmEnabled()).isTrue();
    verify(openSearchIndexOperations).indexExists("destinationIndexName");
    assertThat(openSearchIndexOperations.indexExists("destinationIndexName")).isTrue();
    verify(richOpenSearchClient).ism();
  }

  @Test
  public void testSetIndexLifeCycleNoIndex() throws ClassNotFoundException {
    when(operateProperties.getArchiver()).thenReturn(archiverProperties);
    when(richOpenSearchClient.index()).thenReturn(openSearchIndexOperations);
    when(archiverProperties.isIlmEnabled()).thenReturn(true);
    when(openSearchIndexOperations.indexExists(anyString())).thenReturn(false);
    underTest.setIndexLifeCycle("destinationIndexName");
    verify(archiverProperties).isIlmEnabled();
    assertThat(archiverProperties.isIlmEnabled()).isTrue();
    verify(openSearchIndexOperations).indexExists("destinationIndexName");
    assertThat(openSearchIndexOperations.indexExists("destinationIndexName")).isFalse();
  }

  @Test
  public void testDeleteDocuments() {
    when(operateProperties.getOpensearch()).thenReturn(operateOpensearchProperties);
    when(operateOpensearchProperties.getNumberOfShards()).thenReturn(5);
    try (final MockedStatic<OpensearchUtil> opensearchUtil = mockStatic(OpensearchUtil.class)) {
      try (final MockedStatic<Timer> mockedTimer = mockStatic(Timer.class)) {
        final Timer.Sample timer = mock(Timer.Sample.class);
        when(timer.stop(any())).thenReturn(1000L);
        mockedTimer.when(Timer::start).thenReturn(timer);
        final DeleteByQueryResponse mockResponse = mock(DeleteByQueryResponse.class);
        opensearchUtil
            .when(() -> OpensearchUtil.deleteAsync(any(), any()))
            .thenReturn(CompletableFuture.completedFuture(mockResponse));
        opensearchUtil
            .when((() -> OpensearchUtil.handleResponse(any(), any(), anyString())))
            .thenReturn(Either.right(0L));
        final CompletableFuture<Void> res = underTest.deleteDocuments("index", "id", List.of());
        res.join();
        assertThat(res).isCompleted();
      }
    }
  }

  @Test
  public void testDeleteDocumentsError() {
    when(operateProperties.getOpensearch()).thenReturn(operateOpensearchProperties);
    when(operateOpensearchProperties.getNumberOfShards()).thenReturn(5);
    try (final MockedStatic<OpensearchUtil> opensearchUtil = mockStatic(OpensearchUtil.class)) {
      try (final MockedStatic<Timer> mockedTimer = mockStatic(Timer.class)) {
        final Timer.Sample timer = mock(Timer.Sample.class);
        mockedTimer.when(Timer::start).thenReturn(timer);
        final CompletableFuture<DeleteByQueryResponse> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new Exception("test error"));
        opensearchUtil
            .when(() -> OpensearchUtil.deleteAsync(any(), any()))
            .thenReturn(failedFuture);
        opensearchUtil
            .when((() -> OpensearchUtil.handleResponse(any(), any(), anyString())))
            .thenReturn(Either.right(0L));
        final CompletableFuture<Void> res = underTest.deleteDocuments("index", "id", List.of());
        try {
          res.join();
        } catch (final Exception e) {
          assertThat(e instanceof ArchiverException).isTrue();
          assertThat(e.getMessage()).contains("test error");
        }
      }
    }
  }

  @Test
  public void testReindexDocuments() {
    when(operateProperties.getOpensearch()).thenReturn(operateOpensearchProperties);
    when(operateOpensearchProperties.getNumberOfShards()).thenReturn(5);
    try (final MockedStatic<OpensearchUtil> opensearchUtil = mockStatic(OpensearchUtil.class)) {
      try (final MockedStatic<Timer> mockedTimer = mockStatic(Timer.class)) {
        final Timer.Sample timer = mock(Timer.Sample.class);
        when(timer.stop(any())).thenReturn(1000L);
        mockedTimer.when(Timer::start).thenReturn(timer);
        final ReindexResponse mockResponse = mock(ReindexResponse.class);
        opensearchUtil
            .when(() -> OpensearchUtil.reindexAsync(any(), any()))
            .thenReturn(CompletableFuture.completedFuture(mockResponse));
        opensearchUtil
            .when((() -> OpensearchUtil.handleResponse(any(), any(), anyString())))
            .thenReturn(Either.right(0L));
        final CompletableFuture<Void> res =
            underTest.reindexDocuments("sourceIndex", "destinationIndex", "id", List.of());
        res.join();
        assertThat(res).isCompleted();
      }
    }
  }

  @Test
  public void testReindexDocumentsError() {
    when(operateProperties.getOpensearch()).thenReturn(operateOpensearchProperties);
    when(operateOpensearchProperties.getNumberOfShards()).thenReturn(5);
    try (final MockedStatic<OpensearchUtil> opensearchUtil = mockStatic(OpensearchUtil.class)) {
      try (final MockedStatic<Timer> mockedTimer = mockStatic(Timer.class)) {
        final Timer.Sample timer = mock(Timer.Sample.class);
        mockedTimer.when(Timer::start).thenReturn(timer);
        final CompletableFuture<Void> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new Exception("test error"));
        opensearchUtil
            .when(() -> OpensearchUtil.reindexAsync(any(), any()))
            .thenReturn(failedFuture);
        opensearchUtil
            .when((() -> OpensearchUtil.handleResponse(any(), any(), anyString())))
            .thenReturn(Either.right(0L));
        final CompletableFuture<Void> res =
            underTest.reindexDocuments("sourceIndex", "destinationIndex", "id", List.of());
        try {
          res.join();
        } catch (final Exception e) {
          assertThat(e instanceof ArchiverException).isTrue();
          assertThat(e.getMessage()).contains("test error");
        }
      }
    }
  }

  @Test
  public void testGetProcessInstancesNextBatchEmptyHitsReturnsNull() {
    setProcessInstancesMocks();
    try (final MockedStatic<RequestDSL> requestDSLMockedStatic = mockStatic(RequestDSL.class);
        final MockedStatic<QueryDSL> queryDSLMockedStatic = mockStatic(QueryDSL.class);
        final MockedStatic<Timer> timerMockedStatic = mockStatic(Timer.class);
        final MockedStatic<OpensearchUtil> opensearchUtil = mockStatic(OpensearchUtil.class)) {
      setupPISearchRequestBuilderMock(requestDSLMockedStatic);

      final Timer.Sample timerSample = mock(Timer.Sample.class);
      timerMockedStatic.when(Timer::start).thenReturn(timerSample);
      when(metrics.getTimer(any())).thenReturn(mock(Timer.class));

      final HitsMetadata<Object> hitsMeta = mock(HitsMetadata.class);
      final SearchResponse<Object> resp = mock(SearchResponse.class);
      when(resp.hits()).thenReturn(hitsMeta);
      when(hitsMeta.hits()).thenReturn(List.of());

      opensearchUtil
          .when(() -> OpensearchUtil.searchAsync(any(), any(), any()))
          .thenReturn(CompletableFuture.completedFuture(resp));

      final CompletableFuture<ArchiveBatch> result =
          underTest.getProcessInstancesNextBatch(PARTITION_IDS);
      result.join();
      assertThat(result).isCompleted();
      assertThat(result.join()).isNull();
    }
  }

  @Test
  public void testGetProcessInstancesNextBatchPacksEarliestBucketOnly() {
    setProcessInstancesMocks();
    try (final MockedStatic<RequestDSL> requestDSLMockedStatic = mockStatic(RequestDSL.class);
        final MockedStatic<QueryDSL> queryDSLMockedStatic = mockStatic(QueryDSL.class);
        final MockedStatic<Timer> timerMockedStatic = mockStatic(Timer.class);
        final MockedStatic<OpensearchUtil> opensearchUtil = mockStatic(OpensearchUtil.class)) {
      setupPISearchRequestBuilderMock(requestDSLMockedStatic);

      final Timer.Sample timerSample = mock(Timer.Sample.class);
      timerMockedStatic.when(Timer::start).thenReturn(timerSample);
      when(metrics.getTimer(any())).thenReturn(mock(Timer.class));

      // hits: "a" and "b" on 2024-01-01 (same bucket); "c" on 2024-01-02 (next bucket)
      // hit "c" fields() IS read by takeWhile predicate, but id() is NOT mapped
      final Hit<Object> hitA = hitWithEndDate("a", ListViewTemplate.END_DATE, "2024-01-01");
      final Hit<Object> hitB = hitWithEndDate("b", ListViewTemplate.END_DATE, "2024-01-01");
      final Hit<Object> hitC = hitWithEndDateNoId(ListViewTemplate.END_DATE, "2024-01-02");

      final HitsMetadata<Object> hitsMeta = mock(HitsMetadata.class);
      final SearchResponse<Object> resp = mock(SearchResponse.class);
      when(resp.hits()).thenReturn(hitsMeta);
      when(hitsMeta.hits()).thenReturn(List.of(hitA, hitB, hitC));

      opensearchUtil
          .when(() -> OpensearchUtil.searchAsync(any(), any(), any()))
          .thenReturn(CompletableFuture.completedFuture(resp));

      final CompletableFuture<ArchiveBatch> result =
          underTest.getProcessInstancesNextBatch(PARTITION_IDS);
      result.join();
      assertThat(result).isCompleted();
      final ArchiveBatch batch = result.join();
      assertThat(batch).isNotNull();
      assertThat(batch.getFinishDate()).isEqualTo("2024-01-01");
      assertThat(batch.getIds()).isEqualTo(List.of("a", "b"));
    }
  }

  @Test
  void shouldDisallowPartialSearchResultsWhenGettingArchiveDocIdsBatch() {
    // given
    final SearchResponse<Object> searchResponse = mock(SearchResponse.class);
    final var hitsMetadata = mock(org.opensearch.client.opensearch.core.search.HitsMetadata.class);
    when(searchResponse.hits()).thenReturn(hitsMetadata);
    when(hitsMetadata.hits()).thenReturn(List.of());

    final ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);

    try (final MockedStatic<Timer> mockedTimer = mockStatic(Timer.class)) {
      final Timer.Sample timerSample = mock(Timer.Sample.class);
      mockedTimer.when(Timer::start).thenReturn(timerSample);

      try (final MockedStatic<OpensearchUtil> mockedStatic = mockStatic(OpensearchUtil.class)) {
        mockedStatic
            .when(() -> OpensearchUtil.searchAsync(captor.capture(), any(), any()))
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
  public void testGetBatchOperationsNextBatchEmptyHitsReturnsNull() {
    setBatchOperationMocks();
    try (final MockedStatic<RequestDSL> requestDSLMockedStatic = mockStatic(RequestDSL.class);
        final MockedStatic<QueryDSL> queryDSLMockedStatic = mockStatic(QueryDSL.class);
        final MockedStatic<Timer> timerMockedStatic = mockStatic(Timer.class);
        final MockedStatic<OpensearchUtil> opensearchUtil = mockStatic(OpensearchUtil.class)) {
      setupPlainSearchRequestBuilderMock(requestDSLMockedStatic);

      final Timer.Sample timerSample = mock(Timer.Sample.class);
      timerMockedStatic.when(Timer::start).thenReturn(timerSample);
      when(metrics.getTimer(any())).thenReturn(mock(Timer.class));

      final HitsMetadata<Object> hitsMeta = mock(HitsMetadata.class);
      final SearchResponse<Object> resp = mock(SearchResponse.class);
      when(resp.hits()).thenReturn(hitsMeta);
      when(hitsMeta.hits()).thenReturn(List.of());

      opensearchUtil
          .when(() -> OpensearchUtil.searchAsync(any(), any(), any()))
          .thenReturn(CompletableFuture.completedFuture(resp));

      final CompletableFuture<ArchiveBatch> result = underTest.getBatchOperationNextBatch();
      assertThat(result).isCompleted();
      assertThat(result.join()).isNull();
    }
  }

  @Test
  public void testGetBatchOperationsNextBatchPacksEarliestBucketOnly() {
    setBatchOperationMocks();
    try (final MockedStatic<RequestDSL> requestDSLMockedStatic = mockStatic(RequestDSL.class);
        final MockedStatic<QueryDSL> queryDSLMockedStatic = mockStatic(QueryDSL.class);
        final MockedStatic<Timer> timerMockedStatic = mockStatic(Timer.class);
        final MockedStatic<OpensearchUtil> opensearchUtil = mockStatic(OpensearchUtil.class)) {
      setupPlainSearchRequestBuilderMock(requestDSLMockedStatic);

      final Timer.Sample timerSample = mock(Timer.Sample.class);
      timerMockedStatic.when(Timer::start).thenReturn(timerSample);
      when(metrics.getTimer(any())).thenReturn(mock(Timer.class));

      final Hit<Object> hitA = hitWithEndDate("a", BatchOperationTemplate.END_DATE, "2024-01-01");
      final Hit<Object> hitB = hitWithEndDate("b", BatchOperationTemplate.END_DATE, "2024-01-01");
      final Hit<Object> hitC = hitWithEndDateNoId(BatchOperationTemplate.END_DATE, "2024-01-02");

      final HitsMetadata<Object> hitsMeta = mock(HitsMetadata.class);
      final SearchResponse<Object> resp = mock(SearchResponse.class);
      when(resp.hits()).thenReturn(hitsMeta);
      when(hitsMeta.hits()).thenReturn(List.of(hitA, hitB, hitC));

      opensearchUtil
          .when(() -> OpensearchUtil.searchAsync(any(), any(), any()))
          .thenReturn(CompletableFuture.completedFuture(resp));

      final ArchiveBatch batch = underTest.getBatchOperationNextBatch().join();
      assertThat(batch).isNotNull();
      assertThat(batch.getFinishDate()).isEqualTo("2024-01-01");
      assertThat(batch.getIds()).isEqualTo(List.of("a", "b"));
    }
  }

  @Test
  public void testGetStandaloneDecisionInstancesNextBatchEmptyHitsReturnsNull() {
    setDecisionInstanceMocks();
    try (final MockedStatic<RequestDSL> requestDSLMockedStatic = mockStatic(RequestDSL.class);
        final MockedStatic<QueryDSL> queryDSLMockedStatic = mockStatic(QueryDSL.class);
        final MockedStatic<Timer> timerMockedStatic = mockStatic(Timer.class);
        final MockedStatic<OpensearchUtil> opensearchUtil = mockStatic(OpensearchUtil.class)) {
      setupPlainSearchRequestBuilderMock(requestDSLMockedStatic);

      final Timer.Sample timerSample = mock(Timer.Sample.class);
      timerMockedStatic.when(Timer::start).thenReturn(timerSample);
      when(metrics.getTimer(any())).thenReturn(mock(Timer.class));

      final HitsMetadata<Object> hitsMeta = mock(HitsMetadata.class);
      final SearchResponse<Object> resp = mock(SearchResponse.class);
      when(resp.hits()).thenReturn(hitsMeta);
      when(hitsMeta.hits()).thenReturn(List.of());

      opensearchUtil
          .when(() -> OpensearchUtil.searchAsync(any(), any(), any()))
          .thenReturn(CompletableFuture.completedFuture(resp));

      final CompletableFuture<ArchiveBatch> result =
          underTest.getStandaloneDecisionNextBatch(PARTITION_IDS);
      assertThat(result).isCompleted();
      assertThat(result.join()).isNull();
    }
  }

  @Test
  public void testGetStandaloneDecisionInstancesNextBatchPacksEarliestBucketOnly() {
    setDecisionInstanceMocks();
    try (final MockedStatic<RequestDSL> requestDSLMockedStatic = mockStatic(RequestDSL.class);
        final MockedStatic<QueryDSL> queryDSLMockedStatic = mockStatic(QueryDSL.class);
        final MockedStatic<Timer> timerMockedStatic = mockStatic(Timer.class);
        final MockedStatic<OpensearchUtil> opensearchUtil = mockStatic(OpensearchUtil.class)) {
      setupPlainSearchRequestBuilderMock(requestDSLMockedStatic);

      final Timer.Sample timerSample = mock(Timer.Sample.class);
      timerMockedStatic.when(Timer::start).thenReturn(timerSample);
      when(metrics.getTimer(any())).thenReturn(mock(Timer.class));

      final Hit<Object> hitA =
          hitWithEndDate("a", DecisionInstanceTemplate.EVALUATION_DATE, "2024-01-01");
      final Hit<Object> hitB =
          hitWithEndDate("b", DecisionInstanceTemplate.EVALUATION_DATE, "2024-01-01");
      final Hit<Object> hitC =
          hitWithEndDateNoId(DecisionInstanceTemplate.EVALUATION_DATE, "2024-01-02");

      final HitsMetadata<Object> hitsMeta = mock(HitsMetadata.class);
      final SearchResponse<Object> resp = mock(SearchResponse.class);
      when(resp.hits()).thenReturn(hitsMeta);
      when(hitsMeta.hits()).thenReturn(List.of(hitA, hitB, hitC));

      opensearchUtil
          .when(() -> OpensearchUtil.searchAsync(any(), any(), any()))
          .thenReturn(CompletableFuture.completedFuture(resp));

      final ArchiveBatch batch = underTest.getStandaloneDecisionNextBatch(PARTITION_IDS).join();
      assertThat(batch).isNotNull();
      assertThat(batch.getFinishDate()).isEqualTo("2024-01-01");
      assertThat(batch.getIds()).isEqualTo(List.of("a", "b"));
    }
  }

  @Test
  void shouldPassRoutingToBulkDeleteRequest() throws Exception {
    // given
    final var docs =
        List.of(
            new IdWithRouting("child-doc", "99"), // child doc routed by PI key
            new IdWithRouting("pi-doc", null)); // PI doc has no custom routing

    final ArgumentCaptor<BulkRequest> bulkCaptor = ArgumentCaptor.forClass(BulkRequest.class);
    final BulkResponse bulkResponse = mock(BulkResponse.class);
    when(bulkResponse.errors()).thenReturn(false);
    when(bulkResponse.items()).thenReturn(List.of());
    when(openSearchAsyncClient.bulk(bulkCaptor.capture()))
        .thenReturn(CompletableFuture.completedFuture(bulkResponse));

    try (final MockedStatic<Timer> mockedTimer = mockStatic(Timer.class)) {
      final Timer.Sample timer = mock(Timer.Sample.class);
      mockedTimer.when(Timer::start).thenReturn(timer);

      // when
      underTest.deleteDocumentsById("source-index", docs, Runnable::run).join();
    }

    // then — routing is forwarded exactly as supplied
    final var ops = bulkCaptor.getValue().operations();
    assertThat(ops.get(0).delete().id()).isEqualTo("child-doc");
    assertThat(ops.get(0).delete().routing()).isEqualTo("99");
    assertThat(ops.get(1).delete().id()).isEqualTo("pi-doc");
    assertThat(ops.get(1).delete().routing()).isNull();
  }

  @SuppressWarnings("unchecked")
  private void setupPISearchRequestBuilderMock(
      final MockedStatic<RequestDSL> requestDSLMockedStatic) {
    final SearchRequest.Builder searchRequestBuilder = mock(SearchRequest.Builder.class);
    requestDSLMockedStatic
        .when(() -> RequestDSL.searchRequestBuilder(anyString()))
        .thenReturn(searchRequestBuilder);
    when(searchRequestBuilder.query((Query) any())).thenReturn(searchRequestBuilder);
    when(searchRequestBuilder.source((SourceConfig) any())).thenReturn(searchRequestBuilder);
    when(searchRequestBuilder.fields(any(java.util.function.Function.class)))
        .thenReturn(searchRequestBuilder);
    when(searchRequestBuilder.size(anyInt())).thenReturn(searchRequestBuilder);
    when(searchRequestBuilder.sort((SortOptions) any())).thenReturn(searchRequestBuilder);
    when(searchRequestBuilder.requestCache(false)).thenReturn(searchRequestBuilder);
    when(searchRequestBuilder.aggregations(anyString(), (Aggregation) any()))
        .thenReturn(searchRequestBuilder);
  }

  @SuppressWarnings("unchecked")
  private void setupPlainSearchRequestBuilderMock(
      final MockedStatic<RequestDSL> requestDSLMockedStatic) {
    final SearchRequest.Builder searchRequestBuilder = mock(SearchRequest.Builder.class);
    requestDSLMockedStatic
        .when(() -> RequestDSL.searchRequestBuilder(anyString()))
        .thenReturn(searchRequestBuilder);
    when(searchRequestBuilder.query((Query) any())).thenReturn(searchRequestBuilder);
    when(searchRequestBuilder.source((SourceConfig) any())).thenReturn(searchRequestBuilder);
    when(searchRequestBuilder.fields(any(java.util.function.Function.class)))
        .thenReturn(searchRequestBuilder);
    when(searchRequestBuilder.size(anyInt())).thenReturn(searchRequestBuilder);
    when(searchRequestBuilder.sort((SortOptions) any())).thenReturn(searchRequestBuilder);
    when(searchRequestBuilder.requestCache(false)).thenReturn(searchRequestBuilder);
  }

  @SuppressWarnings("unchecked")
  private Hit<Object> hitWithEndDate(
      final String id, final String fieldName, final String endDate) {
    final Hit<Object> hit = mock(Hit.class);
    when(hit.id()).thenReturn(id);
    final JsonValue jsonValue = mock(JsonValue.class);
    final JsonArray jsonArray = mock(JsonArray.class);
    final JsonData jsonData = mock(JsonData.class);
    when(hit.fields()).thenReturn(java.util.Map.of(fieldName, jsonData));
    when(jsonData.toJson()).thenReturn(jsonValue);
    when(jsonValue.asJsonArray()).thenReturn(jsonArray);
    when(jsonArray.getString(0)).thenReturn(endDate);
    return hit;
  }

  @SuppressWarnings("unchecked")
  private Hit<Object> hitWithEndDateNoId(final String fieldName, final String endDate) {
    final Hit<Object> hit = mock(Hit.class);
    final JsonValue jsonValue = mock(JsonValue.class);
    final JsonArray jsonArray = mock(JsonArray.class);
    final JsonData jsonData = mock(JsonData.class);
    when(hit.fields()).thenReturn(java.util.Map.of(fieldName, jsonData));
    when(jsonData.toJson()).thenReturn(jsonValue);
    when(jsonValue.asJsonArray()).thenReturn(jsonArray);
    when(jsonArray.getString(0)).thenReturn(endDate);
    return hit;
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
    when(processInstanceTemplate.getFullQualifiedName()).thenReturn("qualifiedName");
  }

  private void setBatchOperationMocks() {
    setupMocks();
    when(batchOperationTemplate.getFullQualifiedName()).thenReturn("qualifiedName");
  }

  private void setDecisionInstanceMocks() {
    setupMocks();
    when(decisionInstanceTemplate.getFullQualifiedName()).thenReturn("decisionsQualifiedName");
  }

  @Test
  public void testGetProcessInstancesNextBatchIncludesTotalPendingByPartition() {
    setProcessInstancesMocks();
    try (final MockedStatic<RequestDSL> requestDSLMockedStatic = mockStatic(RequestDSL.class);
        final MockedStatic<QueryDSL> queryDSLMockedStatic = mockStatic(QueryDSL.class);
        final MockedStatic<Timer> timerMockedStatic = mockStatic(Timer.class);
        final MockedStatic<OpensearchUtil> opensearchUtil = mockStatic(OpensearchUtil.class)) {
      setupPISearchRequestBuilderMock(requestDSLMockedStatic);

      final Timer.Sample timerSample = mock(Timer.Sample.class);
      timerMockedStatic.when(Timer::start).thenReturn(timerSample);
      when(metrics.getTimer(any())).thenReturn(mock(Timer.class));

      // one hit so the batch is non-null and we reach getTotalPendingCount
      final Hit<Object> hitA = hitWithEndDate("a", ListViewTemplate.END_DATE, "2024-01-01");
      final HitsMetadata<Object> hitsMeta = mock(HitsMetadata.class);
      final SearchResponse<Object> resp = mock(SearchResponse.class);
      when(resp.hits()).thenReturn(hitsMeta);
      when(hitsMeta.hits()).thenReturn(List.of(hitA));

      // set up aggregations with partition counts
      final LongTermsAggregate ltermsAggregate = mock(LongTermsAggregate.class);
      final Aggregate totalsAggregate = mock(Aggregate.class);
      when(totalsAggregate._kind()).thenReturn(Aggregate.Kind.Lterms);
      when(totalsAggregate.lterms()).thenReturn(ltermsAggregate);

      final LongTermsBucket bucket1 = mock(LongTermsBucket.class);
      when(bucket1.key()).thenReturn("1");
      when(bucket1.docCount()).thenReturn(42L);
      final LongTermsBucket bucket2 = mock(LongTermsBucket.class);
      when(bucket2.key()).thenReturn("2");
      when(bucket2.docCount()).thenReturn(17L);

      final Buckets<LongTermsBucket> buckets = mock(Buckets.class);
      when(buckets.array()).thenReturn(List.of(bucket1, bucket2));
      when(ltermsAggregate.buckets()).thenReturn(buckets);

      final Map<String, Aggregate> aggregations = new HashMap<>();
      aggregations.put("total_pending_archive_count", totalsAggregate);
      when(resp.aggregations()).thenReturn(aggregations);

      opensearchUtil
          .when(() -> OpensearchUtil.searchAsync(any(), any(), any()))
          .thenReturn(CompletableFuture.completedFuture(resp));

      final CompletableFuture<ArchiveBatch> result =
          underTest.getProcessInstancesNextBatch(PARTITION_IDS);
      final ArchiveBatch batch = result.join();
      assertThat(batch).isNotNull();
      assertThat(batch.getTotalPendingByPartition()).containsEntry(1, 42L);
      assertThat(batch.getTotalPendingByPartition()).containsEntry(2, 17L);
    }
  }
}
