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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

final class RingBufferTest {

  private static final LogStreamMetricsImpl METRICS =
      new LogStreamMetricsImpl(new SimpleMeterRegistry());

  /** Creates a new InFlightEntry with the given highestPosition. */
  private static InFlightEntry newEntry(final long highestPosition) {
    final var entry = new InFlightEntry(METRICS, null, null);
    entry.highestPosition = highestPosition;
    return entry;
  }

  /** Creates a new InFlightEntry with highestPosition = 0. */
  private static InFlightEntry newEntry() {
    return newEntry(0);
  }

  @Test
  void putReturnsSequentialIndices() {
    // given
    final var buffer = new RingBuffer(4);

    // when
    final var idx0 = buffer.put(newEntry(10));
    final var idx1 = buffer.put(newEntry(20));
    final var idx2 = buffer.put(newEntry(30));

    // then
    assertThat(idx0).isEqualTo(0);
    assertThat(idx1).isEqualTo(1);
    assertThat(idx2).isEqualTo(2);
  }

  @Test
  void getReturnsStoredEntry() {
    // given
    final var buffer = new RingBuffer(4);
    final var entry = newEntry(42);

    // when
    final long idx = buffer.put(entry);

    // then
    assertThat(buffer.get(idx, 42)).isSameAs(entry);
  }

  @Test
  void getReturnsNullWhenHighestPositionDoesNotMatch() {
    // given
    final var buffer = new RingBuffer(4);
    final var entry = newEntry(42);
    final long idx = buffer.put(entry);

    // when
    final var result = buffer.get(idx, 99);

    // then
    assertThat(result).isNull();
  }

  @Test
  void getReturnsNullAfterDisplacement() {
    // given
    final var buffer = new RingBuffer(4);
    final var first = newEntry(10);
    final long firstIdx = buffer.put(first);
    buffer.put(newEntry(20));
    buffer.put(newEntry(30));
    buffer.put(newEntry(40));

    // when — slot 0 is overwritten by index 4
    final var displaced = newEntry(50);
    final long displacedIdx = buffer.put(displaced);

    // then
    assertThat(buffer.get(firstIdx, 10)).isNull();
    assertThat(buffer.get(displacedIdx, 50)).isSameAs(displaced);
  }

  @Test
  void putSetsPositionOnEntry() {
    // given
    final var buffer = new RingBuffer(4);
    final var entry = newEntry(100);

    // when
    final long idx = buffer.put(entry);

    // then
    assertThat(entry.position).isEqualTo(idx);
  }

  @Test
  void findAndRemoveFindsEntryByHighestPosition() {
    // given
    final var buffer = new RingBuffer(8);
    final var e1 = newEntry(100);
    final var e2 = newEntry(200);
    final var e3 = newEntry(300);
    buffer.put(e1);
    buffer.put(e2);
    buffer.put(e3);

    // when / then — should find entries in order
    assertThat(buffer.findAndRemove(100)).isSameAs(e1);
    assertThat(buffer.findAndRemove(200)).isSameAs(e2);
    assertThat(buffer.findAndRemove(300)).isSameAs(e3);
  }

  @Test
  void findAndRemoveReturnsNullWhenNotFound() {
    // given
    final var buffer = new RingBuffer(4);
    buffer.put(newEntry(100));

    // when
    final var result = buffer.findAndRemove(999);

    // then
    assertThat(result).isNull();
  }

  @Test
  void findAndRemoveCleansUpEntriesWithLowerHighestPosition() {
    // given
    final var buffer = new RingBuffer(8);
    final var skipped = newEntry(100);
    final var target = newEntry(200);
    final long skippedIdx = buffer.put(skipped);
    buffer.put(target);

    // when — findAndRemove(200) should clean up the entry with hp=100
    final var found = buffer.findAndRemove(200);

    // then
    assertThat(found).isSameAs(target);
    assertThat(buffer.get(skippedIdx, 100))
        .as("skipped entry should be removed from the buffer")
        .isNull();
  }

