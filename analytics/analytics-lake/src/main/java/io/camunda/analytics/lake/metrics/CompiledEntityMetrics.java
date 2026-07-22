/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.metrics;

import io.camunda.analytics.lake.sink.ColumnType;
import io.camunda.analytics.lake.sink.TableSchema;
import io.camunda.analytics.lake.sink.algebra.Algebra;
import io.camunda.analytics.lake.sink.algebra.ExpHistogramAlgebra;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * An {@link EntityMetrics} declaration, validated and compiled: the flat {@link RiderPlan}, the two
 * generated partials-table schemas, a stable {@link #fingerprint()}, and the generated SQL that
 * merges and finalizes those partials. See {@code io.camunda.analytics.lake.metrics}'s package
 * javadoc for why nothing here is wired into a pipeline yet.
 *
 * <h2>The two partials tables</h2>
 *
 * <ul>
 *   <li>{@code <entity>_metrics} — one <b>wide</b> row per (dims, window): every {@link
 *       Algebra.PartialsShape#WIDE} measure (e.g. scalar stats) contributes its own
 *       measure-prefixed columns to this same row.
 *   <li>{@code <entity>_hist} — one <b>tall</b> row per (dims, window, measure, scheme, bin): every
 *       {@link Algebra.PartialsShape#TALL} measure (e.g. a histogram) shares this one generic
 *       layout, disambiguated by its own {@code measure}/{@code scheme} columns instead of column
 *       prefixing — unlike the metrics table, this schema does not vary with which/how many
 *       measures use it, so it is generated unconditionally (see {@link #histSchema()}'s javadoc).
 * </ul>
 */
public final class CompiledEntityMetrics {

  private static final String METRICS_SUFFIX = "_metrics";
  private static final String HIST_SUFFIX = "_hist";
  private static final String WINDOW_START = "window_start";
  private static final String MEASURE_COLUMN = "measure";
  private static final String SCHEME_COLUMN = "scheme";

  private final String entityName;
  private final TableSchema rawSchema;
  private final List<String> dims;
  private final long windowMicros;
  private final List<MeasureDeclaration> measures;
  private final RiderPlan riderPlan;

  CompiledEntityMetrics(
      final String entityName,
      final TableSchema rawSchema,
      final List<String> dims,
      final long windowMicros,
      final List<MeasureDeclaration> measures,
      final RiderPlan riderPlan) {
    this.entityName = entityName;
    this.rawSchema = rawSchema;
    this.dims = List.copyOf(dims);
    this.windowMicros = windowMicros;
    this.measures = List.copyOf(measures);
    this.riderPlan = riderPlan;
  }

  /** The precomputed, per-record-interpretation-free folding plan — see {@link RiderPlan}. */
  public RiderPlan riderPlan() {
    return riderPlan;
  }

  /** The declared entity name (also the prefix of both generated partials table names). */
  public String entityName() {
    return entityName;
  }

  /** The raw table schema this declaration folds from. */
  public TableSchema rawSchema() {
    return rawSchema;
  }

  /** Declared dim column names, in declaration order (the generated schemas' dim order). */
  public List<String> dims() {
    return dims;
  }

  /** Declared measures with their algebras, in declaration order. */
  List<MeasureDeclaration> measures() {
    return measures;
  }

  /** The wide {@code <entity>_metrics} partials schema — see this class's own javadoc. */
  public TableSchema metricsSchema() {
    final List<TableSchema.Column> columns = new ArrayList<>();
    final int[] fieldId = {1};
    columns.add(
        new TableSchema.Column(
            WINDOW_START,
            ColumnType.LONG,
            fieldId[0]++,
            false,
            dims.size(), // sort order: after every dim
            true, // family-day source
            TableSchema.LogicalType.TIMESTAMPTZ));
    for (int i = 0; i < dims.size(); i++) {
      columns.add(dimColumn(dims.get(i), i, fieldId));
    }
    for (final MeasureDeclaration measure : measures) {
      for (final Algebra algebra : measure.algebras()) {
        if (algebra.shape() == Algebra.PartialsShape.WIDE) {
          for (final Algebra.PartialColumn column : algebra.partialColumns(measure.name())) {
            columns.add(
                new TableSchema.Column(
                    column.name(), ColumnType.LONG, fieldId[0]++, column.nullable(), -1, false));
          }
        }
      }
    }
    return new TableSchema(entityName + METRICS_SUFFIX, columns);
  }

  /**
   * The tall {@code <entity>_hist} partials schema — see this class's own javadoc. Generated
   * unconditionally, even for an entity with no histogram-shaped measure: its layout never varies
   * with which measures use it (unlike {@link #metricsSchema()}), so there is no meaningful
   * "smaller" schema to produce in that case, and an unused table is harmless.
   */
  public TableSchema histSchema() {
    final List<TableSchema.Column> columns = new ArrayList<>();
    final int[] fieldId = {1};
    columns.add(
        new TableSchema.Column(
            WINDOW_START,
            ColumnType.LONG,
            fieldId[0]++,
            false,
            dims.size(),
            true,
            TableSchema.LogicalType.TIMESTAMPTZ));
    for (int i = 0; i < dims.size(); i++) {
      columns.add(dimColumn(dims.get(i), i, fieldId));
    }
    columns.add(
        new TableSchema.Column(
            MEASURE_COLUMN, ColumnType.STRING_DICT, fieldId[0]++, false, dims.size() + 1, false));
    columns.add(
        new TableSchema.Column(
            SCHEME_COLUMN, ColumnType.STRING_DICT, fieldId[0]++, false, -1, false));
    columns.add(
        new TableSchema.Column(
            "bin_lo", ColumnType.LONG, fieldId[0]++, false, dims.size() + 2, false));
    columns.add(new TableSchema.Column("bin_hi", ColumnType.LONG, fieldId[0]++, false, -1, false));
    columns.add(new TableSchema.Column("cnt", ColumnType.LONG, fieldId[0]++, false, -1, false));
    return new TableSchema(entityName + HIST_SUFFIX, columns);
  }

  private TableSchema.Column dimColumn(final String dim, final int sortOrder, final int[] fieldId) {
    final TableSchema.Column raw = rawSchema.columns().get(riderPlan.dimColumnIndexes()[sortOrder]);
    return new TableSchema.Column(dim, raw.type(), fieldId[0]++, raw.nullable(), sortOrder, false);
  }

  /**
   * 12 hex characters of {@code SHA-256} over a canonical string answering exactly one question:
   * "do stored partial rows produced under this declaration mean the same thing as one produced
   * under that declaration" — i.e. can they ever be merged together. Folds in the entity name, dims
   * (sorted — declaration order does not change meaning), window duration, and every (measure,
   * scheme) pair (sorted), plus the literal {@code "nulls=skip"} marking today's fixed choice to
   * never emit a partial row for an empty group (see {@link Algebra.Accumulator#drain}'s javadoc)
   * as part of what the fingerprint answers for.
   *
   * <p>Deliberately <b>excluded</b>: anything that changes only <em>how</em> rows are produced, not
   * what they mean — declared dim/measure order (a reordering test asserts this), sort order, flush
   * cadence, ring/segment sizing, and the algebra implementation class names themselves (only their
   * {@link Algebra#scheme()} id is folded in, since two different classes implementing the
   * identical scheme would still produce mergeable rows, and the same class renamed/refactored must
   * not look like a new scheme).
   */
  public String fingerprint() {
    final StringBuilder canonical = new StringBuilder();
    canonical.append(entityName).append('|');
    canonical.append(String.join(",", dims.stream().sorted().toList())).append('|');
    canonical.append(windowMicros).append('|');
    // Which raw column assigns the slot changes what a row means (same duration, different slot),
    // so the window source is part of the fingerprint; "-" when unwindowed.
    canonical
        .append(
            riderPlan.windowSourceColumn() < 0
                ? "-"
                : rawSchema.columns().get(riderPlan.windowSourceColumn()).name())
        .append('|');
    final List<String> measureSchemePairs = new ArrayList<>();
    for (final MeasureDeclaration measure : measures) {
      for (final Algebra algebra : measure.algebras()) {
        measureSchemePairs.add(measure.name() + ":" + algebra.scheme());
      }
    }
    canonical.append(String.join(",", measureSchemePairs.stream().sorted().toList())).append('|');
    canonical.append("nulls=skip");
    return sha256Hex12(canonical.toString());
  }

  private static String sha256Hex12(final String canonical) {
    final MessageDigest digest;
    try {
      digest = MessageDigest.getInstance("SHA-256");
    } catch (final NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 must be available on every JVM", e);
    }
    final byte[] hash = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
    final StringBuilder hex = new StringBuilder(12);
    for (int i = 0; i < 6; i++) {
      hex.append(String.format("%02x", hash[i]));
    }
    return hex.toString();
  }

  /**
   * Full SQL merging finer {@code _metrics} partial rows from {@code sourceTable} into coarser
   * partial rows bucketed to {@code targetWindowMicros} (which may equal this entity's own window
   * for a same-granularity compaction merge, or a coarser multiple of it, e.g. minute -> hour).
   */
  public String mergeMetricsSql(final String sourceTable, final long targetWindowMicros) {
    final String dimList = String.join(", ", dims);
    final String windowExpr = coarsenExpr(targetWindowMicros);
    final List<String> projections = new ArrayList<>();
    for (final MeasureDeclaration measure : measures) {
      for (final Algebra algebra : measure.algebras()) {
        if (algebra.shape() == Algebra.PartialsShape.WIDE) {
          projections.add(algebra.mergeProjection(measure.name()));
        }
      }
    }
    return "SELECT "
        + dimList
        + ", "
        + windowExpr
        + " AS "
        + WINDOW_START
        + ", "
        + String.join(", ", projections)
        + " FROM "
        + sourceTable
        + " GROUP BY "
        + dimList
        + ", "
        + windowExpr;
  }

  /** Same idea as {@link #mergeMetricsSql} for the tall {@code _hist} table. */
  public String mergeHistSql(final String sourceTable, final long targetWindowMicros) {
    final String dimList = String.join(", ", dims);
    final String windowExpr = coarsenExpr(targetWindowMicros);
    final Algebra histAlgebra = anyHistAlgebra();
    final String cntProjection = histAlgebra.mergeProjection("");
    return "SELECT "
        + dimList
        + ", "
        + windowExpr
        + " AS "
        + WINDOW_START
        + ", "
        + MEASURE_COLUMN
        + ", "
        + SCHEME_COLUMN
        + ", bin_lo, bin_hi, "
        + cntProjection
        + " FROM "
        + sourceTable
        + " GROUP BY "
        + dimList
        + ", "
        + windowExpr
        + ", "
        + MEASURE_COLUMN
        + ", "
        + SCHEME_COLUMN
        + ", bin_lo, bin_hi";
  }

  /**
   * Full SQL turning {@code _metrics} partial rows into answers: a flat, non-aggregating projection
   * (the table is already one row per (dims, window)) assembled from every {@link
   * Algebra.PartialsShape#WIDE} measure's own {@link Algebra#finalizeProjection}.
   */
  public String finalizeMetricsSql(final String sourceTable) {
    final String dimList = String.join(", ", dims);
    final List<String> projections = new ArrayList<>();
    for (final MeasureDeclaration measure : measures) {
      for (final Algebra algebra : measure.algebras()) {
        if (algebra.shape() == Algebra.PartialsShape.WIDE) {
          projections.add(algebra.finalizeProjection(measure.name()));
        }
      }
    }
    return "SELECT "
        + dimList
        + ", "
        + WINDOW_START
        + ", "
        + String.join(", ", projections)
        + " FROM "
        + sourceTable;
  }

  /**
   * Full, parameterized (one positional {@code ?} = the requested quantile in {@code [0, 1]}) SQL
   * estimating a percentile per (dims, window, measure, scheme) group from {@code _hist} partial
   * rows — see {@link ExpHistogramAlgebra#finalizeQuery} for the cumulative-count-walk algorithm
   * this delegates to. Independent of which histogram scale(s) the entity actually declares: the
   * finalize walk only ever reads the stored {@code bin_lo}/{@code bin_hi}/{@code cnt} columns, so
   * any declared histogram algebra produces textually identical SQL here.
   */
  public String finalizeHistSql(final String sourceTable) {
    final ExpHistogramAlgebra histAlgebra = (ExpHistogramAlgebra) anyHistAlgebra();
    final List<String> groupByColumns = new ArrayList<>(dims);
    groupByColumns.add(WINDOW_START);
    groupByColumns.add(MEASURE_COLUMN);
    groupByColumns.add(SCHEME_COLUMN);
    return histAlgebra.finalizeQuery(sourceTable, groupByColumns);
  }

  /** Same idea as {@link #finalizeHistSql}, packaged as a reusable DuckDB table macro. */
  public String finalizeHistMacroSql(final String macroName, final String sourceTable) {
    final ExpHistogramAlgebra histAlgebra = (ExpHistogramAlgebra) anyHistAlgebra();
    final List<String> groupByColumns = new ArrayList<>(dims);
    groupByColumns.add(WINDOW_START);
    groupByColumns.add(MEASURE_COLUMN);
    groupByColumns.add(SCHEME_COLUMN);
    return histAlgebra.finalizeMacroSql(macroName, sourceTable, groupByColumns);
  }

  private String coarsenExpr(final long targetWindowMicros) {
    return "(" + WINDOW_START + " // " + targetWindowMicros + ") * " + targetWindowMicros;
  }

  private Algebra anyHistAlgebra() {
    for (final MeasureDeclaration measure : measures) {
      final Optional<Algebra> tall =
          measure.algebras().stream()
              .filter(a -> a.shape() == Algebra.PartialsShape.TALL)
              .findFirst();
      if (tall.isPresent()) {
        return tall.get();
      }
    }
    throw new IllegalStateException(
        "entity '"
            + entityName
            + "' declares no histogram-shaped measure; the _hist table has no"
            + " rows to merge/finalize");
  }
}
