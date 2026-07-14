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
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

final class FlatSegmentTest {

  @Test
  void shouldFindEntryForPresentKey() {
    // given
    final FlatSegment segment =
        segment(1, put(bytes(0x01), bytes(0x0A), false), put(bytes(0x02), bytes(0x0B), true));

    // when
    final Entry entry = segment.findEntry(bytes(0x02));

    // then
    assertThat(entry).isNotNull();
    assertThat(entry.key()).isEqualTo(bytes(0x02));
    assertThat(entry.value()).isEqualTo(bytes(0x0B));
    assertThat(entry.flushed()).isTrue();
    assertThat(entry.tombstone()).isFalse();
  }

  @Test
  void shouldReturnNullForAbsentKey() {
    // given
    final FlatSegment segment = segment(1, put(bytes(0x01), bytes(0x0A), false));

    // when
    final Entry entry = segment.findEntry(bytes(0x02));

    // then
    assertThat(entry).isNull();
  }

  @Test
  void shouldReturnNullForKeysOutsideMinMaxRange() {
    // given -- min 0x10, max 0x20
    final FlatSegment segment =
        segment(1, put(bytes(0x10), bytes(0x0A), false), put(bytes(0x20), bytes(0x0B), false));

    // when / then -- probes below min and above max miss; the boundary keys themselves hit
    assertThat(segment.findEntry(bytes(0x05))).isNull();
    assertThat(segment.findEntry(bytes(0x30))).isNull();
    assertThat(segment.findEntry(bytes(0x10))).isNotNull();
    assertThat(segment.findEntry(bytes(0x20))).isNotNull();
  }

  @Test
  void shouldReturnNothingFromEmptySegmentProbes() {
    // given
    final FlatSegment segment = segment(1);

    // when / then
    assertThat(segment.findEntry(bytes(0x01))).isNull();
    assertThat(keysOf(segment.range(bytes(0x01)))).isEmpty();
    assertThat(keysOf(segment.range(bytes()))).isEmpty();
  }

  @Test
  void shouldReturnEmptyRangeForPrefixOutsideKeyRange() {
    // given -- keys under the 0x10 prefix only
    final FlatSegment segment =
        segment(
            1,
            put(bytes(0x10, 0x01), bytes(0x0A), false),
            put(bytes(0x10, 0x02), bytes(0x0B), false));

    // when / then -- prefixes sorting below min and above max match nothing
    assertThat(keysOf(segment.range(bytes(0x05)))).isEmpty();
    assertThat(keysOf(segment.range(bytes(0x20)))).isEmpty();
  }

  @Test
  void shouldIterateRangeForPrefixesTouchingTheRangeBoundaries() {
    // given -- boundary keys both longer and shorter than the probed prefixes
    final FlatSegment segment =
        segment(
            1,
            put(bytes(0x10, 0x01), bytes(0x0A), false),
            put(bytes(0x11), bytes(0x0B), false),
            put(bytes(0x12, 0x05), bytes(0x0C), false));

    // when / then -- prefixes of the min and max keys match exactly those keys ...
    assertThat(keysOf(segment.range(bytes(0x10)))).containsExactly(bytes(0x10, 0x01));
    assertThat(keysOf(segment.range(bytes(0x12)))).containsExactly(bytes(0x12, 0x05));
    // ... a prefix longer than the max key matches nothing ...
    assertThat(keysOf(segment.range(bytes(0x12, 0x05, 0x01)))).isEmpty();
    // ... and an in-range prefix with no matching key stays empty
    assertThat(keysOf(segment.range(bytes(0x11, 0x01)))).isEmpty();
  }

  @Test
  void shouldFindTombstoneEntry() {
    // given
    final FlatSegment segment = segment(1, tombstone(bytes(0x01), true));

    // when
    final Entry entry = segment.findEntry(bytes(0x01));

    // then
    assertThat(entry).isNotNull();
    assertThat(entry.tombstone()).isTrue();
    assertThat(entry.value()).isNull();
    assertThat(entry.flushed()).isTrue();
  }

