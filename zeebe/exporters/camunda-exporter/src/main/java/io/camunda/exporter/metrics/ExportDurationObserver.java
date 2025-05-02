/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.metrics;

import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.protocol.record.Record;
import java.time.InstantSource;
import org.agrona.collections.Long2LongHashMap;
import org.agrona.collections.LongArrayList;

/**
 * Utility class to measure the time it takes between writing a record and flushing it to ES/OS.
 *
 * <p>The time is computed as the difference between the flush time (which is assumed to be when
 * {@link #observeDurations()} is called) and the record's {@link Record#getTimestamp()}.
 *
 * <p>The flush time is obtained using the provided {@link Context#clock()}, which is the stream
 * processing clock that can be modified. That way, we have semi-accurate time intervals even if it
 * gets modified.
 *
 * <p>NOTE: semi-accurate because since the exporter is not sequenced with the processor, on replay,
 * the clock modification may take place at a different "wall" time, so the observed duration may
 * not always be deterministic.
 */
public final class ExportDurationObserver {
  private final Long2LongHashMap writeTimestamps = new Long2LongHashMap(-1);

  private final CamundaExporterMetrics metrics;
  private final InstantSource streamClock;

  public ExportDurationObserver(
      final CamundaExporterMetrics metrics, final InstantSource streamClock) {
    this.metrics = metrics;
    this.streamClock = streamClock;
  }

  /**
   * Caches a single record's write timestamp; this method is idempotent and can be called multiple
   * times without any effect until {@link #observeDurations()} has been called.
   */
  public void cacheRecordTimestamp(final long key, final long timestamp) {
    writeTimestamps.put(key, timestamp);
  }

  /**
   * Computes the time between now and each record's write timestamp, and records it via a metric
   * timer.
   */
  public void observeDurations() {
    final var flushTime = streamClock.instant().toEpochMilli();
    final var timestamps = new LongArrayList(writeTimestamps.size(), -1);
    timestamps.addAll(writeTimestamps.values());

    // ensure we don't observe the same record write timestamp twice
    writeTimestamps.clear();
    timestamps.stream()
        .map(timestamp -> flushTime - timestamp)
        .forEach(metrics::observeRecordExportDurationMillis);
  }
}
