/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.service;

import io.camunda.db.rdbms.sql.HistoryCleanupMapper.CleanupHistoryDto;
import io.camunda.db.rdbms.sql.MessageCorrelationMapper;
import io.camunda.db.rdbms.sql.ProcessBasedHistoryCleanupMapper;
import io.camunda.db.rdbms.write.domain.MessageCorrelationDbModel;
import io.camunda.db.rdbms.write.queue.ContextType;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.queue.QueueItem;
import io.camunda.db.rdbms.write.queue.WriteStatementType;
import java.time.OffsetDateTime;

public class MessageCorrelationWriter {

  private final ExecutionQueue executionQueue;
  private final MessageCorrelationMapper mapper;

  public MessageCorrelationWriter(
      final ExecutionQueue executionQueue, final MessageCorrelationMapper mapper) {
    this.executionQueue = executionQueue;
    this.mapper = mapper;
  }

  public void create(final MessageCorrelationDbModel messageCorrelation) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.MESSAGE_CORRELATION,
            WriteStatementType.INSERT,
            // Using a composite key made from subscription and message keys
            messageCorrelation.subscriptionKey() + "_" + messageCorrelation.messageKey(),
            "io.camunda.db.rdbms.sql.MessageCorrelationMapper.insert",
            messageCorrelation));
  }

  public void update(final MessageCorrelationDbModel messageCorrelation) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.MESSAGE_CORRELATION,
            WriteStatementType.UPDATE,
            // Using a composite key made from subscription and message keys
            messageCorrelation.subscriptionKey() + "_" + messageCorrelation.messageKey(),
            "io.camunda.db.rdbms.sql.MessageCorrelationMapper.update",
            messageCorrelation));
  }

  public void scheduleForHistoryCleanup(
      final Long processInstanceKey, final OffsetDateTime historyCleanupDate) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.MESSAGE_CORRELATION,
            WriteStatementType.UPDATE,
            processInstanceKey,
            "io.camunda.db.rdbms.sql.MessageCorrelationMapper.updateHistoryCleanupDate",
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
