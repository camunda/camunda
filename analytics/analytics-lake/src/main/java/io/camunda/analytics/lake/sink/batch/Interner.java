/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.sink.batch;

/**
 * Interns {@link CharSequence} values to small integer codes, shared by every {@code STRING_DICT}
 * column of one pipeline (see {@link SegmentFactory}).
 *
 * <h2>Allocation</h2>
 *
 * <p>{@link #intern(CharSequence)} is allocation-free for values seen before: it hashes the
 * candidate directly off the {@link CharSequence} (no {@code toString()}) and probes an
 * open-addressing table that stores codes, not values, so the equality check is {@link
 * String#contentEquals(CharSequence)} against the already-interned string — no new object created
 * on the lookup path. Only the first appearance of a distinct value allocates (a {@code String}
 * copy, plus occasionally a doubled backing array): rare by construction, since real dictionary
 * columns are low-cardinality, and permanent — codes are never retired.
 *
 * <h2>Thread-safety</h2>
 *
 * <p>Written only by the poll thread (via {@link #intern}); read only by the flush thread (via
 * {@link #valueOf}), which only ever resolves codes it already observed in a sealed segment.
 * Publication works by storing the (possibly unchanged) {@code values} array reference to a {@code
 * volatile} field on every {@link #intern} call, whether or not that call grew the array: the plain
 * writes that populate a new slot happen strictly before that volatile store, in the writing
 * thread's program order, so the JMM's happens-before rule for a volatile write followed by a
 * volatile read of the same value guarantees any later reader of the field sees at least that slot.
 * In practice the flush thread's visibility is doubly guaranteed here: every code it resolves was
 * interned strictly before the poll thread sealed the segment carrying it, and sealing itself
 * piggybacks its own volatile store ({@link
 * io.camunda.analytics.lake.sink.ColumnarSegmentRing#seal}) which the flush thread must already
 * have observed before it can see the segment at all.
 *
 * <p>The open-addressing hash table used to locate existing codes is write-side-only state — the
 * flush thread never touches it — so it needs no synchronization of its own.
 */
public final class Interner {

  private static final int DEFAULT_INITIAL_CAPACITY = 64;
  private static final float MAX_LOAD_FACTOR = 0.5f;

  private volatile String[] values;
  private int size;

  private int[] table; // write-side (poll-thread-only) open-addressing index: slot holds code + 1
  private int tableMask;
  private int tableEntries;

  public Interner() {
    this(DEFAULT_INITIAL_CAPACITY);
  }

  public Interner(final int initialCapacity) {
    final int capacity = Math.max(initialCapacity, 1);
    values = new String[capacity];
    table = new int[nextPowerOfTwo(capacity * 2)];
    tableMask = table.length - 1;
  }

  /** Poll thread only. Allocation-free if {@code value} was interned before. */
  public int intern(final CharSequence value) {
    final int hash = hash(value);
    int slot = hash & tableMask;
    while (true) {
      final int codePlusOne = table[slot];
      if (codePlusOne == 0) {
        return insert(value, slot);
      }
      final int code = codePlusOne - 1;
      if (values[code].contentEquals(value)) {
        return code;
      }
      slot = (slot + 1) & tableMask;
    }
  }

  /**
   * Flush thread only (cold path; may box). Resolves a code assigned by a prior {@link #intern}.
   */
  public String valueOf(final int code) {
    return values[code];
  }

  private int insert(final CharSequence value, final int slot) {
    String[] arr = values;
    if (size == arr.length) {
      arr = growValues(arr);
    }
    final int code = size;
    arr[code] = value.toString();
    size++;
    table[slot] = code + 1;
    tableEntries++;
    values = arr; // volatile store: publishes this entry (and any growth) to the flush thread
    if (tableEntries > table.length * MAX_LOAD_FACTOR) {
      growTable();
    }
    return code;
  }

  private static String[] growValues(final String[] old) {
    final String[] next = new String[old.length * 2];
    System.arraycopy(old, 0, next, 0, old.length);
    return next;
  }

  private void growTable() {
    final int[] old = table;
    final int[] next = new int[old.length * 2];
    final int mask = next.length - 1;
    for (final int codePlusOne : old) {
      if (codePlusOne == 0) {
        continue;
      }
      int slot = hash(values[codePlusOne - 1]) & mask;
      while (next[slot] != 0) {
        slot = (slot + 1) & mask;
      }
      next[slot] = codePlusOne;
    }
    table = next;
    tableMask = mask;
  }

  private static int hash(final CharSequence value) {
    int h = 0;
    final int length = value.length();
    for (int i = 0; i < length; i++) {
      h = 31 * h + value.charAt(i);
    }
    return h ^ (h >>> 16);
  }

  private static int nextPowerOfTwo(final int value) {
    int result = 1;
    while (result < value) {
      result <<= 1;
    }
    return result;
  }
}
