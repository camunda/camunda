/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.logstreams.impl.flowcontrol;

import com.netflix.concurrency.limits.Limiter.Listener;
import io.camunda.zeebe.logstreams.impl.LogStreamMetrics;
import io.camunda.zeebe.logstreams.impl.log.LogAppendEntryMetadata;
import io.prometheus.client.Histogram;
import java.util.List;
import java.util.Optional;

/**
 * This contains different classes describing log entries that are still in flight. Each class
 * represents a different state of the log entry. They are used to update metrics and notify the
 * limiters.
 */
public final class InFlightEntry {
  private InFlightEntry() {}

  /**
   * Represents a log entry that is not yet appended. Once it is appended, it can produce new
   * objects that track the next stages such as {@link Unwritten}, {@link Uncommitted} and {@link
   * Unprocessed}.
   */
  public static final class PendingAppend {
    private final LogStreamMetrics metrics;
    private final List<LogAppendEntryMetadata> entryMetadata;
    private final Listener appendListener;
    private final Listener requestListener;

    PendingAppend(
        final LogStreamMetrics metrics,
        final List<LogAppendEntryMetadata> entryMetadata,
        final Listener appendListener,
        final Listener requestListener) {
      this.metrics = metrics;
      this.entryMetadata = entryMetadata;
      this.appendListener = appendListener;
      this.requestListener = requestListener;
    }

    public Unwritten unwritten() {
      return new Unwritten(metrics, entryMetadata);
    }

    public Uncommitted uncommitted() {
      return new Uncommitted(metrics, appendListener);
    }

    /**
     * If the log entry is for a user command (i.e. a "request"), returns an object to track the
     * entry until it is processed. Otherwise, it returns empty.
     */
    public Optional<Unprocessed> unprocessed() {
      return Optional.ofNullable(requestListener)
          .map(listener -> new Unprocessed(metrics, listener));
    }
  }

  /** Log entry that has not been written yet. */
  public static final class Unwritten {
    private final LogStreamMetrics metrics;
    private final List<LogAppendEntryMetadata> entryMetadata;
    private final Histogram.Timer writeTimer;

    Unwritten(final LogStreamMetrics metrics, final List<LogAppendEntryMetadata> entryMetadata) {
      this.entryMetadata = entryMetadata;
      writeTimer = metrics.startWriteTimer();
      this.metrics = metrics;
    }

    public void finish(final long position) {
      writeTimer.close();
      entryMetadata.forEach(
          metadata ->
              metrics.recordAppendedEntry(
                  1, metadata.recordType(), metadata.valueType(), metadata.intent()));
      metrics.setLastWrittenPosition(position);
    }
  }

  /** Log entry that has not been committed yet. */
  public static final class Uncommitted {
    private final LogStreamMetrics metrics;
    private final Histogram.Timer commitTimer;
    private final Listener appendListener;

    Uncommitted(final LogStreamMetrics metrics, final Listener appendListener) {
      this.metrics = metrics;
      commitTimer = metrics.startCommitTimer();
      this.appendListener = appendListener;
      metrics.increaseInflightAppends();
    }

    public void finish(final long position) {
      metrics.decreaseInflightAppends();
      metrics.setLastCommittedPosition(position);
      commitTimer.close();
      appendListener.onSuccess();
    }
  }

  /** Log entry for a request that isn't processed yet. */
  public static final class Unprocessed {
    private final LogStreamMetrics metrics;
    private final Listener requestListener;

    Unprocessed(final LogStreamMetrics metrics, final Listener requestListener) {
      this.metrics = metrics;
      this.requestListener = requestListener;
      metrics.increaseInflightRequests();
    }

    public void finish() {
      requestListener.onSuccess();
      metrics.decreaseInflightRequests();
    }
  }
}
