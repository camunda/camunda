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

  /** Copies or decodes the value of the current key into {@code target}. */
  void readValue(DbValue target);

  @Override
  void close();
}