  @Test
  void shouldOrderKeysUnsigned() {
    // given -- 0x80 is negative as a signed byte but must sort after 0x7F
    final FlatSegment segment =
        segment(
            1,
            put(bytes(0x80), bytes(0x02), false),
            put(bytes(0x7F), bytes(0x01), false),
            put(bytes(0xFF), bytes(0x03), false));

    // when
    final List<byte[]> keys = keysOf(segment.range(bytes()));

    // then
    assertThat(keys).containsExactly(bytes(0x7F), bytes(0x80), bytes(0xFF));
  }

  @Test
  void shouldIterateEverythingForEmptyPrefix() {
    // given
    final FlatSegment segment =
        segment(
            1,
            put(bytes(0x01), bytes(0x0A), false),
            put(bytes(0x01, 0x02), bytes(0x0B), false),
            tombstone(bytes(0xFE), true));

    // when
    final List<byte[]> keys = keysOf(segment.range(bytes()));

    // then
    assertThat(keys).containsExactly(bytes(0x01), bytes(0x01, 0x02), bytes(0xFE));
  }

  @Test
  void shouldIterateOnlyContiguousPrefixMatches() {
    // given
    final FlatSegment segment =
        segment(
            1,
            put(bytes(0x01, 0xFF), bytes(0x0A), false),
            put(bytes(0x02, 0x00), bytes(0x0B), false),
            put(bytes(0x02, 0x7F), bytes(0x0C), false),
            put(bytes(0x02, 0x80), bytes(0x0D), false),
            put(bytes(0x03, 0x00), bytes(0x0E), false));

    // when
    final List<byte[]> keys = keysOf(segment.range(bytes(0x02)));

    // then
    assertThat(keys).containsExactly(bytes(0x02, 0x00), bytes(0x02, 0x7F), bytes(0x02, 0x80));
  }

  @Test
  void shouldIterateAllFfPrefixAtEndOfSegment() {
    // given -- an all-0xFF prefix has no successor prefix; the range must stop at the array end
    final FlatSegment segment =
        segment(
            1,
            put(bytes(0xFE, 0xFF), bytes(0x0A), false),
            put(bytes(0xFF, 0xFF), bytes(0x0B), false),
            put(bytes(0xFF, 0xFF, 0x00), bytes(0x0C), false),
            put(bytes(0xFF, 0xFF, 0xFF), bytes(0x0D), false));

    // when
    final List<byte[]> keys = keysOf(segment.range(bytes(0xFF, 0xFF)));

    // then
    assertThat(keys)
        .containsExactly(bytes(0xFF, 0xFF), bytes(0xFF, 0xFF, 0x00), bytes(0xFF, 0xFF, 0xFF));
  }

  @Test
  void shouldIterateMatchesAtStartOfSegment() {
    // given
    final FlatSegment segment =
        segment(
            1,
            put(bytes(0x00, 0x01), bytes(0x0A), false),
            put(bytes(0x00, 0x02), bytes(0x0B), false),
            put(bytes(0x01, 0x00), bytes(0x0C), false));

    // when
    final List<byte[]> keys = keysOf(segment.range(bytes(0x00)));

    // then
    assertThat(keys).containsExactly(bytes(0x00, 0x01), bytes(0x00, 0x02));
  }

  @Test
  void shouldReturnEmptyRangeForAbsentPrefix() {
    // given
    final FlatSegment segment =
        segment(1, put(bytes(0x01), bytes(0x0A), false), put(bytes(0x03), bytes(0x0B), false));

    // when
    final Iterator<Entry> between = segment.range(bytes(0x02));
    final Iterator<Entry> beyond = segment.range(bytes(0x04));
    final Iterator<Entry> longerThanKey = segment.range(bytes(0x01, 0x00));

    // then
    assertThat(between.hasNext()).isFalse();
    assertThat(beyond.hasNext()).isFalse();
    assertThat(longerThanKey.hasNext()).isFalse();
  }

