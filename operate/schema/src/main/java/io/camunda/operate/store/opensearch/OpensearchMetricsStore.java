/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.store.opensearch;

import static io.camunda.operate.schema.indices.MetricIndex.EVENT;
import static io.camunda.operate.schema.indices.MetricIndex.EVENT_TIME;
import static io.camunda.operate.schema.indices.MetricIndex.VALUE;
import static io.camunda.operate.store.opensearch.dsl.AggregationDSL.cardinalityAggregation;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.and;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.gteLte;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.or;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.term;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.searchRequestBuilder;

import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.entities.MetricEntity;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.schema.indices.MetricIndex;
import io.camunda.operate.store.AggregationResult;
import io.camunda.operate.store.BatchRequest;
import io.camunda.operate.store.MetricsStore;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import java.time.OffsetDateTime;
import org.opensearch.client.opensearch._types.aggregations.Aggregate;
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
  @Autowired private MetricIndex metricIndex;
  @Autowired private RichOpenSearchClient richOpenSearchClient;

  private AggregationResult searchWithAggregation(
      SearchRequest.Builder requestBuilder, String aggregationName) {
    final Aggregate aggregate;

    try {
      aggregate =
          richOpenSearchClient
              .doc()
              .search(requestBuilder, Object.class)
              .aggregations()
              .get(aggregationName);
    } catch (OperateRuntimeException e) {
      return AggregationResult.ERROR;
    }

    if (aggregate == null) {
      throw new OperateRuntimeException("Search with aggregation returned no aggregation");
    }

    if (!aggregate.isCardinality()) {
      throw new OperateRuntimeException("Unexpected response for aggregations");
    }

    return new AggregationResult(false, aggregate.cardinality().value());
  }

  @Override
  public Long retrieveProcessInstanceCount(OffsetDateTime startTime, OffsetDateTime endTime) {

    final var searchRequestBuilder =
        searchRequestBuilder(metricIndex.getFullQualifiedName())
            .query(
                and(
                    gteLte(EVENT_TIME, startTime, endTime),
                    or(
                        term(EVENT, MetricsStore.EVENT_PROCESS_INSTANCE_FINISHED),
                        term(EVENT, EVENT_PROCESS_INSTANCE_STARTED))))
            .aggregations(
                PROCESS_INSTANCES_AGG_NAME,
                cardinalityAggregation(VALUE, PRECISION_THRESHOLD)._toAggregation());

    return searchWithAggregation(searchRequestBuilder, PROCESS_INSTANCES_AGG_NAME).totalDocs();
  }

  @Override
  public Long retrieveDecisionInstanceCount(
      final OffsetDateTime startTime, final OffsetDateTime endTime) {

    final var searchRequestBuilder =
        searchRequestBuilder(metricIndex.getFullQualifiedName())
            .query(
                and(
                    term(EVENT, MetricsStore.EVENT_DECISION_INSTANCE_EVALUATED),
                    gteLte(EVENT_TIME, startTime, endTime)))
            .aggregations(
                DECISION_INSTANCES_AGG_NAME,
                cardinalityAggregation(VALUE, PRECISION_THRESHOLD)._toAggregation());

    return searchWithAggregation(searchRequestBuilder, DECISION_INSTANCES_AGG_NAME).totalDocs();
  }

  @Override
  public void registerProcessInstanceStartEvent(
      String processInstanceKey,
      String tenantId,
      OffsetDateTime timestamp,
      BatchRequest batchRequest)
      throws PersistenceException {
    final MetricEntity metric =
        createProcessInstanceStartedKey(processInstanceKey, tenantId, timestamp);
    batchRequest.add(metricIndex.getFullQualifiedName(), metric);
  }

  @Override
  public void registerDecisionInstanceCompleteEvent(
      final String decisionInstanceKey,
      String tenantId,
      final OffsetDateTime timestamp,
      BatchRequest batchRequest)
      throws PersistenceException {
    final MetricEntity metric =
        createDecisionsInstanceEvaluatedKey(decisionInstanceKey, tenantId, timestamp);
    batchRequest.add(metricIndex.getFullQualifiedName(), metric);
  }

  private MetricEntity createProcessInstanceStartedKey(
      String processInstanceKey, String tenantId, OffsetDateTime timestamp) {
    return new MetricEntity()
        .setEvent(EVENT_PROCESS_INSTANCE_STARTED)
        .setValue(processInstanceKey)
        .setTenantId(tenantId)
        .setEventTime(timestamp);
  }

  private MetricEntity createDecisionsInstanceEvaluatedKey(
      String decisionInstanceKey, String tenantId, OffsetDateTime timestamp) {
    return new MetricEntity()
        .setEvent(EVENT_DECISION_INSTANCE_EVALUATED)
        .setValue(decisionInstanceKey)
        .setTenantId(tenantId)
        .setEventTime(timestamp);
  }
}