  @Test
  void findAndRemoveCleansUpMultipleSkippedEntries() {
    // given
    final var buffer = new RingBuffer(8);
    final var e1 = newEntry(100);
    final var e2 = newEntry(200);
    final var e3 = newEntry(300);
    final long idx1 = buffer.put(e1);
    final long idx2 = buffer.put(e2);
    buffer.put(e3);

    // when — findAndRemove(300) should clean up both hp=100 and hp=200
    final var found = buffer.findAndRemove(300);

    // then
    assertThat(found).isSameAs(e3);
    assertThat(buffer.get(idx1, 100)).as("entry hp=100 should be cleaned up").isNull();
    assertThat(buffer.get(idx2, 200)).as("entry hp=200 should be cleaned up").isNull();
  }

  @Test
  void findAndRemoveCleansUpRequestListenerOnSkippedEntries() {
    // given
    final var dropped = new AtomicBoolean(false);
    final Listener listener =
        new Listener() {
          @Override
          public void onSuccess() {}

          @Override
          public void onIgnore() {}

          @Override
          public void onDropped() {
            dropped.set(true);
          }
        };
    final var buffer = new RingBuffer(8);
    final var skipped = new InFlightEntry(METRICS, null, listener);
    skipped.highestPosition = 100;
    skipped.onAppend();
    buffer.put(skipped);
    buffer.put(newEntry(200));

    // when
    buffer.findAndRemove(200);

    // then — cleanup() should have called onDropped on the skipped entry's listener
    assertThat(dropped.get()).as("skipped entry's listener should receive onDropped").isTrue();
  }

  /**
   * Verifies that findAndRemove finds first-lap entries after wraparound has overwritten earlier
   * slots.
   */
  @Test
  void findAndRemoveFindsFirstLapEntriesAfterWraparound() {
    // given — capacity = 4, slots 0-3
    final var buffer = new RingBuffer(4);

    // Fill the buffer: indices 0-3, highestPositions 5,10,15,20
    buffer.put(newEntry(5)); // index 0 → slot 0
    buffer.put(newEntry(10)); // index 1 → slot 1
    buffer.put(newEntry(15)); // index 2 → slot 2
    buffer.put(newEntry(20)); // index 3 → slot 3

    // Wraparound: indices 4-5 overwrite slots 0-1
    buffer.put(newEntry(25)); // index 4 → slot 0 (displaces hp=5)
    buffer.put(newEntry(30)); // index 5 → slot 1 (displaces hp=10)

    // Buffer state:
    //   slot 0: {index=4, hp=25}  ← second lap
    //   slot 1: {index=5, hp=30}  ← second lap
    //   slot 2: {index=2, hp=15}  ← first lap, still present
    //   slot 3: {index=3, hp=20}  ← first lap, still present

    // when
    final var found = buffer.findAndRemove(15);

    // then
    assertThat(found).as("findAndRemove(15) should find the entry at slot 2").isNotNull();
    assertThat(found.highestPosition).isEqualTo(15);

    final var found2 = buffer.findAndRemove(20);
    assertThat(found2).as("findAndRemove(20) should find the entry at slot 3").isNotNull();
    assertThat(found2.highestPosition).isEqualTo(20);
  }

