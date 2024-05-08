/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.logstreams.impl.flowcontrol;

import com.netflix.concurrency.limits.Limit;
import com.netflix.concurrency.limits.Limiter;
import com.netflix.concurrency.limits.limit.VegasLimit;
import io.camunda.zeebe.logstreams.impl.LogStreamMetrics;
import io.camunda.zeebe.logstreams.impl.flowcontrol.CommandRateLimiter.CommandRateLimiterBuilder;
import io.camunda.zeebe.logstreams.impl.flowcontrol.FlowControl.Rejection.AppendLimitExhausted;
import io.camunda.zeebe.logstreams.impl.log.LogAppendEntryMetadata;
import io.camunda.zeebe.logstreams.log.WriteContext;
import io.camunda.zeebe.logstreams.storage.LogStorage.AppendListener;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.util.Either;
import java.util.List;
import java.util.concurrent.ConcurrentSkipListMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FlowControl implements AppendListener {
  private static final Logger LOG = LoggerFactory.getLogger(FlowControl.class);

  private final Limiter<Void> appendLimiter;
  private final RequestLimiter<Intent> requestLimiter;
  private final LogStreamMetrics metrics;
  private final ConcurrentSkipListMap<Long, InFlightEntry> inFlightEntries =
      new ConcurrentSkipListMap<>();

  public FlowControl(final LogStreamMetrics metrics) {
    this(metrics, VegasLimit.newDefault(), StabilizingAIMDLimit.newBuilder().build());
  }

  public FlowControl(
      final LogStreamMetrics metrics, final Limit appendLimit, final Limit requestLimit) {
    this.metrics = metrics;
    appendLimiter =
        appendLimit != null
            ? AppendLimiter.builder().limit(appendLimit).metrics(metrics).build()
            : new NoopLimiter();
    requestLimiter =
        requestLimit != null
            ? new CommandRateLimiterBuilder().limit(requestLimit).build(metrics)
            : new NoopRequestLimiter<>();
  }

  /**
   * Tries to acquire a free in-flight spot, applying backpressure as needed.
   *
   * @return An Optional containing a {@link InFlightEntry} if append was accepted, an empty
   *     Optional otherwise.
   */
  public Either<Rejection, InFlightEntry> tryAcquire(
      final WriteContext context, final List<LogAppendEntryMetadata> batchMetadata) {
    final var appendListener = appendLimiter.acquire(null).orElse(null);
    if (appendListener == null) {
      metrics.increaseDeferredAppends();
      LOG.trace("Skipping append due to backpressure");
      return Either.left(new AppendLimitExhausted());
    }

    return Either.right(new InFlightEntry(batchMetadata, appendListener, metrics));
  }

  public void onAppend(final InFlightEntry inFlightEntry, final long highestPosition) {
    inFlightEntries.put(highestPosition, inFlightEntry);
    inFlightEntry.onAppend(highestPosition);
  }

  @Override
  public void onWrite(final long index, final long highestPosition) {
    inFlightEntries.get(highestPosition).onWrite();
  }

  @Override
  public void onCommit(final long index, final long highestPosition) {
    inFlightEntries.remove(highestPosition).onCommit();
  }

  public sealed interface Rejection {
    record AppendLimitExhausted() implements Rejection {}
  }
}
