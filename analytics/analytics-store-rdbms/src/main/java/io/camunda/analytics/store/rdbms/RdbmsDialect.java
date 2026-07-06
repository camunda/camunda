/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.store.rdbms;

import io.camunda.analytics.dimension.DimensionType;
import java.util.Locale;

/**
 * The SQL dialects the analytics RDBMS store targets. Captures the few places generic ANSI SQL is
 * not enough: the binary column type and the upsert idiom. Detected once from the JDBC driver's
 * product name.
 */
public enum RdbmsDialect {
  H2,
  POSTGRESQL;

  public static RdbmsDialect fromProductName(final String productName) {
    final String name = productName == null ? "" : productName.toLowerCase(Locale.ROOT);
    return name.contains("postgres") ? POSTGRESQL : H2;
  }

  /**
   * Whether the dialect supports declarative range partitioning of the cube table by {@code
   * window_start} (Layer C). Postgres does; H2 (dev/test) has no declarative partitioning and keeps
   * the plain table + the Layer-A scan index.
   */
  public boolean supportsPartitioning() {
    return this == POSTGRESQL;
  }

  /** The column type for a stored accumulator blob. */
  public String blobType() {
    return this == POSTGRESQL ? "BYTEA" : "VARBINARY";
  }

  /** The column type for a finalized scalar ({@code <meter>value}). Both dialects accept this. */
  public String doubleType() {
    return "DOUBLE PRECISION";
  }

  /** The DDL column type for a dimension. */
  public String columnType(final DimensionType type) {
    return switch (type) {
      case STRING -> "VARCHAR(4000)";
      case LONG -> "BIGINT";
      case INT -> "INTEGER";
      case BOOLEAN -> "BOOLEAN";
    };
  }
}
