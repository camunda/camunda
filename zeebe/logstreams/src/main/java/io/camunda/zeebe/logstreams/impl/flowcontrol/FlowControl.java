/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.logstreams.impl.flowcontrol;

import com.google.common.util.concurrent.RateLimiter;
import com.netflix.concurrency.limits.Limit;
import com.netflix.concurrency.limits.Limiter;
import com.netflix.concurrency.limits.Limiter.Listener;
import io.camunda.zeebe.logstreams.impl.LogStreamMetricsImpl;
import io.camunda.zeebe.logstreams.impl.flowcontrol.RequestLimiter.CommandRateLimiterBuilder;
import io.camunda.zeebe.logstreams.impl.log.LogAppendEntryMetadata;
import io.camunda.zeebe.logstreams.log.LogAppendEntry;
import io.camunda.zeebe.logstreams.log.WriteContext;
import io.camunda.zeebe.logstreams.log.WriteContext.Internal;
import io.camunda.zeebe.logstreams.log.WriteContext.ProcessingResult;
import io.camunda.zeebe.logstreams.log.WriteContext.UserCommand;
import io.camunda.zeebe.logstreams.storage.LogStorage.AppendListener;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.scheduler.clock.ActorClock;
import io.camunda.zeebe.util.Either;
import java.time.Duration;
import java.util.List;

/**
 * Maintains a view of in-flight entries as they are being appended, written, committed and finally
 * processed.
 *
 * <p>If enabled, a write rate limiter is used to limit the rate of appends to the log storage.
 * Additionally, a request limiter is used to limit the amount of unprocessed user commands to
 * ensure fast response times.
 *
 * <h3>Thread safety</h3>
 *
 * Access patterns:
 *
 * <ol>
 *   <li>Calls to {@link #tryAcquire(WriteContext, List)} from the sequencer, outside the
 *       sequencer's write lock. Multiple calls can overlap concurrently.
 *   <li>Calls to {@link #registerEntry(long, InFlightEntry)} from the sequencer, under the
 *       sequencer's write lock. Only one call at a time.
 *   <li>Calls to {@link #onAppended(InFlightEntry)} from the sequencer, outside the sequencer's
 *       write lock. Multiple calls can overlap concurrently and may also overlap with {@link
 *       #onWrite(long, long)} and {@link #onCommit(long, long)} callbacks.
 *   <li>Calls to {@link #onWrite(long, long)} from the log storage, serialized through the single
 *       raft thread.
 *   <li>Calls to {@link #onCommit(long, long)} from the log storage, serialized through the single
 *       raft thread.
 *   <li>Calls to {@link #onProcessed(long)} from the stream processor, serialized through the
 *       stream processor actor.
 * </ol>
 *
 * The entry is registered in the {@link #inFlight} ring buffer via {@link #registerEntry(long,
 * InFlightEntry)} inside the sequencer lock, before {@code logStorage.append} is called. This
 * guarantees that the entry is present in the buffer when {@link #onWrite(long, long)} and {@link
 * #onCommit(long, long)} callbacks fire.
 *
 * <p>{@link #onWrite(long, long)} and {@link #onCommit(long, long)} and {@link #onProcessed(long)}
 * can be called in any order.
 *
 * <p>The {@link #inFlight} ring buffer is a fixed-capacity {@link RingBuffer} indexed by position.
 * It is modified by {@link #registerEntry(long, InFlightEntry)} (under the sequencer lock) and read
 * from other methods. The ring buffer uses an {@link
 * java.util.concurrent.atomic.AtomicReferenceArray} internally, providing volatile read/write
 * semantics per slot. This ensures that entries written by the sequencer thread are visible to the
 * raft thread ({@code onWrite}, {@code onCommit}) and the stream processor thread ({@code
 * onProcessed}) without requiring external synchronization.
 *
 * <p>When a new entry is registered and the slot is already occupied (the previous entry was never
 * processed), the displaced entry's {@link InFlightEntry#cleanup()} is called to release resources.
 *
 * <p>The RateMeasurement#observe method only returns true when a new observation value is
 * available. This way we prevent updating the metrics too often with repeated values. We use the
 * RateMeasurements to update the cluster load and the exporting rate metrics.
 */
@SuppressWarnings("UnstableApiUsage")
public final class FlowControl implements AppendListener {

  private final LogStreamMetricsImpl metrics;
  private RateLimit writeRateLimit;
  private Limit requestLimit;
  private Limiter<Intent> processingLimiter;
  private RateLimiter writeRateLimiter;
  private final RateMeasurement exportingRate =
      new RateMeasurement(
          ActorClock::currentTimeMillis, Duration.ofMinutes(5), Duration.ofSeconds(10));
  private final RateMeasurement writeRate =
      new RateMeasurement(
          ActorClock::currentTimeMillis, Duration.ofMinutes(5), Duration.ofSeconds(10));
  private RateLimitThrottle writeRateThrottle;
  private volatile long lastWrittenPosition = -1;
  private volatile long lastExportedPosition;

  private final RingBuffer inFlight;

  public FlowControl(final LogStreamMetricsImpl metrics) {
    this(metrics, StabilizingAIMDLimit.newBuilder().build(), RateLimit.disabled(), 0);
  }

  public FlowControl(
      final LogStreamMetricsImpl metrics, final Limit requestLimit, final RateLimit writeRateLimit) {
    this(metrics, requestLimit, writeRateLimit, 0);
  }

