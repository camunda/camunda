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
import io.camunda.zeebe.logstreams.impl.flowcontrol.FlowControl.Rejection.RequestLimitExhausted;
import io.camunda.zeebe.logstreams.impl.flowcontrol.FlowControl.Rejection.WriteRateLimitExhausted;
import io.camunda.zeebe.logstreams.impl.flowcontrol.RequestLimiter.CommandRateLimiterBuilder;
import io.camunda.zeebe.logstreams.impl.log.LogAppendEntryMetadata;
import io.camunda.zeebe.logstreams.log.WriteContext;
import io.camunda.zeebe.logstreams.log.WriteContext.UserCommand;
import io.camunda.zeebe.logstreams.storage.LogStorage.AppendListener;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.util.Either;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("UnstableApiUsage")
public final class FlowControl implements AppendListener {
  private final LogStreamMetrics metrics;
  private final Limiter<Intent> processingLimiter;
  private final RateLimiter writeRateLimiter;

  private final Map<Long, Listener> unprocessed = new ConcurrentHashMap<>();

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
    writeRateLimiter = writeRateLimit.limiter();
  }

  /**
   * Tries to acquire a free in-flight spot, applying backpressure as needed.
   *
   * @return An Optional containing a {@link InFlightEntry} if append was accepted, an empty
   *     Optional otherwise.
   */
  public Either<Rejection, InFlightEntry> tryAcquire(
      final WriteContext context, final List<LogAppendEntryMetadata> batchMetadata) {
    metrics.received(context);

    final Listener requestListener;
    if (context instanceof UserCommand(final var intent)) {
      requestListener = processingLimiter.acquire(intent).orElse(null);
      if (requestListener == null) {
        metrics.dropped(context);
        return Either.left(new RequestLimitExhausted());
      }
    } else {
      requestListener = null;
    }

    if (writeRateLimiter != null && !writeRateLimiter.tryAcquire()) {
      if (requestListener != null) {
        requestListener.onIgnore();
      }
      metrics.dropped(context);
      return Either.left(new WriteRateLimitExhausted());
    }

    return Either.right(new InFlightEntry(batchMetadata, requestListener));
  }

  public void onAppend(final InFlightEntry entry, final long highestPosition) {
    if (entry.requestListener() == null) {
      return;
    }
    unprocessed.put(highestPosition, entry.requestListener());
  }

  public void onProcessed(final long position) {
    final var processed = unprocessed.remove(position);
    if (processed != null) {
      processed.onSuccess();
    }
  }

  public sealed interface Rejection {
    record WriteRateLimitExhausted() implements Rejection {}

    record RequestLimitExhausted() implements Rejection {}
  }
}
