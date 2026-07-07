/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.store.rdbms;

import io.camunda.analytics.dataset.CompiledDataset;
import io.camunda.analytics.dataset.CompiledMeter;
import io.camunda.analytics.dataset.CompiledTable;
import io.camunda.analytics.dimension.DimensionColumn;
import io.camunda.analytics.dimension.DimensionKey;
import io.camunda.analytics.dimension.DimensionType;
import io.camunda.analytics.meter.BoundMeter;
import io.camunda.analytics.meter.PushdownColumn;
import io.camunda.analytics.meter.PushdownSpec;
import io.camunda.analytics.serving.internal.SketchScalar;
import io.camunda.analytics.serving.spi.DatasetWriter;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

/**
 * The RDBMS {@link DatasetWriter}: idempotent, dialect-aware upserts. Cube cells use a keyed upsert
 * on the deterministic {@code cell_key}, setting only the one meter's own columns so meters of the
 * same cell coexist; projected rows upsert on {@code row_key}. Writes go through a single MyBatis
 * session held open for the batch, committed on {@link #flush()} — many upserts coalesce into one
 * transaction, and a re-emit or replay overwrites rather than duplicates.
 *
 * <p>Uses a {@link PreparedStatement} on the session's connection rather than a MyBatis mapper: the
 * accumulator is a {@code byte[]} blob and the column set is per-dataset dynamic, which bind far
 * more robustly through JDBC than through a mapper. The dynamic <em>read</em> path is where MyBatis
 * earns its keep (see {@link RdbmsDatasetQueryClient}).
 *
 * <p>Upserts are <em>JDBC-batched</em>: each distinct upsert SQL keeps one {@link
 * PreparedStatement} open on the session's connection, every cell {@code addBatch()}es onto it, and
 * {@link #flush()} runs {@code executeBatch()} on each before committing. So a whole commit's worth
 * of cells for a cube goes to the database as one batched round-trip instead of one {@code
 * executeUpdate()} per cell — the win is large over a network (Postgres) and still real in-process
 * (H2).
 */
public final class RdbmsDatasetWriter implements DatasetWriter {

  private final SqlSessionFactory sessionFactory;
  private final RdbmsDialect dialect;
  private final RdbmsDatasetSchemaManager schemaManager;

  /**
   * The {@code (cubeId, month)}s whose time partition this writer has already ensured, so only the
   * first cell of a new month issues partition DDL. Empty and unused on H2 (partition-ensure is a
   * no-op there); bounded by the number of distinct months a writer touches.
   */
  private final Set<String> ensuredPartitions = new HashSet<>();

  /**
   * One open {@link PreparedStatement} per distinct upsert SQL, reused across flushes for batching.
   */
  private final Map<String, PreparedStatement> statements = new HashMap<>();

  private SqlSession session;

  public RdbmsDatasetWriter(
      final SqlSessionFactory sessionFactory,
      final RdbmsDialect dialect,
      final RdbmsDatasetSchemaManager schemaManager) {
    this.sessionFactory = sessionFactory;
    this.dialect = dialect;
    this.schemaManager = schemaManager;
  }

