/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.store.rdbms;

import io.camunda.analytics.dimension.DimensionKey;
import io.camunda.analytics.serving.support.Identifiers;
import java.time.YearMonth;

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
   * The time-leading secondary index on a cube's {@code dataset_<id>} table: {@code (window_size,
   * window_start, …dims)}. It leads with the always-present, selective read predicates (tier + time
   * range) so a range read is an index seek, not a full scan.
   */
  static String scanIndex(final long cubeId) {
    return "idx_dataset_" + cubeId + "_scan";
  }

  /**
   * A monthly range partition of a cube's {@code dataset_<id>} table (Layer C, Postgres only):
   * {@code dataset_<id>_<yyyy>_<mm>}, e.g. {@code dataset_1_2026_07}. The month is derived from the
   * cell's {@code window_start} at UTC; the zero-padded {@code yyyy_mm} suffix is a safe constant
   * token (no user input), so it never needs the {@link Identifiers} allowlist.
   */
  static String childPartition(final long cubeId, final YearMonth month) {
    return String.format("dataset_%d_%04d_%02d", cubeId, month.getYear(), month.getMonthValue());
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
   * The blob column holding a meter's still-encoded, app-mergeable accumulator — the portable Layer
   * A representation, kept for <em>every</em> meter (additive and sketch alike) so the streaming
   * app-merge stays correct. Named {@code <meter>blob} so it never collides with an additive
   * meter's single native column (whose empty suffix names it {@code <meter>}).
   */
  static String blobColumn(final String name) {
    return column(name) + "blob";
  }

  static String quotedBlobColumn(final String name) {
    return "\"" + blobColumn(name) + "\"";
  }

  /**
   * A meter's finalized scalar column ({@code <meter>value}) — the cheap denormalized {@code
   * getResult} of a non-pushable meter's own cell, so a matching-granularity {@code DIRECT} read
   * can skip the blob. Distinct from {@link #blobColumn} and {@link #pushdownColumn} to avoid
   * collisions.
   */
  static String valueColumn(final String name) {
    return column(name) + "value";
  }

  static String quotedValueColumn(final String name) {
    return "\"" + valueColumn(name) + "\"";
  }

  /**
   * The native numeric column of one {@link io.camunda.analytics.meter.PushdownColumn} of a
   * pushable meter: the meter's base column plus the column's {@code suffix} (an empty suffix — the
   * single-column meters count/sum/level — names it just {@code <meter>}). The suffix is a safe
   * constant token from the meter model, so it never needs the {@link Identifiers} allowlist.
   */
  static String pushdownColumn(final String name, final String suffix) {
    return column(name) + suffix;
  }

  static String quotedPushdownColumn(final String name, final String suffix) {
    return "\"" + pushdownColumn(name, suffix) + "\"";
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
