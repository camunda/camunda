/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.service;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.db.rdbms.write.domain.HistoryDeletionDbModel;
import io.camunda.db.rdbms.write.queue.ContextType;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.queue.QueueItem;
import io.camunda.db.rdbms.write.queue.WriteStatementType;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HistoryDeletionWriterTest {

  private final ExecutionQueue executionQueue = mock(ExecutionQueue.class);
  private final HistoryDeletionWriter writer = new HistoryDeletionWriter(executionQueue);

  @Test
  void shouldInsertHistoryDeletion() {
    final var model = mock(HistoryDeletionDbModel.class);
    when(model.getId()).thenReturn("2251799813685385");

    writer.create(model);

    verify(executionQueue)
        .executeInQueue(
            eq(
                new QueueItem(
                    ContextType.HISTORY_DELETION,
                    WriteStatementType.INSERT,
                    "2251799813685385",
                    "io.camunda.db.rdbms.sql.HistoryDeletionMapper.insert",
                    model)));
  }

  @Test
  void shouldDeleteHistoryDeletion() {
    final var resourceKey = 2251799813685385L;
    final var batchOperationKey = 2251799813685312L;

    writer.delete(resourceKey, batchOperationKey);

    verify(executionQueue)
        .executeInQueue(
            eq(
                new QueueItem(
                    ContextType.HISTORY_DELETION,
                    WriteStatementType.DELETE,
                    "2251799813685312_2251799813685385",
                    "io.camunda.db.rdbms.sql.HistoryDeletionMapper.delete",
                    Map.of("resourceKey", resourceKey, "batchOperationKey", batchOperationKey))));
  }
}
