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
import static io.camunda.tasklist.util.ElasticsearchUtil.AGGREGATION_TERMS_SIZE;
import static io.camunda.webapps.schema.descriptors.template.UsageMetricTUTemplate.ASSIGNEE_HASH;
import static io.camunda.webapps.schema.descriptors.template.UsageMetricTUTemplate.END_TIME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.refEq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ExpandWildcard;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.Buckets;
import co.elastic.clients.elasticsearch._types.aggregations.LongTermsAggregate;
import co.elastic.clients.elasticsearch._types.aggregations.LongTermsBucket;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.webapps.schema.descriptors.template.UsageMetricTUTemplate;
import io.camunda.webapps.schema.entities.metrics.UsageMetricsTUEntity;
import io.camunda.webapps.schema.entities.usertask.TaskEntity;
import io.camunda.zeebe.util.HashUtil;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class TaskMetricsStoreElasticSearchTest {

  private static final String METRIC_INDEX_NAME = "usage_metric_tu_x.0.0";
  @Mock private UsageMetricTUTemplate template;
  @Mock private ElasticsearchClient es8Client;

  @InjectMocks private TaskMetricsStoreElasticSearch instance;

  @BeforeEach
  void setUp() {
    when(template.getFullQualifiedName()).thenReturn(METRIC_INDEX_NAME);
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
            .setStartTime(now)
            .setEndTime(now)
            .setTenantId("<default>")
            .setPartitionId(0);
    final var indexRes = mock(co.elastic.clients.elasticsearch.core.IndexResponse.class);
    final var expectedReq =
        co.elastic.clients.elasticsearch.core.IndexRequest.of(
            b ->
                b.index(template.getFullQualifiedName())
                    .id(expectedEntry.getId())
                    .document(expectedEntry));
    when(es8Client.index(any(co.elastic.clients.elasticsearch.core.IndexRequest.class)))
        .thenReturn(indexRes);
    when(indexRes.result()).thenReturn(co.elastic.clients.elasticsearch._types.Result.Created);

    // When
    instance.registerTaskAssigned(task);

    // Then
    verify(es8Client).index(refEq(expectedReq));
  }

  @Test
  public void exceptionIsNotHandledOnReaderWriterLevel() throws IOException {
    // Given
    final OffsetDateTime now = OffsetDateTime.now();
    final OffsetDateTime oneHourBefore = OffsetDateTime.now().withHour(1);

    when(es8Client.search(any(SearchRequest.class), eq(Void.class)))
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

    when(es8Client.search(any(SearchRequest.class), eq(Void.class)))
        .thenThrow(new IOException("IO exception occurred"));

    // When - Then
    assertThatThrownBy(
            () -> instance.retrieveDistinctAssigneesBetweenDates(oneHourBefore, now, null))
        .isInstanceOf(TasklistRuntimeException.class)
        .hasMessage("Error while retrieving assigned users between dates");
  }

  @Test
  @SuppressWarnings("unchecked")
  public void expectedResponseWhenResultsAreEmpty() throws IOException {
    // Given
    final OffsetDateTime now = OffsetDateTime.now();
    final OffsetDateTime oneHourBefore = OffsetDateTime.now().withHour(1);

    final SearchResponse<Void> searchResponse = mock(SearchResponse.class);
    when(es8Client.search(any(SearchRequest.class), eq(Void.class))).thenReturn(searchResponse);

    final LongTermsAggregate longTermsAggregate = mock(LongTermsAggregate.class);
    final Aggregate aggregate = mock(Aggregate.class);
    when(aggregate.lterms()).thenReturn(longTermsAggregate);
    when(searchResponse.aggregations()).thenReturn(Map.of(ASSIGNEE, aggregate));

    final Buckets<LongTermsBucket> buckets = mock(Buckets.class);
    when(longTermsAggregate.buckets()).thenReturn(buckets);
    when(buckets.array()).thenReturn(Collections.emptyList());

    // When
    final var result = instance.retrieveDistinctAssigneesBetweenDates(oneHourBefore, now, null);

    // Then
    assertThat(result).isEmpty();
    final ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
    verify(es8Client).search(captor.capture(), eq(Void.class));
    final SearchRequest capturedRequest = captor.getValue();
    assertThat(capturedRequest.index()).containsExactly(METRIC_INDEX_NAME);
    assertThat(capturedRequest.aggregations()).containsKey(ASSIGNEE);
  }

  private SearchRequest buildSearchRequest(
      final OffsetDateTime startTime, final OffsetDateTime endTime) {
    final BoolQuery.Builder boolQueryBuilder = new BoolQuery.Builder();
    boolQueryBuilder.must(
        QueryBuilders.range(
            r -> r.date(d -> d.field(END_TIME).gte(startTime.toString()).lt(endTime.toString()))));

    final Query query = boolQueryBuilder.build()._toQuery();

    final Aggregation termsAggregation =
        Aggregation.of(a -> a.terms(t -> t.field(ASSIGNEE_HASH).size(AGGREGATION_TERMS_SIZE)));

    return SearchRequest.of(
        s ->
            s.index(METRIC_INDEX_NAME)
                .ignoreUnavailable(true)
                .allowNoIndices(true)
                .ignoreThrottled(true)
                .expandWildcards(ExpandWildcard.Open)
                .query(query)
                .aggregations(ASSIGNEE, termsAggregation)
                .size(0));
  }

  @Test
  @SuppressWarnings("unchecked")
  public void expectedResponseWhenResultsAreReturned() throws IOException {
    // Given
    final OffsetDateTime now = OffsetDateTime.now();
    final OffsetDateTime oneHourBefore = OffsetDateTime.now().withHour(1);

    final SearchResponse<Void> searchResponse = mock(SearchResponse.class);
    when(es8Client.search(any(SearchRequest.class), eq(Void.class))).thenReturn(searchResponse);

    final LongTermsAggregate longTermsAggregate = mock(LongTermsAggregate.class);
    final Aggregate aggregate = mock(Aggregate.class);
    when(aggregate.lterms()).thenReturn(longTermsAggregate);
    when(searchResponse.aggregations()).thenReturn(Map.of(ASSIGNEE, aggregate));

    final Buckets<LongTermsBucket> buckets = mock(Buckets.class);
    when(longTermsAggregate.buckets()).thenReturn(buckets);

    final LongTermsBucket bucket = mock(LongTermsBucket.class);
    when(bucket.key()).thenReturn(1234567L);
    when(buckets.array()).thenReturn(List.of(bucket));

    // When
    final var result = instance.retrieveDistinctAssigneesBetweenDates(oneHourBefore, now, null);

    // Then
    assertThat(result).containsExactly(1234567L);
    final ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
    verify(es8Client).search(captor.capture(), eq(Void.class));
    final SearchRequest capturedRequest = captor.getValue();
    assertThat(capturedRequest.index()).containsExactly(METRIC_INDEX_NAME);
    assertThat(capturedRequest.aggregations()).containsKey(ASSIGNEE);
  }
}
