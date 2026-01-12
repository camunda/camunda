/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.db.rdbms.write.RdbmsWriterMetrics;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.TransactionIsolationLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;

class DefaultExecutionQueueTest {

  private SqlSession session;
  private SqlSessionFactory sqlSessionFactory;
  private RdbmsWriterMetrics metrics;

  private DefaultExecutionQueue executionQueue;

  @BeforeEach
  public void beforeEach() {
    session = mock(SqlSession.class);
    sqlSessionFactory = mock(SqlSessionFactory.class);
    metrics = mock(RdbmsWriterMetrics.class);
    when(sqlSessionFactory.openSession(
            ExecutorType.BATCH, TransactionIsolationLevel.READ_UNCOMMITTED))
        .thenReturn(session);

    executionQueue = new DefaultExecutionQueue(sqlSessionFactory, 1, 10, 0, metrics);
  }

  @Test
  public void whenElementIsAddedNoFlushHappensBelowLimit() {
    executionQueue.executeInQueue(mock(QueueItem.class));

    Mockito.verifyNoInteractions(sqlSessionFactory);
  }

  @Test
  public void whenElementIsAddedNoFlushHappens() {
    executionQueue = new DefaultExecutionQueue(sqlSessionFactory, 1, 0, 0, metrics);

    executionQueue.executeInQueue(mock(QueueItem.class));

    Mockito.verifyNoInteractions(sqlSessionFactory);
  }

  @Test
  public void whenFlushLimitIsZeroFlushShouldNotHappenAfterCheck() {
    executionQueue = new DefaultExecutionQueue(sqlSessionFactory, 1, 0, 0, metrics);
    final var item1 =
        new QueueItem(
            ContextType.PROCESS_INSTANCE,
            WriteStatementType.INSERT,
            1L,
            "statement1",
            "parameter1");
    executionQueue.executeInQueue(item1);

    verifyNoInteractions(sqlSessionFactory);

    final var result = executionQueue.checkQueueForFlush();
    assertThat(result).isFalse();

    verifyNoInteractions(sqlSessionFactory);
  }

  @Test
  public void whenFlushLimitIsNotActivatedFlushShouldNotHappenAfterCheck() {
    executionQueue = new DefaultExecutionQueue(sqlSessionFactory, 1, 3, 0, metrics);
    final var item1 =
        new QueueItem(
            ContextType.PROCESS_INSTANCE,
            WriteStatementType.INSERT,
            1L,
            "statement1",
            "parameter1");
    executionQueue.executeInQueue(item1);

    verifyNoInteractions(sqlSessionFactory);

    final var result = executionQueue.checkQueueForFlush();
    assertThat(result).isFalse();

    verifyNoInteractions(sqlSessionFactory);
  }

  @Test
  public void whenFlushLimitIsActivatedFlushShouldHappenAfterCheck() {
    executionQueue = new DefaultExecutionQueue(sqlSessionFactory, 1, 3, 0, metrics);
    final var item1 =
        new QueueItem(
            ContextType.PROCESS_INSTANCE,
            WriteStatementType.INSERT,
            1L,
            "statement1",
            "parameter1");
    final var item2 =
        new QueueItem(
            ContextType.PROCESS_INSTANCE,
            WriteStatementType.INSERT,
            1L,
            "statement2",
            "parameter2");
    final var item3 =
        new QueueItem(
            ContextType.PROCESS_INSTANCE,
            WriteStatementType.INSERT,
            1L,
            "statement3",
            "parameter3");
    executionQueue.executeInQueue(item1);
    executionQueue.executeInQueue(item2);
    executionQueue.executeInQueue(item3);

    verifyNoInteractions(sqlSessionFactory);

    final var result = executionQueue.checkQueueForFlush();
    assertThat(result).isTrue();

    verify(sqlSessionFactory)
        .openSession(ExecutorType.BATCH, TransactionIsolationLevel.READ_UNCOMMITTED);
    verify(session).update("statement1", "parameter1");
    verify(session).update("statement2", "parameter2");
    verify(session).flushStatements();
    verify(session).commit();
  }

