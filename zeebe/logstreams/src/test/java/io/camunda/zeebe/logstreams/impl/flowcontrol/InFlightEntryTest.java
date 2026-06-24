/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.logstreams.impl.flowcontrol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.netflix.concurrency.limits.Limiter.Listener;
import io.camunda.zeebe.logstreams.impl.LogStreamMetrics;
import io.camunda.zeebe.util.CloseableSilently;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

final class InFlightEntryTest {

  private LogStreamMetrics metrics;
  private CountingCloseable writeTimerFromMetrics;
  private CountingCloseable commitTimerFromMetrics;

  @BeforeEach
  void setUp() {
    writeTimerFromMetrics = new CountingCloseable();
    commitTimerFromMetrics = new CountingCloseable();
    metrics = mock(LogStreamMetrics.class);
    when(metrics.startWriteTimer()).thenReturn(writeTimerFromMetrics);
    when(metrics.startCommitTimer()).thenReturn(commitTimerFromMetrics);
  }

  private static final class CountingCloseable implements CloseableSilently {
    final AtomicInteger closeCount = new AtomicInteger();

    @Override
    public void close() {
      closeCount.incrementAndGet();
    }
  }

  private static final class CountingListener implements Listener {
    final AtomicInteger successCount = new AtomicInteger();
    final AtomicInteger ignoreCount = new AtomicInteger();
    final AtomicInteger droppedCount = new AtomicInteger();

    @Override
    public void onSuccess() {
      successCount.incrementAndGet();
    }

    @Override
    public void onIgnore() {
      ignoreCount.incrementAndGet();
    }

    @Override
    public void onDropped() {
      droppedCount.incrementAndGet();
    }
  }

  @Nested
  class OnAppend {

    @Test
    void setsWriteAndCommitTimers() {
      final var entry = new InFlightEntry(metrics, null, null);

      entry.onAppend();

      assertThat(entry.writeTimer.get()).isSameAs(writeTimerFromMetrics);
      assertThat(entry.commitTimer.get()).isSameAs(commitTimerFromMetrics);
    }

    @Test
    void doesNotFailWithoutListener() {
      final var entry = new InFlightEntry(metrics, null, null);

      entry.onAppend();

      assertThat(entry.writeTimer.get()).isNotNull();
    }
  }

  @Nested
  class OnWrite {

    @Test
    void closesWriteTimer() {
      final var entry = new InFlightEntry(metrics, null, null);
      entry.onAppend();

      entry.onWrite();

      assertThat(writeTimerFromMetrics.closeCount.get()).isEqualTo(1);
    }

    @Test
    void isIdempotent() {
      final var entry = new InFlightEntry(metrics, null, null);
      entry.onAppend();

      entry.onWrite();
      entry.onWrite();

      assertThat(writeTimerFromMetrics.closeCount.get()).isEqualTo(1);
    }
  }

  @Nested
  class OnCommit {

    @Test
    void closesCommitTimer() {
      final var entry = new InFlightEntry(metrics, null, null);
      entry.onAppend();

      entry.onCommit();

      assertThat(commitTimerFromMetrics.closeCount.get()).isEqualTo(1);
    }

    @Test
    void isIdempotent() {
      final var entry = new InFlightEntry(metrics, null, null);
      entry.onAppend();

      entry.onCommit();
      entry.onCommit();

      assertThat(commitTimerFromMetrics.closeCount.get()).isEqualTo(1);
    }
  }

  @Nested
  class OnProcessed {

    @Test
    void callsOnSuccess() {
      final var listener = new CountingListener();
      final var entry = new InFlightEntry(metrics, null, listener);
      entry.onAppend();

      entry.onProcessed();

      assertThat(listener.successCount.get()).isEqualTo(1);
    }

