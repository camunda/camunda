/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.writebehind;

import io.camunda.zeebe.db.DbValue;
import org.jspecify.annotations.Nullable;

/**
 * A committed value of one key, held as an object. Equality is identity on purpose: the writer
 * removes a version from the dirty index only if it is still the exact instance that it flushed.
 *
 * <p>The value is a private copy that nobody changes after the commit: readers copy out of it, and
 * the writer serializes it. Neither changes its state, so both can read it at the same time.
 */
final class Version {

  private static final int ENTRY_OVERHEAD = 64;

  /**
   * Fixed guess of a value's size. Measuring the real size means encoding the value, which is the
   * work this layer exists to avoid.
   */
  private static final int ESTIMATED_VALUE_BYTES = 256;

  private final @Nullable DbValue value;
  private final long seq;

  Version(final @Nullable DbValue value, final long seq) {
    this.value = value;
    this.seq = seq;
  }

  /** The value, or null for a delete. Shared: callers must copy it before handing it out. */
  @Nullable DbValue value() {
    return value;
  }

  /** The commit sequence of the transaction that wrote this version. */
  long seq() {
    return seq;
  }

  boolean isTombstone() {
    return value == null;
  }

  /** Rough heap estimate of a key with this version, for byte budgets. */
  int weigh(final RawKey key) {
    return ENTRY_OVERHEAD + key.bytes().length + (value == null ? 0 : ESTIMATED_VALUE_BYTES);
  }
}
