/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
