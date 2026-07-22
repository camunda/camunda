/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.sink.pipeline;

import io.camunda.analytics.lake.sink.BatchEncoder;
import io.camunda.analytics.lake.sink.DataFileResult;
import io.camunda.analytics.lake.sink.SortedRun;
import io.camunda.analytics.lake.sink.TableSchema;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal per-flush-thread file router: keeps at most {@value #MAX_OPEN_DAYS} open {@link
 * BatchEncoder}s (one per family day) plus one spill file for mixed/overflow days, appending each
 * {@link SortedRun.DayRange} to the right one. Flush thread only, one instance per {@link
 * FlushLoop}, reused across every window it closes.
 *
 * <p>This deliberately duplicates the shape of the encoder stream's own day router so the control
 * path here does not depend on it (see the module's parallel-build constraint). Candidate for
 * unification with that class at integration time — nothing about this class is control-path
 * specific beyond living in this package.
 *
 * <p>Byte accounting is an estimate, not a measurement: {@link BatchEncoder} exposes no size probe,
 * so {@link #estimatedBytes()} sums {@code rows appended * BYTES_PER_ROW_ESTIMATE}. That is only
 * ever used to decide the (purely-timing) SIZE_CAP trigger, so an estimate is exactly as
 * appropriate as a measurement would be — see {@link SinkConfig#fileTargetBytes()}.
 */
final class FileWindow {

  private static final int MAX_OPEN_DAYS = 3;
  private static final long BYTES_PER_ROW_ESTIMATE = 200L;

  private final BatchEncoder.Factory factory;
  private final TableSchema schema;
  private final Map<Long, BatchEncoder> openByDay = new LinkedHashMap<>();
  private BatchEncoder spill;
  private long estimatedBytes;

  FileWindow(final BatchEncoder.Factory factory, final TableSchema schema) {
    this.factory = factory;
    this.schema = schema;
  }

  /** Appends every day range of {@code run} to the matching open encoder (opening one if new). */
  void append(final SortedRun run) {
    for (final SortedRun.DayRange range : run.dayRanges()) {
      final BatchEncoder encoder = encoderFor(range.epochDay());
      encoder.append(run, range.fromIndex(), range.toIndex());
      estimatedBytes += (range.toIndex() - range.fromIndex()) * BYTES_PER_ROW_ESTIMATE;
    }
  }

  private BatchEncoder encoderFor(final long epochDay) {
    if (epochDay < 0) {
      return spillEncoder();
    }
    final BatchEncoder existing = openByDay.get(epochDay);
    if (existing != null) {
      return existing;
    }
    if (openByDay.size() >= MAX_OPEN_DAYS) {
      // a 4th distinct day in one window overflows into the spill file rather than opening
      // another encoder — spill files carry wider stats but stay correct (see BatchEncoder.Factory
      // javadoc on epochDay < 0).
      return spillEncoder();
    }
    final BatchEncoder opened = factory.newFile(schema, epochDay);
    openByDay.put(epochDay, opened);
    return opened;
  }

  private BatchEncoder spillEncoder() {
    if (spill == null) {
      spill = factory.newFile(schema, -1);
    }
    return spill;
  }

  long estimatedBytes() {
    return estimatedBytes;
  }

  /** Whether anything has been appended into this (still open) window since the last reset. */
  boolean hasData() {
    return !openByDay.isEmpty() || spill != null;
  }

  /**
   * Finishes every open encoder, resets for the next window, and returns their results.
   *
   * <p>Removes each encoder from {@link #openByDay} the moment its {@link BatchEncoder#finish()}
   * returns, rather than clearing the map in one shot afterwards: if a later encoder's {@code
   * finish()} throws, the caller recovers by calling {@link #abortAll()}, which must never re-touch
   * an encoder that already finished successfully (that would violate {@link
   * BatchEncoder#abort()}'s "safe to call after a failed append" contract, which does not cover
   * calling abort after a successful finish). Leaving a not-yet-attempted encoder in the map is
   * exactly what {@link #abortAll()} needs to see.
   */
  List<DataFileResult> finishAll() {
    final List<DataFileResult> results = new ArrayList<>(openByDay.size() + 1);
    final Iterator<Map.Entry<Long, BatchEncoder>> it = openByDay.entrySet().iterator();
    while (it.hasNext()) {
      final BatchEncoder encoder = it.next().getValue();
      results.add(encoder.finish());
      it.remove();
    }
    if (spill != null) {
      final BatchEncoder finishing = spill;
      // cleared before finish() runs, for the same reason: a throw must not leave a
      // successfully-finished (or already-attempted) encoder reachable from abortAll().
      spill = null;
      results.add(finishing.finish());
    }
    estimatedBytes = 0;
    return results;
  }

  /** Abandons every open encoder (failure path) and resets. */
  void abortAll() {
    for (final BatchEncoder encoder : openByDay.values()) {
      encoder.abort();
    }
    openByDay.clear();
    if (spill != null) {
      spill.abort();
      spill = null;
    }
    estimatedBytes = 0;
  }
}
