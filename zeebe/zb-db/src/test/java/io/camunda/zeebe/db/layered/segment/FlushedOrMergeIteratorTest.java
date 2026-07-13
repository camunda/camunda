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
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

final class FlushedOrMergeIteratorTest {

  private static final byte[] EMPTY_PREFIX = new byte[0];

  @Test
  void shouldMergeStreamsInUnsignedKeyOrder() {
    // given -- 0x80 is negative as a signed byte but must sort after 0x7F
    final Iterator<Entry> first = entries(put(bytes(0x01), 0x0A), put(bytes(0x80), 0x0B));
    final Iterator<Entry> second = entries(put(bytes(0x7F), 0x0C), put(bytes(0xFF), 0x0D));

    // when
    final FlushedOrMergeIterator merged = new FlushedOrMergeIterator(List.of(first, second));

    // then
    assertThat(keysOf(merged)).containsExactly(bytes(0x01), bytes(0x7F), bytes(0x80), bytes(0xFF));
  }

  @Test
  void shouldEmitNewestValueOnEqualKeys() {
    // given
    final Iterator<Entry> newest = entries(put(bytes(0x01), 0x0A));
    final Iterator<Entry> oldest = entries(put(bytes(0x01), 0x0B));

    // when
    final FlushedOrMergeIterator merged = new FlushedOrMergeIterator(List.of(newest, oldest));

    // then
    final Entry entry = merged.next();
    assertThat(entry.value()).isEqualTo(bytes(0x0A));
    assertThat(merged.hasNext()).isFalse();
  }

  @Test
  void shouldStickFlushedAcrossShadowedVersions() {
    // given -- an older flushed put shadowed by a newer never-flushed tombstone: the delegate
    // will hold the key, so the emitted tombstone must carry flushed=true to reach it
    final Iterator<Entry> newest = entries(tombstone(bytes(0x01), false));
    final Iterator<Entry> oldest = entries(new Entry(bytes(0x01), bytes(0x0B), true));

    // when
    final FlushedOrMergeIterator merged = new FlushedOrMergeIterator(List.of(newest, oldest));

    // then
    final Entry entry = merged.next();
    assertThat(entry.tombstone()).isTrue();
    assertThat(entry.flushed()).isTrue();
    assertThat(merged.hasNext()).isFalse();
  }

  @Test
  void shouldOrFlushedFromAnyShadowedVersionAcrossManyStreams() {
    // given -- only the middle (shadowed) version was flushed
    final Iterator<Entry> newest = entries(put(bytes(0x01), 0x0A));
    final Iterator<Entry> middle = entries(new Entry(bytes(0x01), bytes(0x0B), true));
    final Iterator<Entry> oldest = entries(put(bytes(0x01), 0x0C));

    // when
    final FlushedOrMergeIterator merged =
        new FlushedOrMergeIterator(List.of(newest, middle, oldest));

    // then -- newest value wins, but the flushed flag is the OR over all three versions
    final Entry entry = merged.next();
    assertThat(entry.value()).isEqualTo(bytes(0x0A));
    assertThat(entry.flushed()).isTrue();
    assertThat(merged.hasNext()).isFalse();
  }

  @Test
  void shouldKeepFlushedFalseWhenNoVersionWasFlushed() {
    // given
    final Iterator<Entry> newest = entries(tombstone(bytes(0x01), false));
    final Iterator<Entry> oldest = entries(put(bytes(0x01), 0x0B));

    // when
    final FlushedOrMergeIterator merged = new FlushedOrMergeIterator(List.of(newest, oldest));

    // then -- an annihilated pair: the drain may skip this tombstone entirely
    final Entry entry = merged.next();
    assertThat(entry.tombstone()).isTrue();
    assertThat(entry.flushed()).isFalse();
    assertThat(merged.hasNext()).isFalse();
  }

  @Test
  void shouldNotLeakFlushedAcrossDistinctKeys() {
    // given -- a flushed version of one key next to a never-flushed other key in the same stream
    final Iterator<Entry> newest = entries(put(bytes(0x01), 0x0A), put(bytes(0x02), 0x0B));
    final Iterator<Entry> oldest = entries(new Entry(bytes(0x01), bytes(0x0C), true));

    // when
    final FlushedOrMergeIterator merged = new FlushedOrMergeIterator(List.of(newest, oldest));
    final List<Entry> result = drain(merged);

    // then
    assertThat(result).hasSize(2);
    assertThat(result.get(0).key()).isEqualTo(bytes(0x01));
    assertThat(result.get(0).flushed()).isTrue();
    assertThat(result.get(1).key()).isEqualTo(bytes(0x02));
    assertThat(result.get(1).flushed()).isFalse();
  }

