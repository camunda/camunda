/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.db.rdbms.config.VendorDatabaseProperties;
import io.camunda.db.rdbms.sql.AgentInstanceMapper;
import io.camunda.db.rdbms.write.domain.AgentInstanceDbModel;
import io.camunda.db.rdbms.write.queue.ContextType;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.queue.QueueItem;
import io.camunda.db.rdbms.write.queue.WriteStatementType;
import io.camunda.search.entities.AgentInstanceEntity;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentInstanceWriterTest {

  private final ExecutionQueue executionQueue = mock(ExecutionQueue.class);
  private final AgentInstanceMapper mapper = mock(AgentInstanceMapper.class);
  private final VendorDatabaseProperties vendorDatabaseProperties =
      mock(VendorDatabaseProperties.class);
  private final AgentInstanceWriter writer =
      new AgentInstanceWriter(executionQueue, mapper, vendorDatabaseProperties);

  AgentInstanceWriterTest() {
    when(vendorDatabaseProperties.userCharColumnSize()).thenReturn(Integer.MAX_VALUE);
    when(vendorDatabaseProperties.charColumnMaxBytes()).thenReturn(null);
  }

  @Test
  void shouldEnqueueInsertOnCreate() {
    // given
    final var model = buildModel(1L, List.of(100L));

    // when
    writer.create(model);

    // then
    verify(executionQueue)
        .executeInQueue(
            eq(
                new QueueItem(
                    ContextType.AGENT_INSTANCE,
                    WriteStatementType.INSERT,
                    1L,
                    "io.camunda.db.rdbms.sql.AgentInstanceMapper.insert",
                    model)));
    verify(executionQueue)
        .executeInQueue(
            eq(
                new QueueItem(
                    ContextType.AGENT_INSTANCE,
                    WriteStatementType.DELETE,
                    1L,
                    "io.camunda.db.rdbms.sql.AgentInstanceMapper.deleteElementInstanceKeys",
                    1L)));
    verify(executionQueue)
        .executeInQueue(
            eq(
                new QueueItem(
                    ContextType.AGENT_INSTANCE,
                    WriteStatementType.INSERT,
                    1L,
                    "io.camunda.db.rdbms.sql.AgentInstanceMapper.insertElementInstanceKeys",
                    model)));
  }

  @Test
  void shouldEnqueueDeleteButNotInsertWhenElementInstanceKeysAreNull() {
    // given
    final var model = buildModel(2L, null);

    // when
    writer.create(model);

    // then: main INSERT and DELETE are enqueued; no bulk INSERT for empty key list
    verify(executionQueue)
        .executeInQueue(
            eq(
                new QueueItem(
                    ContextType.AGENT_INSTANCE,
                    WriteStatementType.INSERT,
                    2L,
                    "io.camunda.db.rdbms.sql.AgentInstanceMapper.insert",
                    model)));
    verify(executionQueue)
        .executeInQueue(
            eq(
                new QueueItem(
                    ContextType.AGENT_INSTANCE,
                    WriteStatementType.DELETE,
                    2L,
                    "io.camunda.db.rdbms.sql.AgentInstanceMapper.deleteElementInstanceKeys",
                    2L)));
    verify(executionQueue, never())
        .executeInQueue(
            argThat(
                item ->
                    "io.camunda.db.rdbms.sql.AgentInstanceMapper.insertElementInstanceKeys"
                        .equals(item.statementId())));
  }

  @Test
  void shouldEnqueueUpdateWhenNotMerged() {
    // given
    when(executionQueue.tryMergeWithExistingQueueItem(any())).thenReturn(false);

    final var model = buildModel(3L, List.of(200L));

    // when
    writer.update(model);

    // then: UPDATE, DELETE child rows, then bulk INSERT child rows
    verify(executionQueue)
        .executeInQueue(
            eq(
                new QueueItem(
                    ContextType.AGENT_INSTANCE,
                    WriteStatementType.UPDATE,
                    3L,
                    "io.camunda.db.rdbms.sql.AgentInstanceMapper.update",
                    model)));
    verify(executionQueue)
        .executeInQueue(
            eq(
                new QueueItem(
                    ContextType.AGENT_INSTANCE,
                    WriteStatementType.DELETE,
                    3L,
                    "io.camunda.db.rdbms.sql.AgentInstanceMapper.deleteElementInstanceKeys",
                    3L)));
    verify(executionQueue)
        .executeInQueue(
            eq(
                new QueueItem(
                    ContextType.AGENT_INSTANCE,
                    WriteStatementType.INSERT,
                    3L,
                    "io.camunda.db.rdbms.sql.AgentInstanceMapper.insertElementInstanceKeys",
                    model)));
  }

  private AgentInstanceDbModel buildModel(final long key, final List<Long> elementInstanceKeys) {
    return new AgentInstanceDbModel.Builder()
        .agentInstanceKey(key)
        .status(AgentInstanceEntity.AgentInstanceStatus.IDLE)
        .inputTokens(0L)
        .outputTokens(0L)
        .modelCalls(0)
        .toolCalls(0)
        .lastUpdatedDate(OffsetDateTime.now())
        .elementInstanceKeys(elementInstanceKeys)
        .build();
  }
}
