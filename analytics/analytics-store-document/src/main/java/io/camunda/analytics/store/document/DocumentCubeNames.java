/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.store.document;

import io.camunda.analytics.dimension.DimensionKey;
import io.camunda.analytics.serving.support.Identifiers;
import java.util.Base64;

/**
 * Physical naming for the document serving store: one {@code dataset_<cubeId>} index per cube (plus
 * {@code dataset_<cubeId>_snapshots} for periodic snapshots) and {@code projection_<cubeId>} per
 * projected dataset. A cube cell is <b>one document</b> carrying every meter's serving columns (ADR
 * 0009 — one writer per row, one atomic upsert, never torn), keyed by a deterministic per-(dims,
 * window/tier) id so a re-write is an idempotent overwrite.
 *
 * <p>Meter fields mirror the RDBMS column naming ({@code RdbmsNames}): a per-meter base of {@code
 * mv_<meter>_} (the {@code mv_} prefix keeps meter fields out of the grain-dimension namespace, the
 * trailing underscore keeps suffixes collision-free across meter names) plus the pushdown column
 * suffix for additive meters, or the reserved {@code blob}/{@code value} suffixes for a sketch's
 * app-mergeable accumulator and its finalized scalar. Field identifiers go through the shared
 * {@link Identifiers} allowlist; accumulator blobs travel as Base64.
 */
final class DocumentCubeNames {

  static final String WINDOW_START = "window_start";
  static final String WINDOW_SIZE = "window_size";
  static final String SAMPLE_TIME = "sample_time";
  static final String VER_EPOCH = "ver_epoch";
  static final String VER_OFFSET = "ver_offset";

  /**
   * A unique, sortable {@code keyword} copy of the document id (the {@link #cellDocId} / {@link
   * #snapshotDocId}). Sorting on {@code _id} needs fielddata and is discouraged, so a stored
   * keyword field is what {@code search_after} paginates on when streaming.
   */
  static final String DOC_KEY = "doc_key";

  private static final String METER_PREFIX = "mv_";
  private static final String KEY_SEPARATOR = "\u0001";

  private DocumentCubeNames() {}

  static String datasetIndex(final long cubeId) {
    return "dataset_" + cubeId;
  }

  static String rowIndex(final long cubeId) {
    return "projection_" + cubeId;
  }

  /** A cube's periodic-snapshot index: absolute cumulative values per (key, sample_time). */
  static String snapshotIndex(final long cubeId) {
    return "dataset_" + cubeId + "_snapshots";
  }

  static String field(final String declaredName) {
    return Identifiers.safeColumn(declaredName);
  }

  /**
   * The numeric field of one {@link io.camunda.analytics.meter.PushdownColumn} of a pushable meter:
   * the meter's base plus the column's {@code suffix} (empty for the single-column meters
   * count/sum/level, so their field is just the base).
   */
  static String pushdownField(final String meter, final String suffix) {
    return meterBase(meter) + suffix;
  }

  /**
   * The field holding a non-pushable (sketch/summary) meter's still-encoded, app-mergeable
   * accumulator — the streamed + merged representation.
   */
  static String blobField(final String meter) {
    return meterBase(meter) + "blob";
  }

  /**
   * A non-pushable meter's finalized scalar field — the denormalized {@code getResult} of the
   * cell's own accumulator, so a matching-granularity {@code DIRECT} read can skip the blob.
   */
  static String valueField(final String meter) {
    return meterBase(meter) + "value";
  }

  /**
   * The per-meter field namespace: like {@code RdbmsNames.column}, the trailing underscore keeps a
   * suffixed field from ever colliding with another meter's ({@code et} + {@code count} maps to
   * {@code mv_et_count}, while a meter literally named {@code et_count} maps to {@code
   * mv_et_count_}).
   */
  private static String meterBase(final String meter) {
    return METER_PREFIX + Identifiers.safeColumn(meter) + "_";
  }

  /** Deterministic id for one cell — all meters in one document: dimensions + window/tier. */
  static String cellDocId(final DimensionKey key, final long windowStart, final long windowSize) {
    final StringBuilder builder = new StringBuilder();
    for (final Object value : key.values()) {
      builder.append(value == null ? " " : value).append(KEY_SEPARATOR);
    }
    return builder.append('|').append(windowStart).append('|').append(windowSize).toString();
  }

  /** Deterministic id for one snapshot row — all meters in one document: dimensions + boundary. */
  static String snapshotDocId(final DimensionKey key, final long sampleTime) {
    final StringBuilder builder = new StringBuilder();
    for (final Object value : key.values()) {
      builder.append(value == null ? " " : value).append(KEY_SEPARATOR);
    }
    return builder.append('|').append(sampleTime).toString();
  }

  static String encode(final byte[] bytes) {
    return Base64.getEncoder().encodeToString(bytes);
  }

  static byte[] decode(final String base64) {
    return Base64.getDecoder().decode(base64);
  }
}
