/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.logarchive.format;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.journal.JournalMetaStore.InMemory;
import io.camunda.zeebe.journal.file.SegmentedJournal;
import io.camunda.zeebe.journal.record.RecordMetadata;
import io.camunda.zeebe.journal.record.SBESerializer;
import io.camunda.zeebe.util.buffer.DirectBufferWriter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Proves that segment entries are the journal's bytes, using real journals. */
final class SegmentV1JournalTest {

  private static final int MAX_SEGMENT_SIZE = 1024 * 1024;
  private static final SegmentIdentity IDENTITY = new SegmentIdentity("cluster-1", "default", 1);

  @TempDir Path source;
  @TempDir Path restored;

  @Test
  void shouldRestoreAByteIdenticalJournalAndEncodeTheJournalsOwnFrameBytes() throws Exception {
    // given a journal with application entries and entries without asqn
    final var frames = new ArrayList<LogArchiveFrame>();
    try (final var journal = open(source)) {
      long position = 1;
      for (int i = 0; i < 25; i++) {
        final long asqn = i % 4 == 3 ? -1 : position;
        final var payload = ("entry-" + i + "-").repeat(1 + i % 5).getBytes();
        journal.append(asqn, new DirectBufferWriter(new UnsafeBuffer(payload)));
        if (asqn >= 0) {
          position += 2;
        }
      }
      journal.flush();
      try (final var reader = journal.openReader()) {
        while (reader.hasNext()) {
          final var record = reader.next();
          final byte[] serialized = new byte[record.serializedRecord().capacity()];
          record.serializedRecord().getBytes(0, serialized);
          final long asqn = record.asqn();
          frames.add(
              new LogArchiveFrame(
                  record.index(),
                  1,
                  asqn,
                  asqn,
                  asqn >= 0 ? asqn + 1 : -1,
                  record.checksum(),
                  serialized));
        }
      }
    }

    // when it goes through a segment and into a fresh journal
    final var encoded = SegmentV1Encoder.encode(IDENTITY, "tl-0", frames, 1);
    final var decoded = SegmentV1Decoder.decode(encoded.bytes());
    try (final var journal = open(restored)) {
      for (final var frame : decoded.frames()) {
        final var record = journal.append(frame.checksum(), frame.serializedRecord());
        assertThat(record.index()).isEqualTo(frame.index());
        assertThat(record.asqn()).isEqualTo(frame.asqn());
      }
      journal.flush();
    }

    // then the journals are byte for byte the same
    final byte[] original = Files.readAllBytes(onlySegment(source));
    final byte[] rebuilt = Files.readAllBytes(onlySegment(restored));
    assertThat(rebuilt).isEqualTo(original);

    // and the entries are the journal's frame bytes: version, metadata, record data
    final var serializer = new SBESerializer();
    final var expected = new ByteArrayOutputStream();
    for (final var frame : decoded.frames()) {
      final byte[] metadata = new byte[serializer.getMetadataLength()];
      serializer.writeMetadata(
          new RecordMetadata(frame.checksum(), frame.serializedRecord().length),
          new UnsafeBuffer(metadata),
          0);
      expected.write(1);
      expected.writeBytes(metadata);
      expected.writeBytes(frame.serializedRecord());
    }
    assertThat(indexOf(original, expected.toByteArray())).isPositive();
  }

  private static SegmentedJournal open(final Path directory) {
    return SegmentedJournal.builder(new SimpleMeterRegistry())
        .withDirectory(directory.toFile())
        .withName("journal")
        .withMaxSegmentSize(MAX_SEGMENT_SIZE)
        .withMetaStore(new InMemory())
        .build();
  }

  private static Path onlySegment(final Path directory) throws IOException {
    try (final var files = Files.list(directory)) {
      final List<Path> segments = files.filter(p -> p.toString().endsWith(".log")).toList();
      assertThat(segments).hasSize(1);
      return segments.getFirst();
    }
  }

  private static int indexOf(final byte[] haystack, final byte[] needle) {
    outer:
    for (int i = 0; i + needle.length <= haystack.length; i++) {
      for (int j = 0; j < needle.length; j++) {
        if (haystack[i + j] != needle[j]) {
          continue outer;
        }
      }
      return i;
    }
    return -1;
  }
}
