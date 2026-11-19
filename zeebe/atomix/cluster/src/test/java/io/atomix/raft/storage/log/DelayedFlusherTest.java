/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.raft.storage.log;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.atomix.utils.concurrent.Scheduled;
import io.atomix.utils.concurrent.Scheduler;
import io.camunda.zeebe.journal.CheckedJournalException;
import io.camunda.zeebe.journal.CheckedJournalException.FlushException;
import io.camunda.zeebe.journal.Journal;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
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
  void shouldDelayFlushByInterval() throws CheckedJournalException {
    // given
    final var journal = Mockito.mock(Journal.class);
    Mockito.when(journal.isOpen()).thenReturn(true);

    // when
    flusher.flush(journal);

    // then
    assertThat(scheduler.operations).hasSize(1);

    final var scheduled = scheduler.operations.get(0);
    assertThat(scheduled.delay).isEqualTo(Duration.ofSeconds(5));
    Mockito.verify(journal, Mockito.never()).flush();
  }

  @Test
  void shouldFlushWhenScheduledTaskIsRun() throws CheckedJournalException {
    // given
    final var journal = Mockito.mock(Journal.class);
    Mockito.when(journal.isOpen()).thenReturn(true);
    Mockito.when(journal.getLastIndex()).thenReturn(5L);

    // when
    flusher.flush(journal);
    scheduler.runNext();

    // then
    Mockito.verify(journal, Mockito.times(1)).flush();
  }

  @Test
  void shouldNotScheduleIfAlreadyScheduled() throws CheckedJournalException {
    // given
    final var journal = Mockito.mock(Journal.class);
    Mockito.when(journal.getLastIndex()).thenReturn(5L);

    // when
    flusher.flush(journal);
    flusher.flush(journal);
    flusher.flush(journal);

    // then
    assertThat(scheduler.operations).hasSize(1);
    final var scheduled = scheduler.operations.get(0);
    assertThat(scheduled.delay).isEqualTo(Duration.ofSeconds(5));
  }

  @Test
  void shouldCancelScheduledFlushOnClose() throws CheckedJournalException {
    // given
    final var journal = Mockito.mock(Journal.class);

    // when
    flusher.flush(journal);
    flusher.close();

    // then
    final var scheduled = scheduler.operations.get(0);
    assertThat(scheduled.cancelled).isTrue();
  }

  @Test
  void shouldNotScheduleFlushWhenClosed() throws CheckedJournalException {
    // given
    final var journal = Mockito.mock(Journal.class);
    Mockito.when(journal.isOpen()).thenReturn(true);

    // when
    flusher.close();
    flusher.flush(journal);

    // then
    assertThat(scheduler.operations).isEmpty();
  }

  @Test
  void shouldNotRetryFailedFlush() throws CheckedJournalException {
    // given
    final var journal = Mockito.mock(Journal.class);
    Mockito.doThrow(new FlushException(new IOException("Input/output error")))
        .when(journal)
        .flush();

    // when
    flusher.flush(journal);
    scheduler.runNext();

    // then
    assertThat(scheduler.operations).isEmpty();
    Mockito.verify(journal, Mockito.times(1)).flush();
  }

  @Test
  void shouldFailLaterFlushesAfterFailedFlush() throws CheckedJournalException {
    // given
    final var journal = Mockito.mock(Journal.class);
    final var error = new FlushException(new IOException("Input/output error"));
    Mockito.doThrow(error).when(journal).flush();
    flusher.flush(journal);
    scheduler.runNext();

    // when
    final var thrown = catchThrowable(() -> flusher.flush(journal));

    // then
    assertThat(thrown).isInstanceOf(FlushException.class).hasCause(error);
    assertThat(scheduler.operations).isEmpty();
  }

  @Test
  void shouldReportFailedFlushToListener() throws CheckedJournalException {
    // given
    final var journal = Mockito.mock(Journal.class);
    final var error = new FlushException(new IOException("Input/output error"));
    Mockito.doThrow(error).when(journal).flush();
    final var reported = new ArrayList<Throwable>();
    flusher.setAsyncFailureListener(reported::add);

    // when
    flusher.flush(journal);
    scheduler.runNext();

    // then
    assertThat(reported).containsExactly(error);
  }

  @Test
  void shouldReportUncheckedFlushErrorToListener() throws CheckedJournalException {
    // given - e.g. the meta store failing to store the last flushed index
    final var journal = Mockito.mock(Journal.class);
    final var error = new UncheckedIOException(new IOException("Input/output error"));
    Mockito.doThrow(error).when(journal).flush();
    final var reported = new ArrayList<Throwable>();
    flusher.setAsyncFailureListener(reported::add);

    // when
    flusher.flush(journal);
    scheduler.runNext();

    // then
    assertThat(reported).containsExactly(error);
    assertThat(scheduler.operations).isEmpty();
  }

  private static final class TestScheduled implements Scheduled {
    private final Duration delay;
    private final Runnable operation;

    private boolean cancelled;

    private TestScheduled(final Duration delay, final Runnable operation) {
      this.delay = delay;
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
    public Scheduled schedule(final long delay, final TimeUnit timeUnit, final Runnable callback) {
      final var scheduled =
          new TestScheduled(Duration.of(delay, timeUnit.toChronoUnit()), callback);
      operations.add(scheduled);
      return scheduled;
    }

    @Override
    public Scheduled schedule(
        final Duration initialDelay, final Duration interval, final Runnable callback) {
      throw new UnsupportedOperationException("fixed rate scheduling unsupported");
    }

    private void runNext() {
      operations.remove(0).operation.run();
    }
  }
}