  @Test
  void shouldThrowNoSuchElementExceptionWhenRangeIsExhausted() {
    // given
    final FlatSegment segment = segment(1, put(bytes(0x01), bytes(0x0A), false));
    final Iterator<Entry> range = segment.range(bytes());
    range.next();

    // when / then
    assertThat(range.hasNext()).isFalse();
    assertThatThrownBy(range::next).isInstanceOf(NoSuchElementException.class);
  }

  @Test
  void shouldPreferNewestVersionOfKeyOnMerge() {
    // given
    final FlatSegment oldest = segment(1, put(bytes(0x01), bytes(0x0A), false));
    final FlatSegment middle = segment(2, put(bytes(0x01), bytes(0x0B), false));
    final FlatSegment newest = segment(3, put(bytes(0x01), bytes(0x0C), false));

    // when
    final FlatSegment merged = FlatSegment.merge(List.of(oldest, middle, newest), false);

    // then
    assertThat(merged.entryCount()).isEqualTo(1);
    assertThat(merged.findEntry(bytes(0x01)).value()).isEqualTo(bytes(0x0C));
  }

  @Test
  void shouldKeepFlushedStickyAcrossMergedVersions() {
    // given -- only the older version was flushed; the newer version must inherit the flag
    final FlatSegment older = segment(1, put(bytes(0x01), bytes(0x0A), true));
    final FlatSegment newer = segment(2, put(bytes(0x01), bytes(0x0B), false));

    // when
    final FlatSegment merged = FlatSegment.merge(List.of(older, newer), false);

    // then
    final Entry entry = merged.findEntry(bytes(0x01));
    assertThat(entry.value()).isEqualTo(bytes(0x0B));
    assertThat(entry.flushed()).isTrue();
  }

  @Test
  void shouldAbsorbTombstoneOfNeverFlushedKey() {
    // given
    final FlatSegment older = segment(1, put(bytes(0x01), bytes(0x0A), false));
    final FlatSegment newer = segment(2, tombstone(bytes(0x01), false));

    // when
    final FlatSegment merged = FlatSegment.merge(List.of(older, newer), true);

    // then
    assertThat(merged.isEmpty()).isTrue();
    assertThat(merged.findEntry(bytes(0x01))).isNull();
    assertThat(merged.watermark()).isEqualTo(2);
  }

  @Test
  void shouldKeepTombstoneOfFlushedKeyDespiteAbsorb() {
    // given -- the key reached the delegate, so its tombstone must survive the merge
    final FlatSegment older = segment(1, put(bytes(0x01), bytes(0x0A), true));
    final FlatSegment newer = segment(2, tombstone(bytes(0x01), false));

    // when
    final FlatSegment merged = FlatSegment.merge(List.of(older, newer), true);

    // then
    final Entry entry = merged.findEntry(bytes(0x01));
    assertThat(entry).isNotNull();
    assertThat(entry.tombstone()).isTrue();
    assertThat(entry.flushed()).isTrue();
  }

  @Test
  void shouldKeepNeverFlushedTombstoneWhenAbsorbDisabled() {
    // given
    final FlatSegment older = segment(1, put(bytes(0x01), bytes(0x0A), false));
    final FlatSegment newer = segment(2, tombstone(bytes(0x01), false));

    // when
    final FlatSegment merged = FlatSegment.merge(List.of(older, newer), false);

    // then
    final Entry entry = merged.findEntry(bytes(0x01));
    assertThat(entry).isNotNull();
    assertThat(entry.tombstone()).isTrue();
    assertThat(entry.flushed()).isFalse();
  }

  @Test
  void shouldMergeDisjointKeysInUnsignedOrder() {
    // given
    final FlatSegment first =
        segment(1, put(bytes(0x80), bytes(0x0A), false), put(bytes(0x01), bytes(0x0B), false));
    final FlatSegment second =
        segment(2, put(bytes(0x7F), bytes(0x0C), false), put(bytes(0xFF), bytes(0x0D), false));

    // when
    final FlatSegment merged = FlatSegment.merge(List.of(first, second), false);

    // then
    assertThat(keysOf(merged.range(bytes())))
        .containsExactly(bytes(0x01), bytes(0x7F), bytes(0x80), bytes(0xFF));
  }

