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
 * The storage that a {@link WriteBehindStore} keeps up to date, and the only place that knows how
 * keys and values look there. The store holds keys and values as objects; it asks the backend to
 * read, write, scan and size them.
 *
 * <p>Keys are ordered by {@link Comparable}, and that order must be the order in which the backend
 * scans: the store merges its unflushed writes into a scan by comparing keys.
 *
 * <p>Keys belong to groups, for example a column family or an entity type. A group is the unit of
 * the metrics.
 *
 * @param <K> the key type
 * @param <V> the value as the store holds it; immutable once the store has it, so that the writer
 *     and any number of readers can use it at the same time
 * @param <T> the caller's value objects, which reads fill in
 */
public interface Backend<K extends Comparable<K>, V, T> {

  /** The group that the key belongs to. */
  long groupOf(K key);

  /** Reads the committed value, or returns null if there is none. */
  @Nullable V get(K key);

  /** Whether the key has a committed value. */
  boolean exists(K key);

  /**
   * Returns false only if the key is certainly not stored; true means that it may be. Cheaper than
   * {@link #get}, so that the writer can skip deletes of keys that were never stored.
   */
  boolean mayExist(K key);

  /**
   * Whether the key is certainly not stored, so that a read can skip the storage. A cheap, in
   * memory answer only; false means that the storage must be asked.
   */
  default boolean knownAbsent(final K key) {
    return false;
  }

  /**
   * Starts a batch. Only the writer thread uses batches, one at a time, so a backend can reuse
   * scratch space between them.
   */
  Batch<K, V> newBatch();

  /** Opens a cursor on the committed state of a fixed instant. */
  Cursor<K, T> newCursor();

  /** Copies {@code source} into {@code target}, which may be of another value class. */
  void copy(V source, T target);

  /** The heap that a key with this value takes, for byte budgets. */
  int weigh(K key, @Nullable V value);

  interface Batch<K, V> extends AutoCloseable {

    void put(K key, V value);

    void delete(K key);

    /** Applies all puts and deletes atomically. */
    void write();

    @Override
    void close();
  }

  /**
   * Ordered, seekable cursor on committed state. Callers iterate either forward ({@link #seek},
   * {@link #next}) or in reverse ({@link #seekForPrev}, {@link #prev}), never both on one instance.
   */
  interface Cursor<K, T> extends AutoCloseable {

    /** Positions at the first key that is at or past {@code key}. */
    void seek(K key);

    /** Positions at the last key that is at or before {@code key}. */
    void seekForPrev(K key);

    boolean isValid();

    void next();

    void prev();

    K key();

    /** Decodes the current value into {@code target}. */
    void readValue(T target);

    @Override
    void close();
  }
}
