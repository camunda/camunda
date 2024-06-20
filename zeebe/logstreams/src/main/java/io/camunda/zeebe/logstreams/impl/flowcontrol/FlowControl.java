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
import io.camunda.zeebe.logstreams.impl.LogStreamMetrics;
import io.camunda.zeebe.logstreams.impl.flowcontrol.RequestLimiter.CommandRateLimiterBuilder;
import io.camunda.zeebe.logstreams.impl.log.LogAppendEntryMetadata;
import io.camunda.zeebe.logstreams.log.WriteContext;
import io.camunda.zeebe.logstreams.log.WriteContext.UserCommand;
import io.camunda.zeebe.logstreams.storage.LogStorage.AppendListener;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.util.Either;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;

@SuppressWarnings("UnstableApiUsage")
public final class FlowControl implements AppendListener {
  private final LogStreamMetrics metrics;
  private final Limiter<Intent> processingLimiter;
  private final RateLimiter writeRateLimiter;
  private volatile long lastProcessedPosition = -1;

  private final NavigableMap<Long, InFlightEntry> inFlight = new TreeMap<>();

  public FlowControl(final LogStreamMetrics metrics) {
    this(metrics, StabilizingAIMDLimit.newBuilder().build(), RateLimit.disabled());
  }

  public FlowControl(
      final LogStreamMetrics metrics, final Limit requestLimit, final RateLimit writeRateLimit) {
    this.metrics = metrics;
    processingLimiter =
        requestLimit != null
            ? new CommandRateLimiterBuilder().limit(requestLimit).build(metrics)
            : new NoopLimiter<>();
    writeRateLimiter = writeRateLimit == null ? null : writeRateLimit.limiter();
  }

  /**
   * Tries to acquire a free in-flight spot, applying backpressure as needed.
   *
   * @return An Optional containing a {@link InFlightEntry} if append was accepted, an empty
   *     Optional otherwise.
   */
  public Either<Rejection, InFlightEntry> tryAcquire(
      final WriteContext context, final List<LogAppendEntryMetadata> batchMetadata) {
    final var result = tryAcquireInternal(context, batchMetadata);
    switch (result) {
      case Either.Left<Rejection, InFlightEntry>(final var reason) ->
          metrics.flowControlRejected(context, reason);
      case Either.Right<Rejection, InFlightEntry>(final var ignored) ->
          metrics.flowControlAccepted(context);
    }
    return result;
  }

  private Either<Rejection, InFlightEntry> tryAcquireInternal(
      final WriteContext context, final List<LogAppendEntryMetadata> batchMetadata) {
    final Listener requestListener;
    if (context instanceof UserCommand(final var intent)) {
      requestListener = processingLimiter.acquire(intent).orElse(null);
      if (requestListener == null) {
        return Either.left(Rejection.RequestLimitExhausted);
      }
    } else {
      requestListener = null;
    }

    if (writeRateLimiter != null && !writeRateLimiter.tryAcquire()) {
      if (requestListener != null) {
        requestListener.onIgnore();
      }
      return Either.left(Rejection.WriteRateLimitExhausted);
    }

    return Either.right(new InFlightEntry(metrics, batchMetadata, requestListener));
  }

  public void onAppend(final InFlightEntry entry, final long highestPosition) {
    entry.onAppend();
    metrics.increaseInflightAppends();
    final var clearable = inFlight.headMap(lastProcessedPosition, true);
    clearable.forEach((position, inFlightEntry) -> inFlightEntry.cleanup());
    clearable.clear();
    inFlight.put(highestPosition, entry);
  }

  @Override
  public void onWrite(final long index, final long highestPosition) {
    metrics.setLastWrittenPosition(highestPosition);
    final var inFlightEntry = inFlight.get(highestPosition);
    if (inFlightEntry != null) {
      inFlightEntry.onWrite();
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
    }
    lastProcessedPosition = position;
  }

  public enum Rejection {
    WriteRateLimitExhausted,
    RequestLimitExhausted
  }
}
