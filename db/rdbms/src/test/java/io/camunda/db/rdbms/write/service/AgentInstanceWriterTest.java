/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.db.rdbms.config.VendorDatabaseProperties;
import io.camunda.db.rdbms.sql.AgentInstanceMapper;
import io.camunda.db.rdbms.sql.AgentInstanceMapper.AgentInstanceElementInstanceKeysDto;
import io.camunda.db.rdbms.write.RdbmsWriterMetrics;
import io.camunda.db.rdbms.write.domain.AgentInstanceDbModel;
import io.camunda.db.rdbms.write.queue.ContextType;
import io.camunda.db.rdbms.write.queue.DefaultExecutionQueue;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.queue.QueueItem;
import io.camunda.db.rdbms.write.queue.WriteStatementType;
import io.camunda.search.entities.AgentInstanceEntity;
import io.camunda.search.entities.AgentInstanceEntity.AgentInstanceStatus;
import java.sql.Connection;
import java.time.OffsetDateTime;
import java.util.List;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.TransactionIsolationLevel;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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

    // then: main INSERT + child INSERT enqueued; no DELETE (no pre-existing rows on first creation)
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
                    WriteStatementType.INSERT,
                    1L,
                    "io.camunda.db.rdbms.sql.AgentInstanceMapper.insertElementInstanceKeys",
                    new AgentInstanceElementInstanceKeysDto(1L, List.of(100L)))));
    verify(executionQueue, never())
        .executeInQueue(
            argThat(
                item ->
                    "io.camunda.db.rdbms.sql.AgentInstanceMapper.deleteElementInstanceKeys"
                        .equals(item.statementId())));
  }

  @Test
  void shouldNotEnqueueDeleteOrChildInsertWhenElementInstanceKeysAreNull() {
    // given
    final var model = buildModel(2L, null);

    // when
    writer.create(model);

    // then: only main INSERT is enqueued; no DELETE, no child INSERT for empty key list
    verify(executionQueue)
        .executeInQueue(
            eq(
                new QueueItem(
                    ContextType.AGENT_INSTANCE,
                    WriteStatementType.INSERT,
                    2L,
                    "io.camunda.db.rdbms.sql.AgentInstanceMapper.insert",
                    model)));
    verify(executionQueue, never())
        .executeInQueue(
            argThat(
                item ->
                    "io.camunda.db.rdbms.sql.AgentInstanceMapper.deleteElementInstanceKeys"
                        .equals(item.statementId())));
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
                    new AgentInstanceElementInstanceKeysDto(3L, List.of(200L)))));
  }

  @Test
  void shouldApplyUpdateToRowInsertNotToChildKeysInsertWhenMergedBeforeFlush() throws Exception {
    // given: a real ExecutionQueue so the actual merge logic (UpsertMerger + newest-first scan)
    // runs, reproducing https://github.com/camunda/camunda/issues/58968 - create() queues both a
    // row INSERT and an insertElementInstanceKeys INSERT for the same agentInstanceKey; update()
    // must merge into the former, not the latter. The update's elementInstanceKeys is a superset of
    // create()'s, mirroring the real AgentInstanceExportHandler/engine contract that every update
    // carries the full cumulative set of element instance keys (never a delta, never empty right
    // after a non-empty create) - so update()'s unconditional DELETE-then-reinsert of child rows is
    // exercised and asserted here rather than left unchecked.
    final var session = mock(SqlSession.class);
    final var sqlSessionFactory = mock(SqlSessionFactory.class);
    final var metrics = mock(RdbmsWriterMetrics.class);
    when(sqlSessionFactory.openSession(
            ExecutorType.BATCH, TransactionIsolationLevel.READ_COMMITTED))
        .thenReturn(session);
    final var connection = mock(Connection.class);
    when(connection.getAutoCommit()).thenReturn(false);
    when(session.getConnection()).thenReturn(connection);

    final var realExecutionQueue = new DefaultExecutionQueue(sqlSessionFactory, 1, 0, 0, metrics);
    final var realWriter =
        new AgentInstanceWriter(realExecutionQueue, mapper, vendorDatabaseProperties);

    final var created = buildModel(4L, List.of(300L));
    final var updated =
        new AgentInstanceDbModel.Builder()
            .agentInstanceKey(4L)
            .status(AgentInstanceStatus.COMPLETED)
            .inputTokens(42L)
            .outputTokens(7L)
            .modelCalls(3)
            .toolCalls(2)
            .lastUpdatedDate(OffsetDateTime.now())
            .completionDate(OffsetDateTime.now())
            .elementInstanceKeys(List.of(300L, 301L))
            .build();

    // when
    realWriter.create(created);
    realWriter.update(updated);
    realExecutionQueue.flush();

    // then: the row INSERT carries the merged (COMPLETED) status ...
    final var rowInsertParam = ArgumentCaptor.forClass(Object.class);
    verify(session)
        .update(eq("io.camunda.db.rdbms.sql.AgentInstanceMapper.insert"), rowInsertParam.capture());
    assertThat(rowInsertParam.getValue()).isInstanceOf(AgentInstanceDbModel.class);
    assertThat(((AgentInstanceDbModel) rowInsertParam.getValue()).status())
        .isEqualTo(AgentInstanceStatus.COMPLETED);

    // ... no separate UPDATE statement was needed, since the merge absorbed it into the INSERT ...
    verify(session, never())
        .update(eq("io.camunda.db.rdbms.sql.AgentInstanceMapper.update"), any());

    // ... create()'s child-table insert is unaffected by the merge, still carrying its original
    // keys, and update()'s DELETE-then-reinsert of the (now larger) key set runs after it, in
    // insertion order, without touching the row insert or create()'s child insert.
    final var childInsertParam = ArgumentCaptor.forClass(Object.class);
    verify(session, times(2))
        .update(
            eq("io.camunda.db.rdbms.sql.AgentInstanceMapper.insertElementInstanceKeys"),
            childInsertParam.capture());
    assertThat(childInsertParam.getAllValues())
        .containsExactly(
            new AgentInstanceElementInstanceKeysDto(4L, List.of(300L)),
            new AgentInstanceElementInstanceKeysDto(4L, List.of(300L, 301L)));
    verify(session)
        .update(
            eq("io.camunda.db.rdbms.sql.AgentInstanceMapper.deleteElementInstanceKeys"), eq(4L));

    final var order = inOrder(session);
    order.verify(session).update(eq("io.camunda.db.rdbms.sql.AgentInstanceMapper.insert"), any());
    order
        .verify(session)
        .update(
            eq("io.camunda.db.rdbms.sql.AgentInstanceMapper.insertElementInstanceKeys"),
            eq(new AgentInstanceElementInstanceKeysDto(4L, List.of(300L))));
    order
        .verify(session)
        .update(
            eq("io.camunda.db.rdbms.sql.AgentInstanceMapper.deleteElementInstanceKeys"), eq(4L));
    order
        .verify(session)
        .update(
            eq("io.camunda.db.rdbms.sql.AgentInstanceMapper.insertElementInstanceKeys"),
            eq(new AgentInstanceElementInstanceKeysDto(4L, List.of(300L, 301L))));
  }

  @Test
  void shouldMergeConfigurationFieldsIntoRowInsertWhenMergedBeforeFlush() throws Exception {
    // given: a real ExecutionQueue so the actual merge logic (UpsertMerger) runs - an update that
    // coalesces into a not-yet-flushed create() must carry its configuration fields
    // (model/provider/systemPrompt/maxTokens/maxModelCalls/maxToolCalls) onto the pending INSERT,
    // not just agentDefinition/processDefinition/status/metrics/tools.
    final var session = mock(SqlSession.class);
    final var sqlSessionFactory = mock(SqlSessionFactory.class);
    final var metrics = mock(RdbmsWriterMetrics.class);
    when(sqlSessionFactory.openSession(
            ExecutorType.BATCH, TransactionIsolationLevel.READ_COMMITTED))
        .thenReturn(session);
    final var connection = mock(Connection.class);
    when(connection.getAutoCommit()).thenReturn(false);
    when(session.getConnection()).thenReturn(connection);

    final var realExecutionQueue = new DefaultExecutionQueue(sqlSessionFactory, 1, 0, 0, metrics);
    final var realWriter =
        new AgentInstanceWriter(realExecutionQueue, mapper, vendorDatabaseProperties);

    final var created = buildModel(5L, List.of(400L));
    final var updated =
        new AgentInstanceDbModel.Builder()
            .agentInstanceKey(5L)
            .status(AgentInstanceStatus.IDLE)
            .inputTokens(0L)
            .outputTokens(0L)
            .modelCalls(0)
            .toolCalls(0)
            .lastUpdatedDate(OffsetDateTime.now())
            .model("gpt-5")
            .provider("openai")
            .systemPrompt("be helpful")
            .maxTokens(1000L)
            .maxModelCalls(10)
            .maxToolCalls(5)
            .elementInstanceKeys(List.of(400L))
            .build();

    // when
    realWriter.create(created);
    realWriter.update(updated);
    realExecutionQueue.flush();

    // then: the merged row INSERT carries the CONFIGURATION fields from the update, not the
    // (empty) ones from create() - no separate UPDATE statement was needed
    final var rowInsertParam = ArgumentCaptor.forClass(Object.class);
    verify(session)
        .update(eq("io.camunda.db.rdbms.sql.AgentInstanceMapper.insert"), rowInsertParam.capture());
    final var mergedRow = (AgentInstanceDbModel) rowInsertParam.getValue();
    assertThat(mergedRow.model()).isEqualTo("gpt-5");
    assertThat(mergedRow.provider()).isEqualTo("openai");
    assertThat(mergedRow.systemPrompt()).isEqualTo("be helpful");
    assertThat(mergedRow.maxTokens()).isEqualTo(1000L);
    assertThat(mergedRow.maxModelCalls()).isEqualTo(10);
    assertThat(mergedRow.maxToolCalls()).isEqualTo(5);
    verify(session, never())
        .update(eq("io.camunda.db.rdbms.sql.AgentInstanceMapper.update"), any());
  }

  @Test
  void shouldMergeMetricsIntoRowInsertWhenMergedBeforeFlush() throws Exception {
    // given: a real ExecutionQueue so the actual merge logic (UpsertMerger) runs - an update that
    // coalesces into a not-yet-flushed create() must carry all seven metrics fields onto the
    // pending INSERT, not reset them to the (empty) ones from create(). Every field gets a
    // distinct non-zero value so a key/getter mix-up in the merge would fail the test instead of
    // comparing 0 against 0.
    final var session = mock(SqlSession.class);
    final var sqlSessionFactory = mock(SqlSessionFactory.class);
    final var metrics = mock(RdbmsWriterMetrics.class);
    when(sqlSessionFactory.openSession(
            ExecutorType.BATCH, TransactionIsolationLevel.READ_COMMITTED))
        .thenReturn(session);
    final var connection = mock(Connection.class);
    when(connection.getAutoCommit()).thenReturn(false);
    when(session.getConnection()).thenReturn(connection);

    final var realExecutionQueue = new DefaultExecutionQueue(sqlSessionFactory, 1, 0, 0, metrics);
    final var realWriter =
        new AgentInstanceWriter(realExecutionQueue, mapper, vendorDatabaseProperties);

    final var created = buildModel(6L, List.of(500L));
    final var updated =
        new AgentInstanceDbModel.Builder()
            .agentInstanceKey(6L)
            .status(AgentInstanceStatus.COMPLETED)
            .inputTokens(44L)
            .outputTokens(9L)
            .reasoningTokenCount(11L)
            .cacheCreationTokenCount(22L)
            .cacheReadTokenCount(33L)
            .modelCalls(5)
            .toolCalls(6)
            .lastUpdatedDate(OffsetDateTime.now())
            .elementInstanceKeys(List.of(500L))
            .build();

    // when
    realWriter.create(created);
    realWriter.update(updated);
    realExecutionQueue.flush();

    // then: the merged row INSERT carries all metrics fields from the update, not the (empty)
    // ones from create() - no separate UPDATE statement was needed
    final var rowInsertParam = ArgumentCaptor.forClass(Object.class);
    verify(session)
        .update(eq("io.camunda.db.rdbms.sql.AgentInstanceMapper.insert"), rowInsertParam.capture());
    final var mergedRow = (AgentInstanceDbModel) rowInsertParam.getValue();
    assertThat(mergedRow.inputTokens()).isEqualTo(44L);
    assertThat(mergedRow.outputTokens()).isEqualTo(9L);
    assertThat(mergedRow.reasoningTokenCount()).isEqualTo(11L);
    assertThat(mergedRow.cacheCreationTokenCount()).isEqualTo(22L);
    assertThat(mergedRow.cacheReadTokenCount()).isEqualTo(33L);
    assertThat(mergedRow.modelCalls()).isEqualTo(5);
    assertThat(mergedRow.toolCalls()).isEqualTo(6);
    verify(session, never())
        .update(eq("io.camunda.db.rdbms.sql.AgentInstanceMapper.update"), any());
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
