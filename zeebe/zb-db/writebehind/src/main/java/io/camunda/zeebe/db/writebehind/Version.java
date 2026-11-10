/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.writebehind;

import org.jspecify.annotations.Nullable;

/**
 * A committed value of one key. Equality is identity on purpose: the writer removes a version from
 * the dirty index only if it is still the exact instance that it flushed.
 */
final class Version {

  private static final int ENTRY_OVERHEAD = 64;

  private final byte @Nullable [] bytes;
  private final long seq;

  Version(final byte @Nullable [] bytes, final long seq) {
    this.bytes = bytes;
    this.seq = seq;
  }

  /** The value bytes, or null for a delete. Shared: callers must copy before handing them out. */
  byte @Nullable [] bytes() {
    return bytes;
  }

  /** The commit sequence of the transaction that wrote this version. */
  long seq() {
    return seq;
  }

  boolean isTombstone() {
    return bytes == null;
  }

  /** Heap estimate of a key with this version, for byte budgets. */
  int weigh(final RawKey key) {
    return ENTRY_OVERHEAD + key.bytes().length + (bytes == null ? 0 : bytes.length);
  }
}
