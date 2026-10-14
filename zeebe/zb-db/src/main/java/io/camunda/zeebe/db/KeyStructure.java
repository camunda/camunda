/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db;

import org.jspecify.annotations.Nullable;

/**
 * Receives the components of a {@link DbKey} in serialization order, see {@link
 * DbKey#describe(KeyStructure)}.
 *
 * <p>Every call declares one component, in the order {@link DbKey#write} serializes it. All
 * integers are big-endian, which is what makes RocksDB's unsigned byte-wise comparison order
 * non-negative values numerically.
 *
 * <p>The {@code name} is {@code null} where the key has no meaningful name for the component.
 *
 * <p>The {@code nonNegative} flag is {@code true} only when the component is known to never hold a
 * negative value. Negative values sort <em>above</em> all positive ones in RocksDB, so a backend
 * that wants numeric ordering must treat every component not flagged as non-negative as one that
 * may be negative.
 */
public interface KeyStructure {

  /** A signed 64-bit integer, 8 bytes. */
  void int64(@Nullable String name, boolean nonNegative);

  /** A signed 32-bit integer, 4 bytes. */
  void int32(@Nullable String name, boolean nonNegative);

  /** A signed 16-bit integer, 2 bytes. */
  void int16(@Nullable String name, boolean nonNegative);

  /** A signed 8-bit integer, 1 byte. */
  void int8(@Nullable String name, boolean nonNegative);

  /** A string: a 4-byte length prefix followed by that many bytes. */
  void string(@Nullable String name);

  /**
   * A string as {@link #string(String)}, but which is not serialized at all (zero bytes, no length
   * prefix) for some values of the preceding components.
   */
  void omittableString(@Nullable String name);

  /** Raw bytes without length prefix, spanning the remainder of the key. */
  void bytes(@Nullable String name);

  /** An enum ordinal stored as a single byte. */
  void enumOrdinal(@Nullable String name);
}
