/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.compaction;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.eventbridge.broker.compaction.KeyHash.Hash128;
import org.junit.jupiter.api.Test;

/** Verifies the bounded latest-per-key map's update, lookup and overflow behavior. */
final class KeyOffsetMapTest {

  private static Hash128 h(final String key) {
    return KeyHash.hash(key.getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  @Test
  void shouldKeepLatestPositionPerKey() {
    // given
    final var map = new KeyOffsetMap(16);

    // when
    map.recordLatest(h("a"), 1);
    map.recordLatest(h("a"), 5);
    map.recordLatest(h("b"), 3);

    // then
    assertThat(map.latest(h("a"))).isEqualTo(5);
    assertThat(map.latest(h("b"))).isEqualTo(3);
    assertThat(map.latest(h("missing"))).isEqualTo(KeyOffsetMap.NO_ENTRY);
  }

  @Test
  void shouldReportNoEntryForUnknownKey() {
    // given
    final var map = new KeyOffsetMap(4);

    // when / then
    assertThat(map.latest(h("nope"))).isEqualTo(KeyOffsetMap.NO_ENTRY);
  }

  @Test
  void shouldAllowUpdatesToExistingKeysWhenFull() {
    // given a full map
    final var map = new KeyOffsetMap(2);
    assertThat(map.recordLatest(h("a"), 1)).isTrue();
    assertThat(map.recordLatest(h("b"), 2)).isTrue();

    // when updating an existing key at capacity
    final boolean updated = map.recordLatest(h("a"), 10);

    // then it succeeds
    assertThat(updated).isTrue();
    assertThat(map.latest(h("a"))).isEqualTo(10);
    assertThat(map.overflowed()).isFalse();
  }

  @Test
  void shouldOverflowOnNewKeyAtCapacityAndReportHighestFit() {
    // given a full map fed in ascending position order
    final var map = new KeyOffsetMap(2);
    map.recordLatest(h("a"), 1);
    map.recordLatest(h("b"), 2);

    // when a new key arrives with no room
    final boolean recorded = map.recordLatest(h("c"), 3);

    // then it is rejected and overflow is reported at the highest fully-absorbed position
    assertThat(recorded).isFalse();
    assertThat(map.overflowed()).isTrue();
    assertThat(map.highestFitPosition()).isEqualTo(2);
    assertThat(map.overflowPosition()).isEqualTo(3);
  }
}
