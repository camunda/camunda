/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.cache;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

final class DirtyIndexTest {

  private final DirtyIndex dirtyIndex = new DirtyIndex();

  @Test
  void shouldReturnPublishedVersion() {
    // given
    final var key = new CacheKey(TestColumnFamily.FIRST, new byte[] {1});
    final var value = new CachedValue(new byte[] {10, 11}, 1);

    // when
    dirtyIndex.put(key, value);

    // then
    assertThat(dirtyIndex.get(new CacheKey(TestColumnFamily.FIRST, new byte[] {1})))
        .isSameAs(value);
    assertThat(dirtyIndex.get(new CacheKey(TestColumnFamily.SECOND, new byte[] {1}))).isNull();
    assertThat(dirtyIndex.isEmpty()).isFalse();
  }

  @Test
  void shouldKeepOnlyTheNewestVersionOfAKey() {
    // given
    final var key = new CacheKey(TestColumnFamily.FIRST, new byte[] {1});
    dirtyIndex.put(key, new CachedValue(new byte[] {10, 11, 12}, 1));

    // when
    dirtyIndex.put(key, new CachedValue(null, 2));

    // then
    assertThat(dirtyIndex.get(key)).usingRecursiveComparison().isEqualTo(new CachedValue(null, 2));
  }

  @Test
  void shouldRemoveFlushedVersion() {
    // given
    final var key = new CacheKey(TestColumnFamily.FIRST, new byte[] {1});
    dirtyIndex.put(key, new CachedValue(new byte[] {10}, 1));

    // when
    dirtyIndex.removeFlushed(key, 1);

    // then
    assertThat(dirtyIndex.get(key)).isNull();
    assertThat(dirtyIndex.isEmpty()).isTrue();
  }

  @Test
  void shouldKeepNewerVersionWhenAnOlderOneIsFlushed() {
    // given
    final var key = new CacheKey(TestColumnFamily.FIRST, new byte[] {1});
    dirtyIndex.put(key, new CachedValue(new byte[] {10}, 1));
    final var newer = new CachedValue(new byte[] {20}, 2);
    dirtyIndex.put(key, newer);

    // when
    dirtyIndex.removeFlushed(key, 1);

    // then
    assertThat(dirtyIndex.get(key)).isSameAs(newer);
  }

  @Test
  void shouldIgnoreFlushOfAKeyItDoesNotHold() {
    // when
    dirtyIndex.removeFlushed(new CacheKey(TestColumnFamily.FIRST, new byte[] {1}), 1);

    // then
    assertThat(dirtyIndex.isEmpty()).isTrue();
  }
}
