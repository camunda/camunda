/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.es.writer;

import io.camunda.operate.entities.MetricEntity;
import io.camunda.operate.es.dao.UsageMetricDAO;
import org.elasticsearch.action.index.IndexRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.UUID;

import static io.camunda.operate.es.contract.MetricContract.EVENT_DECISION_INSTANCE_EVALUATED;
import static io.camunda.operate.es.contract.MetricContract.EVENT_PROCESS_INSTANCE_FINISHED;
import static io.camunda.operate.es.contract.MetricContract.Writer;

@Component
public class MetricWriter implements Writer {

  private static final Logger LOGGER = LoggerFactory.getLogger(MetricWriter.class);
  @Autowired private UsageMetricDAO dao;

  @Override
  public IndexRequest registerProcessInstanceCompleteEvent(String processInstanceKey, OffsetDateTime timestamp) {
    final MetricEntity metric = createProcessInstanceFinishedKey(processInstanceKey, timestamp);
    return dao.buildESIndexRequest(metric);
  }

  @Override
  public IndexRequest registerDecisionInstanceCompleteEvent(final String decisionInstanceKey,
      final OffsetDateTime timestamp) {
    final MetricEntity metric = createDecisionsInstanceEvaluatedKey(decisionInstanceKey, timestamp);
    return dao.buildESIndexRequest(metric);
  }

  private MetricEntity createProcessInstanceFinishedKey(String processInstanceKey, OffsetDateTime timestamp) {
    return (MetricEntity) new MetricEntity()
        .setEvent(EVENT_PROCESS_INSTANCE_FINISHED)
        .setValue(processInstanceKey)
        .setEventTime(timestamp)
        .setId(UUID.randomUUID().toString());
  }

  private MetricEntity createDecisionsInstanceEvaluatedKey(String decisionInstanceKey, OffsetDateTime timestamp) {
    return (MetricEntity) new MetricEntity()
        .setEvent(EVENT_DECISION_INSTANCE_EVALUATED)
        .setValue(decisionInstanceKey)
        .setEventTime(timestamp)
        .setId(UUID.randomUUID().toString());
  }
}
