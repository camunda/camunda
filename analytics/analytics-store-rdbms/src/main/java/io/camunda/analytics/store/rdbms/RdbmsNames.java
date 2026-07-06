/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.store.rdbms;

import io.camunda.analytics.dataset.store.Identifiers;
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

  static String rowTable(final long cubeId) {
    return "projection_" + cubeId;
  }

  /**
   * Maps a declared dimension/meter name to a SQL column identifier via the shared {@link
   * Identifiers} allowlist — the injection boundary, since column identifiers cannot be
   * parameter-bound — then appends a trailing underscore. The suffix guarantees the identifier can
   * never be a SQL reserved word (none end in {@code _}), so a declared name like {@code order} or
   * {@code distinct} maps to a plain, always-safe {@code order_} / {@code distinct_} column.
   * Rejects anything that is not a plain identifier rather than silently rewriting it.
   */
  static String column(final String name) {
    return Identifiers.safeColumn(name) + "_";
  }

  /**
   * The double-quoted (delimited) form of {@link #column(String)} for use in SQL text. The name is
   * run through the {@link Identifiers} allowlist and given the reserved-word-avoiding suffix
   * first, so no quote or metacharacter can reach the SQL — the quotes only delimit an already-safe
   * identifier. Schema, writes, and reads all go through here (or {@link #column(String)} for the
   * read-back map key), so the suffix is applied uniformly.
   */
  static String quotedColumn(final String name) {
    return "\"" + column(name) + "\"";
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