  /**
   * Verifies that findAndRemove finds second-lap entries after partial processing and a full
   * wraparound, where the scan starts mid-buffer at a slot with a higher sequential index.
   */
  @Test
  void findAndRemoveFindsEntriesAfterPartialProcessingAndWraparound() {
    // given — capacity = 4, slots 0-3
    final var buffer = new RingBuffer(4);

    // First lap: indices 0-3, highestPositions 5,10,15,20
    buffer.put(newEntry(5)); // index 0 → slot 0
    buffer.put(newEntry(10)); // index 1 → slot 1
    buffer.put(newEntry(15)); // index 2 → slot 2
    buffer.put(newEntry(20)); // index 3 → slot 3

    // Process entries 0 and 1 in order → lastProcessedIndex = 1
    assertThat(buffer.findAndRemove(5)).isNotNull(); // lastProcessedIndex → 0
    assertThat(buffer.findAndRemove(10)).isNotNull(); // lastProcessedIndex → 1

    // Second lap: indices 4-7 overwrite all slots
    buffer.put(newEntry(25)); // index 4 → slot 0 (displaces hp=5, already removed)
    buffer.put(newEntry(30)); // index 5 → slot 1 (displaces hp=10, already removed)
    buffer.put(newEntry(35)); // index 6 → slot 2 (displaces hp=15, NOT yet processed!)
    buffer.put(newEntry(40)); // index 7 → slot 3 (displaces hp=20, NOT yet processed!)

    // Buffer state:
    //   slot 0: {index=4, hp=25}  ← second lap
    //   slot 1: {index=5, hp=30}  ← second lap
    //   slot 2: {index=6, hp=35}  ← second lap (displaced hp=15)
    //   slot 3: {index=7, hp=40}  ← second lap (displaced hp=20)

    // hp=15 and hp=20 were displaced → findAndRemove returns null (correct)
    // Now process the second-lap entries in order:

    // when
    final var found = buffer.findAndRemove(25);

    // then
    assertThat(found).as("findAndRemove(25) should find the entry at slot 0").isNotNull();
    assertThat(found.highestPosition).isEqualTo(25);
  }

  @Test
  void constructorRoundsUpToNextPowerOfTwo() {
    // given / when / then
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
    // given / when
    final var buffer = new RingBuffer(0);

    // then
    assertThat(buffer.capacity()).isEqualTo(8192);
  }

  @Test
  void constructorRejectsNegative() {
    // given / when / then
    assertThatThrownBy(() -> new RingBuffer(-1)).isInstanceOf(IllegalArgumentException.class);
  }

  @Nested
  class ConcurrencyTests {

    // Capacity must exceed ITERATIONS to avoid wraparound in tests that assert exact entries
    private static final int CAPACITY = 1 << 17; // 131072
    private static final int ITERATIONS = 100_000;

    /**
     * Simulates the FlowControl access pattern: one writer thread puts entries (sequencer), a
     * reader thread reads them via get(index, highestPosition) (raft thread). Verifies that entries
     * written by the writer are visible to the reader.
     */
    @Test
    void writerIsVisibleToReader() {
      // given
      final var buffer = new RingBuffer(CAPACITY);
      final var failures = new ConcurrentLinkedQueue<Throwable>();
      final var startLatch = new CountDownLatch(1);
      final var indices = new long[ITERATIONS + 1];

      final var writer =
          new Thread(
              () -> {
                awaitLatch(startLatch);
                for (long pos = 1; pos <= ITERATIONS; pos++) {
                  final var entry = newEntry(pos);
                  indices[(int) pos] = buffer.put(entry);
                }
              });

      final var reader =
          new Thread(
              () -> {
                awaitLatch(startLatch);
                for (long pos = 1; pos <= ITERATIONS; pos++) {
                  InFlightEntry entry;
                  while ((entry = buffer.get(indices[(int) pos], pos)) == null) {
                    LockSupport.parkNanos(1);
                  }
                  try {
                    assertThat(entry.highestPosition).isEqualTo(pos);
                  } catch (final AssertionError e) {
                    failures.add(e);
                    return;
                  }
                }
              });

      // when / then
      runAndAwait(startLatch, failures, writer, reader);
      assertThat(failures).isEmpty();
    }

