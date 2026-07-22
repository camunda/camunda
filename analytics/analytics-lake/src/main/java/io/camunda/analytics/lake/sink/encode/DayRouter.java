/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.sink.encode;

import io.camunda.analytics.lake.sink.BatchEncoder;
import io.camunda.analytics.lake.sink.DataFileResult;
import io.camunda.analytics.lake.sink.SortedRun;
import io.camunda.analytics.lake.sink.TableSchema;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages the open {@link BatchEncoder}s for one table during one file window: one per distinct
 * family day seen so far, up to {@link #MAX_OPEN_DAY_ENCODERS}; every day beyond that shares a
 * single spill encoder at {@code epochDay == -1} (the same slot a {@link SortedRun} that already
 * reports a mixed-day range at {@code epochDay < 0} lands in, too — collapsing "explicitly mixed"
 * and "too many distinct days" into the one file is deliberate, not a collision).
 *
 * <p>Not thread-safe: driven entirely from the flush thread, same as everything else in this
 * package (see the {@code io.camunda.analytics.lake.sink} package-info's threading model).
 */
public final class DayRouter {

  private static final int MAX_OPEN_DAY_ENCODERS = 3;
  private static final long SPILL_EPOCH_DAY = -1;

  private final TableSchema schema;
  private final BatchEncoder.Factory encoderFactory;
  private final Map<Long, BatchEncoder> openByDay = new LinkedHashMap<>();
  private BatchEncoder spillEncoder;

  public DayRouter(final TableSchema schema, final BatchEncoder.Factory encoderFactory) {
    this.schema = schema;
    this.encoderFactory = encoderFactory;
  }

  /**
   * Appends every day range of {@code run} to its day's encoder, opening one via the factory on
   * first use of that day.
   */
  public void route(final SortedRun run) {
    for (final SortedRun.DayRange range : run.dayRanges()) {
      encoderFor(range.epochDay()).append(run, range.fromIndex(), range.toIndex());
    }
  }

  private BatchEncoder encoderFor(final long epochDay) {
    if (epochDay == SPILL_EPOCH_DAY) {
      return spillEncoder();
    }
    final BatchEncoder existing = openByDay.get(epochDay);
    if (existing != null) {
      return existing;
    }
    if (openByDay.size() < MAX_OPEN_DAY_ENCODERS) {
      final BatchEncoder created = encoderFactory.newFile(schema, epochDay);
      openByDay.put(epochDay, created);
      return created;
    }
    return spillEncoder();
  }

  private BatchEncoder spillEncoder() {
    if (spillEncoder == null) {
      spillEncoder = encoderFactory.newFile(schema, SPILL_EPOCH_DAY);
    }
    return spillEncoder;
  }

  /**
   * Finishes every open encoder (day encoders, then the spill encoder if one was ever opened) and
   * returns every {@link DataFileResult}. Leaves this router with no open encoders; a later {@link
   * #route} call opens fresh ones.
   */
  public List<DataFileResult> closeAll() {
    final List<DataFileResult> results = new ArrayList<>(openByDay.size() + 1);
    for (final BatchEncoder encoder : openByDay.values()) {
      results.add(encoder.finish());
    }
    openByDay.clear();
    if (spillEncoder != null) {
      results.add(spillEncoder.finish());
      spillEncoder = null;
    }
    return results;
  }

  /** Abandons every open encoder (shutdown/crash path); never throws. */
  public void abortAll() {
    for (final BatchEncoder encoder : openByDay.values()) {
      encoder.abort();
    }
    openByDay.clear();
    if (spillEncoder != null) {
      spillEncoder.abort();
      spillEncoder = null;
    }
  }
}
