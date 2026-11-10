/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db;

/**
 * Ordered, seekable cursor over raw keys and values, sorted bytewise (unsigned). Mirrors the subset
 * of RocksDB's iterator that column families use, so that other implementations can back an
 * iteration.
 *
 * <p>Callers iterate either forward ({@link #seek}, {@link #next}) or in reverse ({@link
 * #seekForPrev}, {@link #prev}), never both on the same instance.
 */
public interface DbIterator extends AutoCloseable {

  /** Positions at the first key that is at or past {@code key}. */
  void seek(byte[] key);

  /** Positions at the last key that is at or before {@code key}. */
  void seekForPrev(byte[] key);

  boolean isValid();

  void next();

  void prev();

  /** Returns a fresh copy of the current key. */
  byte[] key();

  /** Returns a fresh copy of the current value. */
  byte[] value();

  /**
   * Copies the current key into {@code buffer}.
   *
   * @return the full key length, which exceeds {@code buffer.length} if the key was truncated
   */
  int key(byte[] buffer);

  /**
   * Copies the current value into {@code buffer}.
   *
   * @return the full value length, which exceeds {@code buffer.length} if the value was truncated
   */
  int value(byte[] buffer);

  @Override
  void close();
}