  @Test
  public void whenFlushIsCalledFlushShouldHappen() {
    final var item1 =
        new QueueItem(
            ContextType.PROCESS_INSTANCE,
            WriteStatementType.INSERT,
            1L,
            "statement1",
            "parameter1");
    executionQueue.executeInQueue(item1);

    // when
    executionQueue.flush();

    verify(sqlSessionFactory)
        .openSession(ExecutorType.BATCH, TransactionIsolationLevel.READ_UNCOMMITTED);
    verify(session).update("statement1", "parameter1");
    verify(session).flushStatements();
    verify(session).commit();
    verify(session).close();
  }

  @Test
  public void whenFlushIsCalledOnEmptyQueueNothingShouldHappen() {
    // when
    executionQueue.flush();

    verifyNoInteractions(sqlSessionFactory);
  }

  @Test
  public void whenFlushIsCalledFlushListenersAreCalled() {
    final var item1 =
        new QueueItem(
            ContextType.PROCESS_INSTANCE,
            WriteStatementType.INSERT,
            1L,
            "statement1",
            "parameter1");
    executionQueue.executeInQueue(item1);

    final var preFlushListener = mock(PreFlushListener.class);
    final var postFlushListener = mock(PostFlushListener.class);
    executionQueue.registerPreFlushListener(preFlushListener);
    executionQueue.registerPostFlushListener(postFlushListener);

    // when
    executionQueue.flush();

    verify(preFlushListener).onPreFlush();
    verify(session).commit();
    verify(postFlushListener).onPostFlush();
  }

  @Test
  public void whenPreFlushListenerAddsQueueItemTheItemIsAlsoFlushed() {
    final var item1 =
        new QueueItem(
            ContextType.PROCESS_INSTANCE,
            WriteStatementType.INSERT,
            1L,
            "statement1",
            "parameter1");
    final var preFlushItem =
        new QueueItem(
            ContextType.EXPORTER_POSITION,
            WriteStatementType.UPDATE,
            1L,
            "statement2",
            "parameter2");
    executionQueue.executeInQueue(item1);

    final var preFlushListener = mock(PreFlushListener.class);
    Mockito.doAnswer(
            invocation -> {
              executionQueue.executeInQueue(preFlushItem);
              return null;
            })
        .when(preFlushListener)
        .onPreFlush();

    executionQueue.registerPreFlushListener(preFlushListener);

    // when
    executionQueue.flush();

    // then
    verify(session).update(preFlushItem.statementId(), preFlushItem.parameter());
  }

  @Test
  public void whenFlushIsExceptionalSessionIsRolledBack() {
    final var item1 =
        new QueueItem(
            ContextType.PROCESS_INSTANCE,
            WriteStatementType.INSERT,
            1L,
            "statement1",
            "parameter1");
    executionQueue.executeInQueue(item1);

    final var preFlushListener = mock(PreFlushListener.class);
    final var postFlushListener = mock(PostFlushListener.class);
    executionQueue.registerPreFlushListener(preFlushListener);
    executionQueue.registerPostFlushListener(postFlushListener);

    final var e = new RuntimeException("Some error");
    when(session.flushStatements()).thenThrow(e);

    // when
    assertThatThrownBy(() -> executionQueue.flush()).isEqualTo(e);

    verify(preFlushListener).onPreFlush();
    verify(session).rollback();
    verify(session).close();
    verify(session, never()).commit();
    verify(postFlushListener, never()).onPostFlush();
  }

  @Test
  public void whenMatchingItemFoundShouldMergeItems() {
    final var item1 =
        new QueueItem(
            ContextType.PROCESS_INSTANCE,
            WriteStatementType.INSERT,
            1L,
            "statement1",
            "parameter1");
    final var item2 =
        new QueueItem(
            ContextType.PROCESS_INSTANCE,
            WriteStatementType.INSERT,
            2L,
            "statement2",
            "parameter2");
    executionQueue.executeInQueue(item1);
    executionQueue.executeInQueue(item2);

    final var result =
        executionQueue.tryMergeWithExistingQueueItem(
            new QueueItemMerger() {
              @Override
              public boolean canBeMerged(final QueueItem queueItem) {
                return queueItem.id().equals(1L);
              }

              @Override
              public QueueItem merge(final QueueItem originalItem) {
                return new QueueItem(
                    originalItem.contextType(),
                    WriteStatementType.INSERT,
                    1L,
                    "statement1",
                    "parameter1+");
              }
            });

    assertThat(result).isTrue();
    assertThat(executionQueue.getQueue()).hasSize(2);
    assertThat(executionQueue.getQueue().get(0)).isNotSameAs(item1);
    assertThat(executionQueue.getQueue().get(0).parameter()).isEqualTo("parameter1+");
    assertThat(executionQueue.getQueue().get(1)).isSameAs(item2);
  }

