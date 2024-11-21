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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.TransactionIsolationLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ExecutionQueueTest {

  private SqlSession session;
  private SqlSessionFactory sqlSessionFactory;

  private ExecutionQueue executionQueue;

  @BeforeEach
  public void beforeEach() {
    session = mock(SqlSession.class);
    sqlSessionFactory = mock(SqlSessionFactory.class);
    when(sqlSessionFactory.openSession(
            ExecutorType.BATCH, TransactionIsolationLevel.READ_UNCOMMITTED))
        .thenReturn(session);

    executionQueue = new ExecutionQueue(sqlSessionFactory, 1, 5);
  }

  @Test
  public void whenElementIsAddedNoFlushHappens() {
    executionQueue.executeInQueue(mock(QueueItem.class));

    Mockito.verifyNoInteractions(sqlSessionFactory);
  }

  @Test
  public void whenFlushLimitIsActivatedFlushShouldHappen() {
    final var item1 = new QueueItem(ContextType.PROCESS_INSTANCE, 1L, "statement1", "parameter1");
    final var item2 = new QueueItem(ContextType.PROCESS_INSTANCE, 1L, "statement2", "parameter2");
    final var item3 = new QueueItem(ContextType.PROCESS_INSTANCE, 1L, "statement3", "parameter3");
    final var item4 = new QueueItem(ContextType.PROCESS_INSTANCE, 1L, "statement4", "parameter4");
    final var item5 = new QueueItem(ContextType.PROCESS_INSTANCE, 1L, "statement5", "parameter5");
    executionQueue.executeInQueue(item1);
    executionQueue.executeInQueue(item2);
    executionQueue.executeInQueue(item3);
    executionQueue.executeInQueue(item4);
    executionQueue.executeInQueue(item5);

    verify(sqlSessionFactory)
        .openSession(ExecutorType.BATCH, TransactionIsolationLevel.READ_UNCOMMITTED);
    verify(session).update("statement1", "parameter1");
    verify(session).update("statement2", "parameter2");
    verify(session).flushStatements();
    verify(session).commit();
  }

  @Test
  public void whenFlushIsCalledFlushShouldHappen() {
    final var item1 = new QueueItem(ContextType.PROCESS_INSTANCE, 1L, "statement1", "parameter1");
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
    final var item1 = new QueueItem(ContextType.PROCESS_INSTANCE, 1L, "statement1", "parameter1");
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
  public void whenFlushIsExceptionalSessionIsRolledBack() {
    final var item1 = new QueueItem(ContextType.PROCESS_INSTANCE, 1L, "statement1", "parameter1");
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
    final var item1 = new QueueItem(ContextType.PROCESS_INSTANCE, 1L, "statement1", "parameter1");
    final var item2 = new QueueItem(ContextType.PROCESS_INSTANCE, 2L, "statement2", "parameter2");
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
                return new QueueItem(originalItem.contextType(), 1L, "statement1", "parameter1+");
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
    final var item1 = new QueueItem(ContextType.PROCESS_INSTANCE, 1L, "statement1", "parameter1");
    final var item2 = new QueueItem(ContextType.PROCESS_INSTANCE, 2L, "statement2", "parameter2");
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
                return new QueueItem(originalItem.contextType(), 1L, "statement1", "parameter1+");
              }
            });

    assertThat(result).isFalse();
    assertThat(executionQueue.getQueue()).hasSize(2);
    assertThat(executionQueue.getQueue().get(0)).isSameAs(item1);
    assertThat(executionQueue.getQueue().get(1)).isSameAs(item2);
  }
}
