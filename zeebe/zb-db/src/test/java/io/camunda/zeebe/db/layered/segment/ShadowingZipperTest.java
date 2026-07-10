/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.layered.segment;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.db.layered.Entry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

final class ShadowingZipperTest {

  @Test
  void shouldEmitOnlyBaseWhenOverlayIsEmpty() {
    // given
    final Iterator<Entry> overlay = Collections.emptyIterator();
    final Consumer<BiConsumer<byte[], byte[]>> base = pushing(pair(0x01, 0x0A), pair(0x02, 0x0B));

    // when
    final List<Map.Entry<byte[], byte[]>> result = zip(overlay, base);

    // then
    assertThat(keysOf(result)).containsExactly(bytes(0x01), bytes(0x02));
    assertThat(result.get(0).getValue()).isEqualTo(bytes(0x0A));
    assertThat(result.get(1).getValue()).isEqualTo(bytes(0x0B));
  }

  @Test
  void shouldDrainOverlayWhenBaseIsEmpty() {
    // given
    final Iterator<Entry> overlay = entries(put(bytes(0x01), 0x0A), put(bytes(0x02), 0x0B));
    final Consumer<BiConsumer<byte[], byte[]>> base = pushing();

    // when
    final List<Map.Entry<byte[], byte[]>> result = zip(overlay, base);

    // then
    assertThat(keysOf(result)).containsExactly(bytes(0x01), bytes(0x02));
  }

  @Test
  void shouldShadowEveryBasePairOnEqualKeys() {
    // given
    final Iterator<Entry> overlay = entries(put(bytes(0x01), 0x0A), put(bytes(0x02), 0x0B));
    final Consumer<BiConsumer<byte[], byte[]>> base = pushing(pair(0x01, 0x1A), pair(0x02, 0x1B));

    // when
    final List<Map.Entry<byte[], byte[]>> result = zip(overlay, base);

    // then -- the overlay versions win, each key appears once
    assertThat(keysOf(result)).containsExactly(bytes(0x01), bytes(0x02));
    assertThat(result.get(0).getValue()).isEqualTo(bytes(0x0A));
    assertThat(result.get(1).getValue()).isEqualTo(bytes(0x0B));
  }

  @Test
  void shouldHideBasePairBehindOverlayTombstone() {
    // given
    final Iterator<Entry> overlay = entries(tombstone(bytes(0x02)));
    final Consumer<BiConsumer<byte[], byte[]>> base = pushing(pair(0x01, 0x0A), pair(0x02, 0x0B));

    // when
    final List<Map.Entry<byte[], byte[]>> result = zip(overlay, base);

    // then -- the tombstone hides the base pair and is not visited itself
    assertThat(keysOf(result)).containsExactly(bytes(0x01));
  }

  @Test
  void shouldDropTombstonesWithoutBaseCounterpart() {
    // given -- tombstones before, between and after the base keys
    final Iterator<Entry> overlay =
        entries(tombstone(bytes(0x00)), tombstone(bytes(0x02)), tombstone(bytes(0x04)));
    final Consumer<BiConsumer<byte[], byte[]>> base = pushing(pair(0x01, 0x0A), pair(0x03, 0x0B));

    // when
    final List<Map.Entry<byte[], byte[]>> result = zip(overlay, base);

    // then
    assertThat(keysOf(result)).containsExactly(bytes(0x01), bytes(0x03));
  }

  @Test
  void shouldInterleaveOverlayAndBaseInUnsignedKeyOrder() {
    // given -- 0x80 is negative as a signed byte but must sort after 0x7F
    final Iterator<Entry> overlay = entries(put(bytes(0x02), 0x0A), put(bytes(0x80), 0x0B));
    final Consumer<BiConsumer<byte[], byte[]>> base = pushing(pair(0x01, 0x1A), pair(0x7F, 0x1B));

    // when
    final List<Map.Entry<byte[], byte[]>> result = zip(overlay, base);

    // then
    assertThat(keysOf(result)).containsExactly(bytes(0x01), bytes(0x02), bytes(0x7F), bytes(0x80));
  }

  @Test
  void shouldEmitOverlayTailAfterBaseEnds() {
    // given
    final Iterator<Entry> overlay =
        entries(put(bytes(0x01), 0x0A), put(bytes(0x05), 0x0B), put(bytes(0x06), 0x0C));
    final Consumer<BiConsumer<byte[], byte[]>> base = pushing(pair(0x02, 0x1A));

    // when
    final List<Map.Entry<byte[], byte[]>> result = zip(overlay, base);

    // then
    assertThat(keysOf(result)).containsExactly(bytes(0x01), bytes(0x02), bytes(0x05), bytes(0x06));
  }

  @Test
  void shouldZipKWayMergedOverlayWithDuplicateKeysAcrossPriorities() {
    // given -- the same key in two overlay layers: the k-way merge collapses it to the
    // highest-priority version before the zipper sees it
    final KWayMergeIterator overlay =
        new KWayMergeIterator(
            List.of(
                entries(put(bytes(0x01), 0x0A)),
                entries(put(bytes(0x01), 0x0B), put(bytes(0x03), 0x0C))));
    final Consumer<BiConsumer<byte[], byte[]>> base = pushing(pair(0x01, 0x1A), pair(0x02, 0x1B));

    // when
    final List<Map.Entry<byte[], byte[]>> result = zip(overlay, base);

    // then
    assertThat(keysOf(result)).containsExactly(bytes(0x01), bytes(0x02), bytes(0x03));
    assertThat(result.get(0).getValue()).isEqualTo(bytes(0x0A));
  }

  @Test
  void shouldEmitNothingWhenBothSidesAreEmpty() {
    // given / when
    final List<Map.Entry<byte[], byte[]>> result = zip(Collections.emptyIterator(), pushing());

    // then
    assertThat(result).isEmpty();
  }

  private static List<Map.Entry<byte[], byte[]>> zip(
      final Iterator<Entry> overlay, final Consumer<BiConsumer<byte[], byte[]>> baseScan) {
    final List<Map.Entry<byte[], byte[]>> result = new ArrayList<>();
    ShadowingZipper.merge(overlay, baseScan, (key, value) -> result.add(Map.entry(key, value)));
    return result;
  }

  @SafeVarargs
  private static Consumer<BiConsumer<byte[], byte[]>> pushing(
      final Map.Entry<byte[], byte[]>... pairs) {
    return callback -> {
      for (final Map.Entry<byte[], byte[]> pair : pairs) {
        callback.accept(pair.getKey(), pair.getValue());
      }
    };
  }

  private static Map.Entry<byte[], byte[]> pair(final int key, final int value) {
    return Map.entry(bytes(key), bytes(value));
  }

  private static Iterator<Entry> entries(final Entry... entries) {
    return List.of(entries).iterator();
  }

  private static Entry put(final byte[] key, final int value) {
    return new Entry(key, bytes(value), false);
  }

  private static Entry tombstone(final byte[] key) {
    return new Entry(key, null, true);
  }

  private static byte[] bytes(final int... values) {
    final byte[] result = new byte[values.length];
    for (int i = 0; i < values.length; i++) {
      result[i] = (byte) values[i];
    }
    return result;
  }

  private static List<byte[]> keysOf(final List<Map.Entry<byte[], byte[]>> pairs) {
    final List<byte[]> keys = new ArrayList<>();
    pairs.forEach(pair -> keys.add(pair.getKey()));
    return keys;
  }
}
