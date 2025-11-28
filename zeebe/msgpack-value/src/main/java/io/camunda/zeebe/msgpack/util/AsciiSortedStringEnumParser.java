/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.msgpack.util;

import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.IntStream;
import org.agrona.DirectBuffer;

public final class AsciiSortedStringEnumParser<E extends Enum<E>> implements EnumParser<E> {
  private final byte[][] enumNamesBytes; // Pre-computed byte arrays
  private final E[] enumValues;

  public AsciiSortedStringEnumParser(final Class<E> enumClass) {
    final E[] values = enumClass.getEnumConstants();
    final var enumNamesBytes = new byte[values.length][];
    final var enumValues = values.clone();

    // Pre-compute byte arrays and sort
    for (int i = 0; i < values.length; i++) {
      enumNamesBytes[i] = values[i].name().getBytes(StandardCharsets.US_ASCII);
    }

    // Sort both arrays together
    final Integer[] indices = IntStream.range(0, values.length).boxed().toArray(Integer[]::new);
    Arrays.sort(indices, (i, j) -> compareBytes(enumNamesBytes[i], enumNamesBytes[j]));

    // Reorder arrays
    final byte[][] sortedNames = new byte[values.length][];
    final E[] sortedValues = (E[]) Array.newInstance(enumClass, values.length);
    for (int i = 0; i < values.length; i++) {
      sortedNames[i] = enumNamesBytes[indices[i]];
      sortedValues[i] = enumValues[indices[i]];
    }

    this.enumNamesBytes = sortedNames;
    this.enumValues = sortedValues;
  }

  @Override
  public E parse(final DirectBuffer buffer, final int offset, final int length) {
    int left = 0;
    int right = enumNamesBytes.length - 1;

    while (left <= right) {
      final int mid = (left + right) >>> 1;
      final int cmp = compareBufferToBytes(buffer, offset, length, enumNamesBytes[mid]);

      if (cmp == 0) {
        return enumValues[mid];
      } else if (cmp < 0) {
        right = mid - 1;
      } else {
        left = mid + 1;
      }
    }

    return null;
  }

  @Override
  public E parse(
      final DirectBuffer buffer, final int offset, final int length, final E defaultValue) {
    var value = parse(buffer, offset, length);
    if (value == null) {
      value = defaultValue;
    }
    return value;
  }

  private int compareBufferToBytes(
      final DirectBuffer buffer, final int offset, final int length, final byte[] target) {
    final int minLength = Math.min(length, target.length);

    for (int i = 0; i < minLength; i++) {
      final int bufferByte = buffer.getByte(offset + i) & 0xFF;
      final int targetByte = target[i] & 0xFF;

      if (bufferByte != targetByte) {
        return bufferByte - targetByte;
      }
    }

    return length - target.length;
  }

  /** Compare two byte arrays lexicographically */
  private static int compareBytes(final byte[] a, final byte[] b) {
    final int minLength = Math.min(a.length, b.length);

    for (int i = 0; i < minLength; i++) {
      final int aByte = a[i] & 0xFF; // Convert to unsigned
      final int bByte = b[i] & 0xFF; // Convert to unsigned

      if (aByte != bByte) {
        return aByte - bByte;
      }
    }

    // If all compared bytes are equal, shorter array comes first
    return a.length - b.length;
  }
}