  @Override
  public void upsertCell(
      final CompiledDataset dataset,
      final DimensionKey key,
      final long windowStart,
      final long windowSize,
      final String meterName,
      final byte[] accumulator) {
    ensurePartition(dataset, windowStart);
    final List<DimensionColumn> grain = dataset.grain().columns();
    final String dimCols =
        grain.stream()
            .map(c -> RdbmsNames.quotedColumn(c.name()))
            .collect(Collectors.joining(", "));
    final String table = RdbmsNames.datasetTable(dataset.cubeId());

    // A pushable (additive) meter decomposes into its native numeric columns (Layer B pushdown); a
    // sketch/summary writes its app-mergeable blob (Layer A) plus a finalized scalar (the DIRECT
    // fast path). Only the meter's own columns are in the upsert, so meters of the same cell
    // coexist
    // under the shared cell_key.
    final CompiledMeter compiled = compiledMeter(dataset, meterName, windowSize);
    final Optional<PushdownSpec<?, ?>> spec = compiled.pushdown();

    final List<String> meterCols = new ArrayList<>();
    final List<Object> pushdownValues;
    final double scalarValue;
    if (spec.isPresent()) {
      // Additive: native numeric columns only — no blob (read via PUSH_DOWN/DIRECT, never
      // streamed).
      pushdownValues = decompose(compiled.bound(), accumulator);
      for (final PushdownColumn column : spec.get().columns()) {
        meterCols.add(RdbmsNames.quotedPushdownColumn(meterName, column.suffix()));
      }
      scalarValue = Double.NaN;
    } else {
      // Sketch / summary: the app-mergeable blob + a finalized scalar for the DIRECT fast path.
      pushdownValues = List.of();
      meterCols.add(RdbmsNames.quotedBlobColumn(meterName));
      meterCols.add(RdbmsNames.quotedValueColumn(meterName));
      scalarValue = finalizedValue(compiled.bound(), accumulator);
    }

    final String columns =
        "cell_key, "
            + (dimCols.isEmpty() ? "" : dimCols + ", ")
            + "window_start, window_size, "
            + String.join(", ", meterCols);
    final int columnCount = 1 + grain.size() + 2 + meterCols.size();
    // On a Postgres partitioned parent the primary key is the composite (cell_key, window_start) —
    // the partition key must be in the PK — so the upsert conflict target is composite too;
    // window_start is functionally implied by cell_key, so this stays effectively keyed by
    // cell_key.
    // H2 keeps the single-column MERGE … KEY (cell_key).
    final String conflictTarget =
        dialect.supportsPartitioning() ? "cell_key, window_start" : "cell_key";
    final String sql =
        upsertSql(dialect, table, columns, columnCount, "cell_key", conflictTarget, meterCols);
    final List<PushdownColumn> pushdownColumns = spec.map(PushdownSpec::columns).orElse(List.of());

    execute(
        sql,
        statement -> {
          int index = 1;
          statement.setString(index++, RdbmsNames.cellKey(key, windowStart, windowSize));
          for (int i = 0; i < grain.size(); i++) {
            bind(statement, index++, grain.get(i).type(), key.get(i));
          }
          statement.setLong(index++, windowStart);
          statement.setLong(index++, windowSize);
          if (spec.isPresent()) {
            for (int i = 0; i < pushdownColumns.size(); i++) {
              bind(statement, index++, pushdownColumns.get(i).type(), pushdownValues.get(i));
            }
          } else {
            statement.setBytes(index++, accumulator);
            statement.setDouble(index, scalarValue);
          }
        },
        "cube " + dataset.name());
  }

  /**
   * The compiled meter for {@code (name, windowSize)}, or any tier of the name (specs are shared).
   */
  private static CompiledMeter compiledMeter(
      final CompiledDataset dataset, final String meterName, final long windowSize) {
    CompiledMeter fallback = null;
    for (final CompiledMeter meter : dataset.meters()) {
      if (meter.meterName().equals(meterName)) {
        if (meter.windowMs() == windowSize) {
          return meter;
        }
        fallback = meter;
      }
    }
    if (fallback == null) {
      throw new IllegalStateException(
          "no compiled meter '" + meterName + "' in cube '" + dataset.name() + "'");
    }
    return fallback;
  }

  /** Decodes the accumulator and decomposes it into the pushdown columns' per-cell values. */
  @SuppressWarnings("unchecked")
  private static List<Object> decompose(final BoundMeter<?, ?> boundRaw, final byte[] bytes) {
    final BoundMeter<Object, Object> bound = (BoundMeter<Object, Object>) boundRaw;
    final Object accumulator = bound.accumulatorCodec().fromBytes(bytes);
    return bound.pushdown().orElseThrow().decompose().apply(accumulator);
  }

  /** The cell's finalized scalar for a non-pushable meter (the denormalized {@code _value}). */
  @SuppressWarnings("unchecked")
  private static double finalizedValue(final BoundMeter<?, ?> boundRaw, final byte[] bytes) {
    final BoundMeter<Object, Object> bound = (BoundMeter<Object, Object>) boundRaw;
    final Object accumulator = bound.accumulatorCodec().fromBytes(bytes);
    return SketchScalar.of(bound.aggregate().getResult(accumulator));
  }

  @Override
  public void upsertRow(final CompiledTable table, final String rowKey, final List<Object> values) {
    final List<DimensionColumn> cols = table.columns();
    final String colNames =
        cols.stream().map(c -> RdbmsNames.quotedColumn(c.name())).collect(Collectors.joining(", "));
    final String columns = "row_key" + (colNames.isEmpty() ? "" : ", " + colNames);
    final int columnCount = 1 + cols.size();
    final List<String> updateColumns =
        cols.stream().map(c -> RdbmsNames.quotedColumn(c.name())).collect(Collectors.toList());
    final String sql =
        upsertSql(
            dialect,
            RdbmsNames.rowTable(table.cubeId()),
            columns,
            columnCount,
            "row_key",
            "row_key",
            updateColumns.isEmpty() ? List.of("row_key") : updateColumns);

    execute(
        sql,
        statement -> {
          int index = 1;
          statement.setString(index++, rowKey);
          for (int i = 0; i < cols.size(); i++) {
            bind(statement, index++, cols.get(i).type(), values.get(i));
          }
        },
        "table " + table.name());
  }