    @Test
    void isIdempotent() {
      final var listener = new CountingListener();
      final var entry = new InFlightEntry(metrics, null, listener);
      entry.onAppend();

      entry.onProcessed();
      entry.onProcessed();

      assertThat(listener.successCount.get()).isEqualTo(1);
    }

    @Test
    void doesNothingWithoutListener() {
      final var entry = new InFlightEntry(metrics, null, null);

      entry.onProcessed();
    }
  }

  @Nested
  class Cleanup {

    @Test
    void callsOnDropped() {
      final var listener = new CountingListener();
      final var entry = new InFlightEntry(metrics, null, listener);
      entry.onAppend();

      entry.cleanup();

      assertThat(listener.droppedCount.get()).isEqualTo(1);
    }

    @Test
    void discardsTimersWithoutRecording() {
      final var entry = new InFlightEntry(metrics, null, null);
      entry.onAppend();

      entry.cleanup();

      assertThat(writeTimerFromMetrics.closeCount.get()).isEqualTo(0);
      assertThat(commitTimerFromMetrics.closeCount.get()).isEqualTo(0);
      assertThat(entry.writeTimer.get()).isNull();
      assertThat(entry.commitTimer.get()).isNull();
    }

    @Test
    void isIdempotent() {
      final var listener = new CountingListener();
      final var entry = new InFlightEntry(metrics, null, listener);
      entry.onAppend();

      entry.cleanup();
      entry.cleanup();

      assertThat(listener.droppedCount.get()).isEqualTo(1);
    }

    @Test
    void doesNothingWithoutListener() {
      final var entry = new InFlightEntry(metrics, null, null);

      entry.cleanup();
    }
  }

  @Nested
  class ConcurrentLifecycle {

    @Test
    @Timeout(30)
    void allResourcesReleasedExactlyOnce() throws InterruptedException {
      final int entryCount = 100_000;
      final int threadCount = 3;

      final var writeTimers = new CountingCloseable[entryCount];
      final var commitTimers = new CountingCloseable[entryCount];
      final var listeners = new CountingListener[entryCount];
      final var entries = new InFlightEntry[entryCount];

      // Each entry gets its own timers via the mock
      final var index = new AtomicInteger();
      when(metrics.startWriteTimer()).thenAnswer(inv -> writeTimers[index.get()]);
      when(metrics.startCommitTimer()).thenAnswer(inv -> commitTimers[index.get()]);

      for (int i = 0; i < entryCount; i++) {
        index.set(i);
        writeTimers[i] = new CountingCloseable();
        commitTimers[i] = new CountingCloseable();
        listeners[i] = new CountingListener();
        entries[i] = new InFlightEntry(metrics, null, listeners[i]);
        entries[i].onAppend();
      }

      // 3 threads race on the remaining lifecycle methods
      final var startLatch = new CountDownLatch(1);
      final var doneLatch = new CountDownLatch(threadCount);

      for (int t = 0; t < threadCount; t++) {
        new Thread(
                () -> {
                  try {
                    startLatch.await();
                    for (final var entry : entries) {
                      entry.onWrite();
                      entry.onCommit();
                      entry.onProcessed();
                      entry.cleanup();
                    }
                  } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                  } finally {
                    doneLatch.countDown();
                  }
                })
            .start();
      }

      startLatch.countDown();
      doneLatch.await();

      for (int i = 0; i < entryCount; i++) {
        // Timer closed at most once: onWrite/onCommit may close it, or cleanup may discard it
        assertThat(writeTimers[i].closeCount.get())
            .describedAs("write timer %d closed at most once", i)
            .isLessThanOrEqualTo(1);
        assertThat(commitTimers[i].closeCount.get())
            .describedAs("commit timer %d closed at most once", i)
            .isLessThanOrEqualTo(1);
        final int callbacks = listeners[i].successCount.get() + listeners[i].ignoreCount.get();
        assertThat(callbacks).describedAs("listener %d called back exactly once", i).isEqualTo(1);
      }
    }
  }
}
