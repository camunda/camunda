/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.writebehind;

import org.jspecify.annotations.Nullable;

final class Version<V> {

  private final @Nullable V value;
  private final long seq;
  private final @Nullable Version<V> previous;

  Version(final @Nullable V value, final long seq) {
    this(value, seq, null);
  }

  /**
   * @param previous the version of the same key in the same batch that this one replaced, so that a
   *     reader of an older commit still finds the value it needs
   */
  Version(final @Nullable V value, final long seq, final @Nullable Version<V> previous) {
    this.value = value;
    this.seq = seq;
    this.previous = previous;
  }

  /** The latest version up to the commit with the given sequence, or null if there is none. */
  @Nullable Version<V> asOf(final long commitSeq) {
    Version<V> version = this;
    while (version != null && version.seq > commitSeq) {
      version = version.previous;
    }
    return version;
  }

  /** The value, or null for a delete. Shared: callers must copy it before handing it out. */
  @Nullable V value() {
    return value;
  }

  /** The commit sequence of the transaction that wrote this version. */
  long seq() {
    return seq;
  }

  boolean isTombstone() {
    return value == null;
  }
}