  @Override
  public void flush() {
    if (session == null) {
      return;
    }
    executeBatches();
    // force: the batched upserts run as raw JDBC on the session's connection, so MyBatis does not
    // see the session as dirty and a plain commit() would be a no-op, dropping the writes on close.
    session.commit(true);
  }

  @Override
  public void close() {
    if (session == null) {
      return;
    }
    executeBatches();
    session.commit(true);
    for (final PreparedStatement statement : statements.values()) {
      try {
        statement.close();
      } catch (final SQLException e) {
        // best effort on close
      }
    }
    statements.clear();
    session.close();
    session = null;
  }

  /** Runs the accumulated batch on every open statement (an empty batch is a harmless no-op). */
  private void executeBatches() {
    for (final PreparedStatement statement : statements.values()) {
      try {
        statement.executeBatch();
      } catch (final SQLException e) {
        throw new IllegalStateException("failed to execute upsert batch", e);
      }
    }
  }

  /**
   * Ensures the target month's time partition exists before the upsert (Layer C, Postgres only).
   * The in-writer cache means only the first cell of a new {@code (cubeId, month)} issues DDL; on
   * H2 the schema manager's ensure is a no-op so nothing changes and the cache short-circuits
   * cheaply.
   */
  private void ensurePartition(final CompiledDataset dataset, final long windowStart) {
    if (!dialect.supportsPartitioning()) {
      return;
    }
    final YearMonth month = RdbmsDatasetSchemaManager.monthOf(windowStart);
    if (ensuredPartitions.add(dataset.cubeId() + "|" + month)) {
      schemaManager.ensurePartition(dataset, windowStart);
    }
  }

  /**
   * Builds the upsert idiom for the dialect: H2 {@code MERGE … KEY (keyColumn)}, Postgres {@code ON
   * CONFLICT (conflictTarget)}. The two can differ: a Postgres partitioned table's unique
   * constraint (hence the conflict target) is the composite PK, while H2's single-column {@code
   * MERGE … KEY} stays keyed on {@code keyColumn}.
   */
  static String upsertSql(
      final RdbmsDialect dialect,
      final String table,
      final String columns,
      final int columnCount,
      final String keyColumn,
      final String conflictTarget,
      final List<String> updateColumns) {
    final String placeholders =
        IntStream.range(0, columnCount).mapToObj(i -> "?").collect(Collectors.joining(", "));
    if (dialect == RdbmsDialect.POSTGRESQL) {
      final String setClause =
          updateColumns.stream().map(c -> c + " = EXCLUDED." + c).collect(Collectors.joining(", "));
      return "INSERT INTO "
          + table
          + " ("
          + columns
          + ") VALUES ("
          + placeholders
          + ") ON CONFLICT ("
          + conflictTarget
          + ") DO UPDATE SET "
          + setClause;
    }
    return "MERGE INTO "
        + table
        + " ("
        + columns
        + ") KEY ("
        + keyColumn
        + ") VALUES ("
        + placeholders
        + ")";
  }

  private void execute(final String sql, final Binder binder, final String what) {
    try {
      final PreparedStatement statement = statementFor(sql);
      binder.bind(statement);
      statement.addBatch();
    } catch (final SQLException e) {
      throw new IllegalStateException("failed to batch upsert into " + what, e);
    }
  }

  /**
   * The cached prepared statement for this SQL on the session's connection, opened on first use.
   */
  private PreparedStatement statementFor(final String sql) throws SQLException {
    PreparedStatement statement = statements.get(sql);
    if (statement == null) {
      statement = session().getConnection().prepareStatement(sql);
      statements.put(sql, statement);
    }
    return statement;
  }

  private SqlSession session() {
    if (session == null) {
      session = sessionFactory.openSession(false);
    }
    return session;
  }

  private static void bind(
      final PreparedStatement statement,
      final int index,
      final DimensionType type,
      final Object value)
      throws SQLException {
    if (value == null) {
      statement.setNull(index, Types.NULL);
      return;
    }
    switch (type) {
      case STRING, TEXT -> statement.setString(index, (String) value);
      case LONG -> statement.setLong(index, ((Number) value).longValue());
      case INT -> statement.setInt(index, ((Number) value).intValue());
      case BOOLEAN -> statement.setBoolean(index, (Boolean) value);
    }
  }

  @FunctionalInterface
  private interface Binder {
    void bind(PreparedStatement statement) throws SQLException;
  }
}
