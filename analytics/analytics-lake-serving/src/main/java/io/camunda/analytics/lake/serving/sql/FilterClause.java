/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.serving.sql;

import java.util.Collection;
import java.util.Map;

/**
 * Builds a {@code col1 = 'v1' AND col2 = 2 AND col3 = TRUE} SQL predicate from a caller-supplied
 * {@code Map<String, Object>} of dim filters (JSON {@code string|number|boolean} values, matching
 * every tool endpoint's {@code filters} field), validating every key against a known-good column
 * set first — a request naming an unknown dim is a {@code 400}, never silently ignored or, worse,
 * interpolated as an arbitrary identifier.
 */
public final class FilterClause {

  private FilterClause() {}

  /**
   * @param filters dim name -> exact-match value (a {@link String}, {@link Number}, or {@link
   *     Boolean} — the three JSON scalar types the wire format allows); {@code null} or empty
   *     yields {@code "TRUE"}
   * @param allowedColumns every column name this entity actually has (dims, plus any other column a
   *     caller might legitimately filter on)
   * @throws IllegalArgumentException if a filter key isn't in {@code allowedColumns}
   */
  public static String toSql(
      final Map<String, ?> filters, final Collection<String> allowedColumns) {
    if (filters == null || filters.isEmpty()) {
      return "TRUE";
    }
    final StringBuilder sql = new StringBuilder();
    for (final Map.Entry<String, ?> entry : filters.entrySet()) {
      final String column = entry.getKey();
      if (!allowedColumns.contains(column)) {
        throw new IllegalArgumentException(
            "Unknown filter dim '" + column + "'; known columns: " + allowedColumns);
      }
      if (!sql.isEmpty()) {
        sql.append(" AND ");
      }
      sql.append(SqlText.identifier(column)).append(" = ").append(valueLiteral(entry.getValue()));
    }
    return sql.toString();
  }

  /** Renders a filter value as a SQL literal, dispatching on its JSON scalar type. */
  private static String valueLiteral(final Object value) {
    if (value instanceof final Boolean bool) {
      return bool ? "TRUE" : "FALSE";
    }
    if (value instanceof final Number number) {
      return number.toString();
    }
    return SqlText.literal(String.valueOf(value));
  }
}
