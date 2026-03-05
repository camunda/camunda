/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.logstreams.impl.flowcontrol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.netflix.concurrency.limits.Limiter.Listener;
import io.camunda.zeebe.logstreams.impl.LogStreamMetricsImpl;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

final class RingBufferTest {

  private static final LogStreamMetricsImpl METRICS = new LogStreamMetricsImpl(new SimpleMeterRegistry());

  /** Creates a new InFlightEntry. Position is set by {@link RingBuffer#put}. */
  private static InFlightEntry newEntry() {
    return new InFlightEntry(METRICS, null, null);
  }

  @Test
  void getReturnsStoredEntry() {
    final var buffer = new RingBuffer(4);
    final var entry = newEntry();
    buffer.put(1, entry);
    assertThat(buffer.get(1)).isSameAs(entry);
  }

  @Test
  void removeNullsTheSlot() {
    final var buffer = new RingBuffer(4);
    final var entry = newEntry();
    buffer.put(3, entry);
    buffer.remove(entry);
    assertThat(buffer.get(3)).isNull();
  }

  @Test
  void removeDoesNotRemoveOverwrittenEntry() {
    final var buffer = new RingBuffer(4);
    final var position = 3;
    final var positionWrapped = position + buffer.capacity();
    final var entry = newEntry();
    buffer.put(position, entry);
    final var secondEntry = newEntry();
    buffer.put(positionWrapped, secondEntry);
    buffer.remove(entry);
    assertThat(buffer.get(position)).isNull();
    assertThat(buffer.get(positionWrapped)).isSameAs(secondEntry);
  }

  @Test
  void getReturnsNullWhenPositionDoesNotMatch() {
    // Position guard: after wraparound, get(oldPosition) returns null because
    // the entry in the slot was stamped with the new position.
    final var buffer = new RingBuffer(4);
    buffer.put(5, newEntry());
    // Overwrite slot with position 9 (same slot: 9 & 3 == 1 == 5 & 3)
    final var lastEntry = newEntry();
    buffer.put(9, lastEntry);
    // get(5) should return null — the slot holds an entry for position 9
    assertThat(buffer.get(5)).isNull();
    // get(9) should return the entry
    assertThat(buffer.get(9)).isNotNull().isSameAs(lastEntry);
  }

  @Test
  void putSetsPositionOnEntry() {
    final var buffer = new RingBuffer(4);
    final var entry = newEntry();
    buffer.put(42, entry);
    assertThat(entry.position).isEqualTo(42);
  }

  @Test
  void constructorRoundsUpToNextPowerOfTwo() {
    assertThat(new RingBuffer(1).capacity()).isEqualTo(1);
    assertThat(new RingBuffer(2).capacity()).isEqualTo(2);
    assertThat(new RingBuffer(3).capacity()).isEqualTo(4);
    assertThat(new RingBuffer(5).capacity()).isEqualTo(8);
    assertThat(new RingBuffer(7).capacity()).isEqualTo(8);
    assertThat(new RingBuffer(9).capacity()).isEqualTo(16);
    assertThat(new RingBuffer(1000).capacity()).isEqualTo(1024);
    assertThat(new RingBuffer(1024).capacity()).isEqualTo(1024);
    assertThat(new RingBuffer(1025).capacity()).isEqualTo(2048);
  }

  @Test
  void constructorUsesDefaultForZero() {
    final var buffer = new RingBuffer(0);
    assertThat(buffer.capacity()).isEqualTo(8192);
  }

  @Test
  void constructorRejectsNegative() {
    assertThatThrownBy(() -> new RingBuffer(-1)).isInstanceOf(IllegalArgumentException.class);
  }

  @Nested
  class ConcurrencyTests {

    // Capacity must exceed ITERATIONS to avoid wraparound in tests that assert exact entries
    private static final int CAPACITY = 1 << 17; // 131072
    private static final int ITERATIONS = 100_000;

    /**
     * Simulates the FlowControl access pattern: one writer thread puts entries (sequencer), a
     * reader thread reads them (raft thread). Verifies that entries written by the writer are
     * visible to the reader.
     */
    @Test
    void writerIsVisibleToReader() {
      final var buffer = new RingBuffer(CAPACITY);
      final var failures = new ConcurrentLinkedQueue<Throwable>();
      final var startLatch = new CountDownLatch(1);

      final var writer =
          new Thread(
              () -> {
                awaitLatch(startLatch);
                for (long pos = 1; pos <= ITERATIONS; pos++) {
                  buffer.put(pos, newEntry());
                }
              });

      final var reader =
          new Thread(
              () -> {
                awaitLatch(startLatch);
                for (long pos = 1; pos <= ITERATIONS; pos++) {
                  InFlightEntry entry;
                  while ((entry = buffer.get(pos)) == null) {
                    LockSupport.parkNanos(1);
                  }
                  try {
                    assertThat(entry.position).isEqualTo(pos);
                  } catch (final AssertionError e) {
                    failures.add(e);
                    return;
                  }
                }
              });

      runAndAwait(startLatch, failures, writer, reader);
      assertThat(failures).isEmpty();
    }

