/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.compaction;

import static io.camunda.eventbridge.broker.compaction.CompactionRecords.key;
import static io.camunda.eventbridge.broker.compaction.CompactionRecords.put;
import static io.camunda.eventbridge.broker.compaction.CompactionRecords.tombstone;
import static io.camunda.eventbridge.broker.compaction.CompactionRecords.unkeyed;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.eventbridge.protocol.EventBridgeBatch;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Round-trips records through the writer and reader and pins the seek/gap-skip contract. */
final class CleanSegmentWriterReaderTest {

  @TempDir Path dir;

  private List<CompactionRecord> readAll(final List<CleanSegment> segments) {
    final var out = new ArrayList<CompactionRecord>();
    for (final CleanSegment segment : segments) {
      final var reader = new CleanSegmentReader(dir.resolve(segment.fileName()));
      while (reader.hasNext()) {
        out.add(reader.next());
      }
    }
    return out;
  }

  @Test
  void shouldRoundTripRecordsPreservingAllFields() {
    // given
    final var writer = new CleanSegmentWriter(dir, 100, 42L, 1 << 20);
    final var records =
        List.of(put(10, "a", "va"), tombstone(20, "b"), unkeyed(30, "raw"), put(40, "c", "vc"));

    // when
    records.forEach(writer::append);
    final List<CleanSegment> segments = writer.finish();

    // then — one segment, all fields preserved verbatim
    assertThat(segments).hasSize(1);
    final List<CompactionRecord> read = readAll(segments);
    assertThat(read).containsExactlyElementsOf(records);
    assertThat(read.get(1).isTombstone()).isTrue();
    assertThat(read.get(2).hasKey()).isFalse();
    assertThat(read.get(2).keyed()).isFalse();
  }

  @Test
  void shouldRollToMultipleSegmentsAtSizeBound() {
    // given a tiny size bound so every record rolls into its own segment
    final var writer = new CleanSegmentWriter(dir, 100, 1L, 64);
    final var records = List.of(put(1, "a", "va"), put(2, "b", "vb"), put(3, "c", "vc"));

    // when
    records.forEach(writer::append);
    final List<CleanSegment> segments = writer.finish();

    // then — multiple segments, each recording its first position; reading them all recovers order
    assertThat(segments).hasSizeGreaterThan(1);
    assertThat(segments.get(0).firstPosition()).isEqualTo(1);
    assertThat(readAll(segments)).containsExactlyElementsOf(records);
  }

  @Test
  void shouldSeekToFirstBatchAtOrAfterTarget() {
    // given a segment with a gap between positions 20 and 50
    final var writer = new CleanSegmentWriter(dir, 100, 1L, 1 << 20);
    writer.append(put(10, "a", "va"));
    writer.append(put(20, "b", "vb"));
    writer.append(put(50, "c", "vc"));
    final CleanSegment segment = writer.finish().get(0);
    final var reader = new CleanSegmentReader(dir.resolve(segment.fileName()));

    // when seeking to a swept position inside the gap
    reader.seek(35);

    // then the reader lands on the next surviving record (gap-skip)
    assertThat(reader.hasNext()).isTrue();
    assertThat(key(reader.next())).isEqualTo("c");
  }

  @Test
  void shouldSeekToExactPosition() {
    // given
    final var writer = new CleanSegmentWriter(dir, 100, 1L, 1 << 20);
    writer.append(put(10, "a", "va"));
    writer.append(put(20, "b", "vb"));
    final CleanSegment segment = writer.finish().get(0);
    final var reader = new CleanSegmentReader(dir.resolve(segment.fileName()));

    // when
    reader.seek(20);

    // then
    assertThat(key(reader.next())).isEqualTo("b");
  }

  @Test
  void shouldBehaveDistinctlyBelowStartAndBeyondEnd() {
    // given
    final var writer = new CleanSegmentWriter(dir, 100, 1L, 1 << 20);
    writer.append(put(10, "a", "va"));
    writer.append(put(20, "b", "vb"));
    final CleanSegment segment = writer.finish().get(0);
    final var reader = new CleanSegmentReader(dir.resolve(segment.fileName()));

    // when seeking below the first position → lands on the first record
    reader.seek(1);
    // then
    assertThat(reader.hasNext()).isTrue();
    assertThat(key(reader.next())).isEqualTo("a");

    // when seeking beyond the last position → exhausted
    reader.seek(999);
    // then
    assertThat(reader.hasNext()).isFalse();
  }

  @Test
  void shouldPreserveRawAttributesBitIdenticallyOnRewrap() throws IOException {
    // given a record whose source batch carried a non-KEYED attribute bit (a compression codec)
    final int attributes = EventBridgeBatch.KEYED_MASK | EventBridgeBatch.COMPRESSION_LZ4;
    final var record =
        new CompactionRecord(
            10,
            10,
            "a".getBytes(StandardCharsets.UTF_8),
            "va".getBytes(StandardCharsets.UTF_8),
            attributes);
    final var writer = new CleanSegmentWriter(dir, 100, 42L, 1 << 20);

    // when
    writer.append(record);
    final CleanSegment segment = writer.finish().get(0);

    // then — the decoded record carries the identical attributes int
    final var reader = new CleanSegmentReader(dir.resolve(segment.fileName()));
    final CompactionRecord read = reader.next();
    assertThat(read.attributes()).isEqualTo(attributes);
    assertThat(read.keyed()).isTrue();

    // and the raw header int on disk is bit-identical, with a valid recomputed CRC
    final var bytes = new UnsafeBuffer(Files.readAllBytes(dir.resolve(segment.fileName())));
    assertThat(EventBridgeBatch.getAttributes(bytes, 0)).isEqualTo(attributes);
    assertThat(EventBridgeBatch.validateCrc(bytes, 0)).isTrue();
  }

  @Test
  void shouldProduceIdenticalBytesForIdenticalInput() throws IOException {
    // given two writers over separate directories with identical input
    final Path dirA = dir.resolve("a");
    final Path dirB = dir.resolve("b");
    Files.createDirectories(dirA);
    Files.createDirectories(dirB);
    final var records = List.of(put(10, "a", "va"), tombstone(20, "b"), put(30, "c", "vc"));

    // when
    final var writerA = new CleanSegmentWriter(dirA, 100, 7L, 1 << 20);
    final var writerB = new CleanSegmentWriter(dirB, 100, 7L, 1 << 20);
    records.forEach(writerA::append);
    records.forEach(writerB::append);
    final CleanSegment a = writerA.finish().get(0);
    final CleanSegment b = writerB.finish().get(0);

    // then — byte-identical files and identical names
    assertThat(a.fileName()).isEqualTo(b.fileName());
    assertThat(Files.readAllBytes(dirA.resolve(a.fileName())))
        .isEqualTo(Files.readAllBytes(dirB.resolve(b.fileName())));
  }
}
