/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.compaction;

import static io.camunda.eventbridge.broker.compaction.CompactionRecords.put;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The zero-copy header-walk used by the composite fetch path: no key/value is ever decoded, only
 * position/offset/length spans are collected.
 */
final class CleanSegmentScanTest {

  @TempDir Path dir;

  private Path writeSegment(final CompactionRecord... records) {
    final var writer = new CleanSegmentWriter(dir, 100, 1 << 20);
    for (final CompactionRecord record : records) {
      writer.append(record);
    }
    final List<CleanSegment> segments = writer.finish();
    return dir.resolve(segments.get(0).fileName());
  }

  @Test
  void shouldCollectEntriesAtAndAfterFromPosition() throws IOException {
    // given three records at positions 2, 5, 9
    final Path file = writeSegment(put(2, "a", "va"), put(5, "b", "vb"), put(9, "c", "vc"));

    try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
      // when
      final var entries = CleanSegmentScan.scan(channel, 5, Integer.MAX_VALUE);

      // then
      assertThat(entries).extracting(e -> e.lowestAsqn()).containsExactly(5L, 9L);
    }
  }

  @Test
  void shouldGapSkipToTheNextSurvivingRecord() throws IOException {
    // given records at 2 and 9 (position 5 was swept away by an earlier pass)
    final Path file = writeSegment(put(2, "a", "va"), put(9, "c", "vc"));

    try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
      // when: seeking to the swept position 5
      final var entries = CleanSegmentScan.scan(channel, 5, Integer.MAX_VALUE);

      // then: lands on the next surviving record
      assertThat(entries).extracting(e -> e.lowestAsqn()).containsExactly(9L);
    }
  }

  @Test
  void shouldReturnEmptyWhenPositionIsBeyondTheLastRecord() throws IOException {
    // given
    final Path file = writeSegment(put(2, "a", "va"), put(5, "b", "vb"));

    try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
      // when
      final var entries = CleanSegmentScan.scan(channel, 100, Integer.MAX_VALUE);

      // then
      assertThat(entries).isEmpty();
    }
  }

  @Test
  void shouldStopCollectingOnceMaxBytesIsReached() throws IOException {
    // given three records, each wrapped as its own single-entry batch
    final Path file = writeSegment(put(1, "a", "va"), put(2, "b", "vb"), put(3, "c", "vc"));

    try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
      // when: a maxBytes budget that only covers the first batch
      final long firstBatchSize = channel.size() / 3;
      final var entries = CleanSegmentScan.scan(channel, 1, (int) firstBatchSize);

      // then: at least the first entry is returned, but not all three
      assertThat(entries).isNotEmpty();
      assertThat(entries.size()).isLessThan(3);
      assertThat(entries.getFirst().lowestAsqn()).isEqualTo(1L);
    }
  }

  @Test
  void shouldReportByteSpansThatCoverExactlyOneSelfFramedBatch() throws IOException {
    // given
    final Path file = writeSegment(put(2, "a", "va"));

    try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
      // when
      final var entries = CleanSegmentScan.scan(channel, 2, Integer.MAX_VALUE);

      // then: the single entry's span covers the whole (single-batch) segment file
      assertThat(entries).hasSize(1);
      final var entry = entries.getFirst();
      assertThat(entry.position()).isZero();
      assertThat(entry.length()).isEqualTo(channel.size());
    }
  }
}
