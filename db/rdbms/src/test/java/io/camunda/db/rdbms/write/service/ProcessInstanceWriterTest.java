/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.db.rdbms.sql.ProcessInstanceMapper.EndProcessInstanceDto;
import io.camunda.db.rdbms.write.queue.ContextType;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.queue.QueueItem;
import io.camunda.db.rdbms.write.queue.UpsertMerger;
import io.camunda.search.entities.ProcessInstanceEntity.ProcessInstanceState;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProcessInstanceWriterTest {

  private static final OffsetDateTime NOW = OffsetDateTime.now();

  private ExecutionQueue executionQueue;
  private ProcessInstanceWriter writer;

  @BeforeEach
  void setUp() {
    executionQueue = mock(ExecutionQueue.class);
    writer = new ProcessInstanceWriter(executionQueue);
  }

  @Test
  void whenFinishProcessCanBeMergedWithInsertNoItemShouldBeEnqueued() {
    when(executionQueue.tryMergeWithExistingQueueItem(any(UpsertMerger.class))).thenReturn(true);

    writer.finish(1L, ProcessInstanceState.COMPLETED, NOW);

    verify(executionQueue, never()).executeInQueue(any(QueueItem.class));
  }

  @Test
  void whenFinishProcessCannotBeMergedWithInsertItemShouldBeEnqueued() {
    when(executionQueue.tryMergeWithExistingQueueItem(any(UpsertMerger.class))).thenReturn(false);

    writer.finish(1L, ProcessInstanceState.COMPLETED, NOW);

    verify(executionQueue)
        .executeInQueue(
            eq(
                new QueueItem(
                    ContextType.PROCESS_INSTANCE,
                    1L,
                    "io.camunda.db.rdbms.sql.ProcessInstanceMapper.updateStateAndEndDate",
                    new EndProcessInstanceDto(1L, ProcessInstanceState.COMPLETED, NOW))));
  }
}
