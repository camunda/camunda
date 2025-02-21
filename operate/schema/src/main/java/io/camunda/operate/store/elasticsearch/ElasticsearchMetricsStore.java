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
import static io.camunda.webapps.schema.descriptors.operate.index.MetricIndex.*;
import static io.camunda.webapps.schema.descriptors.operate.index.MetricIndex.VALUE;

import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.store.BatchRequest;
import io.camunda.operate.store.MetricsStore;
import io.camunda.operate.store.elasticsearch.dao.Query;
import io.camunda.operate.store.elasticsearch.dao.UsageMetricDAO;
import io.camunda.operate.store.elasticsearch.dao.response.AggregationResponse;
import io.camunda.webapps.schema.descriptors.operate.index.MetricIndex;
import io.camunda.webapps.schema.entities.MetricEntity;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(ElasticsearchCondition.class)
@Component
public class ElasticsearchMetricsStore implements MetricsStore {

  private static final Logger LOGGER = LoggerFactory.getLogger(ElasticsearchMetricsStore.class);
  @Autowired private MetricIndex metricIndex;

  @Autowired private UsageMetricDAO dao;

  @Override
  public Long retrieveProcessInstanceCount(
      final OffsetDateTime startTime, final OffsetDateTime endTime) {
    final int limit = 1; // limiting to one, as we just care about the total documents number
    final Query query =
        Query.whereEquals(EVENT, MetricsStore.EVENT_PROCESS_INSTANCE_FINISHED)
            .or(whereEquals(EVENT, EVENT_PROCESS_INSTANCE_STARTED))
            .and(range(EVENT_TIME, startTime, endTime))
            .aggregate(PROCESS_INSTANCES_AGG_NAME, VALUE, limit);

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
      final OffsetDateTime startTime, final OffsetDateTime endTime) {
    final int limit = 1; // limiting to one, as we just care about the total documents number
    final Query query =
        Query.whereEquals(EVENT, MetricsStore.EVENT_DECISION_INSTANCE_EVALUATED)
            .and(range(EVENT_TIME, startTime, endTime))
            .aggregate(DECISION_INSTANCES_AGG_NAME, VALUE, limit);

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
      final String processInstanceKey,
      final String tenantId,
      final OffsetDateTime timestamp,
      final BatchRequest batchRequest)
      throws PersistenceException {
    final MetricEntity metric =
        createProcessInstanceStartedKey(processInstanceKey, tenantId, timestamp);
    batchRequest.add(metricIndex.getFullQualifiedName(), metric);
  }

  @Override
  public void registerDecisionInstanceCompleteEvent(
      final String decisionInstanceKey,
      final String tenantId,
      final OffsetDateTime timestamp,
      final BatchRequest batchRequest)
      throws PersistenceException {
    final MetricEntity metric =
        createDecisionsInstanceEvaluatedKey(decisionInstanceKey, tenantId, timestamp);
    batchRequest.add(metricIndex.getFullQualifiedName(), metric);
  }

  private MetricEntity createProcessInstanceStartedKey(
      final String processInstanceKey, final String tenantId, final OffsetDateTime timestamp) {
    return new MetricEntity()
        .setEvent(EVENT_PROCESS_INSTANCE_STARTED)
        .setValue(processInstanceKey)
        .setEventTime(timestamp)
        .setTenantId(tenantId);
  }

  private MetricEntity createDecisionsInstanceEvaluatedKey(
      final String decisionInstanceKey, final String tenantId, final OffsetDateTime timestamp) {
    return new MetricEntity()
        .setEvent(EVENT_DECISION_INSTANCE_EVALUATED)
        .setValue(decisionInstanceKey)
        .setEventTime(timestamp)
        .setTenantId(tenantId);
  }
}
