/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.store.rdbms;

import io.camunda.analytics.dataset.CompiledDataset;
import io.camunda.analytics.dataset.CompiledTable;
import io.camunda.analytics.dataset.store.DatasetWriter;
import io.camunda.analytics.dimension.DimensionColumn;
import io.camunda.analytics.dimension.DimensionKey;
import io.camunda.analytics.dimension.DimensionType;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

/**
 * The RDBMS {@link DatasetWriter}: idempotent, dialect-aware upserts. Cube cells use a keyed upsert
 * on the deterministic {@code cell_key}, setting only the one meter's blob column so meters of the
 * same cell coexist; projected rows upsert on {@code row_key}. Writes go through a single MyBatis
 * session held open for the batch, committed on {@link #flush()} — many upserts coalesce into one
 * transaction, and a re-emit or replay overwrites rather than duplicates.
 *
 * <p>Uses a {@link PreparedStatement} on the session's connection rather than a MyBatis mapper: the
 * accumulator is a {@code byte[]} blob and the column set is per-dataset dynamic, which bind far
 * more robustly through JDBC than through a mapper. The dynamic <em>read</em> path is where MyBatis
 * earns its keep (see {@link RdbmsDatasetQueryClient}).
 */
public final class RdbmsDatasetWriter implements DatasetWriter {

  private final SqlSessionFactory sessionFactory;
  private final RdbmsDialect dialect;
  private SqlSession session;

  public RdbmsDatasetWriter(final SqlSessionFactory sessionFactory, final RdbmsDialect dialect) {
    this.sessionFactory = sessionFactory;
    this.dialect = dialect;
  }

  @Override
  public void upsertCell(
      final CompiledDataset dataset,
      final DimensionKey key,
      final long windowStart,
      final long windowSize,
      final String meterName,
      final byte[] accumulator) {
    final List<DimensionColumn> grain = dataset.grain().columns();
    final String dimCols =
        grain.stream()
            .map(c -> RdbmsNames.quotedColumn(c.name()))
            .collect(Collectors.joining(", "));
    final String meterCol = RdbmsNames.quotedColumn(meterName);
    final String table = RdbmsNames.datasetTable(dataset.cubeId());
    final String columns =
        "cell_key, "
            + (dimCols.isEmpty() ? "" : dimCols + ", ")
            + "window_start, window_size, "
            + meterCol;
    final int columnCount = 1 + grain.size() + 2 + 1;
    final String sql = upsertSql(table, columns, columnCount, "cell_key", List.of(meterCol));

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
          statement.setBytes(index, accumulator);
        },
        "cube " + dataset.name());
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
            RdbmsNames.rowTable(table.cubeId()),
            columns,
            columnCount,
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
    if (session != null) {
      // force: the upserts run as raw JDBC on the session's connection, so MyBatis does not see the
      // session as dirty and a plain commit() would be a no-op, dropping the writes on close.
      session.commit(true);
    }
  }

  @Override
  public void close() {
    if (session != null) {
      session.commit(true);
      session.close();
      session = null;
    }
  }

  /**
   * Builds the upsert idiom for the dialect: H2 {@code MERGE … KEY}, Postgres {@code ON CONFLICT}.
   */
  private String upsertSql(
      final String table,
      final String columns,
      final int columnCount,
      final String keyColumn,
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
          + keyColumn
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
    final Connection connection = session().getConnection();
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      binder.bind(statement);
      statement.executeUpdate();
    } catch (final SQLException e) {
      throw new IllegalStateException("failed to upsert into " + what, e);
    }
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
      case STRING -> statement.setString(index, (String) value);
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
