/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.fetch;

import io.camunda.eventbridge.broker.fetch.EventStreamFetcher.ReadOutcome.EndOfLog;
import io.camunda.eventbridge.broker.fetch.EventStreamFetcher.ReadOutcome.OutOfRange;
import io.camunda.eventbridge.broker.fetch.EventStreamFetcher.ReadOutcome.Success;
import io.camunda.eventbridge.broker.fetch.EventStreamFetcher.ReadOutcome.Timeout;
import io.camunda.eventbridge.broker.flowcontrol.FlowControl;
import io.camunda.eventbridge.broker.logstreams.EventStreamReader;
import io.camunda.eventbridge.broker.transport.fetch.FetchRequest;
import io.camunda.eventbridge.broker.transport.fetch.FetchResponse;
import io.camunda.eventbridge.broker.watermark.HighWatermark;
import io.camunda.zeebe.util.IndexScanResult;
import java.time.InstantSource;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Per-partition fetch service coordinating stateless reads.
 *
 * <p><b>Performance Design:</b> Uses Virtual Threads to absorb memory-mapped I/O stalls. Employs
 * extensive pre-flight checks to avoid touching the disk or allocating buffers for requests that
 * can be parked or discarded immediately.
 */
public final class EventStreamFetcher implements AutoCloseable {

  private final int partitionId;
  private final Supplier<EventStreamReader> readerFactory;
  private final ExecutorService executor;
  private final FetchPurgatory purgatory;
  private final HighWatermark highWatermark;
  private final FlowControl flowControl;
  private final InstantSource clock;
  private final int maxReadBytesPerFetch;

  public EventStreamFetcher(
      final int partitionId,
      final Supplier<EventStreamReader> readerFactory,
      final ExecutorService executor,
      final FetchPurgatory purgatory,
      final HighWatermark highWatermark,
      final FlowControl flowControl,
      final InstantSource clock,
      final int maxReadBytesPerFetch) {
    this.partitionId = partitionId;
    this.readerFactory = readerFactory;
    this.executor = executor;
    this.purgatory = purgatory;
    this.highWatermark = highWatermark;
    this.flowControl = flowControl;
    this.clock = clock;
    this.maxReadBytesPerFetch = maxReadBytesPerFetch;
  }

  public int partitionId() {
    return partitionId;
  }

  @Override
  public void close() {
    purgatory.close();
  }

  public CompletableFuture<FetchResponse> handleFetch(final FetchRequest request) {
    final var responseFuture = new CompletableFuture<FetchResponse>();
    final var deadlineMs = clock.millis() + request.maxWaitMs();
    submitSafe(new FetchTask(request, responseFuture, deadlineMs));
    return responseFuture;
  }

  public void dispatchFetch(final FetchTask task) {
    submitSafe(task);
  }

  /**
   * Centralized infrastructure wrapper. Handles Virtual Thread submission, interrupt restoration,
   * and exception propagation.
   */
  private void submitSafe(final FetchTask task) {
    executor.submit(
        () -> {
          try {
            if (!task.isCancelled()) {
              doEvaluateTask(task);
            }
          } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            task.responseFuture().completeExceptionally(e);
          } catch (final Exception e) {
            task.responseFuture().completeExceptionally(e);
          }
        });
  }

  /** Pure domain logic for evaluating a fetch task against the log state. */
  private void doEvaluateTask(final FetchTask task) throws Exception {
    final var preReadWatermark = highWatermark.get();
    final int actualMinBytes = Math.min(task.minBytes(), maxReadBytesPerFetch);

    final var outcome = safeReadBatches(task);

    switch (outcome) {
      case final Timeout timeout ->
          task.responseFuture().complete(null); // Or FetchResponse.empty()

      case final OutOfRange outOfRange ->
          // Fast-Fail: Truncated or uncommitted Raft data. Trigger consumer offset reset.
          task.responseFuture()
              .completeExceptionally(
                  new IllegalArgumentException("OFFSET_OUT_OF_RANGE: " + task.offset()));

      case final EndOfLog endOfLog -> {
        // Tip of the log reached. Park to await new appends — unless the deadline has already
        // passed (e.g. an immediate maxWaitMs=0 fetch, or a long-poll that just timed out and was
        // re-dispatched), in which case complete empty so the request does not spin.
        if (clock.millis() >= task.deadlineMs()) {
          task.responseFuture().complete(null);
        } else {
          purgatory.park(task, actualMinBytes, preReadWatermark);
        }
      }

      case final Success success -> {
        final FetchResponse response = success.response();
        final int readBytes = response.dataLength();

        if (readBytes >= actualMinBytes || clock.millis() >= task.deadlineMs()) {
          // Requirement met OR time expired. Complete and hand off the lease to Netty!
          task.responseFuture().complete(response);

        } else {
          // MIN-BYTES NOT MET: We have partial data, but time remains.
          // CRITICAL: We must release the lease before discarding this read to park,
          // otherwise this segment will be permanently pinned in memory.
          response.releaseLease();
          purgatory.park(task, actualMinBytes - readBytes, preReadWatermark);
        }
      }
    }
  }

  private ReadOutcome safeReadBatches(final FetchTask task) {
    final int maxBytes = Math.min(task.maxBytes(), maxReadBytesPerFetch);

    // Always attempt at least one read so an immediate fetch (maxWaitMs=0, hence deadline=now)
    // still returns data that is already committed. Waiting for more data is handled afterwards by
    // parking in the purgatory on EndOfLog / min-bytes-not-met. Acquiring a flow-control permit
    // with a zero (or already-elapsed) deadline still succeeds immediately when a permit is free.
    final long acquireMs = Math.max(0, task.deadlineMs() - clock.millis());

    if (flowControl.tryAcquire(1, acquireMs, TimeUnit.MILLISECONDS)) {
      try {
        return readBatches(task.offset(), maxBytes);
      } finally {
        flowControl.release(1);
      }
    }

    return ReadOutcome.Timeout.INSTANCE;
  }

  private ReadOutcome readBatches(final long position, final int limit) {
    try (final var reader = readerFactory.get()) {
      // Assuming reader.scanIndex passes the call through to our SegmentedJournalReader
      final IndexScanResult result = reader.scan(position, limit);

      return switch (result) {
        case final IndexScanResult.Truncated t -> OutOfRange.INSTANCE;
        case final IndexScanResult.FutureOffset f -> OutOfRange.INSTANCE;
        case final IndexScanResult.EndOfLog e -> EndOfLog.INSTANCE;

        case final IndexScanResult.Success success -> {
          final var entries = success.entries();

          int dataLength = 0;
          for (final var entry : entries) {
            dataLength += entry.length();
          }

          final var firstPosition = entries.getFirst().lowestAsqn();
          final var highestPosition = entries.getLast().highestAsqn();

          // FetchResponse now encapsulates the result (which holds the lease)
          final var response =
              new FetchResponse(
                  firstPosition,
                  highestPosition,
                  highWatermark.get().commitPosition(),
                  success,
                  dataLength);
          yield new Success(response);
        }
      };
    }
  }

  sealed interface ReadOutcome {
    record Success(FetchResponse response) implements ReadOutcome {}

    enum Timeout implements ReadOutcome {
      INSTANCE
    }

    enum OutOfRange implements ReadOutcome {
      INSTANCE
    }

    enum EndOfLog implements ReadOutcome {
      INSTANCE
    }
  }
}
