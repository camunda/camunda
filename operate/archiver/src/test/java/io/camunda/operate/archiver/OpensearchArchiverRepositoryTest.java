/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.archiver;

import static io.camunda.operate.archiver.AbstractArchiverJob.DATES_AGG;
import static io.camunda.operate.schema.SchemaManager.OPERATE_DELETE_ARCHIVED_INDICES;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.sortOptions;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.client.opensearch._types.SortOrder.Asc;

import io.camunda.operate.Metrics;
import io.camunda.operate.exceptions.ArchiverException;
import io.camunda.operate.property.ArchiverProperties;
import io.camunda.operate.property.OperateOpensearchProperties;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.templates.BatchOperationTemplate;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.store.opensearch.client.sync.OpenSearchISMOperations;
import io.camunda.operate.store.opensearch.client.sync.OpenSearchIndexOperations;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.store.opensearch.dsl.AggregationDSL;
import io.camunda.operate.store.opensearch.dsl.QueryDSL;
import io.camunda.operate.store.opensearch.dsl.RequestDSL;
import io.camunda.operate.util.Either;
import io.camunda.operate.util.OpensearchUtil;
import io.micrometer.core.instrument.Timer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch._types.SortOptions;
import org.opensearch.client.opensearch._types.aggregations.Aggregate;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.aggregations.BucketSortAggregation;
import org.opensearch.client.opensearch._types.aggregations.Buckets;
import org.opensearch.client.opensearch._types.aggregations.DateHistogramAggregate;
import org.opensearch.client.opensearch._types.aggregations.DateHistogramAggregation;
import org.opensearch.client.opensearch._types.aggregations.TopHitsAggregation;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.DeleteByQueryResponse;
import org.opensearch.client.opensearch.core.ReindexResponse;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.SourceConfig;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@ExtendWith(MockitoExtension.class)
public class OpensearchArchiverRepositoryTest {

