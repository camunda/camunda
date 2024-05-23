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
import com.netflix.concurrency.limits.limiter.SimpleLimiter;
import io.camunda.zeebe.logstreams.impl.LogStreamMetrics;
import io.camunda.zeebe.logstreams.impl.flowcontrol.FlowControl.Rejection.AppendLimitExhausted;
import io.camunda.zeebe.logstreams.impl.flowcontrol.FlowControl.Rejection.ExportLimitExhausted;
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

  private final LogStreamMetrics metrics;
  private final Limit appendLimit;
  private final Limit requestLimit;
  private final Limit exportLimit;
  private final Limiter<Void> appendLimiter;
  private final Limiter<Intent> requestLimiter;
  private final Limiter<Void> exportLimiter;

  private final Map<Long, InFlightEntry.Unwritten> unwritten = new ConcurrentHashMap<>();
  private final Map<Long, InFlightEntry.Uncommitted> uncommitted = new ConcurrentHashMap<>();
  private final Map<Long, InFlightEntry.Unprocessed> unprocessed = new ConcurrentHashMap<>();
  private final Map<Long, InFlightEntry.Unexported> unexported = new ConcurrentHashMap<>();

  public FlowControl(final LogStreamMetrics metrics) {
    this(metrics, VegasLimit.newDefault(), StabilizingAIMDLimit.newBuilder().build(), null);
  }

  public FlowControl(
      final LogStreamMetrics metrics,
      final Limit appendLimit,
      final Limit requestLimit,
      final Limit exportLimit) {
    this.metrics = metrics;
    this.appendLimit = appendLimit;
    this.requestLimit = requestLimit;
    this.exportLimit = exportLimit;
    appendLimiter =
        appendLimit != null
            ? AppendLimiter.builder().limit(appendLimit).metrics(metrics).build()
            : new NoopLimiter<>();
    requestLimiter =
        requestLimit != null
            ? new CommandRateLimiterBuilder().limit(requestLimit).build(metrics)
            : new NoopLimiter<>();
    exportLimiter =
        exportLimit != null
            ? SimpleLimiter.newBuilder().limit(exportLimit).build()
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

    metrics.received(context);

    final var appendListener = appendLimiter.acquire(null).orElse(null);
    if (appendListener == null) {
      metrics.dropped(context);
      return Either.left(new AppendLimitExhausted());
    }

    final var exportListener = exportLimiter.acquire(null).orElse(null);
    if (exportListener == null) {
      metrics.dropped(context);
      appendListener.onDropped();
      return Either.left(new ExportLimitExhausted());
    }

    if (!(context instanceof UserCommand(final var intent))) {
      return Either.right(
          new PendingAppend(metrics, batchMetadata, appendListener, null, exportListener));
    }

    final var requestListener = requestLimiter.acquire(intent).orElse(null);
    if (requestListener == null) {
      metrics.dropped(context);
      appendListener.onDropped();
      exportListener.onDropped();
      return Either.left(new RequestLimitExhausted());
    }

    return Either.right(
        new PendingAppend(metrics, batchMetadata, appendListener, requestListener, exportListener));
  }

  public void onAppend(final PendingAppend nowAppended, final long highestPosition) {
    unwritten.put(highestPosition, nowAppended.unwritten());
    uncommitted.put(highestPosition, nowAppended.uncommitted());
    nowAppended.unprocessed().ifPresent(inFlight -> unprocessed.put(highestPosition, inFlight));
    unexported.put(highestPosition, nowAppended.unexported());
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

  public void onExported(final long position) {
    final var exported = unexported.remove(position);
    if (exported != null) {
      exported.finish();
    }
    cleanupUnexported(position);
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

  private void cleanupUnexported(final long highestPosition) {
    final var size = unexported.size();
    final var limit = exportLimit != null ? 2 * exportLimit.getLimit() : 2048;
    if (size > 2 * limit) {
      final var removedAny = unexported.keySet().removeIf(position -> position <= highestPosition);
      if (removedAny) {
        LOG.warn(
            "Removed {} unexported entries that were not acknowledged", size - unexported.size());
      }
    }
  }

  public sealed interface Rejection {
    record AppendLimitExhausted() implements Rejection {}

    record RequestLimitExhausted() implements Rejection {}

    record ExportLimitExhausted() implements Rejection {}
  }
}
