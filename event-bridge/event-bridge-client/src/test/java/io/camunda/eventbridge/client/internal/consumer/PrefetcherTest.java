/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.client.internal.consumer;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.camunda.eventbridge.batch.BatchBuilder;
import io.camunda.eventbridge.client.FetchResult;
import io.camunda.eventbridge.client.OffsetResetPolicy;
import io.camunda.eventbridge.client.TopicPartition;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

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
  void shouldBufferAnInFlightFetchThatLandsAfterPause() {
    // given: one controllable in-flight fetch for the partition
    final AtomicReference<CompletableFuture<FetchResult>> inFlight = new AtomicReference<>();
    final Fetcher fetcher =
        (topic, partition, offset, maxBytes, minBytes, maxWaitMs) -> {
          final CompletableFuture<FetchResult> fetch = new CompletableFuture<>();
          inFlight.compareAndSet(null, fetch); // keep the first; any later fetch never completes
          return fetch;
        };
    final SubscriptionState subscription =
        new SubscriptionState(fetcher, OffsetResetPolicy.EARLIEST);
    final PrefetchBuffer buffer = new PrefetchBuffer(1);
    final TopicPartition tp = new TopicPartition("t1", 1);
    buffer.runLocked(
        () -> {
          subscription.applyOwnedPartitions(List.of(tp));
          subscription.setNextPosition(tp, 0L);
        });
    final Prefetcher prefetcher =
        new Prefetcher(fetcher, executor, subscription, buffer, () -> false, 1 << 20, 0, 5_000L);
    prefetcher.kick();

    // when: the partition is paused while the fetch is in flight, and the fetch then lands
    buffer.runLocked(() -> buffer.pause(tp));
    inFlight
        .get()
        .complete(
            FetchResult.parse(
                fetchBody(0L, 0L, 1L, new BatchBuilder().add("v".getBytes(UTF_8)).build())));

    // then: the landed events are retained (not delivered) until the partition is resumed
    assertThat(buffer.drain(10, 0L, () -> false)).isEmpty();
    buffer.runLocked(() -> buffer.resume(tp));
    assertThat(buffer.drain(10, 0L, () -> false)).hasSize(1);
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

  @Test
  void shouldLogTheEmptyPartitionWaitOnceAtDebugInsteadOfWarning() {
    // given: a fresh consumer on an empty partition — every fetch answers out of range, even from
    // the initial position the reset policy resolves (nothing has been published yet)
    final List<CompletableFuture<FetchResult>> fetches = new ArrayList<>();
    final Fetcher fetcher =
        (topic, partition, offset, maxBytes, minBytes, maxWaitMs) -> {
          final CompletableFuture<FetchResult> fetch = new CompletableFuture<>();
          fetches.add(fetch);
          return fetch;
        };
    final SubscriptionState subscription =
        new SubscriptionState(fetcher, OffsetResetPolicy.EARLIEST);
    final PrefetchBuffer buffer = new PrefetchBuffer(1);
    final TopicPartition tp = new TopicPartition("t1", 1);
    buffer.runLocked(() -> subscription.applyOwnedPartitions(List.of(tp)));
    final Prefetcher prefetcher =
        new Prefetcher(fetcher, executor, subscription, buffer, () -> false, 1 << 20, 0, 5_000L);
    final ListAppender<ILoggingEvent> log = attachLog();

    try {
      // when: the initial fetch and two backed-off retries all come back out of range
      for (int attempt = 0; attempt < 3; attempt++) {
        prefetcher.kick();
        fetches.get(attempt).complete(FetchResult.outOfRange());
      }

      // then: the benign wait is noted once at DEBUG, never at WARN
      assertThat(outOfRangeEvents(log, Level.WARN)).isEmpty();
      assertThat(outOfRangeEvents(log, Level.DEBUG)).hasSize(1);
    } finally {
      detachLog(log);
    }
  }

  @Test
  void shouldWarnWhenAnEstablishedCursorFallsOutOfRange() {
    // given: a consumer whose cursor was established mid-stream (e.g. a committed offset)
    final List<CompletableFuture<FetchResult>> fetches = new ArrayList<>();
    final Fetcher fetcher =
        (topic, partition, offset, maxBytes, minBytes, maxWaitMs) -> {
          final CompletableFuture<FetchResult> fetch = new CompletableFuture<>();
          fetches.add(fetch);
          return fetch;
        };
    final SubscriptionState subscription =
        new SubscriptionState(fetcher, OffsetResetPolicy.EARLIEST);
    final PrefetchBuffer buffer = new PrefetchBuffer(1);
    final TopicPartition tp = new TopicPartition("t1", 1);
    buffer.runLocked(
        () -> {
          subscription.applyOwnedPartitions(List.of(tp));
          subscription.setNextPosition(tp, 5L);
        });
    final Prefetcher prefetcher =
        new Prefetcher(fetcher, executor, subscription, buffer, () -> false, 1 << 20, 0, 5_000L);
    final ListAppender<ILoggingEvent> log = attachLog();

    try {
      // when: the mid-stream fetch falls out of range (records below the cursor are gone) and the
      // reset-policy retry still finds nothing to serve
      prefetcher.kick();
      fetches.get(0).complete(FetchResult.outOfRange());
      prefetcher.kick();
      fetches.get(1).complete(FetchResult.outOfRange());

      // then: the possible data loss is warned about exactly once; the benign follow-up reset from
      // the re-resolved initial position drops to DEBUG
      assertThat(outOfRangeEvents(log, Level.WARN)).hasSize(1);
      assertThat(outOfRangeEvents(log, Level.DEBUG)).hasSize(1);
    } finally {
      detachLog(log);
    }
  }

  private static ListAppender<ILoggingEvent> attachLog() {
    final Logger logger = (Logger) LoggerFactory.getLogger(Prefetcher.class);
    final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.setLevel(Level.DEBUG);
    logger.addAppender(appender);
    return appender;
  }

  private static void detachLog(final ListAppender<ILoggingEvent> appender) {
    final Logger logger = (Logger) LoggerFactory.getLogger(Prefetcher.class);
    logger.detachAppender(appender);
    logger.setLevel(null);
  }

  private static List<ILoggingEvent> outOfRangeEvents(
      final ListAppender<ILoggingEvent> log, final Level level) {
    return log.list.stream()
        .filter(event -> event.getLevel() == level)
        .filter(event -> event.getFormattedMessage().contains("out of range"))
        .toList();
  }

  /** A successful fetch body: header (positions, high watermark, data length) plus batch data. */
  private static byte[] fetchBody(
      final long firstPos, final long lastPos, final long highWatermark, final byte[] data) {
    return ByteBuffer.allocate(Long.BYTES * 3 + Integer.BYTES + data.length)
        .putLong(firstPos)
        .putLong(lastPos)
        .putLong(highWatermark)
        .putInt(data.length)
        .put(data)
        .array();
  }
}
