/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.db.DbKey;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import org.agrona.ExpandableArrayBuffer;
import org.junit.jupiter.api.Test;

/**
 * Pins the serialized layout of the key types. Backends order keys by comparing these bytes as
 * unsigned values, so the ordering conformance tests only hold if the layout stays as asserted
 * here.
 */
final class DbKeySerializationOrderTest {

  private static final Comparator<byte[]> UNSIGNED = Arrays::compareUnsigned;

  @Test
  void shouldSerializeNegativeLongsAfterPositiveOnesInUnsignedOrder() {
    // given
    final List<Long> ascending = List.of(0L, 1L, Long.MAX_VALUE, Long.MIN_VALUE, -2L, -1L);

    // when
    final var serialized = ascending.stream().map(v -> serialize(longKey(v))).toList();

    // then
    assertThat(serialized).isSortedAccordingTo(UNSIGNED);
    assertThat(serialized.get(0)).hasSize(Long.BYTES);
  }

  @Test
  void shouldSerializeNegativeIntsAfterPositiveOnesInUnsignedOrder() {
    // given
    final List<Integer> ascending = List.of(0, 1, Integer.MAX_VALUE, Integer.MIN_VALUE, -2, -1);

    // when
    final var serialized = ascending.stream().map(v -> serialize(intKey(v))).toList();

    // then
    assertThat(serialized).isSortedAccordingTo(UNSIGNED);
    assertThat(serialized.get(0)).hasSize(Integer.BYTES);
  }

  @Test
  void shouldSerializeStringsWithBigEndianLengthPrefix() {
    // given
    final var string = new DbString();
    string.wrapString("ab");

    // when
    final var bytes = serialize(string);

    // then
    assertThat(bytes).containsExactly(0, 0, 0, 2, 'a', 'b');
  }

  @Test
  void shouldSerializeShorterStringsBeforeLongerOnes() {
    // given
    final List<String> ascending = List.of("", "z", "zz", "aaa");

    // when
    final var serialized = ascending.stream().map(v -> serialize(string(v))).toList();

    // then
    assertThat(serialized).isSortedAccordingTo(UNSIGNED);
  }

  private static DbLong longKey(final long value) {
    final var key = new DbLong();
    key.wrapLong(value);
    return key;
  }

  private static DbInt intKey(final int value) {
    final var key = new DbInt();
    key.wrapInt(value);
    return key;
  }

  private static DbString string(final String value) {
    final var key = new DbString();
    key.wrapString(value);
    return key;
  }

  private static byte[] serialize(final DbKey key) {
    final var buffer = new ExpandableArrayBuffer();
    final int length = key.write(buffer, 0);
    final byte[] bytes = new byte[length];
    buffer.getBytes(0, bytes);
    return bytes;
  }
}
