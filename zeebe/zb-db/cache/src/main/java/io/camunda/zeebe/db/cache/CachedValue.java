/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.cache;

import java.util.Arrays;
import org.jspecify.annotations.Nullable;

/**
 * A committed version of a key.
 *
 * <p>Not a record: EhCache can't measure the size of a record, and then neither stores the entry
 * nor delays its write.
 */
final class CachedValue {

  private final byte @Nullable [] bytes;
  private final long seq;

  /**
   * @param bytes the value as written by {@link io.camunda.zeebe.db.DbValue#write}, or {@code null}
   *     for a tombstone. The array is shared with the cache and the DirtyIndex, so it must never be
   *     mutated or handed out.
   * @param seq the commit sequence; all writes of one transaction share it
   */
  CachedValue(final byte @Nullable [] bytes, final long seq) {
    this.bytes = bytes;
    this.seq = seq;
  }

  byte @Nullable [] bytes() {
    return bytes;
  }

  long seq() {
    return seq;
  }

  boolean isTombstone() {
    return bytes == null;
  }

  @Override
  public String toString() {
    return "CachedValue{bytes=" + Arrays.toString(bytes) + ", seq=" + seq + '}';
  }
}
