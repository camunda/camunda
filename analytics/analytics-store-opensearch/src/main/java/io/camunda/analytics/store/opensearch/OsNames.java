/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.store.opensearch;

import io.camunda.analytics.dataset.store.Identifiers;
import io.camunda.analytics.dimension.DimensionKey;
import java.util.Base64;

/**
 * The physical naming for the OpenSearch backend: one {@code dataset_<cubeId>} index per cube and
 * {@code projection_<cubeId>} per projected dataset, field identifiers validated through the shared
 * {@link Identifiers} allowlist, and a deterministic document id over a cube cell's dimensions +
 * window/tier so a re-write is an idempotent upsert. Accumulator blobs travel as Base64 in {@code
 * binary} fields. Mirrors the Elasticsearch backend's naming.
 */
final class OsNames {

  private static final String KEY_SEPARATOR = "\u0001";

  private OsNames() {}

  static String datasetIndex(final long cubeId) {
    return "dataset_" + cubeId;
  }

  static String projectionIndex(final long cubeId) {
    return "projection_" + cubeId;
  }

  static String field(final String declaredName) {
    return Identifiers.safeColumn(declaredName);
  }

  static String cellId(final DimensionKey key, final long windowStart, final long windowSize) {
    final StringBuilder builder = new StringBuilder();
    for (final Object value : key.values()) {
      builder.append(value == null ? " " : value).append(KEY_SEPARATOR);
    }
    return builder.append('|').append(windowStart).append('|').append(windowSize).toString();
  }

  static String encode(final byte[] bytes) {
    return Base64.getEncoder().encodeToString(bytes);
  }

  static byte[] decode(final String base64) {
    return Base64.getDecoder().decode(base64);
  }
}
