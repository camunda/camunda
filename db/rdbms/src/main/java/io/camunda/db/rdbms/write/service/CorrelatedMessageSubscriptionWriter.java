/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.service;

import io.camunda.db.rdbms.sql.CorrelatedMessageSubscriptionMapper;
import io.camunda.db.rdbms.sql.HistoryCleanupMapper.CleanupHistoryDto;
import io.camunda.db.rdbms.sql.ProcessBasedHistoryCleanupMapper;
import io.camunda.db.rdbms.write.domain.CorrelatedMessageSubscriptionDbModel;
import io.camunda.db.rdbms.write.queue.ContextType;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.queue.QueueItem;
import io.camunda.db.rdbms.write.queue.UpdateHistoryCleanupDateMerger;
import io.camunda.db.rdbms.write.queue.WriteStatementType;
import java.time.OffsetDateTime;

public class CorrelatedMessageSubscriptionWriter {

  private final ExecutionQueue executionQueue;
  private final CorrelatedMessageSubscriptionMapper mapper;

  public CorrelatedMessageSubscriptionWriter(
      final ExecutionQueue executionQueue, final CorrelatedMessageSubscriptionMapper mapper) {
    this.executionQueue = executionQueue;
    this.mapper = mapper;
  }

  public void create(final CorrelatedMessageSubscriptionDbModel correlatedMessageSubscription) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.CORRELATED_MESSAGE_SUBSCRIPTION,
            WriteStatementType.INSERT,
            getCompositeId(correlatedMessageSubscription),
            "io.camunda.db.rdbms.sql.CorrelatedMessageSubscriptionMapper.insert",
            correlatedMessageSubscription));
  }

  public void scheduleForHistoryCleanup(
      final Long processInstanceKey, final OffsetDateTime historyCleanupDate) {
    final var wasMerged =
        executionQueue.tryMergeWithExistingQueueItem(
            new UpdateHistoryCleanupDateMerger(
                ContextType.CORRELATED_MESSAGE_SUBSCRIPTION,
                processInstanceKey,
                historyCleanupDate));

    if (!wasMerged) {
      executionQueue.executeInQueue(
          new QueueItem(
              ContextType.CORRELATED_MESSAGE_SUBSCRIPTION,
              WriteStatementType.UPDATE,
              processInstanceKey,
              "io.camunda.db.rdbms.sql.CorrelatedMessageSubscriptionMapper.updateHistoryCleanupDate",
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

  private static String getCompositeId(
      final CorrelatedMessageSubscriptionDbModel correlatedMessageSubscription) {
    return correlatedMessageSubscription.messageKey()
        + "_"
        + correlatedMessageSubscription.subscriptionKey();
  }
}
