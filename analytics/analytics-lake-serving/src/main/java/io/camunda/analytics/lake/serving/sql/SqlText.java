/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.serving.sql;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

/**
 * Shared SQL-text building blocks for every tool/planner service in this module: identifier
 * quoting, string-literal escaping, and ISO-8601 parsing into a {@code TIMESTAMPTZ} literal.
 *
 * <p>{@link io.camunda.analytics.lake.serving.duckdb.LakeQueryService} only ever accepts a plain
 * SQL string (no {@code PreparedStatement} placeholders are exposed to callers), so every caller-
 * supplied value that ends up in a generated statement — a filter value, an entity/dim/measure name
 * — MUST go through {@link #literal(String)} (values) or be validated against a known catalog name
 * before being treated as an identifier via {@link #identifier(String)} (never interpolated
 * unvalidated). This is the single choke point for that escaping so no tool service re-implements
 * it slightly differently.
 */
public final class SqlText {

  /** See {@link #parseInstant}: 12+ digits reads as epoch milliseconds. */
  private static final Pattern EPOCH_MILLIS = Pattern.compile("\\d{12,}");

  private SqlText() {}

  /** Double-quotes a SQL identifier (table/column name), doubling any embedded quote. */
  public static String identifier(final String name) {
    return "\"" + name.replace("\"", "\"\"") + "\"";
  }

  /**
   * Single-quotes a SQL string literal, doubling any embedded quote. Never used for identifiers.
   */
  public static String literal(final String value) {
    return "'" + value.replace("'", "''") + "'";
  }

  /**
   * Parses an ISO-8601 instant (e.g. {@code 2024-01-01T00:00:00Z}) into a DuckDB {@code
   * TIMESTAMPTZ} literal. Rejects anything that doesn't parse as a proper instant — never falls
   * back to interpolating the raw string, since that string came straight from a request body.
   */
  public static String timestamptzLiteral(final String isoInstant) {
    final Instant instant = parseInstant(isoInstant);
    return "TIMESTAMPTZ " + literal(instant.toString());
  }

  /**
   * Parses an ISO-8601 instant or offset date-time string into an {@link Instant}. Also accepts an
   * epoch-milliseconds digit string (12+ digits, e.g. {@code "1784723383499"}): the webapp keeps
   * time internally as {@code Date.now()} millis and JSON carries them as bare numbers, which
   * Jackson binds to these {@code String} fields as digit strings — accepted here, at every
   * endpoint's one shared parse choke point, rather than chased through each of the client's nested
   * request shapes. Shorter digit runs (e.g. {@code "2026"}) still fall through to the ISO parse
   * and its rejection, so a malformed date never silently becomes a 1970 instant.
   */
  public static Instant parseInstant(final String isoInstant) {
    if (isoInstant == null || isoInstant.isBlank()) {
      throw new IllegalArgumentException("Missing required ISO-8601 timestamp");
    }
    if (EPOCH_MILLIS.matcher(isoInstant).matches()) {
      return Instant.ofEpochMilli(Long.parseLong(isoInstant));
    }
    try {
      return OffsetDateTime.parse(isoInstant).toInstant();
    } catch (final DateTimeParseException e) {
      try {
        return Instant.parse(isoInstant);
      } catch (final DateTimeParseException e2) {
        throw new IllegalArgumentException(
            "Not a valid ISO-8601 timestamp or epoch-milliseconds value: " + isoInstant, e2);
      }
    }
  }

  /** Converts a JDBC-returned timestamp value (DuckDB TIMESTAMPTZ) to an ISO-8601 UTC string. */
  public static String toIsoString(final Object jdbcValue) {
    if (jdbcValue == null) {
      return null;
    }
    if (jdbcValue instanceof final OffsetDateTime odt) {
      return odt.toInstant().toString();
    }
    if (jdbcValue instanceof final Timestamp ts) {
      return ts.toInstant().toString();
    }
    if (jdbcValue instanceof final LocalDateTime ldt) {
      return ldt.toInstant(ZoneOffset.UTC).toString();
    }
    if (jdbcValue instanceof final Instant instant) {
      return instant.toString();
    }
    throw new IllegalStateException(
        "Unexpected timestamp JDBC type: " + jdbcValue.getClass() + " (" + jdbcValue + ")");
  }
}