    /**
     * Simulates the full lifecycle: writer puts entries, a second thread reads and then removes
     * them (like onProcessed). Verifies removal is visible.
     */
    @Test
    void removeIsVisibleAcrossThreads() {
      final var buffer = new RingBuffer(CAPACITY);
      final var failures = new ConcurrentLinkedQueue<Throwable>();
      final var startLatch = new CountDownLatch(1);

      final var writer =
          new Thread(
              () -> {
                awaitLatch(startLatch);
                for (long pos = 1; pos <= ITERATIONS; pos++) {
                  buffer.put(pos, newEntry());
                }
              });

      final var processor =
          new Thread(
              () -> {
                awaitLatch(startLatch);
                for (long pos = 1; pos <= ITERATIONS; pos++) {
                  InFlightEntry entry = null;
                  while (entry == null) {
                    entry = buffer.get(pos);
                    LockSupport.parkNanos(1);
                  }
                  buffer.remove(entry);
                  try {
                    assertThat(buffer.get(pos)).isNull();
                  } catch (final AssertionError e) {
                    failures.add(e);
                    return;
                  }
                }
              });

      runAndAwait(startLatch, failures, writer, processor);
      assertThat(failures).isEmpty();
    }

    /**
     * Multiple reader threads concurrently reading the same positions that a single writer is
     * publishing. Mirrors the FlowControl pattern where onWrite (raft thread), onCommit (raft
     * thread), and onProcessed (stream processor) may all read the same entry.
     */
    @Test
    void multipleReadersSeeWriterUpdates() {
      final var buffer = new RingBuffer(CAPACITY);
      final var failures = new ConcurrentLinkedQueue<Throwable>();
      final var startLatch = new CountDownLatch(1);

      final var writer =
          new Thread(
              () -> {
                awaitLatch(startLatch);
                for (long pos = 1; pos <= ITERATIONS; pos++) {
                  buffer.put(pos, newEntry());
                }
              });

      final var readers = new Thread[3];
      for (int i = 0; i < readers.length; i++) {
        readers[i] =
            new Thread(
                () -> {
                  awaitLatch(startLatch);
                  for (long pos = 1; pos <= ITERATIONS; pos++) {
                    InFlightEntry entry;
                    while ((entry = buffer.get(pos)) == null) {
                      LockSupport.parkNanos(1);
                    }
                    try {
                      assertThat(entry.position).isEqualTo(pos);
                    } catch (final AssertionError e) {
                      failures.add(e);
                      return;
                    }
                  }
                });
      }

      final var allThreads = new Thread[readers.length + 1];
      allThreads[0] = writer;
      System.arraycopy(readers, 0, allThreads, 1, readers.length);
      runAndAwait(startLatch, failures, allThreads);
      assertThat(failures).isEmpty();
    }

    /**
     * Writer wraps around with a small capacity. The reader trails the writer using a shared
     * progress counter, reading positions that the writer has recently written. This ensures both
     * paths are exercised:
     *
     * <ul>
     *   <li><b>Hit</b>: the entry is still in the slot (reader is close behind the writer)
     *   <li><b>Miss</b>: the entry was displaced by a newer write (reader fell behind by more than
     *       capacity positions)
     * </ul>
     *
     * The test asserts that both hits and misses occurred and that every hit returned the correct
     * entry (position guard was not bypassed).
     */
    @Test
    void positionGuardWorksUnderConcurrentWraparound() {
      final var smallCapacity = 64;
      final var buffer = new RingBuffer(smallCapacity);
      final var failures = new ConcurrentLinkedQueue<Throwable>();
      final var startLatch = new CountDownLatch(1);

      final var writer =
          new Thread(
              () -> {
                awaitLatch(startLatch);
                for (long pos = 1; pos <= ITERATIONS; pos++) {
                  buffer.put(pos, new InFlightEntry(METRICS, null, limitListener));
                }
              });

      final var hits = new AtomicLong(0);
      final var misses = new AtomicLong(0);

      final var reader =
          new Thread(
              () -> {
                awaitLatch(startLatch);
                long readerPos = 0;
                while (readerPos < ITERATIONS) {
                  final long writerPos = buffer.lastWrittenPosition();
                  if (writerPos <= readerPos) {
                    LockSupport.parkNanos(1);
                    continue;
                  }
                  // Read a position that was recently written — may or may not have been displaced
                  // by now. Read from (writerPos - smallCapacity + 1) to exercise the boundary
                  // where entries are about to be displaced.
                  final long readPos = Math.max(readerPos + 1, writerPos - smallCapacity + 1);
                  readerPos = readPos;
                  try {
                    final var entry = buffer.get(readPos);
                    if (entry != null) {
                      assertThat(entry.position).isEqualTo(readPos);
                      hits.incrementAndGet();
                    } else {
                      misses.incrementAndGet();
                    }
                  } catch (final AssertionError e) {
                    failures.add(e);
                    return;
                  }
                }
              });

      runAndAwait(startLatch, failures, writer, reader);
      assertThat(failures).isEmpty();
      assertThat(hits.get()).as("expected some hits (entry still present)").isGreaterThan(0);
      assertThat(misses.get()).as("expected some misses (entry displaced)").isGreaterThan(0);
    }

    /** Starts all threads, releases the latch, and waits for all threads to finish. */
    private void runAndAwait(
        final CountDownLatch startLatch,
        final ConcurrentLinkedQueue<Throwable> failures,
        final Thread... threads) {
      for (final var thread : threads) {
        thread.setUncaughtExceptionHandler((t, e) -> failures.add(e));
        thread.start();
      }
      startLatch.countDown();

      Awaitility.await("threads complete")
          .atMost(Duration.ofSeconds(30))
          .untilAsserted(
              () -> {
                for (final var thread : threads) {
                  assertThat(thread.isAlive()).isFalse();
                }
              });
    }

    private void awaitLatch(final CountDownLatch latch) {
      try {
        latch.await();
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }
}
