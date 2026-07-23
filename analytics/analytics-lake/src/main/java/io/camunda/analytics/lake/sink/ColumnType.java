/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.sink;

/**
 * The value shapes a sink column can have. Deliberately minimal: flat schemas of primitives,
 * dictionary-encoded strings, and raw binary — the property the whole zero-allocation batch design
 * relies on. Nested types are out of scope by design; if they ever appear, the bespoke encoder rung
 * dies and the library-only path wins (see the write-path design discussion).
 */
public enum ColumnType {
  /** 64-bit integer, stored in a {@code long[]}. */
  LONG,
  /** 32-bit integer, stored in an {@code int[]}. */
  INT,
  /**
   * Low-cardinality string, stored as an {@code int[]} of codes interned by the segment's
   * dictionary. The code never leaves the process: encoders resolve it back to the string value at
   * the handoff seam, so files always carry real values.
   */
  STRING_DICT,
  /** Variable-length bytes (e.g. vars JSON), stored in a byte arena + offsets. */
  BINARY,
  /**
   * 64-bit IEEE 754 floating point, stored in a {@code double[]}. Never part of a sort key (see
   * {@code SegmentSorter}'s own validation): profile-shaped measures never sort on values, and the
   * segment sorter's comparator has no ordering defined for {@code DOUBLE} — attempting to declare
   * one as a sort key column fails fast at pipeline construction rather than at first compare.
   */
  DOUBLE
}
