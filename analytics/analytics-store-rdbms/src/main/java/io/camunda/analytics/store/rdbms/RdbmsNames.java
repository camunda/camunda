/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.store.rdbms;

import io.camunda.analytics.dimension.DimensionKey;

/**
 * The physical naming convention shared by the RDBMS schema manager, writer, and query client: one
 * {@code dataset_<cubeId>} table per cube and {@code projection_<cubeId>} per projected dataset,
 * declared identifiers sanitised to a safe SQL form, and a deterministic {@code cell_key} over a
 * cube cell's dimensions + window/tier so a re-write is an idempotent overwrite.
 */
final class RdbmsNames {

  static final String KEY_SEPARATOR = "\u0001";

  private RdbmsNames() {}

  static String datasetTable(final long cubeId) {
    return "dataset_" + cubeId;
  }

  static String projectionTable(final long cubeId) {
    return "projection_" + cubeId;
  }

  /** Postgres caps identifiers at 63 bytes; stay under it for every target dialect. */
  private static final int MAX_IDENTIFIER_LENGTH = 60;

  /** The allowlist a declared name must match once its namespace dot is folded to an underscore. */
  private static final java.util.regex.Pattern SAFE_IDENTIFIER =
      java.util.regex.Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

  /**
   * Maps a declared dimension/meter name to a SQL column identifier, <b>validating</b> rather than
   * silently rewriting it. Column identifiers cannot be parameter-bound, so they are the one place
   * declared (and, for {@code var.*} dimensions, user-influenced) text reaches SQL text; this is
   * the injection boundary. The only transformation is folding the {@code var.region} namespace dot
   * to an underscore; the result must then be a plain identifier ({@code [A-Za-z_][A-Za-z0-9_]*})
   * within the length cap, or the declaration is rejected. Anything carrying a quote, semicolon,
   * whitespace, or other metacharacter fails the allowlist and throws — it is never coerced into
   * "valid" SQL.
   */
  static String column(final String name) {
    final String identifier = name.replace('.', '_');
    if (!SAFE_IDENTIFIER.matcher(identifier).matches()
        || identifier.length() > MAX_IDENTIFIER_LENGTH) {
      throw new IllegalArgumentException(
          "unsafe SQL identifier derived from declared name '"
              + name
              + "'; names must be [A-Za-z_][A-Za-z0-9_]* (dots allowed for var.* namespaces) and at"
              + " most "
              + MAX_IDENTIFIER_LENGTH
              + " chars");
    }
    return identifier;
  }

  /** A deterministic primary key over the dimension values and the window/tier coordinate. */
  static String cellKey(final DimensionKey key, final long windowStart, final long windowSize) {
    final StringBuilder builder = new StringBuilder();
    for (final Object value : key.values()) {
      builder.append(value == null ? " " : value).append(KEY_SEPARATOR);
    }
    return builder.append('|').append(windowStart).append('|').append(windowSize).toString();
  }
}
