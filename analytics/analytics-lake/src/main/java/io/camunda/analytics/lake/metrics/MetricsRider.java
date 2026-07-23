/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.metrics;

import io.camunda.analytics.lake.sink.BatchEncoder;
import io.camunda.analytics.lake.sink.ColumnType;
import io.camunda.analytics.lake.sink.ColumnVector;
import io.camunda.analytics.lake.sink.DataFileResult;
import io.camunda.analytics.lake.sink.SealRider;
import io.camunda.analytics.lake.sink.Segment;
import io.camunda.analytics.lake.sink.SortedRun;
import io.camunda.analytics.lake.sink.TableSchema;
import io.camunda.analytics.lake.sink.algebra.Algebra;
import io.camunda.analytics.lake.sink.algebra.Algebras;
import io.camunda.analytics.lake.sink.batch.Interner;
import io.camunda.analytics.lake.sink.batch.SegmentFactory;
import io.camunda.analytics.lake.sink.batch.SegmentSorter;
import io.camunda.analytics.lake.sink.encode.DayRouter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The fold-at-flush rider: turns each flush window's sorted raw rows into mergeable metric partials
 * — scalar stats and histogram-bin rows — for the entity's generated {@code _metrics}/{@code _hist}
 * tables, per the compiled declaration (see {@link CompiledEntityMetrics}).
 *
 * <p>Lifecycle per {@link SealRider}'s contract, all on the flush thread: {@link #onSealed} folds
 * one sorted segment into accumulators keyed by {@code (window slot, dims)}; state carries across
 * the window's segments (a group reappearing in a later segment just receives more {@code add()}
 * calls — the group table <em>is</em> the intra-window merge, which is why the algebra's hot side
 * has no {@code merge()}). {@link #onWindowClose} drains every group once into partials segments,
 * which flow through the exact machinery raw rows use — sorter, day router, encoder — so partials
 * files are sorted, day-partitioned, and stats-annotated like every other file in the lake.
 *
 * <p>Group dims are re-coded through rider-lifetime {@link Interner}s (segment dictionary codes are
 * segment-local, so they cannot key state that outlives a segment). Dictionary cardinality of dims
 * (process ids, element ids) is naturally small and stable, so these interners stay tiny.
 *
 * <p>Rows whose window-source or any dim column is null are skipped: no event time means no slot,
 * and a null dim has no group. Measure nulls skip only that measure ("nulls=skip", part of the
 * declaration fingerprint).
 */
public final class MetricsRider implements SealRider {

  private final CompiledEntityMetrics compiled;
  private final RiderPlan plan;

  // --- grouping state (lives across segments within one window) ---
  private final GroupTable groups;
  private final Interner[] dimInterners; // null entry for INT dims
  private final boolean[] dimIsDict;
  private final int[] dimCodeScratch;

  /** One flat entry per (measure, algebra) pair, in declaration order. */
  private final FlatAlgebra[] flats;

  // --- drain machinery (reused across windows) ---
  private final DrainTarget metricsTarget;

  /**
   * {@code null} when {@link CompiledEntityMetrics#hasHistogram()} is false: a declaration with no
   * {@code TALL}-shaped measure has nothing to ever put in a {@code _hist} table, so this rider
   * skips constructing its drain machinery entirely rather than building (and forever leaving
   * empty) one — see {@link CompiledEntityMetrics#hasHistogram()}'s own javadoc.
   */
  private final DrainTarget histTarget;

  public MetricsRider(
      final CompiledEntityMetrics compiled,
      final BatchEncoder.Factory encoderFactory,
      final int drainRowCapacity) {
    this.compiled = compiled;
    plan = compiled.riderPlan();

    final int dimCount = plan.dimColumnIndexes().length;
    groups = new GroupTable(dimCount);
    dimInterners = new Interner[dimCount];
    dimIsDict = new boolean[dimCount];
    dimCodeScratch = new int[dimCount];
    final TableSchema raw = compiled.rawSchema();
    for (int d = 0; d < dimCount; d++) {
      final ColumnType type = raw.columns().get(plan.dimColumnIndexes()[d]).type();
      dimIsDict[d] = type == ColumnType.STRING_DICT;
      if (dimIsDict[d]) {
        dimInterners[d] = new Interner();
      }
    }

    metricsTarget = new DrainTarget(compiled.metricsSchema(), encoderFactory, drainRowCapacity);
    histTarget =
        compiled.hasHistogram()
            ? new DrainTarget(compiled.histSchema(), encoderFactory, drainRowCapacity)
            : null;

    final List<FlatAlgebra> flatList = new ArrayList<>();
    final List<MeasureDeclaration> measures = compiled.measures();
    for (int m = 0; m < measures.size(); m++) {
      final MeasureDeclaration measure = measures.get(m);
      for (final Algebra algebra : measure.algebras()) {
        flatList.add(
            new FlatAlgebra(
                measure.name(),
                plan.measureColumnIndexes()[m],
                algebra,
                algebra.shape() == Algebra.PartialsShape.WIDE
                    ? resolveColumns(compiled.metricsSchema(), algebra, measure.name())
                    : resolveColumns(compiled.histSchema(), algebra, measure.name())));
      }
    }
    if (compiled.counted()) {
      // A count is not a measure: it has no raw column to read/null-check (see FlatAlgebra's own
      // NO_COLUMN javadoc) and folds unconditionally, once per row that reaches a group, in
      // onSealed below. Reusing the same FlatAlgebra/pool machinery still lets drainWideRow drain
      // it through the exact same generic per-flat loop as every other WIDE algebra.
      final Algebra countAlgebra = Algebras.count();
      flatList.add(
          new FlatAlgebra(
              "",
              FlatAlgebra.NO_COLUMN,
              countAlgebra,
              resolveColumns(compiled.metricsSchema(), countAlgebra, "")));
    }
    flats = flatList.toArray(FlatAlgebra[]::new);
  }

  /** Absolute segment-column index of each of {@code algebra}'s partial columns, by name. */
  private static int[] resolveColumns(
      final TableSchema schema, final Algebra algebra, final String measure) {
    final List<Algebra.PartialColumn> partials = algebra.partialColumns(measure);
    final int[] absolute = new int[partials.size()];
    for (int i = 0; i < partials.size(); i++) {
      absolute[i] = columnIndex(schema, partials.get(i).name());
    }
    return absolute;
  }

  private static int columnIndex(final TableSchema schema, final String name) {
    for (int i = 0; i < schema.columns().size(); i++) {
      if (schema.columns().get(i).name().equals(name)) {
        return i;
      }
    }
    throw new IllegalStateException(
        "generated schema " + schema.table() + " is missing expected column " + name);
  }

  // ------------------------------------------------------------------
  // fold side
  // ------------------------------------------------------------------

  @Override
  public void onSealed(final SortedRun run) {
    final int windowSource = plan.windowSourceColumn();
    final long windowMicros = plan.windowMicros();
    final int[] dimCols = plan.dimColumnIndexes();

    rows:
    for (int i = 0; i < run.size(); i++) {
      long slot = 0L;
      if (windowMicros > 0) {
        if (run.isNullAt(windowSource, i)) {
          continue; // no event time, no slot -- see class javadoc
        }
        final long eventMicros = run.longAt(windowSource, i);
        slot = Math.floorDiv(eventMicros, windowMicros) * windowMicros;
      }
      for (int d = 0; d < dimCols.length; d++) {
        if (run.isNullAt(dimCols[d], i)) {
          continue rows; // a null dim has no group -- see class javadoc
        }
        dimCodeScratch[d] =
            dimIsDict[d]
                ? dimInterners[d].intern(run.stringAt(dimCols[d], i))
                : run.intAt(dimCols[d], i);
      }
      final int group = groups.findOrAdd(slot, dimCodeScratch);
      for (final FlatAlgebra flat : flats) {
        if (flat.rawColumn == FlatAlgebra.NO_COLUMN) {
          // count(): no raw column, no null to skip -- increments once per row that reached a
          // group, unconditionally (see FlatAlgebra's own NO_COLUMN javadoc).
          flat.accumulatorFor(group).add(0L);
        } else if (!run.isNullAt(flat.rawColumn, i)) {
          flat.accumulatorFor(group).add(run.longAt(flat.rawColumn, i));
        }
      }
    }
  }

  // ------------------------------------------------------------------
  // drain side
  // ------------------------------------------------------------------

  @Override
  public Map<String, List<DataFileResult>> onWindowClose() {
    if (groups.count() == 0) {
      return Map.of();
    }
    final int groupCount = groups.count();
    for (int g = 0; g < groupCount; g++) {
      drainWideRow(g);
      if (histTarget != null) {
        drainTallRows(g);
      }
    }
    final Map<String, List<DataFileResult>> files = new LinkedHashMap<>();
    final List<DataFileResult> metricsFiles = metricsTarget.finish();
    if (!metricsFiles.isEmpty()) {
      files.put(compiled.metricsSchema().table(), metricsFiles);
    }
    if (histTarget != null) {
      final List<DataFileResult> histFiles = histTarget.finish();
      if (!histFiles.isEmpty()) {
        files.put(compiled.histSchema().table(), histFiles);
      }
    }
    resetWindow();
    return files;
  }

  @Override
  public void abortWindow() {
    metricsTarget.abort();
    if (histTarget != null) {
      histTarget.abort();
    }
    resetWindow();
  }

  private void resetWindow() {
    for (final FlatAlgebra flat : flats) {
      flat.resetAll();
    }
    groups.clear();
  }

  /** One {@code _metrics} row per group: window, dims, then every WIDE algebra's columns. */
  private void drainWideRow(final int group) {
    final Segment segment = metricsTarget.segment();
    final int row = segment.size();
    writePrefix(segment, row, group, metricsTarget.dimColumns());
    for (final FlatAlgebra flat : flats) {
      if (flat.algebra.shape() != Algebra.PartialsShape.WIDE) {
        continue;
      }
      final Algebra.Accumulator accumulator = flat.accumulatorFor(group);
      if (accumulator.isEmpty()) {
        // a group exists because SOME other flat folded something (a measure, or count()); THIS
        // one saw nothing -- an Accumulator must never be drained while empty (see
        // Algebra.Accumulator#drain's own contract), so every one of its columns is written
        // directly here instead: nullable columns (e.g. ScalarStatsAlgebra's min/max) get the
        // honest "nothing observed" NULL, non-nullable ones (cnt/sum, or count()'s own single
        // column) get an honest zero. Schema-driven rather than hardcoded to a fixed column count
        // so this generalizes to every WIDE algebra's own column shape, not just
        // ScalarStatsAlgebra's four.
        for (final int absoluteColumn : flat.absoluteColumns) {
          final TableSchema.Column column = segment.schema().columns().get(absoluteColumn);
          if (column.nullable()) {
            segment.vector(absoluteColumn).setNull(row);
          } else {
            ((ColumnVector.LongColumn) segment.vector(absoluteColumn)).set(row, 0L);
          }
        }
        continue;
      }
      metricsTarget.wideWriter.arm(row, flat.absoluteColumns);
      accumulator.drain(metricsTarget.wideWriter);
    }
    segment.rowCompleted();
    metricsTarget.flushIfFull();
  }

  /** N {@code _hist} rows per group per TALL algebra: one per non-empty bin. */
  private void drainTallRows(final int group) {
    for (final FlatAlgebra flat : flats) {
      if (flat.algebra.shape() != Algebra.PartialsShape.TALL) {
        continue;
      }
      final Algebra.Accumulator accumulator = flat.accumulatorFor(group);
      if (accumulator.isEmpty()) {
        continue;
      }
      histTarget.tallWriter.arm(
          group, flat.measureName, flat.algebra.scheme(), flat.absoluteColumns);
      accumulator.drain(histTarget.tallWriter);
    }
  }

  /** Writes window_start + dims into {@code row}; shared by both drain shapes. */
  private void writePrefix(
      final Segment segment, final int row, final int group, final int[] dimColumns) {
    final TableSchema schema = segment.schema();
    ((ColumnVector.LongColumn) segment.vector(schema.familyDayColumn()))
        .set(row, groups.slotOf(group));
    for (int d = 0; d < dimColumns.length; d++) {
      final int code = groups.dimCodeOf(d, group);
      if (dimIsDict[d]) {
        ((ColumnVector.DictColumn) segment.vector(dimColumns[d]))
            .set(row, dimInterners[d].valueOf(code));
      } else {
        ((ColumnVector.IntColumn) segment.vector(dimColumns[d])).set(row, code);
      }
    }
  }

  // ------------------------------------------------------------------
  // helpers
  // ------------------------------------------------------------------

  /** One (measure, algebra) pair with its per-group accumulator pool. */
  private static final class FlatAlgebra {

    /**
     * Sentinel {@code rawColumn} for the {@code count()} flat: it folds no raw value at all (see
     * {@link io.camunda.analytics.lake.sink.algebra.CountAlgebra}'s own javadoc), so {@link
     * MetricsRider#onSealed}'s per-row loop must not attempt to read (or null-check) a raw column
     * for it — the one thing every other {@code FlatAlgebra} always has.
     */
    static final int NO_COLUMN = -1;

    final String measureName;
    final int rawColumn;
    final Algebra algebra;
    final int[] absoluteColumns;
    private final List<Algebra.Accumulator> pool = new ArrayList<>();

    FlatAlgebra(
        final String measureName,
        final int rawColumn,
        final Algebra algebra,
        final int[] absoluteColumns) {
      this.measureName = measureName;
      this.rawColumn = rawColumn;
      this.algebra = algebra;
      this.absoluteColumns = absoluteColumns;
    }

    Algebra.Accumulator accumulatorFor(final int group) {
      while (pool.size() <= group) {
        pool.add(algebra.create());
      }
      return pool.get(group);
    }

    void resetAll() {
      pool.forEach(Algebra.Accumulator::reset);
    }
  }

  /**
   * One partials table's drain machinery: a reusable segment, its sorter, its day router, and the
   * {@link Algebra.RowWriter} adapters that map an accumulator's relative column writes onto the
   * segment. When the segment fills mid-drain it is sorted, routed, and reset in place — a drain
   * may produce arbitrarily many rows (histogram bins x groups) from one bounded segment.
   */
  private final class DrainTarget {

    private final TableSchema schema;
    private final Segment segment;
    private final SegmentSorter sorter;
    private final DayRouter router;
    private final int[] dimColumns;
    final WideWriter wideWriter = new WideWriter();
    final TallWriter tallWriter = new TallWriter();
    private final int measureColumn;
    private final int schemeColumn;

    DrainTarget(
        final TableSchema schema,
        final BatchEncoder.Factory encoderFactory,
        final int rowCapacity) {
      this.schema = schema;
      final int[] binaryAvg = new int[schema.columns().size()];
      final Interner interner = new Interner();
      segment = SegmentFactory.createSegments(schema, 1, rowCapacity, binaryAvg, interner)[0];
      sorter = new SegmentSorter(schema, rowCapacity, binaryAvg, interner);
      router = new DayRouter(schema, encoderFactory);
      dimColumns = new int[compiled.dims().size()];
      for (int d = 0; d < dimColumns.length; d++) {
        dimColumns[d] = columnIndex(schema, compiled.dims().get(d));
      }
      measureColumn = hasColumn(schema, "measure") ? columnIndex(schema, "measure") : -1;
      schemeColumn = hasColumn(schema, "scheme") ? columnIndex(schema, "scheme") : -1;
    }

    Segment segment() {
      return segment;
    }

    int[] dimColumns() {
      return dimColumns;
    }

    void flushIfFull() {
      if (segment.isFull()) {
        flushSegment();
      }
    }

    private void flushSegment() {
      if (segment.size() == 0) {
        return;
      }
      router.route(sorter.sort(segment));
      segment.reset();
    }

    List<DataFileResult> finish() {
      flushSegment();
      return router.hasPendingResults() ? router.closeAll() : List.of();
    }

    void abort() {
      segment.reset();
      router.abortAll();
    }

    /**
     * Adapter for WIDE algebras: the rider owns the physical row (several measures share it), so
     * {@code beginRow}/{@code endRow} are no-ops and relative writes land at pre-armed absolute
     * columns of the armed row.
     */
    final class WideWriter implements Algebra.RowWriter {

      private int row;
      private int[] absoluteColumns;

      void arm(final int armedRow, final int[] columns) {
        row = armedRow;
        absoluteColumns = columns;
      }

      @Override
      public void beginRow() {}

      @Override
      public void writeLong(final int columnIndex, final long value) {
        ((ColumnVector.LongColumn) segment.vector(absoluteColumns[columnIndex])).set(row, value);
      }

      @Override
      public void endRow() {}
    }

    /**
     * Adapter for TALL algebras: every {@code beginRow} opens a fresh physical row (bin rows), the
     * rider stamps the shared prefix (window, dims, measure, scheme), relative writes land at the
     * armed absolute columns, {@code endRow} completes the row and lets a full segment flush — safe
     * mid-drain, since bins are independent rows.
     */
    final class TallWriter implements Algebra.RowWriter {

      private int group;
      private String measure;
      private String scheme;
      private int[] absoluteColumns;
      private int row;

      void arm(
          final int armedGroup,
          final String armedMeasure,
          final String armedScheme,
          final int[] columns) {
        group = armedGroup;
        measure = armedMeasure;
        scheme = armedScheme;
        absoluteColumns = columns;
      }

      @Override
      public void beginRow() {
        row = segment.size();
        writePrefix(segment, row, group, dimColumns);
        ((ColumnVector.DictColumn) segment.vector(measureColumn)).set(row, measure);
        ((ColumnVector.DictColumn) segment.vector(schemeColumn)).set(row, scheme);
      }

      @Override
      public void writeLong(final int columnIndex, final long value) {
        ((ColumnVector.LongColumn) segment.vector(absoluteColumns[columnIndex])).set(row, value);
      }

      @Override
      public void endRow() {
        segment.rowCompleted();
        flushIfFull();
      }
    }
  }

  private static boolean hasColumn(final TableSchema schema, final String name) {
    for (final TableSchema.Column column : schema.columns()) {
      if (column.name().equals(name)) {
        return true;
      }
    }
    return false;
  }
}
