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
import org.junit.jupiter.api.Test;

/** Verifies the total-buffered-bytes backpressure of {@link PrefetchBuffer}. */
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

  private static Event event(final TopicPartition tp, final long position, final int size) {
    return new Event(position, tp.topic(), tp.partition(), new byte[size]);
  }
}
