/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.es.writer;

import io.camunda.operate.entities.MetricEntity;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.schema.indices.MetricIndex;
import io.camunda.operate.store.BatchRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

import static io.camunda.operate.es.contract.MetricContract.*;

@Component
public class MetricWriter implements Writer {

  @Autowired
  private MetricIndex metricIndex;

  @Override
  public void registerProcessInstanceStartEvent(String processInstanceKey, OffsetDateTime timestamp, BatchRequest batchRequest)
      throws PersistenceException {
    final MetricEntity metric = createProcessInstanceStartedKey(processInstanceKey, timestamp);
    batchRequest.add(metricIndex.getFullQualifiedName(), metric);
  }

  @Override
  public void registerDecisionInstanceCompleteEvent(final String decisionInstanceKey,
      final OffsetDateTime timestamp, BatchRequest batchRequest) throws PersistenceException {
    final MetricEntity metric = createDecisionsInstanceEvaluatedKey(decisionInstanceKey, timestamp);
    batchRequest.add(metricIndex.getFullQualifiedName(), metric);
  }

  private MetricEntity createProcessInstanceStartedKey(String processInstanceKey, OffsetDateTime timestamp) {
    return new MetricEntity()
        .setEvent(EVENT_PROCESS_INSTANCE_STARTED)
        .setValue(processInstanceKey)
        .setEventTime(timestamp);
  }

  private MetricEntity createDecisionsInstanceEvaluatedKey(String decisionInstanceKey, OffsetDateTime timestamp) {
    return new MetricEntity()
        .setEvent(EVENT_DECISION_INSTANCE_EVALUATED)
        .setValue(decisionInstanceKey)
        .setEventTime(timestamp);
  }
}
