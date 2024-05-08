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
import com.netflix.concurrency.limits.Limiter.Listener;
import com.netflix.concurrency.limits.limit.VegasLimit;
import io.camunda.zeebe.logstreams.impl.LogStreamMetrics;
import io.camunda.zeebe.logstreams.impl.flowcontrol.FlowControl.Rejection.AppendLimitExhausted;
import io.camunda.zeebe.logstreams.impl.flowcontrol.FlowControl.Rejection.RequestLimitExhausted;
import io.camunda.zeebe.logstreams.impl.flowcontrol.RequestLimiter.CommandRateLimiterBuilder;
import io.camunda.zeebe.logstreams.impl.log.LogAppendEntryMetadata;
import io.camunda.zeebe.logstreams.log.WriteContext;
import io.camunda.zeebe.logstreams.log.WriteContext.UserCommand;
import io.camunda.zeebe.logstreams.storage.LogStorage.AppendListener;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.util.Either;
import java.util.List;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FlowControl implements AppendListener {
  private static final Logger LOG = LoggerFactory.getLogger(FlowControl.class);

  private final Limiter<Void> appendLimiter;
  private final Limiter<Intent> requestLimiter;
  private final LogStreamMetrics metrics;
  private final NavigableMap<Long, InFlightEntry> unwritten = new ConcurrentSkipListMap<>();
  private final NavigableMap<Long, InFlightEntry> uncommitted = new ConcurrentSkipListMap<>();
  private final NavigableMap<Long, InFlightEntry> unprocessed = new ConcurrentSkipListMap<>();

  public FlowControl(final LogStreamMetrics metrics) {
    this(metrics, VegasLimit.newDefault(), StabilizingAIMDLimit.newBuilder().build());
  }

  public FlowControl(
      final LogStreamMetrics metrics, final Limit appendLimit, final Limit requestLimit) {
    this.metrics = metrics;
    appendLimiter =
        appendLimit != null
            ? AppendLimiter.builder().limit(appendLimit).metrics(metrics).build()
            : new NoopLimiter<>();
    requestLimiter =
        requestLimit != null
            ? new CommandRateLimiterBuilder().limit(requestLimit).build(metrics)
            : new NoopLimiter<>();
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

    final Listener requestListener;
    if (context instanceof UserCommand(final var intent)) {
      metrics.receivedRequest();
      requestListener = requestLimiter.acquire(intent).orElse(null);
      if (requestListener == null) {
        metrics.droppedRequest();
        appendListener.onDropped();
        return Either.left(new RequestLimitExhausted());
      }
    } else {
      requestListener = null;
    }

    return Either.right(new InFlightEntry(batchMetadata, appendListener, requestListener, metrics));
  }

  public void onAppend(final InFlightEntry inFlightEntry, final long highestPosition) {
    unwritten.put(highestPosition, inFlightEntry);
    uncommitted.put(highestPosition, inFlightEntry);
    unprocessed.put(highestPosition, inFlightEntry);
    inFlightEntry.onAppend(highestPosition);
  }

  @Override
  public void onWrite(final long index, final long highestPosition) {
    final var written = unwritten.headMap(highestPosition, true);
    written.forEach((key, value) -> value.onWrite());
    written.clear();
  }

  @Override
  public void onCommit(final long index, final long highestPosition) {
    final var committed = uncommitted.headMap(highestPosition, true);
    committed.forEach((key, value) -> value.onCommit());
    committed.clear();
  }

  public void onProcessed(final long position) {
    final var processed = unprocessed.headMap(position, true);
    processed.forEach((key, value) -> value.onProcessed());
    processed.clear();
  }

  public sealed interface Rejection {
    record AppendLimitExhausted() implements Rejection {}

    record RequestLimitExhausted() implements Rejection {}
  }
}
