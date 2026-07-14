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
import io.camunda.analytics.meter.CompositeAccumulatorValue;
import io.camunda.analytics.meter.PushdownColumn;
import io.camunda.analytics.meter.PushdownSpec;
import io.camunda.analytics.serving.spi.DatasetWriter;
import io.camunda.analytics.serving.spi.VersionedDatasetWriter;
import io.camunda.analytics.serving.spi.WriteVersion;
import io.camunda.analytics.serving.support.SketchScalar;
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
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

/**
 * The RDBMS {@link DatasetWriter}: idempotent, dialect-aware upserts. Cube cells use a keyed upsert
 * on the deterministic {@code cell_key}, writing every meter column of the row from the composite
 * accumulator in one statement (ADR 0009 — one writer per row, never torn); projected rows upsert
 * on {@code row_key}. Writes go through a single MyBatis session held open for the batch, committed
 * on {@link #flush()} — many upserts coalesce into one transaction, and a re-emit or replay
 * overwrites rather than duplicates.
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
public final class RdbmsDatasetWriter implements VersionedDatasetWriter {

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
      final byte[] compositeAccumulator,
      final WriteVersion version) {
    ensurePartition(dataset, windowStart);
    final List<DimensionColumn> grain = dataset.grain().columns();
    final String dimCols =
        grain.stream()
            .map(c -> RdbmsNames.quotedColumn(c.name()))
            .collect(Collectors.joining(", "));
    final String table = RdbmsNames.datasetTable(dataset.cubeId());

    // The composite carries every meter's slot in declared order (ADR 0009), so one statement
    // writes the whole row. A pushable (additive) meter decomposes its slot into native numeric
    // columns (Layer B pushdown); a sketch/summary writes its app-mergeable blob (Layer A) plus a
    // finalized scalar (the DIRECT fast path). The SQL is identical for every cell of a dataset,
    // so the whole commit batches onto one prepared statement.
    final List<CompiledMeter> meters = dataset.meters();
    final List<byte[]> slots =
        CompositeAccumulatorValue.slotBytes(compositeAccumulator, meters.size());

    final List<String> meterCols = new ArrayList<>();
    final List<String> meterColTypes = new ArrayList<>();
    final List<MeterBind> binds = new ArrayList<>();
    for (int slot = 0; slot < meters.size(); slot++) {
      final CompiledMeter meter = meters.get(slot);
      final byte[] slotBytes = slots.get(slot);
      final Optional<PushdownSpec<?, ?>> spec = meter.pushdown();
      if (spec.isPresent()) {
        // Additive: native numeric columns only — no blob (read via PUSH_DOWN/DIRECT, never
        // streamed). An absent slot (older layout) writes the meter's empty accumulator.
        final List<PushdownColumn> columns = spec.get().columns();
        final List<Object> values = decompose(meter.bound(), slotBytes);
        for (int i = 0; i < columns.size(); i++) {
          meterCols.add(
              RdbmsNames.quotedPushdownColumn(meter.meterName(), columns.get(i).suffix()));
          final PushdownColumn column = columns.get(i);
          meterColTypes.add(dialect.columnType(column.type()));
          final Object value = values.get(i);
          binds.add((statement, index) -> bind(statement, index, column.type(), value));
        }
      } else {
        // Sketch / summary: the app-mergeable blob + a finalized scalar for the DIRECT fast path.
        meterCols.add(RdbmsNames.quotedBlobColumn(meter.meterName()));
        meterColTypes.add(dialect.blobType());
        meterCols.add(RdbmsNames.quotedValueColumn(meter.meterName()));
        meterColTypes.add(dialect.doubleType());
        final byte[] blob = slotBytes == null ? emptySlot(meter.bound()) : slotBytes;
        final Double scalar = finalizedValue(meter.bound(), blob);
        binds.add((statement, index) -> statement.setBytes(index, blob));
        binds.add((statement, index) -> statement.setObject(index, scalar, Types.DOUBLE));
      }
    }

    final List<String> insertColumns = new ArrayList<>();
    final List<String> columnTypes = new ArrayList<>();
    insertColumns.add("cell_key");
    columnTypes.add("VARCHAR");
    for (final DimensionColumn dim : grain) {
      insertColumns.add(RdbmsNames.quotedColumn(dim.name()));
      columnTypes.add(dialect.columnType(dim.type()));
    }
    insertColumns.add("window_start");
    columnTypes.add("BIGINT");
    insertColumns.add("window_size");
    columnTypes.add("BIGINT");
    insertColumns.addAll(meterCols);
    columnTypes.addAll(meterColTypes);
    insertColumns.add("ver_epoch");
    columnTypes.add("BIGINT");
    insertColumns.add("ver_offset");
    columnTypes.add("BIGINT");
    final List<String> updateColumns = new ArrayList<>(meterCols);
    updateColumns.add("ver_epoch");
    updateColumns.add("ver_offset");
    // On a Postgres partitioned parent the primary key is the composite (cell_key, window_start) —
    // the partition key must be in the PK — so the upsert conflict target is composite too;
    // window_start is functionally implied by cell_key, so this stays effectively keyed by
    // cell_key. H2 matches on cell_key via MERGE … USING.
    final String conflictTarget =
        dialect.supportsPartitioning() ? "cell_key, window_start" : "cell_key";
    final String sql =
        fencedUpsertSql(
            dialect, table, insertColumns, columnTypes, "cell_key", conflictTarget, updateColumns);

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
          for (final MeterBind meterBind : binds) {
            meterBind.bind(statement, index++);
          }
          statement.setLong(index++, version.epoch());
          statement.setLong(index, version.offset());
        },
        "cube " + dataset.name());
  }

  @Override
  public void upsertSnapshotRow(
      final CompiledDataset dataset,
      final DimensionKey key,
      final long sampleTime,
      final byte[] compositeAccumulator,
      final WriteVersion version) {
    final List<DimensionColumn> grain = dataset.grain().columns();
    final List<CompiledMeter> meters = dataset.meters();
    final List<byte[]> slots =
        CompositeAccumulatorValue.slotBytes(compositeAccumulator, meters.size());

    // Snapshot cubes are additive-only (validated at compile time), so every slot decomposes into
    // native numeric columns — the row carries the key's cumulative absolutes as of sample_time.
    final List<String> meterCols = new ArrayList<>();
    final List<String> meterColTypes = new ArrayList<>();
    final List<MeterBind> binds = new ArrayList<>();
    for (int slot = 0; slot < meters.size(); slot++) {
      final CompiledMeter meter = meters.get(slot);
      final List<PushdownColumn> columns = meter.pushdown().orElseThrow().columns();
      final List<Object> values = decompose(meter.bound(), slots.get(slot));
      for (int i = 0; i < columns.size(); i++) {
        meterCols.add(RdbmsNames.quotedPushdownColumn(meter.meterName(), columns.get(i).suffix()));
        final PushdownColumn column = columns.get(i);
        meterColTypes.add(dialect.columnType(column.type()));
        final Object value = values.get(i);
        binds.add((statement, index) -> bind(statement, index, column.type(), value));
      }
    }

    final List<String> insertColumns = new ArrayList<>();
    final List<String> columnTypes = new ArrayList<>();
    insertColumns.add("row_key");
    columnTypes.add("VARCHAR");
    for (final DimensionColumn dim : grain) {
      insertColumns.add(RdbmsNames.quotedColumn(dim.name()));
      columnTypes.add(dialect.columnType(dim.type()));
    }
    insertColumns.add("sample_time");
    columnTypes.add("BIGINT");
    insertColumns.addAll(meterCols);
    columnTypes.addAll(meterColTypes);
    insertColumns.add("ver_epoch");
    columnTypes.add("BIGINT");
    insertColumns.add("ver_offset");
    columnTypes.add("BIGINT");
    final List<String> updateColumns = new ArrayList<>(meterCols);
    updateColumns.add("ver_epoch");
    updateColumns.add("ver_offset");
    final String sql =
        fencedUpsertSql(
            dialect,
            RdbmsNames.snapshotTable(dataset.cubeId()),
            insertColumns,
            columnTypes,
            "row_key",
            "row_key",
            updateColumns);

    execute(
        sql,
        statement -> {
          int index = 1;
          statement.setString(index++, RdbmsNames.snapshotKey(key, sampleTime));
          for (int i = 0; i < grain.size(); i++) {
            bind(statement, index++, grain.get(i).type(), key.get(i));
          }
          statement.setLong(index++, sampleTime);
          for (final MeterBind meterBind : binds) {
            meterBind.bind(statement, index++);
          }
          statement.setLong(index++, version.epoch());
          statement.setLong(index, version.offset());
        },
        "cube snapshots " + dataset.name());
  }

  /** Binds one meter-derived column value at its statement index. */
  @FunctionalInterface
  private interface MeterBind {
    void bind(PreparedStatement statement, int index) throws SQLException;
  }

  /** Decodes a meter's slot and decomposes it into the pushdown columns' per-cell values. */
  @SuppressWarnings("unchecked")
  private static List<Object> decompose(final BoundMeter<?, ?> boundRaw, final byte[] bytes) {
    final BoundMeter<Object, Object> bound = (BoundMeter<Object, Object>) boundRaw;
    final Object accumulator =
        bytes == null
            ? bound.aggregate().createAccumulator()
            : bound.accumulatorCodec().fromBytes(bytes);
    return bound.pushdown().orElseThrow().decompose().apply(accumulator);
  }

  /** The encoded empty accumulator — an absent slot's blob value. */
  @SuppressWarnings("unchecked")
  private static byte[] emptySlot(final BoundMeter<?, ?> boundRaw) {
    final BoundMeter<Object, Object> bound = (BoundMeter<Object, Object>) boundRaw;
    return bound.accumulatorCodec().toBytes(bound.aggregate().createAccumulator());
  }

  /**
   * The cell's finalized scalar for a non-pushable meter (the denormalized {@code _value}); {@code
   * null} when the meter has no observations in this cell.
   */
  @SuppressWarnings("unchecked")
  private static Double finalizedValue(final BoundMeter<?, ?> boundRaw, final byte[] bytes) {
    final BoundMeter<Object, Object> bound = (BoundMeter<Object, Object>) boundRaw;
    final Object accumulator = bound.accumulatorCodec().fromBytes(bytes);
    return SketchScalar.of(bound.aggregate().getResult(accumulator));
  }

  @Override
  public void upsertRow(
      final CompiledTable table,
      final String rowKey,
      final List<Object> values,
      final WriteVersion version) {
    final List<DimensionColumn> cols = table.columns();
    final List<String> insertColumns = new ArrayList<>();
    final List<String> columnTypes = new ArrayList<>();
    insertColumns.add("row_key");
    columnTypes.add("VARCHAR");
    for (final DimensionColumn col : cols) {
      insertColumns.add(RdbmsNames.quotedColumn(col.name()));
      columnTypes.add(dialect.columnType(col.type()));
    }
    insertColumns.add("ver_epoch");
    columnTypes.add("BIGINT");
    insertColumns.add("ver_offset");
    columnTypes.add("BIGINT");
    final List<String> updateColumns =
        cols.stream().map(c -> RdbmsNames.quotedColumn(c.name())).collect(Collectors.toList());
    updateColumns.add("ver_epoch");
    updateColumns.add("ver_offset");
    final String sql =
        fencedUpsertSql(
            dialect,
            RdbmsNames.rowTable(table.cubeId()),
            insertColumns,
            columnTypes,
            "row_key",
            "row_key",
            updateColumns);

    execute(
        sql,
        statement -> {
          int index = 1;
          statement.setString(index++, rowKey);
          for (int i = 0; i < cols.size(); i++) {
            bind(statement, index++, cols.get(i).type(), values.get(i));
          }
          statement.setLong(index++, version.epoch());
          statement.setLong(index, version.offset());
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

  /** Writes rejected by the version fence — stale by the time they reached the database. */
  private final LongAdder fencedWrites = new LongAdder();

  /** Writes rejected by the version fence since this writer opened (zero outside rebalances). */
  public long fencedWrites() {
    return fencedWrites.sum();
  }

  /** Runs the accumulated batch on every open statement (an empty batch is a harmless no-op). */
  private void executeBatches() {
    for (final PreparedStatement statement : statements.values()) {
      try {
        // A fenced write matches an existing row but fails the version predicate: it affects
        // zero rows. That is the fence working, not an error — count it as the signal it is.
        for (final int updated : statement.executeBatch()) {
          if (updated == 0) {
            fencedWrites.increment();
          }
        }
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
   * Builds the <em>fenced</em> upsert for the dialect: the insert happens unconditionally for a new
   * key, but an existing row is updated only when the write's {@code (ver_epoch, ver_offset)} is
   * at-or-above the row's stored pair — a fenced zombie's stale overwrite affects zero rows (which
   * {@link #executeBatches()} counts). Postgres: {@code ON CONFLICT (conflictTarget) DO UPDATE …
   * WHERE}; H2 has no conditional {@code MERGE … KEY}, so it uses the standard {@code MERGE … USING
   * … WHEN MATCHED AND … / WHEN NOT MATCHED}. The two key shapes differ: a Postgres partitioned
   * table's unique constraint (hence the conflict target) is the composite PK, while H2 matches on
   * {@code keyColumn} alone.
   *
   * <p>{@code insertColumns} must end with {@code ver_epoch, ver_offset}; {@code updateColumns} are
   * the value + version columns an existing row receives (never the key/dims — they are identical
   * by keying).
   */
  static String fencedUpsertSql(
      final RdbmsDialect dialect,
      final String table,
      final List<String> insertColumns,
      final List<String> columnTypes,
      final String keyColumn,
      final String conflictTarget,
      final List<String> updateColumns) {
    final String columns = String.join(", ", insertColumns);
    final String placeholders =
        IntStream.range(0, insertColumns.size())
            .mapToObj(i -> "?")
            .collect(Collectors.joining(", "));
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
          + setClause
          + " WHERE EXCLUDED.ver_epoch > "
          + table
          + ".ver_epoch OR (EXCLUDED.ver_epoch = "
          + table
          + ".ver_epoch AND EXCLUDED.ver_offset >= "
          + table
          + ".ver_offset)";
    }
    final String setClause =
        updateColumns.stream().map(c -> c + " = src." + c).collect(Collectors.joining(", "));
    final String srcColumns =
        insertColumns.stream().map(c -> "src." + c).collect(Collectors.joining(", "));
    // H2 cannot infer a bare ?'s type inside MERGE … USING (VALUES …) — an untyped binary
    // parameter round-trips corrupted — so every source value is cast to its target column type.
    final String typedPlaceholders =
        IntStream.range(0, insertColumns.size())
            .mapToObj(i -> "CAST(? AS " + columnTypes.get(i) + ")")
            .collect(Collectors.joining(", "));
    return "MERGE INTO "
        + table
        + " USING (VALUES ("
        + typedPlaceholders
        + ")) AS src ("
        + columns
        + ") ON "
        + table
        + "."
        + keyColumn
        + " = src."
        + keyColumn
        + " WHEN MATCHED AND (src.ver_epoch > "
        + table
        + ".ver_epoch OR (src.ver_epoch = "
        + table
        + ".ver_epoch AND src.ver_offset >= "
        + table
        + ".ver_offset)) THEN UPDATE SET "
        + setClause
        + " WHEN NOT MATCHED THEN INSERT ("
        + columns
        + ") VALUES ("
        + srcColumns
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
      // Cube-cell dims decode to String; projected-row values may be UTF-8 views — either way
      // this is the mandatory JDBC edge, so materialize via toString (identity for String).
      case STRING, TEXT -> statement.setString(index, value.toString());
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
