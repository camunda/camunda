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
import io.camunda.zeebe.logstreams.impl.flowcontrol.InFlightEntry.PendingAppend;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FlowControl implements AppendListener {
  private static final Logger LOG = LoggerFactory.getLogger(FlowControl.class);

  private final Limiter<Void> appendLimiter;
  private final Limiter<Intent> requestLimiter;
  private final LogStreamMetrics metrics;
  private final Map<Long, InFlightEntry.Unwritten> unwritten = new ConcurrentHashMap<>();
  private final Map<Long, InFlightEntry.Uncommitted> uncommitted = new ConcurrentHashMap<>();
  private final Map<Long, InFlightEntry.Unprocessed> unprocessed = new ConcurrentHashMap<>();
  private final Limit appendLimit;
  private final Limit requestLimit;

  public FlowControl(final LogStreamMetrics metrics) {
    this(metrics, VegasLimit.newDefault(), StabilizingAIMDLimit.newBuilder().build());
  }

  public FlowControl(
      final LogStreamMetrics metrics, final Limit appendLimit, final Limit requestLimit) {
    this.metrics = metrics;
    this.appendLimit = appendLimit;
    this.requestLimit = requestLimit;
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
  public Either<Rejection, InFlightEntry.PendingAppend> tryAcquire(
      final WriteContext context, final List<LogAppendEntryMetadata> batchMetadata) {
    metrics.increaseTriedAppends();
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

    return Either.right(new PendingAppend(metrics, batchMetadata, appendListener, requestListener));
  }

  public void onAppend(final PendingAppend nowAppended, final long highestPosition) {
    unwritten.put(highestPosition, nowAppended.unwritten());
    uncommitted.put(highestPosition, nowAppended.uncommitted());
    nowAppended.unprocessed().ifPresent(inFlight -> unprocessed.put(highestPosition, inFlight));
  }

  @Override
  public void onWrite(final long index, final long highestPosition) {
    final var written = unwritten.remove(highestPosition);
    if (written != null) {
      written.finish(highestPosition);
    }
    cleanupUnwritten(highestPosition);
  }

  @Override
  public void onCommit(final long index, final long highestPosition) {
    final var committed = uncommitted.remove(highestPosition);
    if (committed != null) {
      committed.finish(highestPosition);
    }
    cleanupUncommitted(highestPosition);
  }

  public void onProcessed(final long position) {
    final var processed = unprocessed.remove(position);
    if (processed != null) {
      processed.finish();
    }
    cleanupUnprocessed(position);
  }

  private void cleanupUncommitted(final long highestPosition) {
    final var size = uncommitted.size();
    final var limit = appendLimit != null ? 2 * appendLimit.getLimit() : 2048;
    if (size > 2 * limit) {
      final var removedAny = uncommitted.keySet().removeIf(position -> position <= highestPosition);
      if (removedAny) {
        LOG.warn(
            "Removed {} uncommitted entries that were not acknowledged", size - uncommitted.size());
      }
    }
  }

  private void cleanupUnwritten(final long highestPosition) {
    final var size = unwritten.size();
    final var limit = appendLimit != null ? 2 * appendLimit.getLimit() : 2048;
    if (size > limit) {
      final var removedAny = unwritten.keySet().removeIf(position -> position <= highestPosition);
      if (removedAny) {
        LOG.warn(
            "Removed {} unwritten entries that were not acknowledged", size - unwritten.size());
      }
    }
  }

  private void cleanupUnprocessed(final long highestPosition) {
    final var size = unprocessed.size();
    final var limit = requestLimit != null ? 2 * requestLimit.getLimit() : 2048;
    if (size > 2 * limit) {
      final var removedAny = unprocessed.keySet().removeIf(position -> position <= highestPosition);
      if (removedAny) {
        LOG.warn(
            "Removed {} unprocessed entries that were not acknowledged", size - unprocessed.size());
      }
    }
  }

  public sealed interface Rejection {
    record AppendLimitExhausted() implements Rejection {}

    record RequestLimitExhausted() implements Rejection {}
  }
}
