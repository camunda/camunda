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
 * keys and values look there. The store holds keys and values as objects and never as bytes; it
 * asks the backend to read, write, scan and size them.
 *
 * <p>Keys are ordered by {@link Comparable}, and that order must be the order in which the backend
 * scans: the store merges its unflushed writes into a scan by comparing keys.
 *
 * <p>Keys belong to groups, for example a column family or an entity type. A group is the unit of
 * the key index and of the metrics.
 *
 * @param <K> the key type
 * @param <V> the value type
 */
interface Backend<K extends Comparable<K>, V> {

  /** The group that the key belongs to. */
  long groupOf(K key);

  /**
   * The range of all keys of a group; the cursor of a scan of the group stays in it. The range is
   * the smallest key of the group and the first key after it, or null if there is none.
   */
  KeyRange<K> groupRange(long group);

  /**
   * A key at or after every key of the group, that the group's cursors still accept as a seek key.
   * Used to seek to the last key of a group.
   */
  K groupLastSeekKey(long group);

  /**
   * Reads the committed value, or returns null if there is none. A decoded value is a new instance
   * of the class of {@code prototype}, which only decides how to decode.
   */
  @Nullable V get(K key, V prototype);

  /** Whether the key has a committed value. */
  boolean exists(K key);

  /**
   * Returns false only if the key is certainly not stored; true means that it may be. Cheaper than
   * {@link #get}, so that the writer can skip deletes of keys that were never stored.
   */
  boolean mayExist(K key);

  /**
   * Starts a batch. Only the writer thread uses batches, one at a time, so a backend can reuse
   * scratch space between them.
   */
  Batch<K, V> newBatch();

  /** Opens a cursor on the committed state of a fixed instant. */
  Cursor<K, V> newCursor();

  /** Copies {@code source} into {@code target}, which may be of another value class. */
  void copy(V source, V target);

  /** A rough estimate of the heap that a key with this value takes, for byte budgets. */
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
  interface Cursor<K, V> extends AutoCloseable {

    /** Positions at the first key that is at or past {@code key}. */
    void seek(K key);

    /** Positions at the last key that is at or before {@code key}. */
    void seekForPrev(K key);

    boolean isValid();

    void next();

    void prev();

    K key();

    /** Decodes the current value into {@code target}. */
    void readValue(V target);

    @Override
    void close();
  }
}