  @Test
  void shouldUseMaxWatermarkOnMerge() {
    // given
    final FlatSegment oldest = segment(5, put(bytes(0x01), bytes(0x0A), false));
    final FlatSegment newest = segment(9, put(bytes(0x02), bytes(0x0B), false));

    // when
    final FlatSegment merged = FlatSegment.merge(List.of(oldest, newest), false);

    // then
    assertThat(merged.watermark()).isEqualTo(9);
  }

  @Test
  void shouldMergeEmptySegments() {
    // given
    final FlatSegment first = segment(3);
    final FlatSegment second = segment(7);

    // when
    final FlatSegment merged = FlatSegment.merge(List.of(first, second), true);

    // then
    assertThat(merged.isEmpty()).isTrue();
    assertThat(merged.entryCount()).isZero();
    assertThat(merged.watermark()).isEqualTo(7);
  }

  @Test
  void shouldMergeSingleSegment() {
    // given
    final FlatSegment segment =
        segment(4, put(bytes(0x01), bytes(0x0A), true), tombstone(bytes(0x02), true));

    // when
    final FlatSegment merged = FlatSegment.merge(List.of(segment), true);

    // then
    assertThat(merged.entryCount()).isEqualTo(2);
    assertThat(merged.watermark()).isEqualTo(4);
    assertThat(merged.findEntry(bytes(0x01)).value()).isEqualTo(bytes(0x0A));
    assertThat(merged.findEntry(bytes(0x02)).tombstone()).isTrue();
  }

  @Test
  void shouldMergeNoSegmentsIntoEmptySegment() {
    // given / when
    final FlatSegment merged = FlatSegment.merge(List.of(), true);

    // then
    assertThat(merged.isEmpty()).isTrue();
    assertThat(merged.watermark()).isEqualTo(-1);
  }

  @Test
  void shouldMergeInterleavedSegmentsWithShadowingAndAbsorption() {
    // given
    final FlatSegment oldest =
        segment(
            1,
            put(bytes(0x01), bytes(0x0A), true),
            put(bytes(0x02), bytes(0x0B), false),
            put(bytes(0x03), bytes(0x0C), false));
    final FlatSegment newest =
        segment(
            2,
            tombstone(bytes(0x02), false),
            put(bytes(0x03), bytes(0x0D), false),
            put(bytes(0x04), bytes(0x0E), false));

    // when
    final FlatSegment merged = FlatSegment.merge(List.of(oldest, newest), true);

    // then -- 0x02 annihilates, 0x03 takes the newest value, the rest pass through
    assertThat(keysOf(merged.range(bytes())))
        .containsExactly(bytes(0x01), bytes(0x03), bytes(0x04));
    assertThat(merged.findEntry(bytes(0x03)).value()).isEqualTo(bytes(0x0D));
    assertThat(merged.findEntry(bytes(0x01)).flushed()).isTrue();
  }

  @Test
  void shouldComputeByteSizeFromKeysAndValues() {
    // given -- tombstones contribute only their key
    final FlatSegment segment =
        segment(
            1,
            put(bytes(0x01, 0x02), bytes(0x0A, 0x0B, 0x0C), false),
            tombstone(bytes(0x03), false));

    // when / then
    assertThat(segment.byteSize()).isEqualTo(6);
  }

  private static FlatSegment segment(final long watermark, final Entry... entries) {
    final TreeMap<byte[], Entry> sorted = new TreeMap<>(Arrays::compareUnsigned);
    for (final Entry entry : entries) {
      sorted.put(entry.key(), entry);
    }
    return FlatSegment.of(sorted, watermark, new ChunkWriter(new ChunkPool()));
  }

  private static Entry put(final byte[] key, final byte[] value, final boolean flushed) {
    return new Entry(key, value, flushed);
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

  private static List<byte[]> keysOf(final Iterator<Entry> entries) {
    final List<byte[]> keys = new ArrayList<>();
    entries.forEachRemaining(entry -> keys.add(entry.key()));
    return keys;
  }
}
