/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.sink;

/**
 * One column of a {@link Segment}: a pre-allocated, reusable, type-specialized value container.
 *
 * <p>Contract: implementations allocate all storage at construction and never at steady state;
 * {@link #reset()} rewinds without releasing. Access is index-based and unsynchronized — the ring's
 * ownership rules (single writer while filling, single reader after sealing) are the only
 * concurrency control, published by the ring's volatile head/tail stores.
 *
 * <p>Null handling: nullable columns track a null mask internally; typed getters on null slots
 * return an undefined value — callers must check {@link #isNull(int)} first (only on columns whose
 * schema says {@code nullable}).
 */
public interface ColumnVector {

  ColumnType type();

  /** Rewind to empty. Must not allocate or release storage. */
  void reset();

  boolean isNull(int row);

  void setNull(int row);

  /** {@link ColumnType#LONG} storage. */
  interface LongColumn extends ColumnVector {
    long get(int row);

    void set(int row, long value);
  }

  /** {@link ColumnType#INT} storage. */
  interface IntColumn extends ColumnVector {
    int get(int row);

    void set(int row, int value);
  }

  /**
   * {@link ColumnType#STRING_DICT} storage: values are interned to int codes on append; the code
   * table lives with the vector (or is shared per pipeline) and resolves codes back to values for
   * the encoder handoff.
   */
  interface DictColumn extends ColumnVector {
    int code(int row);

    /** Interns {@code value} (allocation-free for already-seen values) and stores its code. */
    void set(int row, CharSequence value);

    /** Resolves a code back to its string value (encoder-side, cold path). */
    String value(int code);
  }

  /** {@link ColumnType#BINARY} storage: byte arena + offsets. */
  interface BinaryColumn extends ColumnVector {
    /** Copies {@code len} bytes from {@code src} into the arena and stores the slice. */
    void set(int row, byte[] src, int offset, int len);

    int length(int row);

    /** Copies the row's bytes into {@code dst} at {@code dstOffset}; returns the length. */
    int copyTo(int row, byte[] dst, int dstOffset);
  }

  /** {@link ColumnType#DOUBLE} storage. */
  interface DoubleColumn extends ColumnVector {
    double get(int row);

    void set(int row, double value);
  }
}
