/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.serving.duckdb;

import io.camunda.analytics.lake.serving.config.LakeServingProperties;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Executes plain SQL against the embedded DuckDB connection, enforcing a hard row cap ({@link
 * LakeServingProperties#maxRows()}) and a best-effort per-statement timeout ({@link
 * LakeServingProperties#queryTimeoutSeconds()}) so a runaway free-form query can never hang a
 * request indefinitely. Reimplements, in this module, the read pattern {@code analytics-lake}'s own
 * demo UI (LakeUiServer) uses today.
 */
@Service
public class LakeQueryService {

  private final Connection connection;
  private final int maxRows;
  private final int queryTimeoutSeconds;

  public LakeQueryService(final Connection connection, final LakeServingProperties properties) {
    this.connection = connection;
    maxRows = properties.maxRows();
    queryTimeoutSeconds = properties.queryTimeoutSeconds();
  }

  /**
   * Runs {@code sql} and returns at most {@link #maxRows} rows. Statements with no result set
   * (DDL/DML) come back as an empty column/row shape rather than an error. Throws {@link
   * SQLException} for the caller to translate into an HTTP error; never swallows the failure.
   */
  public QueryResult execute(final String sql) throws SQLException {
    return execute(sql, maxRows);
  }

  /**
   * Same as {@link #execute(String)}, but with an explicit row-cap override instead of the
   * configured {@link #maxRows}.
   *
   * <p><b>Internal use only</b> — never exposed through {@code POST /api/query} or any other
   * caller-controlled path. The single-scan explain tools (today: {@code cohort-compare}'s one
   * {@code GROUP BY} over attribute/bucket combinations) can legitimately produce more result rows
   * than the public API's default cap without that indicating a runaway query, since the row count
   * there is bounded by attribute cardinality, not by warehouse size. Every call site passing a cap
   * other than {@link #maxRows} must document why in its own javadoc.
   */
  public QueryResult execute(final String sql, final int rowCap) throws SQLException {
    try (Statement statement = connection.createStatement()) {
      try {
        statement.setQueryTimeout(queryTimeoutSeconds);
      } catch (final SQLException ignored) {
        // Not every JDBC driver build supports setQueryTimeout; best-effort only.
      }
      final boolean hasResultSet = statement.execute(sql);
      if (!hasResultSet) {
        return new QueryResult(List.of(), List.of());
      }
      try (ResultSet resultSet = statement.getResultSet()) {
        return new QueryResult(readColumns(resultSet), readRows(resultSet, rowCap));
      }
    }
  }

  /**
   * The single scalar value of a {@code SELECT <expr> FROM ...}-shaped query (e.g. {@code
   * count(*)}), or empty if it returns no rows. Used for the per-table row counts in {@code GET
   * /api/tables}.
   */
  public Optional<Object> executeScalar(final String sql) throws SQLException {
    try (Statement statement = connection.createStatement();
        ResultSet resultSet = statement.executeQuery(sql)) {
      return resultSet.next() ? Optional.ofNullable(resultSet.getObject(1)) : Optional.empty();
    }
  }

  private List<String> readColumns(final ResultSet resultSet) throws SQLException {
    final ResultSetMetaData meta = resultSet.getMetaData();
    final List<String> columns = new ArrayList<>(meta.getColumnCount());
    for (int i = 1; i <= meta.getColumnCount(); i++) {
      columns.add(meta.getColumnLabel(i));
    }
    return columns;
  }

  private List<List<Object>> readRows(final ResultSet resultSet, final int rowCap)
      throws SQLException {
    final int columnCount = resultSet.getMetaData().getColumnCount();
    final List<List<Object>> rows = new ArrayList<>();
    while (rows.size() < rowCap && resultSet.next()) {
      final List<Object> row = new ArrayList<>(columnCount);
      for (int i = 1; i <= columnCount; i++) {
        row.add(resultSet.getObject(i));
      }
      rows.add(row);
    }
    return rows;
  }

  /** One query's result: column labels in order, and up to {@code maxRows} rows. */
  public record QueryResult(List<String> columns, List<List<Object>> rows) {}
}
