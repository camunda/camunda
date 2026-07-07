/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.dimension;

/**
 * The value type of a {@link DimensionColumn}. {@link #STRING} is a bounded, indexable identifier;
 * {@link #TEXT} is a large character payload (e.g. a process's BPMN XML) that maps to a large-text
 * column (CLOB/TEXT), read and written as a {@code String} — never a grouping key. The rest are the
 * scalar types dimensions take.
 */
public enum DimensionType {
  STRING,
  TEXT,
  LONG,
  INT,
  BOOLEAN;

  /**
   * Coerces a value read back from a serving store to the Java type this dimension's values take
   * (the type {@link DimensionKey} expects for the column): stores return loose representations —
   * JSON numbers deserialize as {@code Integer}, JDBC drivers return dialect-specific numerics, a
   * boolean may arrive as text — and every backend needs the identical normalization. A backend
   * with a store-specific wrapper (e.g. a JDBC CLOB) normalizes that to a plain value first and
   * passes the result in.
   */
  public Object coerce(final Object value) {
    if (value == null) {
      return null;
    }
    return switch (this) {
      case STRING, TEXT -> value.toString();
      case LONG -> ((Number) value).longValue();
      case INT -> ((Number) value).intValue();
      case BOOLEAN -> value instanceof final Boolean b ? b : Boolean.parseBoolean(value.toString());
    };
  }
}
