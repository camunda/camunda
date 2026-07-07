/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.client.internal.consumer;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.eventbridge.client.Event;
import io.camunda.eventbridge.client.TopicPartition;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * Verifies the total-buffered-bytes backpressure and the per-partition pause/resume semantics of
 * {@link PrefetchBuffer}.
 */
final class PrefetchBufferTest {

  private static final TopicPartition A = new TopicPartition("t", 1);
  private static final TopicPartition B = new TopicPartition("t", 2);

  @Test
  void shouldSuppressFetchesWhileBufferedBytesAtCap() {
    // given: a 100-byte cap and partition A filled to the cap
    final PrefetchBuffer buffer = new PrefetchBuffer(1, 100);
    buffer.runLocked(() -> buffer.add(A, event(A, 1L, 100)));

    // when: claiming with an empty partition B that would otherwise be fetched
    final PrefetchBuffer.Claim claim = buffer.claim(List.of(A, B));

    // then: the global byte cap suppresses all fetches, including B's
    assertThat(claim.partitions()).isEmpty();
  }

  @Test
  void shouldResumeFetchesOncePollDrainsBelowCap() {
    // given: A filled to the cap, so fetches are suppressed
    final PrefetchBuffer buffer = new PrefetchBuffer(1, 100);
    buffer.runLocked(() -> buffer.add(A, event(A, 1L, 100)));
    assertThat(buffer.claim(List.of(A, B)).partitions()).isEmpty();

    // when: a poll drains A back under the cap
    final List<Event> drained = buffer.drain(10, 0L, () -> false);

    // then: the buffer is under the cap again and both empty partitions are claimed
    assertThat(drained).hasSize(1);
    assertThat(buffer.claim(List.of(A, B)).partitions()).contains(A, B);
  }

  @Test
  void shouldReleaseBufferedBytesOnClear() {
    // given: A filled to the cap
    final PrefetchBuffer buffer = new PrefetchBuffer(1, 100);
    buffer.runLocked(() -> buffer.add(A, event(A, 1L, 100)));
    assertThat(buffer.claim(List.of(A)).partitions()).isEmpty();

    // when: A's buffer is cleared (e.g. after a seek)
    buffer.runLocked(() -> buffer.clear(A));

    // then: its bytes are released, so fetches resume
    assertThat(buffer.claim(List.of(A)).partitions()).contains(A);
  }

  @Test
  void shouldExcludePausedPartitionFromClaim() {
    // given: A paused, B active, both empty
    final PrefetchBuffer buffer = new PrefetchBuffer(1);
    buffer.runLocked(() -> buffer.pause(A));

    // when
    final PrefetchBuffer.Claim claim = buffer.claim(List.of(A, B));

    // then: no fetch slot is claimed for the paused partition
    assertThat(claim.partitions()).containsExactly(B);
  }

  @Test
  void shouldRetainBufferedEventsOnPauseAndDeliverThemAfterResume() {
    // given: A paused with a buffered event
    final PrefetchBuffer buffer = new PrefetchBuffer(1);
    buffer.runLocked(
        () -> {
          buffer.add(A, event(A, 1L, 10));
          buffer.pause(A);
        });

    // when: a poll drains while A is paused
    final List<Event> whilePaused = buffer.drain(10, 0L, () -> false);

    // then: nothing is delivered, but the event is retained and delivered after resume
    assertThat(whilePaused).isEmpty();
    buffer.runLocked(() -> buffer.resume(A));
    final List<Event> afterResume = buffer.drain(10, 0L, () -> false);
    assertThat(afterResume).hasSize(1);
    assertThat(afterResume.get(0).position()).isEqualTo(1L);
  }

