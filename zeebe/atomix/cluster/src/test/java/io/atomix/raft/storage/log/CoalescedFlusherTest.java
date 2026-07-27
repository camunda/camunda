/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.raft.storage.log;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.atomix.cluster.MemberId;
import io.atomix.raft.DeterministicSingleThreadContext;
import io.camunda.zeebe.journal.CheckedJournalException;
import io.camunda.zeebe.journal.CheckedJournalException.FlushException;
import io.camunda.zeebe.journal.Journal;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.agrona.CloseHelper;
import org.jmock.lib.concurrent.DeterministicScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class CoalescedFlusherTest {
  private final DeterministicScheduler scheduler = new DeterministicScheduler();
  private final CoalescedFlusher flusher =
      new CoalescedFlusher(new DeterministicSingleThreadContext(scheduler, MemberId.from("test")));

  private final AtomicLong lastIndex = new AtomicLong();
  private final AtomicLong lastFlushedIndex = new AtomicLong(-1);
  private final Journal journal = mock(Journal.class);

  @AfterEach
  void afterEach() {
    CloseHelper.quietClose(flusher);
  }

  @Test
  void shouldCompleteImmediatelyWhenIndexAlreadyDurable() throws CheckedJournalException {
    // given
    lastIndex.set(5);
    lastFlushedIndex.set(5);

    // when
    final var result = flusher.flush(journal, 5);

    // then - no flush is scheduled at all
    assertThat(result).isCompleted();
    assertThat(scheduler.isIdle()).isTrue();
    verify(journal, times(0)).flush();
  }

  @Test
  void shouldNotCompleteBeforeFlushCovered() {
    // given
    lastIndex.set(6);

    // when
    final var result = flusher.flush(journal, 6);

    // then - not completed until the scheduled flush ran
    assertThat(result).isNotCompleted();
    scheduler.runUntilIdle();
    assertThat(result).isCompleted();
  }

  @Test
  void shouldCoalescePendingFlushes() throws CheckedJournalException {
    // given - three requests queue up while no flush has run yet
    lastIndex.set(8);
    final var first = flusher.flush(journal, 6);
    final var second = flusher.flush(journal, 7);
    final var third = flusher.flush(journal, 8);

    // when
    scheduler.runUntilIdle();

    // then - a single flush covered all of them
    verify(journal, times(1)).flush();
    assertThat(first).isCompleted();
    assertThat(second).isCompleted();
    assertThat(third).isCompleted();
  }

  @Test
  void shouldCompleteInRequestOrder() {
    // given
    lastIndex.set(8);
    final List<Integer> completionOrder = new ArrayList<>();
    flusher.flush(journal, 6).whenComplete((ok, error) -> completionOrder.add(6));
    flusher.flush(journal, 7).whenComplete((ok, error) -> completionOrder.add(7));
    flusher.flush(journal, 8).whenComplete((ok, error) -> completionOrder.add(8));

    // when
    scheduler.runUntilIdle();

    // then
    assertThat(completionOrder).containsExactly(6, 7, 8);
  }

  @Test
  void shouldCompleteCoveredRequestImmediatelyDespitePendingRequests() {
    // given - index 5 is already durable, and an earlier request for index 6 is still pending
    lastIndex.set(6);
    lastFlushedIndex.set(5);
    final var pending = flusher.flush(journal, 6);

    // when - requesting an already covered index
    final var covered = flusher.flush(journal, 5);

    // then - the covered request completes immediately, without waiting for the pending flush
    assertThat(covered).isCompleted();
    assertThat(pending).isNotCompleted();

    scheduler.runUntilIdle();
    assertThat(pending).isCompleted();
  }

  @Test
  void shouldCompleteCoveredRequestEvenWhenNextFlushFails() throws CheckedJournalException {
    // given - a pending request whose flush will fail, and an already durable index
    lastIndex.set(6);
    lastFlushedIndex.set(5);
    doThrow(new FlushException(new IOException("failed to sync"))).when(journal).flush();
    final var pending = flusher.flush(journal, 6);

    // when
    final var covered = flusher.flush(journal, 5);
    scheduler.runUntilIdle();

    // then - the covered request stays successful, only the request requiring the flush fails
    assertThat(covered).isCompleted();
    assertThat(pending).isCompletedExceptionally();
  }

  @Test
  void shouldFlushAgainForRecordsAppendedDuringFlush() throws CheckedJournalException {
    // given - a flush for index 6 is in progress while a request for index 8 arrives
    lastIndex.set(6);
    final var first = flusher.flush(journal, 6);

    // when - the first flush only covers index 6, the second request needs a follow-up flush
    scheduler.runNextPendingCommand();
    assertThat(first).isCompleted();
    lastIndex.set(8);
    final var second = flusher.flush(journal, 8);
    scheduler.runUntilIdle();

    // then
    verify(journal, times(2)).flush();
    assertThat(second).isCompleted();
  }

  @Test
  void shouldCoverLateRequestsWithFollowUpFlush() throws CheckedJournalException {
    // given - two requests, but the first flush only covers the first one
    doAnswer(
            invocation -> {
              lastFlushedIndex.set(6);
              return null;
            })
        .doAnswer(
            invocation -> {
              lastFlushedIndex.set(8);
              return null;
            })
        .when(journal)
        .flush();
    final var first = flusher.flush(journal, 6);
    final var second = flusher.flush(journal, 8);

    // when
    scheduler.runNextPendingCommand();

    // then - the first result is completed, and a follow-up flush was scheduled for the second
    assertThat(first).isCompleted();
    assertThat(second).isNotCompleted();

    scheduler.runUntilIdle();
    verify(journal, times(2)).flush();
    assertThat(second).isCompleted();
  }

  @Test
  void shouldFailAllPendingWhenFlushFails() throws CheckedJournalException {
    // given
    lastIndex.set(7);
    final var failure = new FlushException(new IOException("failed to sync"));
    doThrow(failure).when(journal).flush();
    final var first = flusher.flush(journal, 6);
    final var second = flusher.flush(journal, 7);

    // when
    scheduler.runUntilIdle();

    // then
    assertThat(first).isCompletedExceptionally();
    assertThat(second).isCompletedExceptionally();
  }

  @Test
  void shouldFailPendingWhenFlushThrowsUnexpectedException() throws CheckedJournalException {
    // given - a failure outside the expected journal exceptions, e.g. from the metastore
    lastIndex.set(6);
    doThrow(new IllegalStateException("unexpected"))
        .doAnswer(
            invocation -> {
              lastFlushedIndex.set(lastIndex.get());
              return null;
            })
        .when(journal)
        .flush();
    final var failed = flusher.flush(journal, 6);

    // when
    scheduler.runUntilIdle();

    // then - the pending result fails instead of hanging, and the flusher stays usable
    assertThat(failed).isCompletedExceptionally();
    final var retried = flusher.flush(journal, 6);
    scheduler.runUntilIdle();
    assertThat(retried).isCompleted();
  }

  @Test
  void shouldRecoverAfterFailedFlush() throws CheckedJournalException {
    // given - a first flush which fails
    lastIndex.set(6);
    final var failure = new FlushException(new IOException("failed to sync"));
    doThrow(failure)
        .doAnswer(
            invocation -> {
              lastFlushedIndex.set(lastIndex.get());
              return null;
            })
        .when(journal)
        .flush();
    final var failed = flusher.flush(journal, 6);
    scheduler.runUntilIdle();
    assertThat(failed).isCompletedExceptionally();

    // when - a new request arrives after the failure
    final var retried = flusher.flush(journal, 6);
    scheduler.runUntilIdle();

    // then
    assertThat(retried).isCompleted();
  }

  @Test
  void shouldFailPendingOnLogTruncation() {
    // given - pending requests both above and below the truncation index
    lastIndex.set(8);
    final var below = flusher.flush(journal, 6);
    final var above = flusher.flush(journal, 8);

    // when
    flusher.onLogTruncation(7);

    // then - all pending results fail, in order, and the leader retries the appends
    assertThat(below).isCompletedExceptionally();
    assertThat(above).isCompletedExceptionally();
  }

  @Test
  void shouldFailPendingOnClose() {
    // given
    lastIndex.set(6);
    final var pending = flusher.flush(journal, 6);

    // when
    flusher.close();

    // then
    assertThat(pending).isCompletedExceptionally();
  }

  @Test
  void shouldFailFlushAfterClose() {
    // given
    flusher.close();

    // when
    final var result = flusher.flush(journal, 6);

    // then
    assertThat(result).isCompletedExceptionally();
  }

  @Test
  void shouldFailPendingWhenJournalIsEmpty() {
    // given - a request which an empty journal can never cover, e.g. requested just before the
    // log was reset
    when(journal.isEmpty()).thenReturn(true);
    final var result = flusher.flush(journal, 6);

    // when
    scheduler.runUntilIdle();

    // then - the result fails instead of endlessly rescheduling flushes which the journal skips
    assertThat(result).isCompletedExceptionally();
    assertThat(scheduler.isIdle()).isTrue();
  }

  @Test
  void shouldFailPendingWhenJournalIsClosed() {
    // given
    lastIndex.set(6);
    when(journal.isOpen()).thenReturn(false);

    // when
    final var result = flusher.flush(journal, 6);
    scheduler.runUntilIdle();

    // then - instead of retrying a journal which can never be flushed again, the result fails
    assertThat(result).isCompletedExceptionally();
  }

  /**
   * Sets up the mocked journal to behave like the real one w.r.t. flushing: a flush covers
   * everything appended so far, i.e. it advances the flushed index to the last index at the time of
   * the call.
   */
  @BeforeEach
  void setupJournal() throws CheckedJournalException {
    when(journal.isOpen()).thenReturn(true);
    when(journal.getLastFlushedIndex()).thenAnswer(invocation -> lastFlushedIndex.get());
    doAnswer(
            invocation -> {
              lastFlushedIndex.set(lastIndex.get());
              return null;
            })
        .when(journal)
        .flush();
  }
}
