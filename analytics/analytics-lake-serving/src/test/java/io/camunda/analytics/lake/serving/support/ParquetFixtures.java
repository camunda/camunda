/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.serving.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Test-only helper: writes a tiny Parquet file via an ephemeral DuckDB connection, laid out exactly
 * as the lake writer would ({@code <warehouseDir>/lake/<table>/data/<partition>/*.parquet}), so
 * tests exercise this module's real discovery + read path rather than a stub.
 */
public final class ParquetFixtures {

  private ParquetFixtures() {}

  /**
   * Writes {@code rowCount} rows (an incrementing {@code id} plus a derived {@code name} column) as
   * one Parquet file under {@code <warehouseDir>/lake/<table>/data/part-0/data.parquet}.
   */
  public static void writeTable(final Path warehouseDir, final String table, final int rowCount) {
    final Path partitionDir =
        warehouseDir.resolve("lake").resolve(table).resolve("data").resolve("part-0");
    try {
      Files.createDirectories(partitionDir);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
    final Path file = partitionDir.resolve("data.parquet");
    try (Connection connection = DriverManager.getConnection("jdbc:duckdb:");
        Statement statement = connection.createStatement()) {
      statement.execute(
          "COPY (SELECT range AS id, 'row-' || range AS name FROM range("
              + rowCount
              + ")) TO '"
              + file.toAbsolutePath()
              + "' (FORMAT PARQUET)");
    } catch (final SQLException e) {
      throw new IllegalStateException("Failed to write fixture parquet for table " + table, e);
    }
  }

  /**
   * The general-purpose fixture primitive every explain-tool test builds its own planted scenario
   * on: materializes an arbitrary {@code SELECT} (typically a {@code VALUES} list or a {@code
   * generate_series}-driven query authored right in the test) as one Parquet file at exactly the
   * path {@link io.camunda.analytics.lake.serving.duckdb.LakeViewRegistry} discovers, under {@code
   * <warehouseDir>/lake/<table>/data/part-0/data.parquet}. Column names/types in {@code selectSql}
   * become the view's real schema (via DuckDB's own Parquet type inference), so a test controls the
   * exact shape {@code MetricRegistry}/the tool services see -- no separate schema description
   * needed.
   *
   * <p><b>Timezone gotcha this method exists partly to hide:</b> {@code CAST(<naive TIMESTAMP
   * literal> AS TIMESTAMPTZ)} in DuckDB interprets the naive literal as wall-clock time in the
   * <em>session's</em> timezone, not UTC -- on a non-UTC machine this silently shifts every fixture
   * timestamp by that offset, breaking any assertion against a literal {@code Z} instant. This
   * connection is pinned to UTC ({@code SET TimeZone='UTC'}) before running {@code selectSql} so a
   * test's own {@code TIMESTAMP '...'} literals mean exactly the UTC wall-clock value written,
   * regardless of the machine (or CI runner) this test executes on.
   */
  public static void writeTableFromQuery(
      final Path warehouseDir, final String table, final String selectSql) {
    final Path partitionDir =
        warehouseDir.resolve("lake").resolve(table).resolve("data").resolve("part-0");
    try {
      Files.createDirectories(partitionDir);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
    final Path file = partitionDir.resolve("data.parquet");
    try (Connection connection = DriverManager.getConnection("jdbc:duckdb:");
        Statement statement = connection.createStatement()) {
      statement.execute("SET TimeZone='UTC'");
      statement.execute(
          "COPY (" + selectSql + ") TO '" + file.toAbsolutePath() + "' (FORMAT PARQUET)");
    } catch (final SQLException e) {
      throw new IllegalStateException("Failed to write fixture parquet for table " + table, e);
    }
  }
}
