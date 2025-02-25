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

import io.camunda.db.rdbms.sql.FlowNodeInstanceMapper;
import io.camunda.db.rdbms.write.queue.ContextType;
import io.camunda.db.rdbms.write.queue.DefaultExecutionQueue;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.queue.QueueItem;
import io.camunda.db.rdbms.write.queue.UpsertMerger;
import io.camunda.db.rdbms.write.queue.WriteStatementType;
import io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeState;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FlowNodeInstanceWriterTest {

  private static final OffsetDateTime NOW = OffsetDateTime.now();

  private ExecutionQueue executionQueue;
  private FlowNodeInstanceMapper mapper;
  private FlowNodeInstanceWriter writer;

  @BeforeEach
  void setUp() {
    executionQueue = mock(DefaultExecutionQueue.class);
    mapper = mock(FlowNodeInstanceMapper.class);
    writer = new FlowNodeInstanceWriter(executionQueue, mapper);
  }

  @Test
  void whenFinishFlowNodeCanBeMergedWithInsertNoItemShouldBeEnqueued() {
    when(executionQueue.tryMergeWithExistingQueueItem(any(UpsertMerger.class))).thenReturn(true);

    writer.finish(1L, FlowNodeState.COMPLETED, NOW);

    verify(executionQueue, never()).executeInQueue(any(QueueItem.class));
  }

  @Test
  void whenFinishFlowNodeCannotBeMergedWithInsertItemShouldBeEnqueued() {
    when(executionQueue.tryMergeWithExistingQueueItem(any(UpsertMerger.class))).thenReturn(false);

    writer.finish(1L, FlowNodeState.COMPLETED, NOW);

    verify(executionQueue)
        .executeInQueue(
            eq(
                new QueueItem(
                    ContextType.FLOW_NODE,
                    WriteStatementType.UPDATE,
                    1L,
                    "io.camunda.db.rdbms.sql.FlowNodeInstanceMapper.updateStateAndEndDate",
                    new FlowNodeInstanceMapper.EndFlowNodeDto(1L, FlowNodeState.COMPLETED, NOW))));
  }
}
