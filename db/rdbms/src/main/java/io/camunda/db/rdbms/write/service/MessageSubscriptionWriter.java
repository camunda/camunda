/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.service;

import io.camunda.db.rdbms.sql.HistoryCleanupMapper.CleanupHistoryDto;
import io.camunda.db.rdbms.sql.MessageSubscriptionMapper;
import io.camunda.db.rdbms.sql.ProcessBasedHistoryCleanupMapper;
import io.camunda.db.rdbms.write.domain.MessageSubscriptionDbModel;
import io.camunda.db.rdbms.write.queue.ContextType;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.queue.QueueItem;
import io.camunda.db.rdbms.write.queue.UpdateHistoryCleanupDateMerger;
import io.camunda.db.rdbms.write.queue.WriteStatementType;
import java.time.OffsetDateTime;

public class MessageSubscriptionWriter {

  private final ExecutionQueue executionQueue;
  private final MessageSubscriptionMapper mapper;

  public MessageSubscriptionWriter(
      final ExecutionQueue executionQueue, final MessageSubscriptionMapper mapper) {
    this.executionQueue = executionQueue;
    this.mapper = mapper;
  }

  public void create(final MessageSubscriptionDbModel messageSubscription) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.MESSAGE_SUBSCRIPTION,
            WriteStatementType.INSERT,
            messageSubscription.messageSubscriptionKey(),
            "io.camunda.db.rdbms.sql.MessageSubscriptionMapper.insert",
            messageSubscription));
  }

  public void update(final MessageSubscriptionDbModel messageSubscription) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.MESSAGE_SUBSCRIPTION,
            WriteStatementType.UPDATE,
            messageSubscription.messageSubscriptionKey(),
            "io.camunda.db.rdbms.sql.MessageSubscriptionMapper.update",
            messageSubscription));
  }

  public void scheduleForHistoryCleanup(
      final Long processInstanceKey, final OffsetDateTime historyCleanupDate) {
    final var wasMerged =
        executionQueue.tryMergeWithExistingQueueItem(
            new UpdateHistoryCleanupDateMerger(
                ContextType.MESSAGE_SUBSCRIPTION, processInstanceKey, historyCleanupDate));

    if (!wasMerged) {
      executionQueue.executeInQueue(
          new QueueItem(
              ContextType.MESSAGE_SUBSCRIPTION,
              WriteStatementType.UPDATE,
              processInstanceKey,
              "io.camunda.db.rdbms.sql.MessageSubscriptionMapper.updateHistoryCleanupDate",
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
