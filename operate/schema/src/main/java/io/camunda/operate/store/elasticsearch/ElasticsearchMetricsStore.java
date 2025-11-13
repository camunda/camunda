/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.store.elasticsearch;

import static io.camunda.operate.schema.indices.MetricIndex.*;
import static io.camunda.operate.schema.indices.MetricIndex.VALUE;
import static io.camunda.operate.store.elasticsearch.dao.Query.range;
import static io.camunda.operate.store.elasticsearch.dao.Query.whereEquals;

import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.entities.MetricEntity;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.schema.indices.MetricIndex;
import io.camunda.operate.store.BatchRequest;
import io.camunda.operate.store.MetricsStore;
import io.camunda.operate.store.elasticsearch.dao.Query;
import io.camunda.operate.store.elasticsearch.dao.UsageMetricDAO;
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
  public Long retrieveProcessInstanceCount(OffsetDateTime startTime, OffsetDateTime endTime) {
    final Query query =
        Query.whereEquals(EVENT, MetricsStore.EVENT_PROCESS_INSTANCE_FINISHED)
            .or(whereEquals(EVENT, EVENT_PROCESS_INSTANCE_STARTED))
            .and(range(EVENT_TIME, startTime, endTime))
            .aggregate(PROCESS_INSTANCES_AGG_NAME, VALUE, PRECISION_THRESHOLD);

    final var response = dao.searchWithAggregation(query);
    if (response.hasError()) {
      final String message = "Error while retrieving process instance count between dates";
      LOGGER.error(message);
      throw new OperateRuntimeException(message);
    }

    return response.totalDocs();
  }

  @Override
  public Long retrieveDecisionInstanceCount(
      final OffsetDateTime startTime, final OffsetDateTime endTime) {
    final Query query =
        Query.whereEquals(EVENT, MetricsStore.EVENT_DECISION_INSTANCE_EVALUATED)
            .and(range(EVENT_TIME, startTime, endTime))
            .aggregate(DECISION_INSTANCES_AGG_NAME, VALUE, PRECISION_THRESHOLD);

    final var response = dao.searchWithAggregation(query);
    if (response.hasError()) {
      final String message = "Error while retrieving decision instance count between dates";
      LOGGER.error(message);
      throw new OperateRuntimeException(message);
    }

    return response.totalDocs();
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
        .setEventTime(timestamp)
        .setTenantId(tenantId);
  }

  private MetricEntity createDecisionsInstanceEvaluatedKey(
      String decisionInstanceKey, String tenantId, OffsetDateTime timestamp) {
    return new MetricEntity()
        .setEvent(EVENT_DECISION_INSTANCE_EVALUATED)
        .setValue(decisionInstanceKey)
        .setEventTime(timestamp)
        .setTenantId(tenantId);
  }
}
