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
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * The poll-fed twin of {@link MetricsRider}: folds directly from qualifying Zeebe records, on the
 * poll thread, into a record-fed entity's partials — for metric families whose source records
 * produce no raw row at all (sequence-flow-taken events, process-instance-started events, a
 * completed instance's own final variable values; see {@code
 * io.camunda.analytics.lake.translate.LakeTranslator}'s own call sites). {@link MetricsRider}
 * cannot serve these: its {@link SealRider#onSealed} only ever sees rows that already exist in a
 * raw table, and these records never produce one.
 *
 * <h2>Two faces, two threads</h2>
 *
 * <ul>
 *   <li>The <b>poll-thread face</b> ({@link #putDict}/{@link #putInt}/{@link #putDoubleMeasure}/
 *       {@link #incrementCounter}/{@link #fold}) is what {@code LakeTranslator} calls once per
 *       qualifying record, mirroring {@code RowAppender}'s own typed, positional, allocation-free
 *       {@code put*}-then-complete convention: set every declared dim (in declaration order) via
 *       {@link #putDict}/{@link #putInt}, optionally arm any {@code DOUBLE} measure value via
 *       {@link #putDoubleMeasure} and/or any named counter via {@link #incrementCounter}, then call
 *       {@link #fold} with the record's event time. Allocation-free at steady state via the same
 *       {@link GroupTable} + rider-lifetime {@link Interner} pattern {@link MetricsRider} uses for
 *       its own dims.
 *   <li>The <b>{@link SealRider} face</b> ({@link #onWindowClose}/{@link #abortWindow}, plus the
 *       {@link SealRider#onPollBoundary}/{@link SealRider#rollbackPollBoundary} hooks below) is
 *       what the host pipeline's flush thread drives, exactly like {@link MetricsRider}.
 * </ul>
 *
 * <h2>The alignment mechanism (load-bearing)</h2>
 *
 * <p>The poll thread owns one "active" {@link ActiveWindow} at a time — the {@link GroupTable} +
 * accumulators every {@link #fold} call writes into. At the exact moment the host pipeline seals a
 * file boundary (TIME_DUE/SIZE_CAP/SHUTDOWN — see {@link SealRider#onPollBoundary}'s own javadoc
 * for precisely where and why), {@link #onPollBoundary()} <b>swaps</b>: the active window is frozen
 * (pushed onto {@link #frozenQueue}) and a fresh, empty one takes its place. The flush thread's
 * {@link #onWindowClose()} drains <em>only</em> the frozen window — never the active one, which by
 * then may already contain records folded after the boundary.
 *
 * <p>The result: a frozen window's increments correspond <em>exactly</em> to the records folded
 * before its boundary — the same guarantee {@code SealSnapshot} gives the raw offset/frontier/
 * watermark fields, obtained the same way (publish-before-seal; see {@link
 * SealRider#onPollBoundary}'s javadoc for the precise ordering argument).
 *
 * <h2>Why no per-key dedup guard is needed on replay</h2>
 *
 * <p>A window's accumulators are reset to empty at <em>every</em> cut (the swap always starts a
 * fresh {@link ActiveWindow}, never reuses one). So after a crash, replay resumes at {@code
 * committedOffset + 1} and re-folds <em>exactly</em> the record range that was never durably
 * committed into a brand new, initially-empty window — there is no way for a previously-committed
 * increment to be folded a second time, because the accumulator that held it was discarded the
 * moment it was drained, and nothing before the resume offset is ever re-folded at all. This is the
 * same reasoning that lets {@link MetricsRider} skip a merge step entirely (see its own class
 * javadoc): "no guard" here means "nothing to guard against", not "guard omitted".
 *
 * <h2>Record-fed vs. row-fed: a first-class declaration shape, not a workaround</h2>
 *
 * <p>The {@link CompiledEntityMetrics} this rider is built from is declared exactly like {@link
 * MetricsRider}'s own, via {@link EntityMetrics#declare}, except its {@code rawSchema} is a purely
 * <b>logical</b> row shape — the tuple of typed fields one qualifying record contributes — with no
 * {@link io.camunda.analytics.lake.sink.pipeline.SinkPipeline} ever materializing rows of it (see
 * {@link EntityMetrics#declare}'s own javadoc). Every method on {@link CompiledEntityMetrics} this
 * rider calls ({@code metricsSchema()}, {@code histSchema()}, {@code fingerprint()}, {@code
 * dims()}, {@code riderPlan()}) already validates purely against column name/type, never against a
 * real backing table, so this "virtual schema" pattern needed no special-casing anywhere in the
 * declaration machinery — it is the intended generalization the {@code rawSchema} parameter always
 * supported.
 *
 * <h2>Scope: {@code DOUBLE}-valued measures only</h2>
 *
 * <p>A declared {@code .measure(...)} must fold through {@code DOUBLE}-valued algebras only (e.g.
 * {@link io.camunda.analytics.lake.sink.algebra.DoubleScalarStatsAlgebra}/{@link
 * io.camunda.analytics.lake.sink.algebra.SignedDoubleExpHistogramAlgebra} — see {@link
 * #putDoubleMeasure}). A {@code LONG}-valued measure (e.g. {@link
 * io.camunda.analytics.lake.sink.algebra.ScalarStatsAlgebra}) is rejected at construction with a
 * clear error directing the caller to {@link MetricsRider} instead: this rider has no raw row to
 * read a {@code LONG} value from at fold time (its whole point is folding record-derived values
 * that are never materialized as rows), and a record-fed declaration's {@code DOUBLE} measures are
 * always folded through this rider's own {@link #putDoubleMeasure} staging, never through a raw
 * column read the way {@link MetricsRider#onSealed} folds a row-fed measure.
 *
 * <h2>Null measures skip only that measure, not the row (symmetric with {@link MetricsRider})</h2>
 *
 * <p>{@link #fold} folds a staged measure's value into every one of that measure's declared
 * algebras only if {@link #putDoubleMeasure} was actually called for it since the last {@link
 * #fold} — a record whose value for one measure is unknown (e.g. the variable's value was not
 * numeric this record) simply never stages it, and {@link #fold} silently skips folding that one
 * measure while still counting the row (via {@link EntityMetrics.Builder#count()}) and any named
 * counter armed via {@link #incrementCounter}. This mirrors {@link MetricsRider#onSealed}'s own
 * "measure nulls skip only that measure" rule (see its class javadoc) exactly — the row is never
 * dropped for a missing measure, only that measure's own fold is skipped.
 */
public final class PollFedRider implements SealRider {

  private static final Algebra COUNT_ALGEBRA = Algebras.count();

  /**
   * Sentinel dim code meaning "explicitly NULL", set by {@link #putDict} when its {@code value}
   * argument is {@code null}. Always distinct from every real {@link Interner} code, which starts
   * at {@code 0} and only ever increments — see {@link Interner#intern}'s own javadoc.
   */
  private static final int NULL_CODE = -1;

  private final CompiledEntityMetrics compiled;
  private final int dimCount;
  private final long windowMicros;

  // --- dim coding (rider-lifetime, shared by every window; see class javadoc) ---
  private final Interner[] dimInterners; // null entry for INT dims
  private final boolean[] dimIsDict;
  private final int[] dimCodeScratch;

  // --- measure/counter fold-time staging (poll thread only, reset every fold()) ---
  private final double[] measureValueScratch;
  private final boolean[] measureArmed;
  private final boolean[] counterArmed;

  /** One entry per declared (measure, algebra) pair, in declaration order. Rider-lifetime. */
  private final FlatMeasure[] flats;

  // --- swap state: poll thread owns `active`; the flush thread only ever reads frozenQueue ---
  private ActiveWindow active;
  private final ConcurrentLinkedDeque<ActiveWindow> frozenQueue = new ConcurrentLinkedDeque<>();

  // --- drain machinery (flush thread only, reused across windows) ---
  private final DrainTarget metricsTarget;
  private final int[] countColumns;
  private final int[][] counterColumns; // one absolute-column array per named counter
  private final int[] dimColumns;

  /**
   * {@code null} when {@link CompiledEntityMetrics#hasHistogram()} is {@code false} — see {@link
   * MetricsRider#histTarget}'s own javadoc for why this is skipped entirely rather than built
   * (permanently) empty.
   */
  private final DrainTarget histTarget;

  public PollFedRider(
      final CompiledEntityMetrics compiled,
      final BatchEncoder.Factory encoderFactory,
      final int drainRowCapacity) {
    if (!compiled.counted()) {
      throw new IllegalArgumentException(
          "entity '"
              + compiled.entityName()
              + "': PollFedRider requires a count() declaration -- a record-fed entity folds"
              + " directly from qualifying records, and every fold() call needs an unconditional"
              + " per-row total to increment (see this rider's own class javadoc).");
    }
    for (final MeasureDeclaration measure : compiled.measures()) {
      for (final Algebra algebra : measure.algebras()) {
        if (algebra.valueType() != ColumnType.DOUBLE) {
          throw new IllegalArgumentException(
              "entity '"
                  + compiled.entityName()
                  + "': PollFedRider only supports DOUBLE-valued measures -- measure '"
                  + measure.name()
                  + "' declares a "
                  + algebra.valueType()
                  + "-valued algebra ('"
                  + algebra.scheme()
                  + "'). A record-fed rider has no raw row to read a LONG value from at fold time;"
                  + " use MetricsRider for a row-fed LONG measure instead (see this rider's own"
                  + " class javadoc's \"Scope\" section).");
        }
      }
    }
    this.compiled = compiled;

    final RiderPlan plan = compiled.riderPlan();
    dimCount = plan.dimColumnIndexes().length;
    windowMicros = plan.windowMicros();
    dimInterners = new Interner[dimCount];
    dimIsDict = new boolean[dimCount];
    dimCodeScratch = new int[dimCount];
    final TableSchema logical = compiled.rawSchema();
    for (int d = 0; d < dimCount; d++) {
      final ColumnType type = logical.columns().get(plan.dimColumnIndexes()[d]).type();
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
    countColumns = resolveColumns(compiled.metricsSchema(), COUNT_ALGEBRA, "");
    dimColumns = metricsTarget.dimColumns();

    final List<String> counterNames = compiled.counters();
    counterColumns = new int[counterNames.size()][];
    for (int c = 0; c < counterNames.size(); c++) {
      counterColumns[c] =
          resolveColumns(compiled.metricsSchema(), COUNT_ALGEBRA, counterNames.get(c));
    }
    counterArmed = new boolean[counterNames.size()];

    final List<MeasureDeclaration> measures = compiled.measures();
    final int measureCount = measures.size();
    measureValueScratch = new double[measureCount];
    measureArmed = new boolean[measureCount];
    final List<FlatMeasure> flatList = new ArrayList<>();
    for (int m = 0; m < measureCount; m++) {
      final MeasureDeclaration measure = measures.get(m);
      for (final Algebra algebra : measure.algebras()) {
        final TableSchema targetSchema =
            algebra.shape() == Algebra.PartialsShape.WIDE
                ? compiled.metricsSchema()
                : compiled.histSchema();
        flatList.add(
            new FlatMeasure(
                m, measure.name(), algebra, resolveColumns(targetSchema, algebra, measure.name())));
      }
    }
    flats = flatList.toArray(FlatMeasure[]::new);

    active = new ActiveWindow();
  }

  private static int[] resolveColumns(
      final TableSchema schema, final Algebra algebra, final String measureOrCounterName) {
    final List<Algebra.PartialColumn> partials = algebra.partialColumns(measureOrCounterName);
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
  // poll-thread face
  // ------------------------------------------------------------------

  /**
   * Sets dim {@code dim}'s (declaration-order index) value for the record about to be {@link
   * #fold}ed, interning it through this rider's own rider-lifetime {@link Interner}
   * (allocation-free for a value seen before). Must be called for every declared {@code
   * STRING_DICT} dim before {@link #fold} — mirrors {@code RowAppender}'s own typed, positional
   * put-then-complete convention, including its trust in the caller to set every column.
   *
   * <p>{@code value} may be {@code null} when the virtual schema declares this dim nullable — the
   * resulting group carries a {@code NULL} for it, it is not dropped. This is deliberately
   * different from {@link MetricsRider}'s own "a null dim has no group" rule (see its class
   * javadoc): a row-fed measure can always be re-observed on a later row once its data arrives, but
   * a record-fed count has exactly one chance to be counted at all, so folding it under an honest
   * {@code NULL} dim beats silently dropping it (see {@code
   * io.camunda.analytics.lake.translate.LakeTranslator}'s branch-count fold, whose {@code
   * source_element_id}/{@code target_element_id} dims are {@code NULL} whenever the flow's
   * definition was never resolved).
   */
  public PollFedRider putDict(final int dim, final CharSequence value) {
    dimCodeScratch[dim] = value == null ? NULL_CODE : dimInterners[dim].intern(value);
    return this;
  }

  /** Same as {@link #putDict}, for a declared {@code INT} dim. */
  public PollFedRider putInt(final int dim, final int value) {
    dimCodeScratch[dim] = value;
    return this;
  }

  /**
   * Stages {@code value} to be folded into declared measure {@code measure}'s (declaration-order
   * index) every algebra when {@link #fold} runs. Not calling this before {@link #fold} for a given
   * record leaves that measure unstaged for it — the measure is simply skipped (see class javadoc's
   * "Null measures skip only that measure" section), not the whole row. The staged value is cleared
   * after every {@link #fold} call, staged or not, so a later record that forgets to call this
   * never accidentally reuses a stale value.
   */
  public PollFedRider putDoubleMeasure(final int measure, final double value) {
    measureValueScratch[measure] = value;
    measureArmed[measure] = true;
    return this;
  }

  /**
   * Arms named counter {@code counter} (declaration order, see {@code
   * EntityMetrics.Builder#counter}) to increment once when {@link #fold} runs for the record about
   * to be folded. Unlike {@link EntityMetrics.Builder#count()}'s own unconditional per-row total,
   * which every {@link #fold} call increments regardless, a named counter only increments for the
   * records that actually call this — the caller decides which counter (if any) a given record
   * contributes to (e.g. exactly one of several mutually exclusive per-type counters).
   */
  public PollFedRider incrementCounter(final int counter) {
    counterArmed[counter] = true;
    return this;
  }

  /**
   * Completes one record's fold: assigns its window slot from {@code eventTimeMicros}, finds or
   * creates its group in the currently-active window (via the dim codes set by {@link #putDict}/
   * {@link #putInt} since the last call), unconditionally increments the group's total count, then
   * increments every counter armed via {@link #incrementCounter} and folds every measure staged via
   * {@link #putDoubleMeasure} since the last call. Poll thread only, allocation-free at steady
   * state. Every armed/staged flag is cleared before returning, whether or not it fired.
   */
  public void fold(final long eventTimeMicros) {
    final long slot =
        windowMicros > 0 ? Math.floorDiv(eventTimeMicros, windowMicros) * windowMicros : 0L;
    final ActiveWindow window = active;
    final int group = window.groups.findOrAdd(slot, dimCodeScratch);
    window.countAccumulatorFor(group).add(0L);
    for (int c = 0; c < counterArmed.length; c++) {
      if (counterArmed[c]) {
        window.counterAccumulatorFor(c, group).add(0L);
        counterArmed[c] = false;
      }
    }
    for (int m = 0; m < measureArmed.length; m++) {
      if (measureArmed[m]) {
        for (int f = 0; f < flats.length; f++) {
          if (flats[f].measureIndex() == m) {
            window.measureAccumulatorFor(f, group).addDouble(measureValueScratch[m]);
          }
        }
        measureArmed[m] = false;
      }
    }
  }

  // ------------------------------------------------------------------
  // SealRider face
  // ------------------------------------------------------------------

  @Override
  public void onSealed(final SortedRun run) {
    // Deliberately a no-op: this rider never receives raw rows -- LakeTranslator calls fold(...)
    // directly on the poll thread for every qualifying record (see class javadoc). FlushLoop still
    // calls onSealed once per sealed segment for every rider uniformly, so this is expected, not a
    // gap.
  }

  @Override
  public void onPollBoundary() {
    frozenQueue.addLast(active);
    active = new ActiveWindow();
  }

  @Override
  public void rollbackPollBoundary() {
    active = frozenQueue.removeLast();
  }

  @Override
  public boolean hasPendingPollFedData() {
    // In practice at most one window is ever queued here (each SinkPipeline boundary swap is
    // matched, in order, by exactly one drain -- see class javadoc's alignment section), but this
    // checks every queued window rather than leaning on that as an unenforced invariant: a stray
    // extra swap with no intervening drain must not hide real pending data behind an earlier,
    // already-empty one.
    for (final ActiveWindow window : frozenQueue) {
      if (window.groups.count() > 0) {
        return true;
      }
    }
    return false;
  }

  @Override
  public Map<String, List<DataFileResult>> onWindowClose() {
    final ActiveWindow frozen = frozenQueue.pollFirst();
    if (frozen == null || frozen.groups.count() == 0) {
      return Map.of();
    }
    final int groupCount = frozen.groups.count();
    for (int g = 0; g < groupCount; g++) {
      drainWideRow(frozen, g);
      if (histTarget != null) {
        drainTallRows(frozen, g);
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
    return files;
  }

  @Override
  public void abortWindow() {
    // Only the frozen queue is touched here -- `active` is poll-thread-owned, and the pipeline is
    // terminal from this point on (the caller must stop feeding it once SinkPipeline#isFailed()
    // reports true), so a last in-flight fold() racing this call is harmless: its data is never
    // drained, and this whole rider is abandoned along with the failed pipeline. Mutating `active`
    // itself here would instead risk a genuine cross-thread data race against that in-flight fold.
    frozenQueue.clear();
    metricsTarget.abort();
    if (histTarget != null) {
      histTarget.abort();
    }
  }

  /**
   * One {@code _metrics} row for {@code group} of {@code window}: window, dims, total count, every
   * named counter, then every {@link Algebra.PartialsShape#WIDE} measure's own columns.
   */
  private void drainWideRow(final ActiveWindow window, final int group) {
    final Segment segment = metricsTarget.segment();
    final int row = segment.size();
    writePrefix(segment, row, window, group, dimColumns);
    final Algebra.Accumulator countAccumulator = window.countAccumulatorFor(group);
    metricsTarget.wideWriter.arm(row, countColumns);
    countAccumulator.drain(metricsTarget.wideWriter);
    for (int c = 0; c < counterColumns.length; c++) {
      drainOrDefaultWide(segment, row, window.counterAccumulatorFor(c, group), counterColumns[c]);
    }
    for (int f = 0; f < flats.length; f++) {
      final FlatMeasure flat = flats[f];
      if (flat.algebra().shape() != Algebra.PartialsShape.WIDE) {
        continue;
      }
      drainOrDefaultWide(
          segment, row, window.measureAccumulatorFor(f, group), flat.absoluteColumns());
    }
    segment.rowCompleted();
    metricsTarget.flushIfFull();
  }

  /**
   * Writes {@code accumulator}'s drained columns at {@code row}, or — when it was never touched for
   * this group (e.g. a counter that never armed for it, or a measure never staged for it) — the
   * schema-driven default (honest {@code NULL} for a nullable column, {@code 0}/{@code 0.0}
   * otherwise). Mirrors {@link MetricsRider#drainWideRow}'s own identical fallback.
   */
  private void drainOrDefaultWide(
      final Segment segment,
      final int row,
      final Algebra.Accumulator accumulator,
      final int[] absoluteColumns) {
    if (accumulator.isEmpty()) {
      for (final int absoluteColumn : absoluteColumns) {
        final TableSchema.Column column = segment.schema().columns().get(absoluteColumn);
        if (column.nullable()) {
          segment.vector(absoluteColumn).setNull(row);
        } else if (column.type() == ColumnType.DOUBLE) {
          ((ColumnVector.DoubleColumn) segment.vector(absoluteColumn)).set(row, 0.0);
        } else {
          ((ColumnVector.LongColumn) segment.vector(absoluteColumn)).set(row, 0L);
        }
      }
      return;
    }
    metricsTarget.wideWriter.arm(row, absoluteColumns);
    accumulator.drain(metricsTarget.wideWriter);
  }

  /** N {@code _hist} rows per group per {@link Algebra.PartialsShape#TALL} measure. */
  private void drainTallRows(final ActiveWindow window, final int group) {
    for (int f = 0; f < flats.length; f++) {
      final FlatMeasure flat = flats[f];
      if (flat.algebra().shape() != Algebra.PartialsShape.TALL) {
        continue;
      }
      final Algebra.Accumulator accumulator = window.measureAccumulatorFor(f, group);
      if (accumulator.isEmpty()) {
        continue;
      }
      histTarget.tallWriter.arm(
          window, group, flat.measureName(), flat.algebra().scheme(), flat.absoluteColumns());
      accumulator.drain(histTarget.tallWriter);
    }
  }

  /**
   * Writes the shared {@code (window_start, dims...)} prefix into {@code row}. {@code
   * targetDimColumns} must be resolved against {@code segment}'s own schema (see the two call
   * sites: {@link #drainWideRow} passes {@link #metricsTarget}'s own, {@link
   * DrainTarget.TallWriter#beginRow} passes its own enclosing {@link DrainTarget}'s own — the
   * {@code _metrics} and {@code _hist} schemas place dims at the same column indices by
   * construction, but this method takes the array explicitly rather than leaning on that
   * coincidence, exactly mirroring {@link MetricsRider#writePrefix}'s own parameterization).
   */
  private void writePrefix(
      final Segment segment,
      final int row,
      final ActiveWindow window,
      final int group,
      final int[] targetDimColumns) {
    final TableSchema schema = segment.schema();
    ((ColumnVector.LongColumn) segment.vector(schema.familyDayColumn()))
        .set(row, window.groups.slotOf(group));
    for (int d = 0; d < targetDimColumns.length; d++) {
      final int code = window.groups.dimCodeOf(d, group);
      if (dimIsDict[d] && code == NULL_CODE) {
        segment.vector(targetDimColumns[d]).setNull(row);
      } else if (dimIsDict[d]) {
        ((ColumnVector.DictColumn) segment.vector(targetDimColumns[d]))
            .set(row, dimInterners[d].valueOf(code));
      } else {
        ((ColumnVector.IntColumn) segment.vector(targetDimColumns[d])).set(row, code);
      }
    }
  }

  // ------------------------------------------------------------------
  // helpers
  // ------------------------------------------------------------------

  /**
   * One declared (measure, algebra) pair's rider-lifetime metadata — {@code measureIndex} names
   * which of {@link #measureValueScratch}/{@link #measureArmed} this flat reads at fold time
   * (several flats, one per algebra, can share the same {@code measureIndex}); {@code measureName}
   * is only needed to arm {@link DrainTarget#tallWriter} for a {@link Algebra.PartialsShape#TALL}
   * flat. Per-group accumulator pools live in {@link ActiveWindow} instead (see that class's own
   * javadoc for why), not here.
   */
  private record FlatMeasure(
      int measureIndex, String measureName, Algebra algebra, int[] absoluteColumns) {}

  /**
   * One window's worth of poll-fed state: a fresh {@link GroupTable}, one count {@link
   * Algebra.Accumulator} per group, one pool per named counter, and one pool per declared (measure,
   * algebra) flat. Exactly one is "active" (poll thread writes it) at a time; every other instance
   * in existence is either queued in {@link #frozenQueue} awaiting drain, or being drained by the
   * flush thread — never both at once, and never written to again once it stops being {@link
   * #active}.
   */
  private final class ActiveWindow {

    private final GroupTable groups = new GroupTable(dimCount);
    private final List<Algebra.Accumulator> countPool = new ArrayList<>();
    private final List<List<Algebra.Accumulator>> counterPools = new ArrayList<>();
    private final List<List<Algebra.Accumulator>> measurePools = new ArrayList<>();

    ActiveWindow() {
      for (int c = 0; c < counterColumns.length; c++) {
        counterPools.add(new ArrayList<>());
      }
      for (int f = 0; f < flats.length; f++) {
        measurePools.add(new ArrayList<>());
      }
    }

    private Algebra.Accumulator countAccumulatorFor(final int group) {
      while (countPool.size() <= group) {
        countPool.add(COUNT_ALGEBRA.create());
      }
      return countPool.get(group);
    }

    private Algebra.Accumulator counterAccumulatorFor(final int counterIndex, final int group) {
      final List<Algebra.Accumulator> pool = counterPools.get(counterIndex);
      while (pool.size() <= group) {
        pool.add(COUNT_ALGEBRA.create());
      }
      return pool.get(group);
    }

    private Algebra.Accumulator measureAccumulatorFor(final int flatIndex, final int group) {
      final List<Algebra.Accumulator> pool = measurePools.get(flatIndex);
      while (pool.size() <= group) {
        pool.add(flats[flatIndex].algebra().create());
      }
      return pool.get(group);
    }
  }

  /**
   * This rider's drain machinery for one generated schema ({@code _metrics} or {@code _hist}): a
   * reusable segment, its sorter, its day router, and the {@link Algebra.RowWriter} adapters that
   * map an accumulator's relative column writes onto the segment — the same shape {@link
   * MetricsRider}'s own {@code DrainTarget} uses, since both riders' drain sides do exactly the
   * same job once the accumulators are ready.
   */
  private final class DrainTarget {

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
     * Adapter for WIDE algebras: the rider owns the physical row (several measures/counters share
     * it), so {@code beginRow}/{@code endRow} are no-ops and relative writes land at pre-armed
     * absolute columns of the armed row.
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
      public void writeDouble(final int columnIndex, final double value) {
        ((ColumnVector.DoubleColumn) segment.vector(absoluteColumns[columnIndex])).set(row, value);
      }

      @Override
      public void writeNull(final int columnIndex) {
        segment.vector(absoluteColumns[columnIndex]).setNull(row);
      }

      @Override
      public void endRow() {}
    }

    /**
     * Adapter for TALL algebras: every {@code beginRow} opens a fresh physical row (bin rows), the
     * rider stamps the shared prefix (window, dims, measure, scheme), relative writes land at the
     * armed absolute columns, {@code endRow} completes the row and lets a full segment flush.
     */
    final class TallWriter implements Algebra.RowWriter {

      private ActiveWindow window;
      private int group;
      private String measure;
      private String scheme;
      private int[] absoluteColumns;
      private int row;

      void arm(
          final ActiveWindow armedWindow,
          final int armedGroup,
          final String armedMeasure,
          final String armedScheme,
          final int[] columns) {
        window = armedWindow;
        group = armedGroup;
        measure = armedMeasure;
        scheme = armedScheme;
        absoluteColumns = columns;
      }

      @Override
      public void beginRow() {
        row = segment.size();
        writePrefix(segment, row, window, group, dimColumns);
        ((ColumnVector.DictColumn) segment.vector(measureColumn)).set(row, measure);
        ((ColumnVector.DictColumn) segment.vector(schemeColumn)).set(row, scheme);
      }

      @Override
      public void writeLong(final int columnIndex, final long value) {
        ((ColumnVector.LongColumn) segment.vector(absoluteColumns[columnIndex])).set(row, value);
      }

      @Override
      public void writeDouble(final int columnIndex, final double value) {
        ((ColumnVector.DoubleColumn) segment.vector(absoluteColumns[columnIndex])).set(row, value);
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