  public FlowControl(
      final LogStreamMetricsImpl metrics,
      final Limit requestLimit,
      final RateLimit writeRateLimit,
      final int inFlightCapacity) {
    this.metrics = metrics;
    inFlight = new RingBuffer(inFlightCapacity);
    setRequestLimit(requestLimit);
    setWriteRateLimit(writeRateLimit);
  }

  /**
   * Tries to acquire a free in-flight spot, applying backpressure as needed.
   *
   * @return An Optional containing a {@link InFlightEntry} if append was accepted, an empty
   *     Optional otherwise.
   */
  // False positive: https://github.com/checkstyle/checkstyle/issues/14891
  @SuppressWarnings("checkstyle:MissingSwitchDefault")
  public Either<Rejection, InFlightEntry> tryAcquire(
      final WriteContext context, final List<LogAppendEntry> batchMetadata) {
    final var result = tryAcquireInternal(context, batchMetadata);
    switch (result) {
      case Either.Left<Rejection, InFlightEntry>(final var reason) ->
          metrics.flowControlRejected(context, batchMetadata.size(), reason);
      case Either.Right<Rejection, InFlightEntry>(final var ignored) -> {
        metrics.flowControlAccepted(context, batchMetadata.size());
      }
    }
    return result;
  }

  private Either<Rejection, InFlightEntry> tryAcquireInternal(
      final WriteContext context, final List<LogAppendEntry> batchMetadata) {
    Listener requestListener = null;
    var alwaysAllowed = false;
    switch (context) {
      case final Internal ignored -> {
        // Internal commands are always accepted for incident response and maintenance.
        alwaysAllowed = true;
      }
      case UserCommand(final var intent) -> {
        alwaysAllowed = WhiteListedCommands.isWhitelisted(intent);
        requestListener = processingLimiter.acquire(intent).orElse(null);
        if (requestListener == null) {
          return Either.left(Rejection.RequestLimitExhausted);
        }
      }
      case ProcessingResult(final var intent) -> {
        alwaysAllowed = WhiteListedCommands.isWhitelisted(intent);
      }
      default -> {}
    }

    if (writeRateLimiter != null
        && (!writeRateLimiter.tryAcquire(batchMetadata.size()) && !alwaysAllowed)) {
      if (requestListener != null) {
        requestListener.onIgnore();
      }
      return Either.left(Rejection.WriteRateLimitExhausted);
    }

    // copy of metadata is required as they are wrapping mutable state:
    // if the entry is written, the callback will be invoked later.
    // in the meantime the RecordMetadata might have been reused already
    final var copiedMetadata = LogAppendEntryMetadata.copyMetadata(batchMetadata);
    return Either.right(new InFlightEntry(metrics, copiedMetadata, requestListener));
  }

  public void registerEntry(final long highestPosition, final InFlightEntry entry) {
    inFlight.put(highestPosition, entry);
  }

  public void onAppended(final InFlightEntry entry) {
    entry.onAppend();
    metrics.increaseInflightAppends();
  }

  @Override
  public void onWrite(final long index, final long highestPosition) {
    lastWrittenPosition = highestPosition;
    updateWriteRateThrottle();
    metrics.setLastWrittenPosition(highestPosition);
    final var inFlightEntry = inFlight.get(highestPosition);
    if (inFlightEntry != null) {
      inFlightEntry.onWrite();
    }
    if (writeRate.observe(highestPosition) && writeRateLimit != null && writeRateLimit.enabled()) {
      metrics.setPartitionLoad(
          Math.min((float) (writeRate.rate() / writeRateLimiter.getRate() * 100L), 100));
    }
  }

  @Override
  public void onCommit(final long index, final long highestPosition) {
    metrics.setLastCommittedPosition(highestPosition);
    metrics.decreaseInflightAppends();
    final var inFlightEntry = inFlight.get(highestPosition);
    if (inFlightEntry != null) {
      inFlightEntry.onCommit();
    }
  }

  public void onProcessed(final long position) {
    final var inFlightEntry = inFlight.get(position);
    if (inFlightEntry != null) {
      inFlightEntry.onProcessed();
      inFlight.remove(inFlightEntry);
    }
  }

  public void onExported(final long position) {
    if (position <= 0) {
      return;
    }
    lastExportedPosition = position;
    if (exportingRate.observe(position)) {
      metrics.setExportingRate(exportingRate.rate());
    }
    updateWriteRateThrottle();
  }

  private void updateWriteRateThrottle() {
    if (writeRateThrottle != null && lastWrittenPosition != -1 && lastExportedPosition != -1) {
      writeRateThrottle.update(
          ActorClock.currentTimeMillis(), lastWrittenPosition - lastExportedPosition);
    }
  }

  public Limit getRequestLimit() {
    return requestLimit;
  }

  public void setRequestLimit(final Limit requestLimit) {
    this.requestLimit = requestLimit;
    processingLimiter =
        requestLimit != null
            ? new CommandRateLimiterBuilder().limit(requestLimit).build(metrics)
            : new NoopLimiter<>();
  }

  public RateLimit getWriteRateLimit() {
    return writeRateLimit;
  }

  public void setWriteRateLimit(final RateLimit writeRateLimit) {
    this.writeRateLimit = writeRateLimit;
    writeRateLimiter = writeRateLimit == null ? null : writeRateLimit.limiter();
    writeRateThrottle =
        new RateLimitThrottle(metrics, writeRateLimit, writeRateLimiter, exportingRate);
    if (writeRateLimit == null || !writeRateLimit.enabled()) {
      // if the write rate limit is disabled, we need to clear the previous values.
      metrics.setPartitionLoad(-1);
    }
  }

  public enum Rejection {
    WriteRateLimitExhausted,
    RequestLimitExhausted
  }
}
