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

  String PROCESS_INSTANCES_AGG_NAME = "process_instances";
  String DECISION_INSTANCES_AGG_NAME = "decision_instances";

  Long retrieveProcessInstanceCount(
      OffsetDateTime startTime, OffsetDateTime endTime, String tenantId);

  Long retrieveDecisionInstanceCount(
      OffsetDateTime startTime, OffsetDateTime endTime, String tenantId);

  void registerProcessInstanceStartEvent(
      long key,
      String tenantId,
      int partitionId,
      OffsetDateTime timestamp,
      BatchRequest batchRequest)
      throws PersistenceException;

  void registerDecisionInstanceCompleteEvent(
      long key,
      String tenantId,
      int partitionId,
      OffsetDateTime timestamp,
      BatchRequest batchRequest)
      throws PersistenceException;
}