    /**
     * Simulates the full lifecycle: writer puts entries, a second thread finds and removes them
     * (like onProcessed). Verifies findAndRemove works correctly.
     */
    @Test
    void findAndRemoveWorksAcrossThreads() {
      // given
      final var buffer = new RingBuffer(CAPACITY);
      final var failures = new ConcurrentLinkedQueue<Throwable>();
      final var startLatch = new CountDownLatch(1);

      final var writer =
          new Thread(
              () -> {
                awaitLatch(startLatch);
                for (long pos = 1; pos <= ITERATIONS; pos++) {
                  buffer.put(newEntry(pos));
                }
              });

      final var processor =
          new Thread(
              () -> {
                awaitLatch(startLatch);
                for (long pos = 1; pos <= ITERATIONS; pos++) {
                  InFlightEntry entry = null;
                  while (entry == null) {
                    entry = buffer.findAndRemove(pos);
                    LockSupport.parkNanos(1);
                  }
                  try {
                    assertThat(entry.highestPosition).isEqualTo(pos);
                  } catch (final AssertionError e) {
                    failures.add(e);
                    return;
                  }
                }
              });

      // when / then
      runAndAwait(startLatch, failures, writer, processor);
      assertThat(failures).isEmpty();
    }

    /**
     * Multiple reader threads concurrently reading the same entries that a single writer is
     * publishing. Mirrors the FlowControl pattern where onWrite (raft thread), onCommit (raft
     * thread), and onProcessed (stream processor) may all read the same entry.
     */
    @Test
    void multipleReadersSeeWriterUpdates() {
      // given
      final var buffer = new RingBuffer(CAPACITY);
      final var failures = new ConcurrentLinkedQueue<Throwable>();
      final var startLatch = new CountDownLatch(1);
      final var indices = new long[ITERATIONS + 1];

      final var writer =
          new Thread(
              () -> {
                awaitLatch(startLatch);
                for (long pos = 1; pos <= ITERATIONS; pos++) {
                  indices[(int) pos] = buffer.put(newEntry(pos));
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
                    while ((entry = buffer.get(indices[(int) pos], pos)) == null) {
                      LockSupport.parkNanos(1);
                    }
                    try {
                      assertThat(entry.highestPosition).isEqualTo(pos);
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

      // when / then
      runAndAwait(startLatch, failures, allThreads);
      assertThat(failures).isEmpty();
    }

    /**
     * Writer wraps around with a small capacity. The reader uses findAndRemove to process entries
     * in order. Because entries are indexed sequentially, displacement only occurs after the full
     * buffer capacity is used (unlike position-based indexing where it depended on batch size).
     *
     * <p>The test asserts that both hits (entry found and processed) and misses (entry displaced
     * before processing) occurred, and that every hit returned the correct entry (position guard
     * was not bypassed).
     */
    @Test
    void sequentialIndexingWorksUnderConcurrentWraparound() {
      // given
      final var smallCapacity = 64;
      final var buffer = new RingBuffer(smallCapacity);
      final var failures = new ConcurrentLinkedQueue<Throwable>();
      final var startLatch = new CountDownLatch(1);
      final var publishedIndex = new AtomicLong(0);

      final var writer =
          new Thread(
              () -> {
                awaitLatch(startLatch);
                for (long pos = 1; pos <= ITERATIONS; pos++) {
                  final var entry = new InFlightEntry(METRICS, null, null);
                  entry.highestPosition = pos;
                  buffer.put(entry);
                  publishedIndex.set(pos);
                }
              });

      final var hits = new AtomicLong(0);
      final var misses = new AtomicLong(0);

      final var reader =
          new Thread(
              () -> {
                awaitLatch(startLatch);
                for (long pos = 1; pos <= ITERATIONS; pos++) {
                  if (pos > publishedIndex.get()) {
                    LockSupport.parkNanos(1);
                  }
                  final var entry = buffer.findAndRemove(pos);
                  if (entry != null) {
                    try {
                      assertThat(entry.highestPosition).isEqualTo(pos);
                    } catch (final AssertionError e) {
                      failures.add(e);
                      return;
                    }
                    hits.incrementAndGet();
                  } else {
                    misses.incrementAndGet();
                  }
                }
              });

      // when / then
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
