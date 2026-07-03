/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.dataset.jdbc;

import io.camunda.analytics.dataset.CompiledDataset;
import io.camunda.analytics.dimension.DimensionColumn;
import io.camunda.analytics.dimension.DimensionKey;
import io.camunda.analytics.dimension.DimensionType;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.List;
import java.util.stream.Collectors;
import javax.sql.DataSource;

/**
 * The inline JDBC serving store for a cube: provisions its table from the {@link CompiledDataset}'s
 * schema and idempotently upserts merged accumulators. It is the single Stage-2 sink, replacing the
 * hand-written per-metric {@code Jdbc*Sink}s — one generic writer driven by the declared schema.
 *
 * <p>One table {@code dataset_<cubeId>} per cube: a deterministic {@code cell_key} primary key (so
 * a re-write is idempotent and a {@code null} dimension — the "unknown" bucket — does not violate
 * the key), the declared dimension columns (nullable, for querying), {@code window_start} + {@code
 * window_size} (the tier), and one accumulator blob column per meter. Each upsert sets just its
 * meter's column (via a keyed {@code MERGE}), so meters of the same cell coexist. Phase 4 abstracts
 * this behind a backend-neutral SPI and adds Elasticsearch/OpenSearch implementations.
 */
public final class JdbcDatasetStore {

  private static final String KEY_SEPARATOR = "\\u0001";

  private final DataSource dataSource;

  public JdbcDatasetStore(final DataSource dataSource) {
    this.dataSource = dataSource;
  }

  /** Creates the cube's serving table if absent (idempotent). */
  public void ensure(final CompiledDataset dataset) {
    final String columns =
        dataset.grain().columns().stream()
            .map(column -> column(column.name()) + " " + sqlType(column.type()))
            .collect(Collectors.joining(", "));
    final String meterColumns =
        dataset.schema().meterNames().stream()
            .map(name -> column(name) + " VARBINARY")
            .collect(Collectors.joining(", "));
    final String ddl =
        "CREATE TABLE IF NOT EXISTS "
            + table(dataset)
            + " (cell_key VARCHAR PRIMARY KEY, "
            + columns
            + (columns.isEmpty() ? "" : ", ")
            + "window_start BIGINT NOT NULL, window_size BIGINT NOT NULL, "
            + meterColumns
            + ")";
    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute(ddl);
    } catch (final SQLException e) {
      throw new IllegalStateException("failed to create table for " + dataset.name(), e);
    }
  }

  /** Idempotently writes {@code accumulator} for one meter of one cell (dims + window + tier). */
  public void upsert(
      final CompiledDataset dataset,
      final DimensionKey key,
      final long windowStart,
      final long windowSize,
      final String meterName,
      final byte[] accumulator) {
    final List<DimensionColumn> grain = dataset.grain().columns();
    final String dimColumns =
        grain.stream().map(c -> column(c.name())).collect(Collectors.joining(", "));
    final String dimPlaceholders = grain.stream().map(c -> "?").collect(Collectors.joining(", "));
    final String sql =
        "MERGE INTO "
            + table(dataset)
            + " (cell_key, "
            + dimColumns
            + (dimColumns.isEmpty() ? "" : ", ")
            + "window_start, window_size, "
            + column(meterName)
            + ") KEY (cell_key) VALUES (?, "
            + dimPlaceholders
            + (dimPlaceholders.isEmpty() ? "" : ", ")
            + "?, ?, ?)";
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      int index = 1;
      statement.setString(index++, cellKey(key, windowStart, windowSize));
      for (int i = 0; i < grain.size(); i++) {
        bind(statement, index++, grain.get(i).type(), key.get(i));
      }
      statement.setLong(index++, windowStart);
      statement.setLong(index++, windowSize);
      statement.setBytes(index, accumulator);
      statement.executeUpdate();
    } catch (final SQLException e) {
      throw new IllegalStateException("failed to upsert into " + dataset.name(), e);
    }
  }

  private static String table(final CompiledDataset dataset) {
    return "dataset_" + dataset.cubeId();
  }

  /** A deterministic primary key over the dimension values and the window/tier coordinate. */
  private static String cellKey(final DimensionKey key, final long windowStart, final long size) {
    final StringBuilder builder = new StringBuilder();
    for (final Object value : key.values()) {
      builder.append(value == null ? " " : value).append(KEY_SEPARATOR);
    }
    return builder.append('|').append(windowStart).append('|').append(size).toString();
  }

  /** Sanitises a declared dimension/meter name into a safe SQL identifier. */
  private static String column(final String name) {
    return name.replaceAll("[^A-Za-z0-9_]", "_");
  }

  private static String sqlType(final DimensionType type) {
    return switch (type) {
      case STRING -> "VARCHAR";
      case LONG -> "BIGINT";
      case INT -> "INTEGER";
      case BOOLEAN -> "BOOLEAN";
    };
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
      case LONG -> statement.setLong(index, (Long) value);
      case INT -> statement.setInt(index, (Integer) value);
      case BOOLEAN -> statement.setBoolean(index, (Boolean) value);
    }
  }
}