  @Test
  void shouldParkPollOverPausedOnlyDataAndWakeOnResume() throws Exception {
    // given: the only buffered data sits on a paused partition
    final PrefetchBuffer buffer = new PrefetchBuffer(1);
    buffer.runLocked(
        () -> {
          buffer.add(A, event(A, 1L, 10));
          buffer.pause(A);
        });

    // when: a poll drains with a long timeout — it must park, not spin or return the paused data
    final AtomicReference<List<Event>> result = new AtomicReference<>();
    final Thread poller =
        new Thread(() -> result.set(buffer.drain(10, TimeUnit.SECONDS.toNanos(10), () -> false)));
    poller.start();
    awaitParked(poller);

    // and: the partition is resumed and the parked poll signalled
    buffer.runLocked(
        () -> {
          buffer.resume(A);
          buffer.signal();
        });

    // then: the poll wakes promptly and delivers the retained event (well before the timeout)
    poller.join(TimeUnit.SECONDS.toMillis(5));
    assertThat(poller.isAlive()).isFalse();
    assertThat(result.get()).hasSize(1);
  }

  @Test
  void shouldDropPauseMarkAndPausedBytesOnRetain() {
    // given: A paused at the byte cap
    final PrefetchBuffer buffer = new PrefetchBuffer(1, 100);
    buffer.runLocked(
        () -> {
          buffer.add(A, event(A, 1L, 100));
          buffer.pause(A);
        });

    // when: a rebalance revokes A
    buffer.runLocked(() -> buffer.retain(List.of(B)));

    // then: the pause mark and its byte accounting die with the partition
    assertThat(buffer.supplyLocked(buffer::pausedPartitions)).isEmpty();
    assertThat(buffer.claim(List.of(B)).partitions()).containsExactly(B);
  }

  @Test
  void shouldExcludePausedBytesFromTheFetchGate() {
    // given: A paused and holding bytes at the cap
    final PrefetchBuffer buffer = new PrefetchBuffer(1, 100);
    buffer.runLocked(
        () -> {
          buffer.add(A, event(A, 1L, 100));
          buffer.pause(A);
        });

    // when: claiming for the active partition B
    final PrefetchBuffer.Claim claim = buffer.claim(List.of(A, B));

    // then: A's retained bytes do not stall B's fetch
    assertThat(claim.partitions()).containsExactly(B);
  }

  @Test
  void shouldRetainAndAccountAnInFlightFetchLandingAfterPause() {
    // given: A paused while a fetch is in flight
    final PrefetchBuffer buffer = new PrefetchBuffer(1, 100);
    buffer.runLocked(() -> buffer.pause(A));

    // when: the in-flight fetch lands on the paused partition
    buffer.runLocked(() -> buffer.add(A, event(A, 1L, 100)));

    // then: its bytes count as paused (B's fetches proceed) and the event survives to resume
    assertThat(buffer.claim(List.of(A, B)).partitions()).containsExactly(B);
    buffer.runLocked(() -> buffer.resume(A));
    assertThat(buffer.drain(10, 0L, () -> false)).hasSize(1);
    // once resumed and drained, A is claimable again (B still holds its in-flight slot)
    assertThat(buffer.claim(List.of(A, B)).partitions()).contains(A);
  }

  @Test
  void shouldKeepPauseMarkAcrossSeekClear() {
    // given: A paused with buffered events
    final PrefetchBuffer buffer = new PrefetchBuffer(1, 100);
    buffer.runLocked(
        () -> {
          buffer.add(A, event(A, 1L, 100));
          buffer.pause(A);
        });

    // when: a seek clears A's buffer
    buffer.runLocked(() -> buffer.clear(A));

    // then: the pause mark survives (claim still skips A) and the byte accounting is consistent
    assertThat(buffer.supplyLocked(buffer::pausedPartitions)).containsExactly(A);
    assertThat(buffer.claim(List.of(A, B)).partitions()).containsExactly(B);
  }

  /** Spins (bounded) until {@code thread} parks in the buffer's timed await. */
  private static void awaitParked(final Thread thread) {
    final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (thread.getState() != Thread.State.TIMED_WAITING && System.nanoTime() < deadline) {
      Thread.onSpinWait();
    }
    assertThat(thread.getState()).isEqualTo(Thread.State.TIMED_WAITING);
  }

  private static Event event(final TopicPartition tp, final long position, final int size) {
    return new Event(position, tp.topic(), tp.partition(), new byte[size]);
  }
}
