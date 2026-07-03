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

  /** Sanitises a declared dimension/meter name into a safe SQL identifier. */
  static String column(final String name) {
    return name.replaceAll("[^A-Za-z0-9_]", "_");
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
