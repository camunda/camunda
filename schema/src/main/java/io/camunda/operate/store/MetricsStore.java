/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.store;

import io.camunda.operate.exceptions.PersistenceException;
import java.time.OffsetDateTime;

public interface MetricsStore {

  String EVENT_PROCESS_INSTANCE_FINISHED = "EVENT_PROCESS_INSTANCE_FINISHED";
  String EVENT_PROCESS_INSTANCE_STARTED = "EVENT_PROCESS_INSTANCE_STARTED";
  String EVENT_DECISION_INSTANCE_EVALUATED = "EVENT_DECISION_INSTANCE_EVALUATED";

  String PROCESS_INSTANCES_AGG_NAME = "process_instances";
  String DECISION_INSTANCES_AGG_NAME = "decision_instances";

  Long retrieveProcessInstanceCount(OffsetDateTime startTime, OffsetDateTime endTime);

  Long retrieveDecisionInstanceCount(OffsetDateTime startTime, OffsetDateTime endTime);

  void registerProcessInstanceStartEvent(
      String processInstanceKey,
      String tenantId,
      OffsetDateTime timestamp,
      BatchRequest batchRequest)
      throws PersistenceException;

  void registerDecisionInstanceCompleteEvent(
      String processInstanceKey,
      String tenantId,
      OffsetDateTime timestamp,
      BatchRequest batchRequest)
      throws PersistenceException;
}
