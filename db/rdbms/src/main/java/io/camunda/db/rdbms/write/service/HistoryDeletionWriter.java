/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.service;

import io.camunda.db.rdbms.write.domain.HistoryDeletionDbModel;
import io.camunda.db.rdbms.write.queue.ContextType;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.queue.QueueItem;
import io.camunda.db.rdbms.write.queue.WriteStatementType;
import java.util.Map;

public class HistoryDeletionWriter implements RdbmsWriter {

  private final ExecutionQueue executionQueue;

  public HistoryDeletionWriter(final ExecutionQueue executionQueue) {
    this.executionQueue = executionQueue;
  }

  public void create(final HistoryDeletionDbModel dbModel) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.HISTORY_DELETION,
            WriteStatementType.INSERT,
            dbModel.getId(),
            "io.camunda.db.rdbms.sql.HistoryDeletionMapper.insert",
            dbModel));
  }

  public void delete(final long resourceKey, final long batchOperationKey) {
    // Create a composite key for the QueueItem identifier
    final String id =
        String.format(HistoryDeletionDbModel.ID_PATTERN, batchOperationKey, resourceKey);
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.HISTORY_DELETION,
            WriteStatementType.DELETE,
            id,
            "io.camunda.db.rdbms.sql.HistoryDeletionMapper.delete",
            Map.of("resourceKey", resourceKey, "batchOperationKey", batchOperationKey)));
  }
}
