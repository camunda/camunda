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

import static io.camunda.operate.es.contract.MetricContract.*;

@Component
public class MetricWriter implements Writer {

  private static final Logger LOGGER = LoggerFactory.getLogger(MetricWriter.class);
  @Autowired private UsageMetricDAO dao;

  @Override
  public IndexRequest registerProcessInstanceStartEvent(String processInstanceKey, OffsetDateTime timestamp) {
    final MetricEntity metric = createProcessInstanceStartedKey(processInstanceKey, timestamp);
    return dao.buildESIndexRequest(metric);
  }

  @Override
  public IndexRequest registerDecisionInstanceCompleteEvent(final String decisionInstanceKey,
      final OffsetDateTime timestamp) {
    final MetricEntity metric = createDecisionsInstanceEvaluatedKey(decisionInstanceKey, timestamp);
    return dao.buildESIndexRequest(metric);
  }

  private MetricEntity createProcessInstanceStartedKey(String processInstanceKey, OffsetDateTime timestamp) {
    return (MetricEntity) new MetricEntity()
        .setEvent(EVENT_PROCESS_INSTANCE_STARTED)
        .setValue(processInstanceKey)
        .setEventTime(timestamp);
  }

  private MetricEntity createDecisionsInstanceEvaluatedKey(String decisionInstanceKey, OffsetDateTime timestamp) {
    return (MetricEntity) new MetricEntity()
        .setEvent(EVENT_DECISION_INSTANCE_EVALUATED)
        .setValue(decisionInstanceKey)
        .setEventTime(timestamp);
  }
}
