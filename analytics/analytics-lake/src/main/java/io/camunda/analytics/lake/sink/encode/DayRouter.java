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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages the open {@link BatchEncoder}s for one table during one file window: one per distinct
 * family day seen so far, up to {@link #MAX_OPEN_DAY_ENCODERS}.
 *
 * <p>A day range beyond that cap (a "straggler") is <b>not</b> merged into any shared file: the
 * tables are partitioned by {@code days(...)} on their family-day column, and a partitioned table's
 * data file must carry exactly one partition tuple, so a file mixing two family days is no longer a
 * legal output at all (contrast the old unpartitioned-table design, which spilled every day beyond
 * the cap into one shared {@code epochDay == -1} file). Instead, a straggler range is opened,
 * written and finished as its own dedicated one-shot file right there in {@link #route}, rather
 * than held open — there is no free slot for it to share. Two straggler ranges for the very same
 * family day across two separate {@link #route} calls each get their own file this way,
 * sequentially; this is expected to be rare (it only happens once more than {@link
 * #MAX_OPEN_DAY_ENCODERS} distinct family days are live in the same file window) and the resulting
 * small files are exactly the kind {@code LakeCompactor}'s periodic pass exists to fold back down.
 *
 * <p>Not thread-safe: driven entirely from the flush thread, same as everything else in this
 * package (see the {@code io.camunda.analytics.lake.sink} package-info's threading model).
 */
public final class DayRouter {

  private static final int MAX_OPEN_DAY_ENCODERS = 3;

  private final TableSchema schema;
  private final BatchEncoder.Factory encoderFactory;
  private final Map<Long, BatchEncoder> openByDay = new LinkedHashMap<>();
  private final List<DataFileResult> stragglerResults = new ArrayList<>();

  public DayRouter(final TableSchema schema, final BatchEncoder.Factory encoderFactory) {
    this.schema = schema;
    this.encoderFactory = encoderFactory;
  }

  /**
   * Appends every day range of {@code run} to its day's encoder, opening one via the factory on
   * first use of that day (or writing a straggler's own one-shot file — see class javadoc — once
   * {@link #MAX_OPEN_DAY_ENCODERS} is already in use by other days).
   *
   * <p>A freshly opened day encoder is put into {@link #openByDay} <em>before</em> its first {@link
   * BatchEncoder#append} call, not after: if that first append throws, {@link #abortAll} must still
   * be able to find and abort it (an encoder the factory opened but this method never tracked would
   * leak — never aborted, never finished).
   */
  public void route(final SortedRun run) {
    for (final SortedRun.DayRange range : run.dayRanges()) {
      final BatchEncoder open = openByDay.get(range.epochDay());
      if (open != null) {
        open.append(run, range.fromIndex(), range.toIndex());
      } else if (openByDay.size() < MAX_OPEN_DAY_ENCODERS) {
        final BatchEncoder created = encoderFactory.newFile(schema, range.epochDay());
        openByDay.put(range.epochDay(), created);
        created.append(run, range.fromIndex(), range.toIndex());
      } else {
        routeStraggler(run, range);
      }
    }
  }

  /**
   * Opens, appends to, and eagerly finishes a straggler's own one-shot file. If either the append
   * or the finish throws, the straggler is aborted before the exception propagates — it is never
   * reachable from {@link #openByDay} or {@link #stragglerResults}, so nothing else would ever
   * abort or finish it otherwise.
   */
  private void routeStraggler(final SortedRun run, final SortedRun.DayRange range) {
    final BatchEncoder straggler = encoderFactory.newFile(schema, range.epochDay());
    final DataFileResult result;
    try {
      straggler.append(run, range.fromIndex(), range.toIndex());
      result = straggler.finish();
    } catch (final RuntimeException e) {
      straggler.abort();
      throw e;
    }
    stragglerResults.add(result);
  }

  /**
   * Finishes every currently-open day encoder and returns every {@link DataFileResult}, including
   * any straggler files already finished eagerly by {@link #route}. Leaves this router with no open
   * encoders; a later {@link #route} call opens fresh ones.
   *
   * <p>Removes each encoder from {@link #openByDay} the moment its {@link BatchEncoder#finish()}
   * returns, rather than clearing the map in one shot afterwards: if a later encoder's {@code
   * finish()} throws, the caller recovers by calling {@link #abortAll()}, which must never re-touch
   * an encoder that already finished successfully (that would violate {@link
   * BatchEncoder#abort()}'s "safe to call after a failed append" contract, which does not cover
   * calling abort after a successful finish). Leaving a not-yet-attempted encoder in the map is
   * exactly what {@link #abortAll()} needs to see.
   */
  public List<DataFileResult> closeAll() {
    final List<DataFileResult> results =
        new ArrayList<>(openByDay.size() + stragglerResults.size());
    final Iterator<Map.Entry<Long, BatchEncoder>> it = openByDay.entrySet().iterator();
    while (it.hasNext()) {
      final BatchEncoder encoder = it.next().getValue();
      results.add(encoder.finish());
      it.remove();
    }
    results.addAll(stragglerResults);
    stragglerResults.clear();
    return results;
  }

  /** Whether anything is currently pending a {@link #closeAll()} call. */
  public boolean hasPendingResults() {
    return !openByDay.isEmpty() || !stragglerResults.isEmpty();
  }

  /** Abandons every open encoder (shutdown/crash path); never throws. */
  public void abortAll() {
    for (final BatchEncoder encoder : openByDay.values()) {
      encoder.abort();
    }
    openByDay.clear();
    // Straggler files were already finished (a complete Parquet file on disk) the moment route()
    // wrote them -- there is nothing left to abort. If this window is discarded, they simply
    // become harmless orphans, never registered with any DescriptorSink -- the same accepted
    // trade-off io.camunda.analytics.lake.write.LakeCompactor's own javadoc documents for a
    // crash between a compacted file's write and its commit.
    stragglerResults.clear();
  }
}
