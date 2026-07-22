/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.sink.algebra;

import java.util.List;

/**
 * A mergeable metric algebra: the contract a measure's raw {@code long} values are folded through
 * to produce <b>partial</b> state that can be merged with any other partial state of the same
 * algebra, forever, without access to the original values.
 *
 * <p>Every algebra is split across two sides that never call each other:
 *
 * <ul>
 *   <li>The <b>hot side</b> ({@link Accumulator}) is plain Java, garbage-free, and runs on the
 *       flush thread once per (dims, window) group per flush: {@link Accumulator#reset()}, {@link
 *       Accumulator#add(long)} per raw value, {@link Accumulator#drain(RowWriter)} to emit the
 *       partial state as columns.
 *   <li>The <b>lazy side</b> ({@link #mergeProjection(String)}/{@link #finalizeProjection(String)},
 *       plus algebra-specific extras such as {@code ExpHistogramAlgebra#finalizeQuery}) is
 *       generated SQL text, executed later by whatever engine reads the partials table (DuckDB in
 *       this codebase, but the SQL is plain and portable).
 * </ul>
 *
 * <h2>Why no Java merge</h2>
 *
 * <p>There is deliberately no {@code Accumulator#merge(Accumulator)} method. Two partial rows are
 * merged by the table itself — {@code INSERT}/{@code GROUP BY} is the merge operator — so any SQL
 * engine, any number of hops downstream, in any process that never even links this library, can
 * keep merging partials forever. A Java-side merge would only merge two accumulators that happen to
 * be co-located in the same JVM; the whole point of storing MERGEABLE PARTIALS rather than
 * pre-computed answers is that merging must be re-derivable from the stored columns alone.
 *
 * <h2>Why denormalized, relational columns and never a blob</h2>
 *
 * <p>Partial state flattens to plain relational columns (longs, occasionally a low-cardinality
 * dictionary string), never a serialized blob. A blob would force every future reader to link (and
 * keep in sync with) this exact library's decoding code; flat columns let any SQL engine merge and
 * finalize the state using only {@code SUM}/{@code MIN}/{@code MAX}/window functions it already
 * has. This is also why {@link io.camunda.analytics.lake.sink.algebra.ExpHistogramAlgebra}
 * denormalizes each bin's boundaries ({@code bin_lo}/{@code bin_hi}) into the row instead of
 * storing just the bin index: a reader must never need this library's {@code index()}/{@code
 * lowerBound()} bit-math to interpret a stored row.
 */
public interface Algebra {

  /**
   * Stable identifier for this algebra instance, including any parameters that change its stored
   * representation (e.g. {@code "exp2ll-3"} for a base-2 log-linear histogram at scale 3). Two
   * partials rows may only ever be merged if they carry the same scheme — this is exactly what
   * {@link io.camunda.analytics.lake.metrics.CompiledEntityMetrics#fingerprint()} folds in to
   * detect a meaning-changing declaration edit.
   */
  String scheme();

  /**
   * Creates a new accumulator. Called at build/wiring time only — the returned instance is pooled
   * and reused across (dims, window) groups and across flushes, never allocated per record.
   */
  Accumulator create();

  /**
   * Which physical partials-table shape this algebra's rows fit: {@link PartialsShape#WIDE} shares
   * one row per (dims, window) with every other wide measure (e.g. scalar stats, prefixing its own
   * columns with the measure name so several measures can coexist on that one row — see the {@code
   * _metrics} table in {@code io.camunda.analytics.lake.metrics.CompiledEntityMetrics}); {@link
   * PartialsShape#TALL} emits its own row per some identity within the group (e.g. one row per
   * histogram bin, disambiguating measures through a {@code measure} column instead of column
   * prefixing — the {@code _hist} table).
   */
  PartialsShape shape();

  /** See {@link #shape()}. */
  enum PartialsShape {
    WIDE,
    TALL
  }

  /**
   * The physical column names (and nullability) this algebra's {@link Accumulator#drain} writes,
   * given the measure it is folding. Names are unprefixed for algebras whose partials table already
   * disambiguates the measure through a dedicated dimension column (e.g. {@code
   * ExpHistogramAlgebra} relies on the {@code measure} column of the {@code _hist} table); they are
   * prefixed with the measure name for algebras sharing one wide row per (dims, window) across
   * every measure (e.g. {@code ScalarStatsAlgebra}'s {@code <measure>_cnt}/{@code
   * <measure>_sum}/...).
   */
  List<PartialColumn> partialColumns(String measure);

