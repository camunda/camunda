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
 * Physical naming for the document serving store: one {@code dataset_<cubeId>} index per cube and
 * {@code projection_<cubeId>} per projected dataset. A cube cell is stored as one document <b>per
 * meter</b> — the shuffle carries deltas, but the serving store holds the current merged value, so
 * a full {@code index()} of the current value with a deterministic per-(cell, meter) id is an
 * idempotent overwrite (no partial update needed). Field identifiers go through the shared {@link
 * Identifiers} allowlist; accumulator blobs travel as Base64.
 */
final class DocumentCubeNames {

  static final String WINDOW_START = "window_start";
  static final String WINDOW_SIZE = "window_size";
  static final String METER_NAME = "meter_name";
  static final String VER_EPOCH = "ver_epoch";
  static final String VER_OFFSET = "ver_offset";
  static final String ACCUMULATOR = "accumulator";

  /**
   * The finalized scalar field of a non-pushable (sketch/summary) meter document — the {@code
   * _value} counterpart of the RDBMS value column, and the field a single-column additive meter
   * (count/sum/level) writes. The {@code mv_} prefix keeps meter fields out of the grain-dimension
   * namespace.
   */
  static final String VALUE = "mv_value";

  /**
   * A unique, sortable {@code keyword} copy of the document id (the {@link #cellDocId}). Sorting on
   * {@code _id} needs fielddata and is discouraged, so a stored keyword field is what {@code
   * search_after} paginates on when streaming cells.
   */
  static final String DOC_KEY = "doc_key";

  private static final String KEY_SEPARATOR = "\u0001";

  private DocumentCubeNames() {}

  static String datasetIndex(final long cubeId) {
    return "dataset_" + cubeId;
  }

  static String rowIndex(final long cubeId) {
    return "projection_" + cubeId;
  }

  static String field(final String declaredName) {
    return Identifiers.safeColumn(declaredName);
  }

  /**
   * The numeric field of one {@link io.camunda.analytics.meter.PushdownColumn} within an additive
   * meter document: {@code mv_<suffix>}, or {@link #VALUE} for the empty (single-column) suffix.
   */
  static String pushdownField(final String suffix) {
    return suffix.isEmpty() ? VALUE : "mv_" + suffix;
  }

  /** Deterministic id for one meter of one cell: dimensions + window/tier + meter. */
  static String cellDocId(
      final DimensionKey key, final long windowStart, final long windowSize, final String meter) {
    final StringBuilder builder = new StringBuilder();
    for (final Object value : key.values()) {
      builder.append(value == null ? " " : value).append(KEY_SEPARATOR);
    }
    return builder
        .append('|')
        .append(windowStart)
        .append('|')
        .append(windowSize)
        .append('|')
        .append(meter)
        .toString();
  }

  static String encode(final byte[] bytes) {
    return Base64.getEncoder().encodeToString(bytes);
  }

  static byte[] decode(final String base64) {
    return Base64.getDecoder().decode(base64);
  }
}
