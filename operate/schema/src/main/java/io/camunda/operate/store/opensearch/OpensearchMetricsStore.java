/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.store.opensearch;

import static io.camunda.operate.store.opensearch.dsl.AggregationDSL.termAggregation;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.and;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.gteLte;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.term;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.searchRequestBuilder;
import static io.camunda.webapps.schema.descriptors.index.UsageMetricIndex.EVENT_TIME;
import static io.camunda.webapps.schema.descriptors.index.UsageMetricIndex.EVENT_TYPE;
import static io.camunda.webapps.schema.descriptors.index.UsageMetricIndex.EVENT_VALUE;
import static io.camunda.webapps.schema.descriptors.index.UsageMetricIndex.TENANT_ID;
import static io.camunda.webapps.schema.entities.metrics.UsageMetricsEventType.EDI;
import static io.camunda.webapps.schema.entities.metrics.UsageMetricsEventType.RPI;

import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.store.BatchRequest;
import io.camunda.operate.store.MetricsStore;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.webapps.schema.descriptors.index.UsageMetricIndex;
import io.camunda.webapps.schema.entities.metrics.UsageMetricsEntity;
import java.time.OffsetDateTime;
import java.util.List;
import org.opensearch.client.opensearch._types.aggregations.Aggregate;
import org.opensearch.client.opensearch._types.aggregations.StringTermsBucket;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(OpensearchCondition.class)
@Component
public class OpensearchMetricsStore implements MetricsStore {

  private static final Logger LOGGER = LoggerFactory.getLogger(OpensearchMetricsStore.class);
  private static final String ID_PATTERN = "%s_%s";

  @Autowired private UsageMetricIndex metricIndex;
  @Autowired private RichOpenSearchClient richOpenSearchClient;

  private AggregationResult searchWithAggregation(
      final SearchRequest.Builder requestBuilder, final String aggregationName) {
    final Aggregate aggregate;

    try {
      aggregate =
          richOpenSearchClient
              .doc()
              .search(requestBuilder, Object.class)
              .aggregations()
              .get(aggregationName);
    } catch (final OperateRuntimeException e) {
      return new AggregationResult(true, null, null);
    }

    if (aggregate == null) {
      throw new OperateRuntimeException("Search with aggregation returned no aggregation");
    }

    if (!aggregate.isSterms()) {
      throw new OperateRuntimeException("Unexpected response for aggregations");
    }

    final List<StringTermsBucket> buckets = aggregate.sterms().buckets().array();

    final List<RichOpenSearchClient.AggregationValue> values =
        buckets.stream()
            .map(
                bucket ->
                    new RichOpenSearchClient.AggregationValue(bucket.key(), bucket.docCount()))
            .toList();

    final long sumOfOtherDocCounts =
        aggregate.sterms().sumOtherDocCount(); // size of documents not in result
    final long total = sumOfOtherDocCounts + values.size(); // size of result + other docs
    return new AggregationResult(false, values, total);
  }

  @Override
  public Long retrieveProcessInstanceCount(
      final OffsetDateTime startTime, final OffsetDateTime endTime, final String tenantId) {
    final int limit = 1; // limiting to one, as we just care about the total documents number

    var query = and(gteLte(EVENT_TIME, startTime, endTime), term(EVENT_TYPE, RPI.name()));
    if (tenantId != null) {
      query = and(query, term(TENANT_ID, tenantId));
    }

    final var searchRequestBuilder =
        searchRequestBuilder(metricIndex.getFullQualifiedName())
            .query(query)
            .aggregations(
                PROCESS_INSTANCES_AGG_NAME, termAggregation(EVENT_VALUE, limit)._toAggregation());

    return searchWithAggregation(searchRequestBuilder, PROCESS_INSTANCES_AGG_NAME).totalDocs();
  }

  @Override
  public Long retrieveDecisionInstanceCount(
      final OffsetDateTime startTime, final OffsetDateTime endTime, final String tenantId) {
    final int limit = 1; // limiting to one, as we just care about the total documents number

    var query = and(term(EVENT_TYPE, EDI.name()), gteLte(EVENT_TIME, startTime, endTime));
    if (tenantId != null) {
      query = and(query, term(TENANT_ID, tenantId));
    }

    final var searchRequestBuilder =
        searchRequestBuilder(metricIndex.getFullQualifiedName())
            .query(query)
            .aggregations(
                DECISION_INSTANCES_AGG_NAME, termAggregation(EVENT_VALUE, limit)._toAggregation());

    return searchWithAggregation(searchRequestBuilder, DECISION_INSTANCES_AGG_NAME).totalDocs();
  }

  @Override
  public void registerProcessInstanceStartEvent(
      final long key,
      final String tenantId,
      final int partitionId,
      final OffsetDateTime timestamp,
      final BatchRequest batchRequest)
      throws PersistenceException {
    final UsageMetricsEntity metric =
        createProcessInstanceStartedKey(key, tenantId, partitionId, timestamp);
    batchRequest.add(metricIndex.getFullQualifiedName(), metric);
  }

  @Override
  public void registerDecisionInstanceCompleteEvent(
      final long key,
      final String tenantId,
      final int partitionId,
      final OffsetDateTime timestamp,
      final BatchRequest batchRequest)
      throws PersistenceException {
    final UsageMetricsEntity metric =
        createDecisionsInstanceEvaluatedKey(key, tenantId, partitionId, timestamp);
    batchRequest.add(metricIndex.getFullQualifiedName(), metric);
  }

  private UsageMetricsEntity createProcessInstanceStartedKey(
      final long key,
      final String tenantId,
      final int partitionId,
      final OffsetDateTime timestamp) {
    return new UsageMetricsEntity()
        .setId(String.format(ID_PATTERN, key, tenantId))
        .setEventType(RPI)
        .setEventValue(1L)
        .setEventTime(timestamp)
        .setTenantId(tenantId)
        .setPartitionId(partitionId);
  }

  private UsageMetricsEntity createDecisionsInstanceEvaluatedKey(
      final long key,
      final String tenantId,
      final int partitionId,
      final OffsetDateTime timestamp) {
    return new UsageMetricsEntity()
        .setId(String.format(ID_PATTERN, key, tenantId))
        .setEventType(EDI)
        .setEventValue(1L)
        .setEventTime(timestamp)
        .setTenantId(tenantId)
        .setPartitionId(partitionId);
  }

  private record AggregationResult(
      boolean error, List<RichOpenSearchClient.AggregationValue> values, Long totalDocs) {}
}
