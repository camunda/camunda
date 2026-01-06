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
import io.camunda.db.rdbms.write.RdbmsWriterConfig;
import io.camunda.db.rdbms.write.domain.DecisionInstanceDbModel;
import io.camunda.db.rdbms.write.queue.ContextType;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.queue.QueueItem;
import io.camunda.db.rdbms.write.queue.UpdateHistoryCleanupDateMerger;
import io.camunda.db.rdbms.write.queue.WriteStatementType;
import java.time.OffsetDateTime;

public class DecisionInstanceWriter extends ProcessInstanceDependant implements RdbmsWriter {

  private final DecisionInstanceMapper mapper;
  private final ExecutionQueue executionQueue;
  private final VendorDatabaseProperties vendorDatabaseProperties;
  private final RdbmsWriterConfig config;

  public DecisionInstanceWriter(
      final DecisionInstanceMapper mapper,
      final ExecutionQueue executionQueue,
      final VendorDatabaseProperties vendorDatabaseProperties,
      final RdbmsWriterConfig config) {
    super(mapper);
    this.mapper = mapper;
    this.executionQueue = executionQueue;
    this.vendorDatabaseProperties = vendorDatabaseProperties;
    this.config = config;
  }

  public void create(final DecisionInstanceDbModel decisionInstance) {
    // Set cleanup date for decision instances without a process instance
    final DecisionInstanceDbModel processedInstance;
    if ((decisionInstance.processInstanceKey() == null
            || decisionInstance.processInstanceKey() == -1L)
        && decisionInstance.historyCleanupDate() == null) {
      processedInstance =
          decisionInstance.toBuilder()
              .historyCleanupDate(
                  decisionInstance.evaluationDate().plus(config.history().decisionInstanceTTL()))
              .build();
    } else {
      processedInstance = decisionInstance;
    }

    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.DECISION_INSTANCE,
            WriteStatementType.INSERT,
            processedInstance.decisionInstanceKey(),
            "io.camunda.db.rdbms.sql.DecisionInstanceMapper.insert",
            processedInstance.truncateErrorMessage(
                vendorDatabaseProperties.errorMessageSize(),
                vendorDatabaseProperties.charColumnMaxBytes())));
    if (processedInstance.evaluatedInputs() != null
        && !processedInstance.evaluatedInputs().isEmpty()) {
      executionQueue.executeInQueue(
          new QueueItem(
              ContextType.DECISION_INSTANCE,
              WriteStatementType.INSERT,
              processedInstance.decisionInstanceKey(),
              "io.camunda.db.rdbms.sql.DecisionInstanceMapper.insertInput",
              processedInstance.truncateErrorMessage(
                  vendorDatabaseProperties.errorMessageSize(),
                  vendorDatabaseProperties.charColumnMaxBytes())));
    }
    if (processedInstance.evaluatedOutputs() != null
        && !processedInstance.evaluatedOutputs().isEmpty()) {
      executionQueue.executeInQueue(
          new QueueItem(
              ContextType.DECISION_INSTANCE,
              WriteStatementType.INSERT,
              processedInstance.decisionInstanceKey(),
              "io.camunda.db.rdbms.sql.DecisionInstanceMapper.insertOutput",
              processedInstance.truncateErrorMessage(
                  vendorDatabaseProperties.errorMessageSize(),
                  vendorDatabaseProperties.charColumnMaxBytes())));
    }
  }

  public void scheduleForHistoryCleanup(
      final Long processInstanceKey, final OffsetDateTime historyCleanupDate) {
    final var wasMerged =
        executionQueue.tryMergeWithExistingQueueItem(
            new UpdateHistoryCleanupDateMerger(
                ContextType.DECISION_INSTANCE, processInstanceKey, historyCleanupDate));

    if (!wasMerged) {
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
