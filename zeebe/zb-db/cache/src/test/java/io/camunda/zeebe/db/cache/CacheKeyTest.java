/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

final class CacheKeyTest {

  @Test
  void shouldSortByColumnFamilyValueThenByUnsignedKeyBytes() {
    // given
    final var firstLow = new CacheKey(TestColumnFamily.FIRST, new byte[] {0x01});
    final var firstHigh = new CacheKey(TestColumnFamily.FIRST, new byte[] {(byte) 0xFF});
    final var firstLonger = new CacheKey(TestColumnFamily.FIRST, new byte[] {(byte) 0xFF, 0x00});
    final var second = new CacheKey(TestColumnFamily.SECOND, new byte[] {0x00});
    final List<CacheKey> keys = new ArrayList<>(List.of(second, firstLonger, firstHigh, firstLow));

    // when
    Collections.sort(keys);

    // then
    assertThat(keys).containsExactly(firstLow, firstHigh, firstLonger, second);
  }

  @Test
  void shouldBeEqualForSameColumnFamilyAndKeyBytes() {
    // given
    final var key = new CacheKey(TestColumnFamily.FIRST, new byte[] {1, 2, 3});
    final var sameKey = new CacheKey(TestColumnFamily.FIRST, new byte[] {1, 2, 3});

    // then
    assertThat(key).isEqualTo(sameKey).hasSameHashCodeAs(sameKey);
    assertThat(key.compareTo(sameKey)).isZero();
  }

  @Test
  void shouldNotBeEqualForDifferentColumnFamilies() {
    // given
    final var key = new CacheKey(TestColumnFamily.FIRST, new byte[] {1, 2, 3});
    final var otherColumnFamily = new CacheKey(TestColumnFamily.SECOND, new byte[] {1, 2, 3});

    // then
    assertThat(key).isNotEqualTo(otherColumnFamily);
    assertThat(key.compareTo(otherColumnFamily)).isNotZero();
  }
}
