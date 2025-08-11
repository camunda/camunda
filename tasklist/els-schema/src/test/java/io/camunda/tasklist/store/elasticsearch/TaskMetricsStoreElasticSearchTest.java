/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.store.elasticsearch;

import static io.camunda.tasklist.store.elasticsearch.TaskMetricsStoreElasticSearch.ASSIGNEE;
import static io.camunda.tasklist.store.elasticsearch.TaskMetricsStoreElasticSearch.TU_ID_PATTERN;
import static io.camunda.tasklist.util.ElasticsearchUtil.LENIENT_EXPAND_OPEN_IGNORE_THROTTLED;
import static io.camunda.webapps.schema.descriptors.index.UsageMetricTUIndex.ASSIGNEE_HASH;
import static io.camunda.webapps.schema.descriptors.index.UsageMetricTUIndex.EVENT_TIME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.refEq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.CommonUtils;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.webapps.schema.descriptors.index.UsageMetricTUIndex;
import io.camunda.webapps.schema.entities.metrics.UsageMetricsTUEntity;
import io.camunda.webapps.schema.entities.usertask.TaskEntity;
import io.camunda.zeebe.util.HashUtil;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedLongTerms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.xcontent.XContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class TaskMetricsStoreElasticSearchTest {

  private static final String METRIC_INDEX_NAME = "usage_metric_tu_x.0.0";
  @Mock private UsageMetricTUIndex index;
  @Mock private RestHighLevelClient esClient;
  @Spy private ObjectMapper objectMapper = CommonUtils.OBJECT_MAPPER;

  @InjectMocks private TaskMetricsStoreElasticSearch instance;

  @BeforeEach
  void setUp() {
    when(index.getFullQualifiedName()).thenReturn(METRIC_INDEX_NAME);
  }

  @Test
  public void verifyRegisterEventWasCalledWithRightArgument() throws IOException {
    // Given
    final OffsetDateTime now = OffsetDateTime.now();
    final String assignee = "John Lennon";
    final long assigneeHash = HashUtil.getStringHashValue(assignee);
    final TaskEntity task = new TaskEntity().setCreationTime(now).setAssignee(assignee);
    final UsageMetricsTUEntity expectedEntry =
        new UsageMetricsTUEntity()
            .setId(String.format(TU_ID_PATTERN, task.getKey(), task.getTenantId(), assigneeHash))
            .setAssigneeHash(assigneeHash)
            .setEventTime(now)
            .setTenantId("<default>")
            .setPartitionId(0);
    final var indexResponse = mock(IndexResponse.class);
    when(indexResponse.status()).thenReturn(RestStatus.CREATED);
    final IndexRequest expectedIndexRequest =
        new IndexRequest(METRIC_INDEX_NAME)
            .id(expectedEntry.getId())
            .source(objectMapper.writeValueAsString(expectedEntry), XContentType.JSON);
    when(esClient.index(any(), eq(RequestOptions.DEFAULT))).thenReturn(indexResponse);

    // When
    instance.registerTaskAssigned(task);

    // Then
    verify(esClient).index(refEq(expectedIndexRequest), eq(RequestOptions.DEFAULT));
  }

  @Test
  public void exceptionIsNotHandledOnReaderWriterLevel() throws IOException {
    // Given
    final OffsetDateTime now = OffsetDateTime.now();
    final OffsetDateTime oneHourBefore = OffsetDateTime.now().withHour(1);

    final SearchRequest searchRequest = buildSearchRequest(now, oneHourBefore);
    when(esClient.search(refEq(searchRequest), eq(RequestOptions.DEFAULT)))
        .thenThrow(new IOException("IO exception raised"));

    // When - Then
    assertThatThrownBy(
            () -> instance.retrieveDistinctAssigneesBetweenDates(oneHourBefore, now, null))
        .isInstanceOf(TasklistRuntimeException.class)
        .hasMessage("Error while retrieving assigned users between dates");
  }

  @Test
  public void throwErrorWhenErrorResponse() throws IOException {
    // Given
    final OffsetDateTime now = OffsetDateTime.now();
    final OffsetDateTime oneHourBefore = OffsetDateTime.now().withHour(1);

    when(esClient.search(any(), eq(RequestOptions.DEFAULT)))
        .thenThrow(new IOException("IO exception occurred"));

    // When - Then
    assertThatThrownBy(
            () -> instance.retrieveDistinctAssigneesBetweenDates(oneHourBefore, now, null))
        .isInstanceOf(TasklistRuntimeException.class)
        .hasMessage("Error while retrieving assigned users between dates");
  }

  @Test
  public void expectedResponseWhenResultsAreEmpty() throws IOException {
    // Given
    final OffsetDateTime now = OffsetDateTime.now();
    final OffsetDateTime oneHourBefore = OffsetDateTime.now().withHour(1);

    final SearchRequest searchRequest = buildSearchRequest(now, oneHourBefore);
    final SearchResponse searchResponse = mock(SearchResponse.class);
    when(esClient.search(refEq(searchRequest), eq(RequestOptions.DEFAULT)))
        .thenReturn(searchResponse);
    final Aggregations aggregations = mock(Aggregations.class);
    when(searchResponse.getAggregations()).thenReturn(aggregations);
    final var aggregation = mock(ParsedLongTerms.class);
    when(aggregations.get(ASSIGNEE)).thenReturn(aggregation);
    when(aggregation.getBuckets()).thenReturn(Collections.emptyList());

    // When
    final var result = instance.retrieveDistinctAssigneesBetweenDates(oneHourBefore, now, null);

    // Then
    assertThat(result).isEmpty();
  }

  private SearchRequest buildSearchRequest(
      final OffsetDateTime now, final OffsetDateTime oneHourBefore) {
    final BoolQueryBuilder rangeQuery =
        boolQuery().must(QueryBuilders.rangeQuery(EVENT_TIME).gte(oneHourBefore).lte(now));
    final TermsAggregationBuilder aggregation =
        AggregationBuilders.terms(ASSIGNEE).field(ASSIGNEE_HASH).size(Integer.MAX_VALUE);

    final SearchSourceBuilder source =
        SearchSourceBuilder.searchSource().query(rangeQuery).aggregation(aggregation);
    final SearchRequest searchRequest =
        new SearchRequest(index.getFullQualifiedName())
            .indicesOptions(LENIENT_EXPAND_OPEN_IGNORE_THROTTLED)
            .source(source);
    return searchRequest;
  }

  @Test
  public void expectedResponseWhenResultsAreReturned() throws IOException {
    // Given
    final OffsetDateTime now = OffsetDateTime.now();
    final OffsetDateTime oneHourBefore = OffsetDateTime.now().withHour(1);

    final SearchRequest searchRequest = buildSearchRequest(now, oneHourBefore);
    final SearchResponse searchResponse = mock(SearchResponse.class);
    when(esClient.search(refEq(searchRequest), eq(RequestOptions.DEFAULT)))
        .thenReturn(searchResponse);
    final Aggregations aggregations = mock(Aggregations.class);
    when(searchResponse.getAggregations()).thenReturn(aggregations);
    final var aggregation = mock(ParsedLongTerms.class);
    when(aggregations.get(ASSIGNEE)).thenReturn(aggregation);
    final var bucket = mock(ParsedLongTerms.ParsedBucket.class);
    when(bucket.getKey()).thenReturn(1234567L);
    when((List<ParsedLongTerms.ParsedBucket>) aggregation.getBuckets()).thenReturn(List.of(bucket));

    // When
    final var result = instance.retrieveDistinctAssigneesBetweenDates(oneHourBefore, now, null);

    // Then
    assertThat(result).containsExactly(1234567L);
  }
}
