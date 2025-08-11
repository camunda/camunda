/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.store.elasticsearch;

import static io.camunda.operate.store.elasticsearch.dao.Query.range;
import static io.camunda.operate.store.elasticsearch.dao.Query.whereEquals;
import static io.camunda.webapps.schema.descriptors.index.UsageMetricIndex.EVENT_TIME;
import static io.camunda.webapps.schema.descriptors.index.UsageMetricIndex.EVENT_TYPE;
import static io.camunda.webapps.schema.descriptors.index.UsageMetricIndex.EVENT_VALUE;
import static io.camunda.webapps.schema.entities.metrics.UsageMetricsEventType.EDI;
import static io.camunda.webapps.schema.entities.metrics.UsageMetricsEventType.RPI;

import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.store.BatchRequest;
import io.camunda.operate.store.MetricsStore;
import io.camunda.operate.store.elasticsearch.dao.Query;
import io.camunda.operate.store.elasticsearch.dao.UsageMetricDAO;
import io.camunda.operate.store.elasticsearch.dao.response.AggregationResponse;
import io.camunda.webapps.schema.descriptors.index.UsageMetricIndex;
import io.camunda.webapps.schema.entities.metrics.UsageMetricsEntity;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(ElasticsearchCondition.class)
@Component
public class ElasticsearchMetricsStore implements MetricsStore {

  public static final String ID_PATTERN = "%s_%s";
  private static final Logger LOGGER = LoggerFactory.getLogger(ElasticsearchMetricsStore.class);

  @Autowired private UsageMetricIndex metricIndex;

  @Autowired private UsageMetricDAO dao;

  @Override
  public Long retrieveProcessInstanceCount(
      final OffsetDateTime startTime, final OffsetDateTime endTime, final String tenantId) {
    final int limit = 1; // limiting to one, as we just care about the total documents number
    Query query =
        Query.whereEquals(EVENT_TYPE, RPI.name()).and(range(EVENT_TIME, startTime, endTime));

    if (tenantId != null) {
      query = query.and(whereEquals(UsageMetricIndex.TENANT_ID, tenantId));
    }
    query = query.aggregate(PROCESS_INSTANCES_AGG_NAME, EVENT_VALUE, limit);

    final AggregationResponse response = dao.searchWithAggregation(query);
    if (response.hasError()) {
      final String message = "Error while retrieving process instance count between dates";
      LOGGER.error(message);
      throw new OperateRuntimeException(message);
    }

    return response.getSumOfTotalDocs();
  }

  @Override
  public Long retrieveDecisionInstanceCount(
      final OffsetDateTime startTime, final OffsetDateTime endTime, final String tenantId) {
    final int limit = 1; // limiting to one, as we just care about the total documents number
    Query query =
        Query.whereEquals(EVENT_TYPE, EDI.name()).and(range(EVENT_TIME, startTime, endTime));
    if (tenantId != null) {
      query = query.and(whereEquals(UsageMetricIndex.TENANT_ID, tenantId));
    }
    query = query.aggregate(DECISION_INSTANCES_AGG_NAME, EVENT_VALUE, limit);

    final AggregationResponse response = dao.searchWithAggregation(query);
    if (response.hasError()) {
      final String message = "Error while retrieving decision instance count between dates";
      LOGGER.error(message);
      throw new OperateRuntimeException(message);
    }

    return response.getSumOfTotalDocs();
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
}
