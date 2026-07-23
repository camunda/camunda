/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.state;

import io.camunda.analytics.lake.state.TranslatorState.VariantAccumulator;
import io.camunda.zeebe.db.DbValue;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

/**
 * ZeebeDb value flyweight for a {@link VariantAccumulator}, in the frozen variant-k1 wire layout
 * (see {@code io.camunda.analytics.lake.translate.LakeTranslator}'s "Variant capture" javadoc
 * section): {@code lastPosition(8) ++ hash(8) ++ count(2, unsigned) ++ seenHashes[count](4 each,
 * sorted ascending)}. The 2-byte {@code count} field doubles as {@code seenHashes}'s length prefix
 * — there is no separate array-length encoding — so a value produced here is exactly the bytes the
 * scheme's own binary layout describes, byte for byte.
 */
final class VariantAccumulatorValue implements DbValue {

  /** {@code count} is a 16-bit unsigned field (see class javadoc) — this is its capacity. */
  private static final int MAX_COUNT = 0xFFFF;

  private static final int[] EMPTY_HASHES = new int[0];

  private long lastPosition;
  private long hash;
  private int[] seenHashes = EMPTY_HASHES;

  VariantAccumulatorValue set(final VariantAccumulator accumulator) {
    lastPosition = accumulator.lastPosition();
    hash = accumulator.hash();
    seenHashes = accumulator.seenHashes();
    if (seenHashes.length > MAX_COUNT) {
      // Never expected in practice (a single BPMN process's distinct element/flow count would
      // have to exceed 65535) -- fail loudly rather than silently truncate the seen-set, which
      // would corrupt the fold's set semantics.
      throw new IllegalStateException(
          "variant accumulator seen-set size "
              + seenHashes.length
              + " exceeds the 16-bit count field's capacity of "
              + MAX_COUNT);
    }
    return this;
  }

  VariantAccumulator toRecord() {
    return new VariantAccumulator(lastPosition, hash, seenHashes.length, seenHashes);
  }

  @Override
  public void wrap(final DirectBuffer buffer, int offset, final int length) {
    lastPosition = buffer.getLong(offset);
    offset += Long.BYTES;
    hash = buffer.getLong(offset);
    offset += Long.BYTES;
    final int count = buffer.getShort(offset) & 0xFFFF;
    offset += Short.BYTES;
    final int[] values = new int[count];
    for (int i = 0; i < count; i++) {
      values[i] = buffer.getInt(offset);
      offset += Integer.BYTES;
    }
    seenHashes = values;
  }

  @Override
  public int getLength() {
    return Long.BYTES + Long.BYTES + Short.BYTES + seenHashes.length * Integer.BYTES;
  }

  @Override
  public int write(final MutableDirectBuffer buffer, int offset) {
    buffer.putLong(offset, lastPosition);
    offset += Long.BYTES;
    buffer.putLong(offset, hash);
    offset += Long.BYTES;
    buffer.putShort(offset, (short) seenHashes.length);
    offset += Short.BYTES;
    for (final int h32 : seenHashes) {
      buffer.putInt(offset, h32);
      offset += Integer.BYTES;
    }
    return getLength();
  }
}