  @Test
  void shouldMergeMultiStreamInterleavings() {
    // given -- three streams with disjoint and overlapping keys of different lengths
    final Iterator<Entry> first = entries(put(bytes(0x02), 0x0A), put(bytes(0x05), 0x0B));
    final Iterator<Entry> second =
        entries(
            new Entry(bytes(0x02), bytes(0x0C), true),
            put(bytes(0x03), 0x0D),
            put(bytes(0x07), 0x0E));
    final Iterator<Entry> third = entries(put(bytes(0x01), 0x0F), put(bytes(0x05), 0x10));

    // when
    final FlushedOrMergeIterator merged = new FlushedOrMergeIterator(List.of(first, second, third));
    final List<Entry> result = drain(merged);

    // then -- one entry per key, in key order, newest value winning, flushed OR-folded
    assertThat(result).hasSize(5);
    assertThat(result.get(0).key()).isEqualTo(bytes(0x01));
    assertThat(result.get(1).key()).isEqualTo(bytes(0x02));
    assertThat(result.get(1).value()).isEqualTo(bytes(0x0A));
    assertThat(result.get(1).flushed()).isTrue();
    assertThat(result.get(2).key()).isEqualTo(bytes(0x03));
    assertThat(result.get(3).key()).isEqualTo(bytes(0x05));
    assertThat(result.get(3).value()).isEqualTo(bytes(0x0B));
    assertThat(result.get(3).flushed()).isFalse();
    assertThat(result.get(4).key()).isEqualTo(bytes(0x07));
  }

  @Test
  void shouldSkipEmptyStreams() {
    // given
    final Iterator<Entry> empty = entries();
    final Iterator<Entry> nonEmpty = entries(put(bytes(0x01), 0x0A));

    // when
    final FlushedOrMergeIterator merged =
        new FlushedOrMergeIterator(List.of(empty, nonEmpty, entries()));

    // then
    assertThat(keysOf(merged)).containsExactly(bytes(0x01));
  }

  @Test
  void shouldBeExhaustedWithoutStreams() {
    // given / when
    final FlushedOrMergeIterator merged = new FlushedOrMergeIterator(List.of());

    // then
    assertThat(merged.hasNext()).isFalse();
  }

  @Test
  void shouldBeExhaustedWhenAllStreamsAreEmpty() {
    // given / when
    final FlushedOrMergeIterator merged = new FlushedOrMergeIterator(List.of(entries(), entries()));

    // then
    assertThat(merged.hasNext()).isFalse();
  }

  @Test
  void shouldThrowNoSuchElementExceptionWhenExhausted() {
    // given
    final FlushedOrMergeIterator merged =
        new FlushedOrMergeIterator(List.of(entries(put(bytes(0x01), 0x0A))));
    merged.next();

    // when / then
    assertThat(merged.hasNext()).isFalse();
    assertThatThrownBy(merged::next).isInstanceOf(NoSuchElementException.class);
  }

  @Test
  void shouldMatchMaterializedMergeOnRandomizedSegmentStacks() {
    // given -- randomized stacks of sorted segments with arbitrary tombstone/flushed combinations
    final Random random = new Random(42);

    for (int trial = 0; trial < 200; trial++) {
      final List<FlatSegment> oldestFirst = randomSegmentStack(random);

      // when -- streaming the merge newest-first
      final List<Iterator<Entry>> newestFirst = new ArrayList<>(oldestFirst.size());
      for (int i = oldestFirst.size() - 1; i >= 0; i--) {
        newestFirst.add(oldestFirst.get(i).range(EMPTY_PREFIX));
      }
      final List<Entry> streamed = drain(new FlushedOrMergeIterator(newestFirst));

      // then -- entry-identical to the materialized merge (the old drain's oracle)
      final List<Entry> materialized =
          drain(FlatSegment.merge(oldestFirst, false).range(EMPTY_PREFIX));
      assertThat(streamed).hasSameSizeAs(materialized);
      for (int i = 0; i < streamed.size(); i++) {
        final Entry actual = streamed.get(i);
        final Entry expected = materialized.get(i);
        assertThat(actual.key()).as("key at %d in trial %d", i, trial).isEqualTo(expected.key());
        assertThat(actual.value())
            .as("value at %d in trial %d", i, trial)
            .isEqualTo(expected.value());
        assertThat(actual.flushed())
            .as("flushed at %d in trial %d", i, trial)
            .isEqualTo(expected.flushed());
      }
    }
  }

  private static List<FlatSegment> randomSegmentStack(final Random random) {
    final int segmentCount = random.nextInt(6); // 0..5, including the empty-stack edge
    final List<FlatSegment> oldestFirst = new ArrayList<>(segmentCount);
    for (int s = 0; s < segmentCount; s++) {
      final TreeMap<byte[], Entry> sorted = new TreeMap<>(Arrays::compareUnsigned);
      final int entryCount = random.nextInt(8); // 0..7, including empty segments
      for (int e = 0; e < entryCount; e++) {
        // a small key space forces shadowing across segments
        final byte[] key = bytes(random.nextInt(10));
        final byte[] value = random.nextBoolean() ? null : bytes(random.nextInt(256));
        sorted.put(key, new Entry(key, value, random.nextBoolean()));
      }
      oldestFirst.add(FlatSegment.of(sorted, s));
    }
    return oldestFirst;
  }

  private static Iterator<Entry> entries(final Entry... entries) {
    return List.of(entries).iterator();
  }

  private static Entry put(final byte[] key, final int value) {
    return new Entry(key, bytes(value), false);
  }

  private static Entry tombstone(final byte[] key, final boolean flushed) {
    return new Entry(key, null, flushed);
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
