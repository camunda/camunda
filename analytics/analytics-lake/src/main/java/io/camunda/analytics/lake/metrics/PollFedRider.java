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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * The poll-fed twin of {@link MetricsRider}: folds directly from qualifying Zeebe records, on the
 * poll thread, into a {@code count()}-only entity's {@code _metrics} partials — for metric families
 * whose source records produce no raw row at all (sequence-flow-taken events, process-instance-
 * started events; see {@code io.camunda.analytics.lake.translate.LakeTranslator}'s own call sites).
 * {@link MetricsRider} cannot serve these: its {@link SealRider#onSealed} only ever sees rows that
 * already exist in a raw table, and these records never produce one.
 *
 * <h2>Two faces, two threads</h2>
 *
 * <ul>
 *   <li>The <b>poll-thread face</b> ({@link #putDict}/{@link #putInt}/{@link #fold}) is what {@code
 *       LakeTranslator} calls once per qualifying record, mirroring {@code RowAppender}'s own
 *       typed, positional, allocation-free {@code put*}-then-complete convention: set every
 *       declared dim (in declaration order) via {@link #putDict}/{@link #putInt}, then call {@link
 *       #fold} with the record's event time. Allocation-free at steady state via the same {@link
 *       GroupTable} + rider-lifetime {@link Interner} pattern {@link MetricsRider} uses for its own
 *       dims.
 *   <li>The <b>{@link SealRider} face</b> ({@link #onWindowClose}/{@link #abortWindow}, plus the
 *       {@link SealRider#onPollBoundary}/{@link SealRider#rollbackPollBoundary} hooks below) is
 *       what the host pipeline's flush thread drives, exactly like {@link MetricsRider}.
 * </ul>
 *
 * <h2>The alignment mechanism (load-bearing)</h2>
 *
 * <p>The poll thread owns one "active" {@link ActiveWindow} at a time — the {@link GroupTable} +
 * count accumulators every {@link #fold} call writes into. At the exact moment the host pipeline
 * seals a file boundary (TIME_DUE/SIZE_CAP/SHUTDOWN — see {@link SealRider#onPollBoundary}'s own
 * javadoc for precisely where and why), {@link #onPollBoundary()} <b>swaps</b>: the active window
 * is frozen (pushed onto {@link #frozenQueue}) and a fresh, empty one takes its place. The flush
 * thread's {@link #onWindowClose()} drains <em>only</em> the frozen window — never the active one,
 * which by then may already contain records folded after the boundary.
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
 * rider calls ({@code metricsSchema()}, {@code fingerprint()}, {@code dims()}, {@code riderPlan()})
 * already validates purely against column name/type, never against a real backing table, so this
 * "virtual schema" pattern needed no special-casing anywhere in the declaration machinery — it is
 * the intended generalization the {@code rawSchema} parameter always supported.
 *
 * <h2>Scope of this milestone</h2>
 *
 * <p>Only {@code count()}-only declarations are supported (no {@code .measure(...)}, hence no
 * histogram): every current record-fed entity (branch counts, started counters) is exactly that
 * shape. Extending this to fold an actual measured value directly from a record (a {@code
 * putMeasure(int, long)} poll-thread setter, mirroring {@link MetricsRider}'s own {@code
 * FlatAlgebra} pool) is a natural next step but is not needed yet, so it is not built — see the
 * constructor's own validation for the explicit, documented boundary.
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

  // --- swap state: poll thread owns `active`; the flush thread only ever reads frozenQueue ---
  private ActiveWindow active;
  private final ConcurrentLinkedDeque<ActiveWindow> frozenQueue = new ConcurrentLinkedDeque<>();

  // --- drain machinery (flush thread only, reused across windows) ---
  private final DrainTarget metricsTarget;
  private final int[] countColumns;
  private final int[] dimColumns;

  public PollFedRider(
      final CompiledEntityMetrics compiled,
      final BatchEncoder.Factory encoderFactory,
      final int drainRowCapacity) {
    if (!compiled.counted()) {
      throw new IllegalArgumentException(
          "entity '"
              + compiled.entityName()
              + "': PollFedRider requires a count() declaration -- a record-fed entity folds"
              + " directly from qualifying records, and counting them is the only thing this"
              + " rider knows how to do today (see its own class javadoc's \"Scope\" section).");
    }
    if (!compiled.measures().isEmpty()) {
      throw new IllegalArgumentException(
          "entity '"
              + compiled.entityName()
              + "': PollFedRider does not support measures yet (only count()) -- declares "
              + compiled.measures().size()
              + " measure(s). Extend this rider (a putMeasure(...) fold-time setter mirroring"
              + " putDict/putInt) before folding a measured value directly from a record.");
    }
    if (compiled.hasHistogram()) {
      // Unreachable given the check above (a TALL algebra can only ever arrive via a measure);
      // kept as its own explicit guard so a future change to either check can't silently let a
      // histogram-shaped declaration through without a _hist drain target ever being built.
      throw new IllegalArgumentException(
          "entity '"
              + compiled.entityName()
              + "': PollFedRider does not support histogram-shaped measures yet.");
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
    countColumns = resolveColumns(compiled.metricsSchema(), COUNT_ALGEBRA);
    dimColumns = metricsTarget.dimColumns();

    active = new ActiveWindow();
  }

  private static int[] resolveColumns(final TableSchema schema, final Algebra algebra) {
    final List<Algebra.PartialColumn> partials = algebra.partialColumns("");
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
   * Completes one record's fold: assigns its window slot from {@code eventTimeMicros}, finds or
   * creates its group in the currently-active window (via the dim codes set by {@link #putDict}/
   * {@link #putInt} since the last call), and increments that group's count. Poll thread only,
   * allocation-free at steady state.
   */
  public void fold(final long eventTimeMicros) {
    final long slot =
        windowMicros > 0 ? Math.floorDiv(eventTimeMicros, windowMicros) * windowMicros : 0L;
    final ActiveWindow window = active;
    final int group = window.groups.findOrAdd(slot, dimCodeScratch);
    window.countAccumulatorFor(group).add(0L);
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
    }
    final List<DataFileResult> files = metricsTarget.finish();
    return files.isEmpty() ? Map.of() : Map.of(compiled.metricsSchema().table(), files);
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
  }

  /**
   * One {@code _metrics} row for {@code group} of {@code window}: window, dims, then {@code cnt}.
   */
  private void drainWideRow(final ActiveWindow window, final int group) {
    final Segment segment = metricsTarget.segment();
    final int row = segment.size();
    writePrefix(segment, row, window, group);
    final Algebra.Accumulator accumulator = window.countAccumulatorFor(group);
    metricsTarget.wideWriter.arm(row, countColumns);
    accumulator.drain(metricsTarget.wideWriter);
    segment.rowCompleted();
    metricsTarget.flushIfFull();
  }

  private void writePrefix(
      final Segment segment, final int row, final ActiveWindow window, final int group) {
    final TableSchema schema = segment.schema();
    ((ColumnVector.LongColumn) segment.vector(schema.familyDayColumn()))
        .set(row, window.groups.slotOf(group));
    for (int d = 0; d < dimColumns.length; d++) {
      final int code = window.groups.dimCodeOf(d, group);
      if (dimIsDict[d] && code == NULL_CODE) {
        segment.vector(dimColumns[d]).setNull(row);
      } else if (dimIsDict[d]) {
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

  /**
   * One window's worth of poll-fed state: a fresh {@link GroupTable} plus one count {@link
   * Algebra.Accumulator} per group. Exactly one is "active" (poll thread writes it) at a time;
   * every other instance in existence is either queued in {@link #frozenQueue} awaiting drain, or
   * being drained by the flush thread — never both at once, and never written to again once it
   * stops being {@link #active}.
   */
  private final class ActiveWindow {

    private final GroupTable groups = new GroupTable(dimCount);
    private final List<Algebra.Accumulator> countPool = new ArrayList<>();

    private Algebra.Accumulator countAccumulatorFor(final int group) {
      while (countPool.size() <= group) {
        countPool.add(COUNT_ALGEBRA.create());
      }
      return countPool.get(group);
    }
  }

  /**
   * This rider's {@code _metrics} drain machinery: a reusable segment, its sorter, and its day
   * router — the wide-only trim of {@link MetricsRider}'s own {@code DrainTarget} (no tall/hist
   * side; see class javadoc's "Scope" section for why).
   */
  private final class DrainTarget {

    private final Segment segment;
    private final SegmentSorter sorter;
    private final DayRouter router;
    private final int[] dimColumns;
    final WideWriter wideWriter = new WideWriter();

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
     * Adapter for the one {@code cnt} column — see {@link MetricsRider}'s own {@code WideWriter}.
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
  }
}
