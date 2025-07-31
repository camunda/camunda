/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.service;

import io.camunda.db.rdbms.config.VendorDatabaseProperties;
import io.camunda.db.rdbms.sql.DecisionInstanceMapper;
import io.camunda.db.rdbms.sql.HistoryCleanupMapper.CleanupHistoryDto;
import io.camunda.db.rdbms.sql.ProcessBasedHistoryCleanupMapper;
import io.camunda.db.rdbms.write.domain.DecisionInstanceDbModel;
import io.camunda.db.rdbms.write.queue.ContextType;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.queue.QueueItem;
import io.camunda.db.rdbms.write.queue.WriteStatementType;
import java.time.OffsetDateTime;

public class DecisionInstanceWriter {

  private final DecisionInstanceMapper mapper;
  private final ExecutionQueue executionQueue;
  private final VendorDatabaseProperties vendorDatabaseProperties;

  public DecisionInstanceWriter(
      final DecisionInstanceMapper mapper,
      final ExecutionQueue executionQueue,
      final VendorDatabaseProperties vendorDatabaseProperties) {
    this.mapper = mapper;
    this.executionQueue = executionQueue;
    this.vendorDatabaseProperties = vendorDatabaseProperties;
  }

  public void create(final DecisionInstanceDbModel decisionInstance) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.DECISION_INSTANCE,
            WriteStatementType.INSERT,
            decisionInstance.decisionInstanceKey(),
            "io.camunda.db.rdbms.sql.DecisionInstanceMapper.insert",
            decisionInstance.truncateErrorMessage(
                vendorDatabaseProperties.errorMessageSize(),
                vendorDatabaseProperties.charColumnMaxBytes())));
    if (decisionInstance.evaluatedInputs() != null
        && !decisionInstance.evaluatedInputs().isEmpty()) {
      executionQueue.executeInQueue(
          new QueueItem(
              ContextType.DECISION_INSTANCE,
              WriteStatementType.INSERT,
              decisionInstance.decisionInstanceKey(),
              "io.camunda.db.rdbms.sql.DecisionInstanceMapper.insertInput",
              decisionInstance.truncateErrorMessage(
                  vendorDatabaseProperties.errorMessageSize(),
                  vendorDatabaseProperties.charColumnMaxBytes())));
    }
    if (decisionInstance.evaluatedOutputs() != null
        && !decisionInstance.evaluatedOutputs().isEmpty()) {
      executionQueue.executeInQueue(
          new QueueItem(
              ContextType.DECISION_INSTANCE,
              WriteStatementType.INSERT,
              decisionInstance.decisionInstanceKey(),
              "io.camunda.db.rdbms.sql.DecisionInstanceMapper.insertOutput",
              decisionInstance.truncateErrorMessage(
                  vendorDatabaseProperties.errorMessageSize(),
                  vendorDatabaseProperties.charColumnMaxBytes())));
    }
  }

  public void scheduleForHistoryCleanup(
      final Long processInstanceKey, final OffsetDateTime historyCleanupDate) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.DECISION_INSTANCE,
            WriteStatementType.UPDATE,
            processInstanceKey,
            "io.camunda.db.rdbms.sql.DecisionInstanceMapper.updateHistoryCleanupDate",
            new ProcessBasedHistoryCleanupMapper.UpdateHistoryCleanupDateDto.Builder()
                .processInstanceKey(processInstanceKey)
                .historyCleanupDate(historyCleanupDate)
                .build()));
  }

  public int cleanupHistory(
      final int partitionId, final OffsetDateTime cleanupDate, final int rowsToRemove) {
    return mapper.cleanupHistory(
        new CleanupHistoryDto.Builder()
            .partitionId(partitionId)
            .cleanupDate(cleanupDate)
            .limit(rowsToRemove)
            .build());
  }
}
