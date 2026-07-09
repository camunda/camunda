/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.internals;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.eventbridge.client.Consumer;
import io.camunda.eventbridge.streaming.CommitCut;
import io.camunda.eventbridge.streaming.OffsetStore;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * The persister's late-submit fallback: a job submitted after {@link CutPersister#close()} — an
 * actor whose stop outlived the runtime's shutdown wait — must neither hang on a queue nobody
 * drains nor race the writer thread; it runs inline on the caller thread once the writer has ended,
 * which is then trivially the only writer of the shared durable resources. Frozen cuts, by
 * contrast, are refused after close (their actor merges them back and a restart replays them).
 */
final class CutPersisterTest {

  private static final String TOPIC = "facts";

  private final List<String> journal = new CopyOnWriteArrayList<>();
  private final Consumer consumer = mock(Consumer.class);
  private final OffsetStore offsets =
      new OffsetStore() {
        @Override
        public Map<Integer, Long> restore() {
          return Map.of();
        }

        @Override
        public void store(final int partition, final long offset) {
          journal.add("offset:" + partition + ":" + offset);
        }
      };

  private CutPersister persister() {
    when(consumer.commitOffset(any(), anyInt(), anyLong()))
        .thenReturn(CompletableFuture.completedFuture(null));
    return new CutPersister(consumer, TOPIC, Runnable::run, offsets, List.of());
  }

  @Test
  void shouldRunAJobSubmittedAfterCloseInlineOnTheCallerThread() {
    // given — a persister whose writer thread ran (a cut went through) and was then closed
    final CutPersister persister = persister();
    final CompletableFuture<Void> cut =
        persister.enqueue(
            1,
            1L,
            new CommitCut() {
              @Override
              public void persist() {
                journal.add("persist:1");
              }

              @Override
              public void complete(final boolean success) {}
            },
            CutMetrics.NOOP);
    assertThat(cut).succeedsWithin(Duration.ofSeconds(5));
    persister.close();

    // when — a late stop commit is submitted after the close
    final AtomicReference<Thread> jobThread = new AtomicReference<>();
    final CompletableFuture<Void> job =
        persister.submit(
            () -> {
              jobThread.set(Thread.currentThread());
              journal.add("late-stop");
            });

    // then — it ran inline on the caller thread (the writer has ended, so that thread is
    // trivially the only writer of the shared durable resources) and completed
    assertThat(job).succeedsWithin(Duration.ofSeconds(5));
    assertThat(jobThread.get()).isSameAs(Thread.currentThread());
    assertThat(journal).containsExactly("offset:1:1", "persist:1", "late-stop");
  }

  @Test
  void shouldRunAJobSubmittedAfterCloseInlineWhenTheWriterNeverStarted() {
    // given — a persister closed before any work ever started its writer thread
    final CutPersister persister = persister();
    persister.close();

    // when
    final AtomicReference<Thread> jobThread = new AtomicReference<>();
    final CompletableFuture<Void> job =
        persister.submit(() -> jobThread.set(Thread.currentThread()));

    // then
    assertThat(job).succeedsWithin(Duration.ofSeconds(5));
    assertThat(jobThread.get()).isSameAs(Thread.currentThread());
  }

  @Test
  void shouldFailAJobThatThrowsAfterClose() {
    // given
    final CutPersister persister = persister();
    persister.close();

    // when — the inline fallback runs a job that throws
    final IllegalStateException failure = new IllegalStateException("commit failed");
    final CompletableFuture<Void> job =
        persister.submit(
            () -> {
              throw failure;
            });

    // then — the future carries the failure instead of throwing on the submitter
    assertThat(job)
        .failsWithin(Duration.ofSeconds(5))
        .withThrowableOfType(ExecutionException.class)
        .withCause(failure);
  }

  @Test
  void shouldRefuseAFrozenCutEnqueuedAfterClose() {
    // given
    final CutPersister persister = persister();
    persister.close();

    // when — unlike a stop commit, a frozen cut needs no fallback: failing it merges the cut back
    // on its actor, and a restart replays from the last durable offset
    final CompletableFuture<Void> cut =
        persister.enqueue(
            1,
            1L,
            new CommitCut() {
              @Override
              public void persist() {}

              @Override
              public void complete(final boolean success) {}
            },
            CutMetrics.NOOP);

    // then
    assertThat(cut)
        .failsWithin(Duration.ofSeconds(5))
        .withThrowableOfType(ExecutionException.class)
        .withCauseInstanceOf(IllegalStateException.class);
  }
}