  /**
   * Extra columns, beyond whatever the caller already groups by, that this algebra's merge/finalize
   * queries must additionally {@code GROUP BY} to avoid collapsing distinct partial rows together.
   * Empty for algebras that emit one row per (dims, window) (e.g. scalar stats); for algebras that
   * emit multiple rows per group (e.g. one row per histogram bin) this names the columns that
   * identify which row is which (e.g. {@code bin_lo}, {@code bin_hi}).
   */
  List<String> mergeGroupColumns();

  /**
   * A SQL projection fragment (comma-joined {@code expr AS alias} pairs, no {@code FROM}/{@code
   * GROUP BY} of its own) that merges finer partial rows of this measure into coarser partial rows
   * of the identical shape. Intended to be embedded, alongside every other measure's own fragment,
   * into one shared {@code SELECT ... GROUP BY} query the caller assembles — this is what lets one
   * wide partials row cover several measures without each algebra needing to know about the others.
   */
  String mergeProjection(String measure);

  /**
   * A SQL projection fragment producing this measure's final answer columns from its own partial
   * columns (e.g. {@code cnt}, {@code sum}, {@code avg}, {@code min}, {@code max} for scalar
   * stats).
   *
   * @throws UnsupportedOperationException if this algebra's finalize step cannot be expressed as a
   *     flat per-row projection alongside other measures' own projections (e.g. a percentile
   *     extraction needs a cumulative scan across several rows of the same group — see {@code
   *     ExpHistogramAlgebra#finalizeQuery}/{@code #finalizeMacroSql} for that algebra's own,
   *     necessarily standalone, finalize SQL instead).
   */
  String finalizeProjection(String measure);

  /**
   * One partial-state column an {@link Accumulator} drains, in the fixed order {@link
   * Accumulator#drain} writes it in.
   *
   * @param name the physical column name (see {@link Algebra#partialColumns})
   * @param nullable whether a merge/finalize reader may ever observe a {@code NULL} here
   */
  record PartialColumn(String name, boolean nullable) {}

  /**
   * The hot side of an algebra: folds raw {@code long} values into partial state. Implementations
   * must be monomorphic, box nothing, and allocate nothing per {@link #add(long)} call — see {@code
   * SinkBatchAllocationGuardTest} in {@code io.camunda.analytics.lake.sink.batch} for the measuring
   * technique this contract is held to.
   */
  interface Accumulator {

    /** Zeroes the accumulator's state without allocating. */
    void reset();

    /** Folds one raw value into the state. Allocation-free. */
    void add(long value);

    /** True if no value has been folded in since the last {@link #reset()} or {@link #drain}. */
    boolean isEmpty();

    /**
     * Writes the current partial state as one or more rows (see {@link #partialColumns}'s javadoc
     * for why some algebras emit more than one row per group) to {@code writer}, then resets. Must
     * not be called while {@link #isEmpty()} — callers skip draining empty groups entirely, which
     * is also why partial columns marked non-nullable here are never actually observed {@code NULL}
     * in practice even though a table schema may still declare them nullable for forward
     * compatibility (see {@code ScalarStatsAlgebra}'s own javadoc for the concrete case).
     */
    void drain(RowWriter writer);
  }

  /**
   * The narrow, allocation-free row-writer callback an {@link Accumulator} drains through.
   * Deliberately defined in this package rather than reusing {@code
   * io.camunda.analytics.lake.sink.RowAppender}: this library must stay wireable into any future
   * pipeline (or none at all, e.g. a test), not coupled to this codebase's own L0 sink.
   */
  interface RowWriter {

    /** Starts one emitted row. */
    void beginRow();

    /**
     * Writes one column of the row started by {@link #beginRow()}.
     *
     * @param columnIndex position into the algebra's own {@link Algebra#partialColumns} list for
     *     the measure being drained (not a raw-schema or partials-table column index — the caller
     *     assembling a full physical row is responsible for that translation)
     */
    void writeLong(int columnIndex, long value);

    /** Completes the row started by {@link #beginRow()}. */
    void endRow();
  }
}
