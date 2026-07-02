/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.client.internal.consumer;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.eventbridge.client.OffsetResetPolicy;
import io.camunda.eventbridge.client.TopicPartition;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies prefetch depth (in-flight pipelining) and minBytes pass-through in {@link Prefetcher}.
 */
final class PrefetcherTest {

  private ScheduledExecutorService executor;

  @BeforeEach
  void setUp() {
    executor = Executors.newSingleThreadScheduledExecutor();
  }

  @AfterEach
  void tearDown() {
    executor.shutdownNow();
  }

  @Test
  void shouldIssueMultipleInFlightFetchesWhenPrefetchDepthAboveOne() {
    // given: a fetcher whose futures never complete, so in-flight fetches accumulate
    final AtomicInteger inFlight = new AtomicInteger();
    final Fetcher fetcher =
        (topic, partition, offset, maxBytes, minBytes, maxWaitMs) -> {
          inFlight.incrementAndGet();
          return new CompletableFuture<>(); // never completes
        };
    final SubscriptionState subscription =
        new SubscriptionState(fetcher, OffsetResetPolicy.EARLIEST);
    final PrefetchBuffer buffer = new PrefetchBuffer(3);
    final TopicPartition tp = new TopicPartition("t1", 1);
    buffer.runLocked(() -> subscription.applyOwnedPartitions(List.of(tp)));
    final Prefetcher prefetcher =
        new Prefetcher(fetcher, executor, subscription, buffer, () -> false, 1 << 20, 0, 5_000L);

    // when
    prefetcher.kick();

    // then: depth-3 lets three fetches pipeline for the one partition
    assertThat(inFlight.get()).isEqualTo(3);
  }

  @Test
  void shouldIssueOnlyOneInFlightFetchAtDepthOne() {
    // given
    final AtomicInteger inFlight = new AtomicInteger();
    final Fetcher fetcher =
        (topic, partition, offset, maxBytes, minBytes, maxWaitMs) -> {
          inFlight.incrementAndGet();
          return new CompletableFuture<>();
        };
    final SubscriptionState subscription =
        new SubscriptionState(fetcher, OffsetResetPolicy.EARLIEST);
    final PrefetchBuffer buffer = new PrefetchBuffer(1);
    final TopicPartition tp = new TopicPartition("t1", 1);
    buffer.runLocked(() -> subscription.applyOwnedPartitions(List.of(tp)));
    final Prefetcher prefetcher =
        new Prefetcher(fetcher, executor, subscription, buffer, () -> false, 1 << 20, 0, 5_000L);

    // when: kicked twice — the second is a no-op because one fetch is already in flight
    prefetcher.kick();
    prefetcher.kick();

    // then
    assertThat(inFlight.get()).isEqualTo(1);
  }

  @Test
  void shouldPassConfiguredMinBytesToFetch() {
    // given
    final AtomicInteger observedMinBytes = new AtomicInteger(-1);
    final AtomicReference<Long> observedMaxWaitMs = new AtomicReference<>();
    final Fetcher fetcher =
        (topic, partition, offset, maxBytes, minBytes, maxWaitMs) -> {
          observedMinBytes.set(minBytes);
          observedMaxWaitMs.set(maxWaitMs);
          return new CompletableFuture<>(); // never completes — one fetch is enough to observe args
        };
    final SubscriptionState subscription =
        new SubscriptionState(fetcher, OffsetResetPolicy.EARLIEST);
    final PrefetchBuffer buffer = new PrefetchBuffer(1);
    final TopicPartition tp = new TopicPartition("t1", 1);
    buffer.runLocked(() -> subscription.applyOwnedPartitions(List.of(tp)));
    final Prefetcher prefetcher =
        new Prefetcher(fetcher, executor, subscription, buffer, () -> false, 4096, 512, 7_000L);

    // when
    prefetcher.kick();

    // then: the configured minBytes (and longPollMs) are threaded into the fetch call
    assertThat(observedMinBytes.get()).isEqualTo(512);
    assertThat(observedMaxWaitMs.get()).isEqualTo(7_000L);
  }
}