  @Mock protected RichOpenSearchClient richOpenSearchClient;
  @Mock protected OpenSearchAsyncClient openSearchAsyncClient;
  @Mock protected ThreadPoolTaskScheduler archiverExecutor;
  @InjectMocks OpensearchArchiverRepository underTest;
  @Mock OperateOpensearchProperties operateOpensearchProperties;
  @Mock private BatchOperationTemplate batchOperationTemplate;
  @Mock private ListViewTemplate processInstanceTemplate;
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
    verify(archiverProperties, times(1)).isIlmEnabled();
    assertThat(archiverProperties.isIlmEnabled()).isTrue();
    verify(openSearchIndexOperations, times(1)).indexExists("destinationIndexName");
    assertThat(openSearchIndexOperations.indexExists("destinationIndexName")).isTrue();
    verify(richOpenSearchClient, times(1)).ism();
  }

  @Test
  public void testSetIndexLifeCycleNoIndex() throws ClassNotFoundException {
    final OpenSearchISMOperations openSearchISMOperations = mock(OpenSearchISMOperations.class);
    when(operateProperties.getArchiver()).thenReturn(archiverProperties);
    when(richOpenSearchClient.index()).thenReturn(openSearchIndexOperations);
    when(archiverProperties.isIlmEnabled()).thenReturn(true);
    when(openSearchIndexOperations.indexExists(anyString())).thenReturn(false);
    underTest.setIndexLifeCycle("destinationIndexName");
    verify(archiverProperties, times(1)).isIlmEnabled();
    assertThat(archiverProperties.isIlmEnabled()).isTrue();
    verify(openSearchIndexOperations, times(1)).indexExists("destinationIndexName");
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
    when(richOpenSearchClient.index()).thenReturn(openSearchIndexOperations);
    when(openSearchIndexOperations.indexExists(anyString())).thenReturn(true);
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
    when(richOpenSearchClient.index()).thenReturn(openSearchIndexOperations);
    when(openSearchIndexOperations.indexExists(anyString())).thenReturn(true);
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
  public void testGetBatchOperationsNextBatch() {
    try (final MockedStatic<QueryDSL> queryDSLMockedStatic = mockStatic(QueryDSL.class)) {
      try (final MockedStatic<AggregationDSL> aggregationDSLMockedStatic =
          mockStatic(AggregationDSL.class)) {
        testGetBatchOperationsNextBatchHelper(queryDSLMockedStatic, aggregationDSLMockedStatic);
      }
    }
  }

  public void testGetBatchOperationsNextBatchHelper(
      final MockedStatic<QueryDSL> queryDSLMockedStatic,
      final MockedStatic<AggregationDSL> aggregationDSLMockedStatic) {

    try (final MockedStatic<RequestDSL> requestDSLMockedStatic = mockStatic(RequestDSL.class)) {
      try (final MockedStatic<Timer> timerMockedStatic = mockStatic(Timer.class)) {
        final Query query = mock(Query.class);
        queryDSLMockedStatic.when(() -> QueryDSL.constantScore(any())).thenReturn(query);
        final SearchRequest.Builder searchRequestBuilder = mock(SearchRequest.Builder.class);
        when(batchOperationTemplate.getFullQualifiedName()).thenReturn("fullQualifiedName");
        when(operateProperties.getArchiver()).thenReturn(archiverProperties);
        when(archiverProperties.getElsRolloverDateFormat()).thenReturn("format");
        when(archiverProperties.getRolloverInterval()).thenReturn("1m");
        when(archiverProperties.getRolloverBatchSize()).thenReturn(12);
        final Aggregation aggregation = mock(Aggregation.class);
        final DateHistogramAggregation dateHistogramAggregation =
            mock(DateHistogramAggregation.class);
        final Aggregation bucketAggregation = mock(Aggregation.class);
        final Aggregation topHitAggregation = mock(Aggregation.class);
        final BucketSortAggregation bucketSortAggregation = mock(BucketSortAggregation.class);
        final TopHitsAggregation topHitsAggregation = mock(TopHitsAggregation.class);
        when(topHitsAggregation._toAggregation()).thenReturn(topHitAggregation);
        aggregationDSLMockedStatic
            .when(() -> AggregationDSL.bucketSortAggregation(any(), any()))
            .thenReturn(bucketSortAggregation);
        when(bucketSortAggregation._toAggregation()).thenReturn(bucketAggregation);
        aggregationDSLMockedStatic
            .when(
                () ->
                    AggregationDSL.dateHistogramAggregation(
                        any(), any(), anyString(), anyBoolean()))
            .thenReturn(dateHistogramAggregation);
        aggregationDSLMockedStatic
            .when(
                () ->
                    AggregationDSL.withSubaggregations(
                        (DateHistogramAggregation) any(), (Map<String, Aggregation>) any()))
            .thenReturn(aggregation);
        aggregationDSLMockedStatic
            .when(() -> AggregationDSL.topHitsAggregation((List<String>) any(), anyInt(), any()))
            .thenReturn(topHitsAggregation);
        requestDSLMockedStatic
            .when(() -> RequestDSL.searchRequestBuilder(anyString()))
            .thenReturn(searchRequestBuilder);
        when(searchRequestBuilder.query(query)).thenReturn(searchRequestBuilder);
        when(searchRequestBuilder.aggregations(anyString(), (Aggregation) any()))
            .thenReturn(searchRequestBuilder);
        when(searchRequestBuilder.source((SourceConfig) any())).thenReturn(searchRequestBuilder);
        when(searchRequestBuilder.size(0)).thenReturn(searchRequestBuilder);
        when(searchRequestBuilder.sort((SortOptions) any())).thenReturn(searchRequestBuilder);
        when(searchRequestBuilder.requestCache(false)).thenReturn(searchRequestBuilder);

        final SearchResponse searchResponse = mock(SearchResponse.class);
        final CompletableFuture<SearchResponse<Object>> completableFuture =
            new CompletableFuture<>();
        completableFuture.complete(searchResponse);
        try (final MockedStatic<OpensearchUtil> opensearchUtil = mockStatic(OpensearchUtil.class)) {
          opensearchUtil
              .when(() -> OpensearchUtil.searchAsync(any(), any(), any()))
              .thenReturn(completableFuture);
          final Map<String, Aggregate> aggregations = new HashMap<>();
          when(searchResponse.aggregations()).thenReturn(aggregations);
          final Aggregate aggregate = mock(Aggregate.class);
          aggregations.put(DATES_AGG, aggregate);
          final DateHistogramAggregate dateHistogramAggregate = mock(DateHistogramAggregate.class);
          when(aggregate.dateHistogram()).thenReturn(dateHistogramAggregate);
          final Buckets buckets = mock(Buckets.class);
          when(dateHistogramAggregate.buckets()).thenReturn(buckets);
          final Map<String, String> bucketsMap = new HashMap<>();
          when(buckets.keyed()).thenReturn(bucketsMap);
          final Timer timer = mock(Timer.class);
          final Timer.Sample timerSample = mock(Timer.Sample.class);
          when(metrics.getTimer(any())).thenReturn(timer);
          timerMockedStatic.when(Timer::start).thenReturn(timerSample);
          when(timerSample.stop(any())).thenReturn(5L);
          final CompletableFuture<ArchiveBatch> res = underTest.getBatchOperationNextBatch();
          assertThat(res).isCompleted();
          verify(searchRequestBuilder, times(1)).query(query);
          verify(searchRequestBuilder, times(1)).aggregations(DATES_AGG, aggregation);
          verify(searchRequestBuilder, times(1)).source((SourceConfig) any());
          verify(searchRequestBuilder, times(1)).size(0);
          verify(searchRequestBuilder, times(1)).sort(sortOptions("endDate", Asc));
          verify(searchRequestBuilder, times(1)).requestCache(false);
          verify(bucketSortAggregation, times(1))._toAggregation();
          verify(topHitsAggregation, times(1))._toAggregation();
          verify(searchResponse, times(1)).aggregations();
          verify(aggregate, times(1)).dateHistogram();
          verify(dateHistogramAggregate, times(1)).buckets();
        }
      }
    }
  }
}