  @Test
  public void whenNoMatchingItemFoundShouldNotMergeItems() {
    final var item1 =
        new QueueItem(
            ContextType.PROCESS_INSTANCE,
            WriteStatementType.INSERT,
            1L,
            "statement1",
            "parameter1");
    final var item2 =
        new QueueItem(
            ContextType.PROCESS_INSTANCE,
            WriteStatementType.INSERT,
            2L,
            "statement2",
            "parameter2");
    executionQueue.executeInQueue(item1);
    executionQueue.executeInQueue(item2);

    final var result =
        executionQueue.tryMergeWithExistingQueueItem(
            new QueueItemMerger() {
              @Override
              public boolean canBeMerged(final QueueItem queueItem) {
                return queueItem.id().equals(3L);
              }

              @Override
              public QueueItem merge(final QueueItem originalItem) {
                return new QueueItem(
                    originalItem.contextType(),
                    WriteStatementType.INSERT,
                    1L,
                    "statement1",
                    "parameter1+");
              }
            });

    assertThat(result).isFalse();
    assertThat(executionQueue.getQueue()).hasSize(2);
    assertThat(executionQueue.getQueue().get(0)).isSameAs(item1);
    assertThat(executionQueue.getQueue().get(1)).isSameAs(item2);
  }

  @Test
  public void shouldSortQueueItemsDuringFlush() {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.PROCESS_INSTANCE,
            WriteStatementType.UPDATE,
            1L,
            "statement1",
            "parameter1"));
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.PROCESS_INSTANCE,
            WriteStatementType.INSERT,
            1L,
            "statement2",
            "parameter2"));
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.PROCESS_INSTANCE,
            WriteStatementType.DELETE,
            1L,
            "statement3",
            "parameter3"));
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.FLOW_NODE, WriteStatementType.UPDATE, 1L, "statement4", "parameter4"));
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.FLOW_NODE, WriteStatementType.INSERT, 1L, "statement5", "parameter5"));
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.USER_TASK, WriteStatementType.DELETE, 1L, "statement6", "parameter6"));
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.USER_TASK, WriteStatementType.INSERT, 1L, "statement7", "parameter7"));
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.USER_TASK, WriteStatementType.DELETE, 1L, "statement8", "parameter8"));

    // when
    executionQueue.flush();

    // then
    verify(session).update(eq("statement5"), any());
    verify(session).update(eq("statement2"), any());
    verify(session).update(eq("statement6"), any());
    verify(session).update(eq("statement7"), any());
    verify(session).update(eq("statement8"), any());
    verify(session).update(eq("statement4"), any());
    verify(session).update(eq("statement1"), any());
    verify(session).update(eq("statement3"), any());
  }

  @ParameterizedTest
  @CsvSource({
    // statementId, expectedResult
    "foo.updateHistoryCleanupDate,true",
    "io.camunda.db.rdbms.sql.SequenceFlowMapper.updateHistoryCleanupDate,true",
    "io.camunda.db.rdbms.sql.SequenceFlowMapper.createIfNotExists,true",
    "some.other.Statement,false"
  })
  void shouldIgnoreWhenNoRowsAffectedMatchesPatterns(
      final String statementId, final boolean expected) {
    assertThat(DefaultExecutionQueue.shouldIgnoreWhenNoRowsAffected(statementId))
        .isEqualTo(expected);
  }

  @Test
  public void whenMemoryLimitIsReachedFlushShouldHappen() {
    // Set a small memory limit (1 MB)
    executionQueue = new DefaultExecutionQueue(sqlSessionFactory, 1, 1000, 1, metrics);

    // Create items with large parameters to exceed 1 MB memory limit
    // Each large param is ~600KB, so 2 items should exceed 1 MB
    final var largeParam = "x".repeat(600_000); // ~600 KB each
    final var item1 =
        new QueueItem(
            ContextType.PROCESS_INSTANCE, WriteStatementType.INSERT, 1L, "statement1", largeParam);
    final var item2 =
        new QueueItem(
            ContextType.PROCESS_INSTANCE, WriteStatementType.INSERT, 2L, "statement2", largeParam);

    executionQueue.executeInQueue(item1);
    verifyNoInteractions(sqlSessionFactory);

    executionQueue.executeInQueue(item2);
    verifyNoInteractions(sqlSessionFactory);

    // Check should trigger flush due to memory limit
    final var result = executionQueue.checkQueueForFlush();
    assertThat(result).isTrue();

    verify(sqlSessionFactory)
        .openSession(ExecutorType.BATCH, TransactionIsolationLevel.READ_UNCOMMITTED);
    verify(session).update("statement1", largeParam);
    verify(session).update("statement2", largeParam);
    verify(session).flushStatements();
    verify(session).commit();
  }

  @Test
  public void whenMemoryLimitIsNotReachedNoFlushShouldHappen() {
    // Set a large memory limit (10 MB)
    executionQueue = new DefaultExecutionQueue(sqlSessionFactory, 1, 1000, 10, metrics);

    final var item1 =
        new QueueItem(
            ContextType.PROCESS_INSTANCE,
            WriteStatementType.INSERT,
            1L,
            "statement1",
            "parameter1");

    executionQueue.executeInQueue(item1);
    verifyNoInteractions(sqlSessionFactory);

    final var result = executionQueue.checkQueueForFlush();
    assertThat(result).isFalse();

    verifyNoInteractions(sqlSessionFactory);
  }

  @Test
  public void whenEitherCountOrMemoryLimitIsReachedFlushShouldHappen() {
    // Set both count and memory limits (2 items count, 10 MB memory)
    executionQueue = new DefaultExecutionQueue(sqlSessionFactory, 1, 2, 10, metrics);

    final var item1 =
        new QueueItem(
            ContextType.PROCESS_INSTANCE,
            WriteStatementType.INSERT,
            1L,
            "statement1",
            "parameter1");
    final var item2 =
        new QueueItem(
            ContextType.PROCESS_INSTANCE,
            WriteStatementType.INSERT,
            2L,
            "statement2",
            "parameter2");
    final var item3 =
        new QueueItem(
            ContextType.PROCESS_INSTANCE,
            WriteStatementType.INSERT,
            3L,
            "statement3",
            "parameter3");

    executionQueue.executeInQueue(item1);
    executionQueue.executeInQueue(item2);
    verifyNoInteractions(sqlSessionFactory);

    // Adding third item should trigger count-based flush
    executionQueue.executeInQueue(item3);
    verifyNoInteractions(sqlSessionFactory);

    final var result = executionQueue.checkQueueForFlush();
    assertThat(result).isTrue();

    verify(sqlSessionFactory)
        .openSession(ExecutorType.BATCH, TransactionIsolationLevel.READ_UNCOMMITTED);
  }

  @Test
  public void whenOnlyMemoryLimitIsConfiguredItShouldWork() {
    // Set only memory limit (1 MB), no count limit
    executionQueue = new DefaultExecutionQueue(sqlSessionFactory, 1, 0, 1, metrics);

    // Create items with large parameters to exceed 1 MB memory limit
    final var largeParam = "x".repeat(600_000); // ~600 KB each
    final var item1 =
        new QueueItem(
            ContextType.PROCESS_INSTANCE, WriteStatementType.INSERT, 1L, "statement1", largeParam);
    final var item2 =
        new QueueItem(
            ContextType.PROCESS_INSTANCE, WriteStatementType.INSERT, 2L, "statement2", largeParam);

    executionQueue.executeInQueue(item1);
    executionQueue.executeInQueue(item2);

    final var result = executionQueue.checkQueueForFlush();
    assertThat(result).isTrue();

    verify(sqlSessionFactory)
        .openSession(ExecutorType.BATCH, TransactionIsolationLevel.READ_UNCOMMITTED);
  }
}
