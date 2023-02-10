/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.atomix.raft.storage.log;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.raft.storage.log.RaftLogFlusher.FlushMetaStore;
import io.atomix.utils.concurrent.Scheduled;
import io.atomix.utils.concurrent.Scheduler;
import io.camunda.zeebe.journal.Journal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.agrona.CloseHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

final class DelayedFlusherTest {
  private final TestScheduler scheduler = new TestScheduler();
  private final DelayedFlusher flusher = new DelayedFlusher(scheduler, Duration.ofSeconds(5));

  @AfterEach
  void afterEach() {
    CloseHelper.quietClose(flusher);
  }

  @Test
  void shouldDelayFlushByInterval() {
    // given
    final var journal = Mockito.mock(Journal.class);
    final var flushMetaStore = Mockito.mock(FlushMetaStore.class);
    Mockito.when(journal.isOpen()).thenReturn(true);

    // when
    flusher.flush(journal, flushMetaStore);

    // then
    assertThat(scheduler.operations).hasSize(1);

    final var scheduled = scheduler.operations.get(0);
    assertThat(scheduled)
        .extracting(t -> t.initialDelay, t -> t.interval)
        .containsExactly(Duration.ZERO, Duration.ofSeconds(5));
    Mockito.verify(journal, Mockito.never()).flush();
    Mockito.verify(flushMetaStore, Mockito.never()).storeLastFlushedIndex(Mockito.anyLong());
  }

  @Test
  void shouldFlushWhenScheduledTaskIsRun() {
    // given
    final var journal = Mockito.mock(Journal.class);
    final var flushMetaStore = Mockito.mock(FlushMetaStore.class);
    Mockito.when(journal.isOpen()).thenReturn(true);
    Mockito.when(journal.getLastIndex()).thenReturn(5L);

    // when
    flusher.flush(journal, flushMetaStore);

    // then
    final var scheduled = scheduler.operations.get(0);
    scheduled.operation.run();
    Mockito.verify(journal, Mockito.times(1)).flush();
    Mockito.verify(flushMetaStore, Mockito.times(1)).storeLastFlushedIndex(5L);
  }

  @Test
  void shouldNotScheduleIfAlreadyScheduled() {
    // given
    final var journal = Mockito.mock(Journal.class);
    final var flushMetaStore = Mockito.mock(FlushMetaStore.class);
    Mockito.when(journal.getLastIndex()).thenReturn(5L);

    // when
    flusher.flush(journal, flushMetaStore);
    flusher.flush(journal, flushMetaStore);
    flusher.flush(journal, flushMetaStore);

    // then
    assertThat(scheduler.operations).hasSize(1);
    final var scheduled = scheduler.operations.get(0);
    assertThat(scheduled)
        .extracting(t -> t.initialDelay, t -> t.interval)
        .containsExactly(Duration.ZERO, Duration.ofSeconds(5));
  }

  @Test
  void shouldCancelScheduledFlushOnClose() {
    // given
    final var journal = Mockito.mock(Journal.class);
    final var flushMetaStore = Mockito.mock(FlushMetaStore.class);

    // when
    flusher.flush(journal, flushMetaStore);
    flusher.close();

    // then
    final var scheduled = scheduler.operations.get(0);
    assertThat(scheduled.cancelled).isTrue();
  }

  @Test
  void shouldNotFlushWhenFlusherIsClosed() {
    // given
    final var journal = Mockito.mock(Journal.class);
    final var flushMetaStore = Mockito.mock(FlushMetaStore.class);
    Mockito.when(journal.isOpen()).thenReturn(true);

    // when
    flusher.flush(journal, flushMetaStore);
    flusher.close();

    // then
    final var scheduled = scheduler.operations.get(0);
    scheduled.operation.run();
    Mockito.verify(journal, Mockito.never()).flush();
    Mockito.verify(flushMetaStore, Mockito.never()).storeLastFlushedIndex(Mockito.anyLong());
  }

  @Test
  void shouldNotFlushIfJournalIsClosed() {
    // given
    final var journal = Mockito.mock(Journal.class);
    final var flushMetaStore = Mockito.mock(FlushMetaStore.class);
    Mockito.when(journal.isOpen()).thenReturn(false);

    // when
    flusher.flush(journal, flushMetaStore);

    // then
    final var scheduled = scheduler.operations.get(0);
    scheduled.operation.run();
    Mockito.verify(journal, Mockito.never()).flush();
    Mockito.verify(flushMetaStore, Mockito.never()).storeLastFlushedIndex(Mockito.anyLong());
  }

  private static final class TestScheduled implements Scheduled {
    private final Duration initialDelay;
    private final Duration interval;
    private final Runnable operation;

    private boolean cancelled;

    private TestScheduled(
        final Duration initialDelay, final Duration interval, final Runnable operation) {
      this.initialDelay = initialDelay;
      this.interval = interval;
      this.operation = operation;
    }

    @Override
    public void cancel() {
      cancelled = true;
    }

    @Override
    public boolean isDone() {
      return cancelled;
    }
  }

  private static final class TestScheduler implements Scheduler {
    private final List<TestScheduled> operations = new ArrayList<>();

    @Override
    public Scheduled schedule(
        final Duration initialDelay, final Duration interval, final Runnable callback) {
      final var scheduled = new TestScheduled(initialDelay, interval, callback);
      operations.add(scheduled);
      return scheduled;
    }
  }
}
