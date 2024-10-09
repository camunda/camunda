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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.Metrics;
import io.camunda.operate.property.ArchiverProperties;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.templates.BatchOperationTemplate;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate;
import io.micrometer.core.instrument.Timer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.IndicesClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.PipelineAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramInterval;
import org.elasticsearch.search.aggregations.bucket.histogram.Histogram;
import org.elasticsearch.search.aggregations.metrics.TopHitsAggregationBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@ExtendWith(MockitoExtension.class)
public class ElasticsearchArchiveRepositoryTest {

  @Mock protected ThreadPoolTaskScheduler threadPoolTaskScheduler;
  @InjectMocks ElasticsearchArchiverRepository underTest;
  @Mock private BatchOperationTemplate batchOperationTemplate;
  @Mock private ListViewTemplate listViewTemplate;
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

    verify(archiverProperties, times(1)).isIlmEnabled();
    assertThat(archiverProperties.isIlmEnabled()).isTrue();
    verify(esClient, times(2)).indices();
    verify(indicesClient, times(1)).putSettings(any(), any());
    verify(indicesClient, times(1)).exists((GetIndexRequest) any(), any());
  }

  @Test
  public void testSetIndexLifeCycleNoIndex() throws IOException {
    final IndicesClient indicesClient = mock(IndicesClient.class);
    when(operateProperties.getArchiver()).thenReturn(archiverProperties);
    when(archiverProperties.isIlmEnabled()).thenReturn(true);
    when(esClient.indices()).thenReturn(indicesClient);
    when(indicesClient.exists((GetIndexRequest) any(), any())).thenReturn(false);
    underTest.setIndexLifeCycle("destinationIndexName");

    verify(archiverProperties, times(1)).isIlmEnabled();
    assertThat(archiverProperties.isIlmEnabled()).isTrue();
    verify(esClient, times(1)).indices();
    verify(indicesClient, times(0)).putSettings(any(), any());
  }

  @Test
  public void testDeleteDocuments() {
    try (final MockedStatic<ElasticsearchUtil> mockedStatic = mockStatic(ElasticsearchUtil.class)) {
      try (final MockedStatic<Timer> mockedTimer = mockStatic(Timer.class)) {
        final Timer.Sample timer = mock(Timer.Sample.class);
        when(timer.stop(any())).thenReturn(1000L);
        mockedTimer.when(Timer::start).thenReturn(timer);
        mockedStatic
            .when(
                () ->
                    ElasticsearchUtil.deleteAsyncWithConnectionRelease(
                        any(), anyString(), anyString(), any(), any(), any()))
            .thenReturn(CompletableFuture.completedFuture(1000L));
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
        final CompletableFuture<Void> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new Exception("test error"));
        mockedStatic
            .when(
                () ->
                    ElasticsearchUtil.deleteAsyncWithConnectionRelease(
                        any(), anyString(), anyString(), any(), any(), any()))
            .thenReturn(failedFuture);
        final CompletableFuture<Void> res = underTest.deleteDocuments("index", "id", List.of());
        try {
          res.join();
        } catch (final Exception e) {
          assertThat(e.getMessage()).isEqualTo("java.lang.Exception: test error");
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
        mockedStatic
            .when(
                () ->
                    ElasticsearchUtil.reindexAsyncWithConnectionRelease(
                        any(), any(), anyString(), any()))
            .thenReturn(CompletableFuture.completedFuture(1000L));
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
            .when(
                () ->
                    ElasticsearchUtil.reindexAsyncWithConnectionRelease(
                        any(), any(), anyString(), any()))
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
  public void testGetBatchOperationsNextBatchEmptyBucket() {
    setBatchOperationMocks();

    final SearchRequest searchRequest = mock(SearchRequest.class);
    final SearchResponse searchResponse = mock(SearchResponse.class);
    try (final MockedStatic<AggregationBuilders> mockedStatic =
        mockStatic(AggregationBuilders.class)) {
      try (final MockedConstruction<SearchRequest> mockedConstruction =
          mockConstruction(
              SearchRequest.class,
              (mock, context) -> {
                when(mock.source(any())).thenReturn(searchRequest);
                when(mock.requestCache(false)).thenReturn(searchRequest);
              })) {
        try (final MockedStatic<Timer> mockedTimer = mockStatic(Timer.class)) {
          testGetBatchOperationsNextBatchEmptyBucketHelper(
              searchResponse, mockedStatic, mockedTimer);
          verify(searchRequest, times(1)).requestCache(false);
        }
      }
    }
  }

  public void testGetBatchOperationsNextBatchEmptyBucketHelper(
      final SearchResponse searchResponse,
      final MockedStatic<AggregationBuilders> mockedStatic,
      final MockedStatic<Timer> mockedTimer) {

    try (final MockedStatic<ElasticsearchUtil> elasticsearchUtilMockedStatic =
        mockStatic(ElasticsearchUtil.class)) {
      final DateHistogramAggregationBuilder dateHistogramAggregationBuilder =
          setHistogramMockForBatchOperations(mockedStatic);
      final TopHitsAggregationBuilder topHitsAggregationBuilder =
          setTopHitsAggregationBuilder(mockedStatic);
      mockedStatic
          .when(() -> AggregationBuilders.topHits(anyString()))
          .thenReturn(topHitsAggregationBuilder);
      final Timer.Sample timer = mock(Timer.Sample.class);
      when(timer.stop(any())).thenReturn(1000L);
      mockedTimer.when(Timer::start).thenReturn(timer);
      elasticsearchUtilMockedStatic
          .when(() -> ElasticsearchUtil.searchAsync(any(), any(), any()))
          .thenReturn(CompletableFuture.completedFuture(searchResponse));
      final Aggregations aggregations = mock(Aggregations.class);
      final Histogram histogram = mock(Histogram.class);
      final CompletableFuture<SearchResponse> completableFuture = new CompletableFuture<>();
      when(searchResponse.getAggregations()).thenReturn(aggregations);
      when(aggregations.get(anyString())).thenReturn(histogram);
      when(histogram.getBuckets()).thenReturn(new ArrayList<>());
      completableFuture.complete(searchResponse);
      final CompletableFuture<ArchiveBatch> res = underTest.getBatchOperationNextBatch();
      assertThat(res).isCompleted();

      verify(dateHistogramAggregationBuilder, times(1)).field("endDate");
      verify(dateHistogramAggregationBuilder, times(1))
          .calendarInterval(new DateHistogramInterval("1m"));
      verify(dateHistogramAggregationBuilder, times(1)).format("format");
      verify(dateHistogramAggregationBuilder, times(1)).keyed(true);
      verify(dateHistogramAggregationBuilder, times(1)).subAggregation((AggregationBuilder) any());
      verify(dateHistogramAggregationBuilder, times(1))
          .subAggregation((PipelineAggregationBuilder) any());
      verify(topHitsAggregationBuilder, times(1)).size(0);
      verify(topHitsAggregationBuilder, times(1)).sort("id", SortOrder.ASC);
      verify(topHitsAggregationBuilder, times(1)).fetchSource("id", null);
      verify(searchResponse, times(1)).getAggregations();
      verify(timer, times(1)).stop(any());
      verify(aggregations, times(1)).get("datesAgg");
      verify(histogram, times(1)).getBuckets();
    }
  }

  private void setBatchOperationMocks() {
    when(operateProperties.getArchiver()).thenReturn(archiverProperties);
    when(archiverProperties.getRolloverInterval()).thenReturn("1m");
    when(archiverProperties.getElsRolloverDateFormat()).thenReturn("format");
    when(batchOperationTemplate.getFullQualifiedName()).thenReturn("qualifiedName");
  }

  private DateHistogramAggregationBuilder setHistogramMockForBatchOperations(
      final MockedStatic<AggregationBuilders> mockedStatic) {
    final DateHistogramAggregationBuilder dateHistogramAggregationBuilder =
        mock(DateHistogramAggregationBuilder.class);
    mockedStatic
        .when(() -> AggregationBuilders.dateHistogram(anyString()))
        .thenReturn(dateHistogramAggregationBuilder);
    when(dateHistogramAggregationBuilder.field(anyString()))
        .thenReturn(dateHistogramAggregationBuilder);
    when(dateHistogramAggregationBuilder.calendarInterval(any()))
        .thenReturn(dateHistogramAggregationBuilder);
    when(dateHistogramAggregationBuilder.format(anyString()))
        .thenReturn(dateHistogramAggregationBuilder);
    when(dateHistogramAggregationBuilder.keyed(true)).thenReturn(dateHistogramAggregationBuilder);
    when(dateHistogramAggregationBuilder.subAggregation((PipelineAggregationBuilder) any()))
        .thenReturn(dateHistogramAggregationBuilder);
    when(dateHistogramAggregationBuilder.subAggregation((TopHitsAggregationBuilder) any()))
        .thenReturn(dateHistogramAggregationBuilder);
    return dateHistogramAggregationBuilder;
  }

  private TopHitsAggregationBuilder setTopHitsAggregationBuilder(
      final MockedStatic<AggregationBuilders> mockedStatic) {
    final TopHitsAggregationBuilder topHitsAggregationBuilder =
        mock(TopHitsAggregationBuilder.class);
    mockedStatic
        .when(() -> AggregationBuilders.topHits(anyString()))
        .thenReturn(topHitsAggregationBuilder);
    when(topHitsAggregationBuilder.size(anyInt())).thenReturn(topHitsAggregationBuilder);
    when(topHitsAggregationBuilder.sort(anyString(), any())).thenReturn(topHitsAggregationBuilder);
    when(topHitsAggregationBuilder.fetchSource(anyString(), eq(null)))
        .thenReturn(topHitsAggregationBuilder);
    return topHitsAggregationBuilder;
  }
}
