/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.messaging.fetch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.eventbridge.messaging.flowcontrol.SemaphoreFlowControl;
import io.camunda.eventbridge.messaging.stream.EventStreamReader;
import io.camunda.eventbridge.messaging.transport.fetch.FetchRequest;
import io.camunda.eventbridge.messaging.watermark.CommittedByteWatermark;
import io.camunda.zeebe.logstreams.storage.LogStorageReader;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.util.IndexScanResult;
import java.time.InstantSource;
import java.util.NoSuchElementException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.agrona.DirectBuffer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Fetch evaluation against an <b>empty</b> partition log. Before the first record is appended, a
 * consumer long-polling from the start (offset 1, the next position to be written) is caught up —
 * it must park in the purgatory (or complete empty when it may not wait), not fail with
 * OFFSET_OUT_OF_RANGE on every poll.
 */
final class EventStreamFetcherTest {

  private static final int PARTITION_ID = 1;
  private static final int MAX_READ_BYTES_PER_FETCH = 4 * 1024 * 1024;

  private final ActorScheduler actorScheduler = ActorScheduler.newActorScheduler().build();
  private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
  private final CommittedByteWatermark highWatermark = new CommittedByteWatermark(PARTITION_ID);
  private final InstantSource clock = InstantSource.system();

  private EventStreamFetcher fetcher;

  @BeforeEach
  void setUp() {
    actorScheduler.start();
    final var purgatory = new FetchPurgatory(PARTITION_ID, highWatermark, clock);
    actorScheduler.submitActor(purgatory).join();

    fetcher =
        new EventStreamFetcher(
            PARTITION_ID,
            () -> new EventStreamReader(new EmptyLogStorageReader()),
            executor,
            purgatory,
            highWatermark,
            new SemaphoreFlowControl(new Semaphore(32)),
            clock,
            MAX_READ_BYTES_PER_FETCH);

    // Mirror the production wiring: parked fetches are re-dispatched on wake-up and on timeout.
    purgatory.setFetchDispatcher(fetcher::dispatchFetch);
    purgatory.setTimeoutHandler(fetcher::dispatchFetch);
  }

  @AfterEach
  void tearDown() throws Exception {
    fetcher.close();
    executor.shutdownNow();
    actorScheduler.close();
  }

  @Test
  void shouldCompleteEmptyWhenFetchingFromStartOfEmptyLogWithoutWaiting() throws Exception {
    // given - an empty log and an immediate fetch from the start
    final var request = fetchRequest(1, 0);

    // when
    final var response = fetcher.handleFetch(request).get(5, TimeUnit.SECONDS);

    // then - the caught-up consumer receives an empty response, not an out-of-range error
    assertThat(response).isNull();
  }

  @Test
  void shouldParkFetchFromStartOfEmptyLogUntilMaxWaitExpires() {
    // given - an empty log and a long-poll fetch from the start
    final var request = fetchRequest(1, 500);

    // when
    final var responseFuture = fetcher.handleFetch(request);

    // then - the fetch parks instead of failing fast with OFFSET_OUT_OF_RANGE
    assertThatThrownBy(() -> responseFuture.get(100, TimeUnit.MILLISECONDS))
        .isInstanceOf(TimeoutException.class);

    // and - completes empty once the long-poll deadline expires without an append
    assertThat(responseFuture)
        .succeedsWithin(5, TimeUnit.SECONDS)
        .satisfies(response -> assertThat(response).isNull());
  }

  @Test
  void shouldRejectOffsetAheadOfCaughtUpPositionOnEmptyLog() {
    // given - an empty log and a fetch beyond the next position to be written
    final var request = fetchRequest(2, 0);

    // when
    final var responseFuture = fetcher.handleFetch(request);

    // then - the consumer is genuinely ahead of the committed log and must be reset
    assertThatThrownBy(() -> responseFuture.get(5, TimeUnit.SECONDS))
        .isInstanceOf(ExecutionException.class)
        .cause()
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("OFFSET_OUT_OF_RANGE");
  }

  private static FetchRequest fetchRequest(final long offset, final long maxWaitMs) {
    return new FetchRequest("consumer", PARTITION_ID, offset, 1024, 1, maxWaitMs);
  }

  /** A reader over a log that contains no batches at all. */
  private static final class EmptyLogStorageReader implements LogStorageReader {

    @Override
    public void seek(final long position) {}

    @Override
    public IndexScanResult scan(final long fromPosition, final int maxBytes) {
      return IndexScanResult.EndOfLog.INSTANCE;
    }

    @Override
    public void close() {}

    @Override
    public boolean hasNext() {
      return false;
    }

    @Override
    public DirectBuffer next() {
      throw new NoSuchElementException();
    }
  }
}
