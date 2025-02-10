/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.journal.file;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.journal.CheckedJournalException.FlushException;
import io.camunda.zeebe.util.buffer.BufferUtil;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.agrona.CloseHelper;
import org.agrona.IoUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class SegmentedJournalWriterTest {
  @AutoClose private final MeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final TestJournalFactory journalFactory =
      new TestJournalFactory("data", 2, this::fillWithOnes);
  private final SegmentsFlusher flusher = new SegmentsFlusher(journalFactory.metaStore());

  private SegmentsManager segments;
  private SegmentedJournalWriter writer;
  private final SegmentDescriptorSerializer descriptorSerializer =
      new SegmentDescriptorSerializerSbe();

  private void fillWithOnes(final FileChannel channel, final long size) {
    // Fill with ones to verify in tests that the append invalidates next entry by overwriting with
    // 0
    IoUtil.fill(channel, 0, size, (byte) 0xff);
  }

  @BeforeEach
  void beforeEach(final @TempDir Path tempDir) {
    segments = journalFactory.segmentsManager(tempDir);
    segments.open();
    writer = new SegmentedJournalWriter(segments, flusher, journalFactory.metrics());
  }

  @AfterEach
  void afterEach() {
    CloseHelper.quietClose(segments);
  }

  @Test
  void shouldResetLastFlushedIndexOnDeleteAfter() throws FlushException {
    // given
    writer.append(1, journalFactory.entry());
    writer.append(2, journalFactory.entry());
    writer.append(3, journalFactory.entry());
    writer.append(4, journalFactory.entry());
    writer.flush();

    // when
    writer.deleteAfter(2);

    // then
    assertThat(flusher.nextFlushIndex()).isEqualTo(3L);
    assertThat(journalFactory.metaStore().loadLastFlushedIndex()).isEqualTo(2L);
  }

  @Test
  void shouldResetLastFlushedIndexOnReset() throws FlushException {
    // given
    writer.append(1, journalFactory.entry());
    writer.append(2, journalFactory.entry());
    writer.flush();

    // when
    writer.reset(8);

    // then
    assertThat(flusher.nextFlushIndex()).isEqualTo(8L);
    assertThat(journalFactory.metaStore().hasLastFlushedIndex()).isFalse();
  }

  @Test
  void shouldUpdateDescriptor() {
    // given
    while (segments.getFirstSegment() == segments.getLastSegment()) {
      writer.append(-1, journalFactory.entry());
    }
    final var lastIndexInFirstSegment = segments.getLastSegment().index() - 1;

    // when
    segments.close();
    segments.open();

    // then
    final SegmentDescriptor descriptor = segments.getFirstSegment().descriptor();
    assertThat(descriptor.lastIndex()).isEqualTo(lastIndexInFirstSegment);
    assertThat(descriptor.lastPosition()).isNotZero();
  }

  @Test
  void shouldResetToLastEntryEvenIfLastPositionInDescriptorIsIncorrect() throws IOException {
    // given
    while (segments.getFirstSegment() == segments.getLastSegment()) {
      writer.append(-1, journalFactory.entry());
    }
    final var lastIndexInFirstSegment = segments.getLastSegment().index() - 1;

    final var descriptor = segments.getFirstSegment().descriptor();
    final var firstSegment = segments.getFirstSegment();
    final var corruptedDescriptor =
        descriptor.withUpdatedIndices(
            descriptor.lastIndex(), firstSegment.descriptor().lastPosition() + 1);
    final var segmentFile = firstSegment.file().file().toPath();
    try (final FileChannel channel =
        FileChannel.open(segmentFile, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
      final MappedByteBuffer buffer =
          channel.map(MapMode.READ_WRITE, 0, descriptorSerializer.encodingLength());
      descriptorSerializer.writeTo(corruptedDescriptor, buffer);
    }

    // when
    segments.close();
    segments.open();

    // then
    assertThat(segments.getFirstSegment().lastIndex()).isEqualTo(lastIndexInFirstSegment);
  }

  @Test
  void shouldInvalidateNextEntryAfterAppend() {
    try (final SegmentedJournalReader reader =
        new SegmentedJournalReader(
            journalFactory.journal(segments), new JournalMetrics(meterRegistry))) {
      // when
      writer.append(-1, journalFactory.entry());

      // then
      assertThat(reader.hasNext()).isTrue();
      reader.next();
      assertThat(reader.hasNext()).describedAs("Second entry does not exists").isFalse();
    }
  }

  @Test
  void shouldInvalidateNextEntryAfterAppendingSerializedRecord(@TempDir final Path tempDir) {
    // given
    final var writtenRecord = writer.append(-1, journalFactory.entry());

    final var followerJournalFactory = new TestJournalFactory("data", 5, this::fillWithOnes);
    final var followerSegments = followerJournalFactory.segmentsManager(tempDir);
    followerSegments.open();
    final var followerWriter =
        new SegmentedJournalWriter(
            followerSegments,
            new SegmentsFlusher(followerJournalFactory.metaStore()),
            followerJournalFactory.metrics());

    try (final SegmentedJournalReader reader =
        new SegmentedJournalReader(
            followerJournalFactory.journal(followerSegments), new JournalMetrics(meterRegistry))) {
      // when
      final byte[] serializedRecord = BufferUtil.bufferAsArray(writtenRecord.serializedRecord());
      followerWriter.append(writtenRecord.checksum(), serializedRecord);

      // then
      assertThat(reader.hasNext()).isTrue();
      reader.next();
      assertThat(reader.hasNext()).describedAs("Second entry does not exists").isFalse();
    }

    followerSegments.close();
  }
}
