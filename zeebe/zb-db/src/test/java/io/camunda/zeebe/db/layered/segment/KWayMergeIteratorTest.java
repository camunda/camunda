/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.layered.segment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.db.layered.Entry;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;

final class KWayMergeIteratorTest {

  @Test
  void shouldMergeStreamsInUnsignedKeyOrder() {
    // given -- 0x80 is negative as a signed byte but must sort after 0x7F
    final Iterator<Entry> first = entries(put(bytes(0x01), 0x0A), put(bytes(0x80), 0x0B));
    final Iterator<Entry> second = entries(put(bytes(0x7F), 0x0C), put(bytes(0xFF), 0x0D));

    // when
    final KWayMergeIterator merged = new KWayMergeIterator(List.of(first, second));

    // then
    assertThat(keysOf(merged)).containsExactly(bytes(0x01), bytes(0x7F), bytes(0x80), bytes(0xFF));
  }

  @Test
  void shouldPreferLowestIndexStreamOnEqualKeys() {
    // given
    final Iterator<Entry> newest = entries(put(bytes(0x01), 0x0A));
    final Iterator<Entry> oldest = entries(put(bytes(0x01), 0x0B));

    // when
    final KWayMergeIterator merged = new KWayMergeIterator(List.of(newest, oldest));

    // then
    final Entry entry = merged.next();
    assertThat(entry.value()).isEqualTo(bytes(0x0A));
    assertThat(merged.hasNext()).isFalse();
  }

  @Test
  void shouldSkipShadowedVersionsInAllLowerPriorityStreams() {
    // given -- the shared key must be emitted once and not stall lower-priority streams
    final Iterator<Entry> first = entries(put(bytes(0x02), 0x0A));
    final Iterator<Entry> second = entries(put(bytes(0x02), 0x0B), put(bytes(0x03), 0x0C));
    final Iterator<Entry> third = entries(put(bytes(0x01), 0x0D), put(bytes(0x02), 0x0E));

    // when
    final KWayMergeIterator merged = new KWayMergeIterator(List.of(first, second, third));
    final List<Entry> result = drain(merged);

    // then
    assertThat(result).hasSize(3);
    assertThat(result.get(0).key()).isEqualTo(bytes(0x01));
    assertThat(result.get(1).key()).isEqualTo(bytes(0x02));
    assertThat(result.get(1).value()).isEqualTo(bytes(0x0A));
    assertThat(result.get(2).key()).isEqualTo(bytes(0x03));
  }

  @Test
  void shouldReturnWinningTombstoneOnEqualKeys() {
    // given -- a newer tombstone hides an older value and is emitted as-is
    final Iterator<Entry> newest = entries(new Entry(bytes(0x01), null, true));
    final Iterator<Entry> oldest = entries(put(bytes(0x01), 0x0B));

    // when
    final KWayMergeIterator merged = new KWayMergeIterator(List.of(newest, oldest));

    // then
    final Entry entry = merged.next();
    assertThat(entry.tombstone()).isTrue();
    assertThat(entry.flushed()).isTrue();
    assertThat(merged.hasNext()).isFalse();
  }

  @Test
  void shouldSkipEmptyStreams() {
    // given
    final Iterator<Entry> empty = entries();
    final Iterator<Entry> nonEmpty = entries(put(bytes(0x01), 0x0A));

    // when
    final KWayMergeIterator merged = new KWayMergeIterator(List.of(empty, nonEmpty, entries()));

    // then
    assertThat(keysOf(merged)).containsExactly(bytes(0x01));
  }

  @Test
  void shouldBeExhaustedWithoutStreams() {
    // given / when
    final KWayMergeIterator merged = new KWayMergeIterator(List.of());

    // then
    assertThat(merged.hasNext()).isFalse();
  }

  @Test
  void shouldBeExhaustedWhenAllStreamsAreEmpty() {
    // given / when
    final KWayMergeIterator merged = new KWayMergeIterator(List.of(entries(), entries()));

    // then
    assertThat(merged.hasNext()).isFalse();
  }

  @Test
  void shouldThrowNoSuchElementExceptionWhenExhausted() {
    // given
    final KWayMergeIterator merged =
        new KWayMergeIterator(List.of(entries(put(bytes(0x01), 0x0A))));
    merged.next();

    // when / then
    assertThat(merged.hasNext()).isFalse();
    assertThatThrownBy(merged::next).isInstanceOf(NoSuchElementException.class);
  }

  @Test
  void shouldDrainStreamsOfDifferentLengths() {
    // given
    final Iterator<Entry> shortStream = entries(put(bytes(0x05), 0x0A));
    final Iterator<Entry> longStream =
        entries(
            put(bytes(0x01), 0x0B),
            put(bytes(0x03), 0x0C),
            put(bytes(0x07), 0x0D),
            put(bytes(0x09), 0x0E));

    // when
    final KWayMergeIterator merged = new KWayMergeIterator(List.of(shortStream, longStream));

    // then
    assertThat(keysOf(merged))
        .containsExactly(bytes(0x01), bytes(0x03), bytes(0x05), bytes(0x07), bytes(0x09));
  }

  private static Iterator<Entry> entries(final Entry... entries) {
    return List.of(entries).iterator();
  }

  private static Entry put(final byte[] key, final int value) {
    return new Entry(key, bytes(value), false);
  }

  private static byte[] bytes(final int... values) {
    final byte[] result = new byte[values.length];
    for (int i = 0; i < values.length; i++) {
      result[i] = (byte) values[i];
    }
    return result;
  }

  private static List<Entry> drain(final Iterator<Entry> iterator) {
    final List<Entry> result = new ArrayList<>();
    iterator.forEachRemaining(result::add);
    return result;
  }

  private static List<byte[]> keysOf(final Iterator<Entry> entries) {
    final List<byte[]> keys = new ArrayList<>();
    entries.forEachRemaining(entry -> keys.add(entry.key()));
    return keys;
  }
}
