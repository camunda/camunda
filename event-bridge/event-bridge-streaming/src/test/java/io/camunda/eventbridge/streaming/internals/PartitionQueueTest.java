/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.internals;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.eventbridge.streaming.internals.SourceEntry.Decoded;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

final class PartitionQueueTest {

  @Test
  void shouldReportCapacitySizeAndRemaining() throws Exception {
    // given
    final PartitionQueue<String> queue = new PartitionQueue<>(4);

    // when
    queue.put(new Decoded<>(1L, "a"));
    queue.put(new Decoded<>(2L, "b"));

    // then
    assertThat(queue.capacity()).isEqualTo(4);
    assertThat(queue.size()).isEqualTo(2);
    assertThat(queue.remainingCapacity()).isEqualTo(2);
    assertThat(queue.isEmpty()).isFalse();
  }

  @Test
  void shouldDrainUpToMaxInOffsetOrder() throws Exception {
    // given
    final PartitionQueue<String> queue = new PartitionQueue<>(8);
    queue.put(new Decoded<>(1L, "a"));
    queue.put(new Decoded<>(2L, "b"));
    queue.put(new Decoded<>(3L, "c"));

    // when — drain at most two
    final List<SourceEntry<String>> out = new ArrayList<>();
    final int drained = queue.drainTo(out, 2);

    // then — the two oldest, in order; the third remains
    assertThat(drained).isEqualTo(2);
    assertThat(out).extracting(SourceEntry::offset).containsExactly(1L, 2L);
    assertThat(queue.size()).isEqualTo(1);
  }

  @Test
  void shouldBlockPutWhenFullThenProceedOnceDrained() throws Exception {
    // given — a queue at capacity
    final PartitionQueue<String> queue = new PartitionQueue<>(1);
    queue.put(new Decoded<>(1L, "a"));
    final AtomicBoolean secondEnqueued = new AtomicBoolean(false);
    final CountDownLatch started = new CountDownLatch(1);

    // when — a second put blocks (back-pressure) until space frees
    final Thread producer =
        new Thread(
            () -> {
              started.countDown();
              try {
                queue.put(new Decoded<>(2L, "b"));
                secondEnqueued.set(true);
              } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
              }
            },
            "producer");
    producer.start();
    assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
    Thread.sleep(50); // give the producer a chance to park in put()
    assertThat(secondEnqueued).isFalse();

    // then — draining one frees capacity and the blocked put completes
    queue.drainTo(new ArrayList<>(), 1);
    producer.join(TimeUnit.SECONDS.toMillis(5));
    assertThat(secondEnqueued).isTrue();
    assertThat(queue.size()).isEqualTo(1);
  }

  @Test
  void shouldRefuseOfferWhenFullWithoutBlocking() {
    // given — a queue at capacity
    final PartitionQueue<String> queue = new PartitionQueue<>(2);
    assertThat(queue.offer(new Decoded<>(1L, "a"))).isTrue();
    assertThat(queue.offer(new Decoded<>(2L, "b"))).isTrue();

    // when — offering into the full queue
    final boolean accepted = queue.offer(new Decoded<>(3L, "c"));

    // then — the offer is refused (the caller pauses instead of blocking) and nothing is lost
    assertThat(accepted).isFalse();
    assertThat(queue.remainingCapacity()).isZero();
    final List<SourceEntry<String>> out = new ArrayList<>();
    queue.drainTo(out, 10);
    assertThat(out).extracting(SourceEntry::offset).containsExactly(1L, 2L);
    assertThat(queue.offer(new Decoded<>(3L, "c"))).isTrue();
  }

  @Test
  void shouldRejectNonPositiveCapacity() {
    assertThatThrownBy(() -> new PartitionQueue<>(0)).isInstanceOf(IllegalArgumentException.class);
  }
}
