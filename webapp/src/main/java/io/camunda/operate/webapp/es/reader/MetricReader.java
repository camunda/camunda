/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.es.reader;

import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.es.contract.MetricContract;
import io.camunda.operate.es.dao.Query;
import io.camunda.operate.es.dao.UsageMetricDAO;
import io.camunda.operate.es.dao.response.AggregationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

import static io.camunda.operate.es.contract.MetricContract.EVENT_PROCESS_INSTANCE_STARTED;
import static io.camunda.operate.es.dao.Query.whereEquals;
import static io.camunda.operate.schema.indices.MetricIndex.EVENT;
import static io.camunda.operate.schema.indices.MetricIndex.EVENT_TIME;
import static io.camunda.operate.schema.indices.MetricIndex.VALUE;
import static io.camunda.operate.es.dao.Query.range;

@Component
public class MetricReader implements MetricContract.Reader {

  public static final String PROCESS_INSTANCES_AGG_NAME = "process_instances";
  public static final String DECISION_INSTANCES_AGG_NAME = "decision_instances";
  private static final Logger LOGGER = LoggerFactory.getLogger(MetricReader.class);
  @Autowired private UsageMetricDAO dao;

  @Override
  public Long retrieveProcessInstanceCount(OffsetDateTime startTime, OffsetDateTime endTime) {
    int limit = 1; // limiting to one, as we just care about the total documents number
    final Query query =
        Query.whereEquals(EVENT, MetricContract.EVENT_PROCESS_INSTANCE_FINISHED)
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
  public Long retrieveDecisionInstanceCount(final OffsetDateTime startTime,
      final OffsetDateTime endTime) {
    int limit = 1; // limiting to one, as we just care about the total documents number
    final Query query =
        Query.whereEquals(EVENT, MetricContract.EVENT_DECISION_INSTANCE_EVALUATED)
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
}
