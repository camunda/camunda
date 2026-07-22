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
import io.camunda.analytics.lake.sink.encode.DayRouter;
import java.util.List;

/**
 * Minimal per-flush-thread file router: a thin wrapper around {@link DayRouter} — which already
 * implements the correct per-family-day routing, including straggler days beyond the concurrently-
 * open cap getting their own dedicated one-shot file rather than a shared "mixed day" file — adding
 * only the byte-estimate tracking {@link FlushLoop} needs for the SIZE_CAP trigger. Flush thread
 * only, one instance per {@link FlushLoop}, reused across every window it closes.
 *
 * <p>This used to duplicate {@link DayRouter}'s shape independently (a since-resolved
 * parallel-build constraint kept the two packages from depending on each other); that constraint is
 * gone, so this class now delegates instead of re-implementing the same routing logic a second
 * time.
 *
 * <p>Byte accounting is an estimate, not a measurement: {@link BatchEncoder} exposes no size probe,
 * so {@link #estimatedBytes()} sums {@code rows appended * BYTES_PER_ROW_ESTIMATE}. That is only
 * ever used to decide the (purely-timing) SIZE_CAP trigger, so an estimate is exactly as
 * appropriate as a measurement would be — see {@link SinkConfig#fileTargetBytes()}.
 */
final class FileWindow {

  private static final long BYTES_PER_ROW_ESTIMATE = 200L;

  private final DayRouter router;
  private long estimatedBytes;

  FileWindow(final BatchEncoder.Factory factory, final TableSchema schema) {
    router = new DayRouter(schema, factory);
  }

  /** Routes every day range of {@code run} to its file via {@link DayRouter#route}. */
  void append(final SortedRun run) {
    router.route(run);
    for (final SortedRun.DayRange range : run.dayRanges()) {
      estimatedBytes += (range.toIndex() - range.fromIndex()) * BYTES_PER_ROW_ESTIMATE;
    }
  }

  long estimatedBytes() {
    return estimatedBytes;
  }

  /** Whether anything has been appended into this (still open) window since the last reset. */
  boolean hasData() {
    return router.hasPendingResults();
  }

  /**
   * Finishes every open file and returns their results, including any straggler files {@link
   * DayRouter#route} already finished eagerly. Resets the byte estimate for the next window.
   */
  List<DataFileResult> finishAll() {
    final List<DataFileResult> results = router.closeAll();
    estimatedBytes = 0;
    return results;
  }

  /** Abandons every open file (failure path) and resets. */
  void abortAll() {
    router.abortAll();
    estimatedBytes = 0;
  }
}
