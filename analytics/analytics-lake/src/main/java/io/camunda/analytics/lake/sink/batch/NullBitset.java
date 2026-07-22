/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.sink.batch;

import java.util.Arrays;

/**
 * Bit-per-row null mask shared by every heap column implementation: bit {@code row} set means
 * "null". Pure storage helper over a caller-owned {@code long[]} — never allocates past {@link
 * #allocate(int)}.
 */
final class NullBitset {

  private static final int BITS_PER_WORD = 64;

  private NullBitset() {}

  static long[] allocate(final int rowCapacity) {
    return new long[wordsFor(rowCapacity)];
  }

  static int wordsFor(final int rowCapacity) {
    return (rowCapacity + BITS_PER_WORD - 1) / BITS_PER_WORD;
  }

  static boolean isSet(final long[] words, final int row) {
    return (words[row / BITS_PER_WORD] & (1L << row)) != 0;
  }

  static void set(final long[] words, final int row) {
    words[row / BITS_PER_WORD] |= 1L << row;
  }

  static void clear(final long[] words, final int row) {
    words[row / BITS_PER_WORD] &= ~(1L << row);
  }

  static void clearAll(final long[] words) {
    Arrays.fill(words, 0L);
  }
}
