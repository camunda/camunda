/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.writebehind;

import io.camunda.zeebe.db.DbValue;

/**
 * A sorted, forward or reverse walk over the committed entries of one column family prefix, without
 * a transaction's own writes. Deleted keys are never returned.
 */
interface CommittedSource extends AutoCloseable {

  /** Positions on the first key that is at or after {@code key}; later steps go forward. */
  void seek(byte[] key);

  /** Positions on the last key that is at or before {@code key}; later steps go backward. */
  void seekForPrev(byte[] key);

  boolean isValid();

  /** The current raw key. Shared: callers must not change it. */
  byte[] key();

  /** Moves on in the direction of the last seek. */
  void advance();

  /**
   * Copies or decodes the value of the current key into {@code target}. Returns false if the source
   * turned {@link #isStale() stale} instead.
   */
  boolean readValue(DbValue target);

  /**
   * Whether the source can't continue and its position is lost. Only a source that doesn't read a
   * fixed instant can turn stale; the caller continues with another source.
   */
  default boolean isStale() {
    return false;
  }

  @Override
  void close();
}
