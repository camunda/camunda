/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.journal.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatCode;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

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
import java.util.SortedMap;
import java.util.TreeMap;
import org.agrona.CloseHelper;
import org.agrona.IoUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

final class SegmentedJournalWriterTest {
  @AutoClose private final MeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final TestJournalFactory journalFactory =
      new TestJournalFactory("data", 2, this::fillWithOnes);

  private SegmentsManager segments;
  private SegmentedJournalWriter writer;
  private final SegmentDescriptorSerializer descriptorSerializer =
      new SegmentDescriptorSerializerSbe();

  private void fillWithOnes(
      final FileChannel channel, final java.io.FileDescriptor fileDescriptor, final long size) {
    // Fill with ones to verify in tests that the append invalidates next entry by overwriting with
    // 0
    IoUtil.fill(channel, 0, size, (byte) 0xff);
  }

  @BeforeEach
  void beforeEach(final @TempDir Path tempDir) {
    segments = journalFactory.segmentsManager(tempDir);
    segments.open();
    writer =
        new SegmentedJournalWriter(segments, journalFactory.metaStore(), journalFactory.metrics());
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
    assertThat(writer.getLastFlushedIndex()).isEqualTo(2L);
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
    assertThat(writer.getLastFlushedIndex()).isEqualTo(7L);
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
            followerSegments, journalFactory.metaStore(), followerJournalFactory.metrics());

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

  @Test
  void shouldFlushAllSegments() throws FlushException {
    // given
    writer.append(1, journalFactory.entry());
    writer.append(2, journalFactory.entry());
    writer.append(3, journalFactory.entry());
    writer.append(4, journalFactory.entry());

    // when
    writer.flush();

    // then

    assertThat(writer.getLastFlushedIndex()).isEqualTo(4L);
  }

  @Test
  void shouldStoreLastFlushedIndexOnPartialFlush() {
    // given

    final SortedMap<Long, FlushableSegment> sortedMap = new TreeMap<>();
    sortedMap.put(1L, new TestSegment(15, true));
    sortedMap.put(2L, new TestSegment(30, false));

    final var segments = Mockito.mock(SegmentsManager.class);
    final var segment = Mockito.mock(Segment.class);
    when(segment.index()).thenReturn(1L);
    when(segments.getLastSegment()).thenReturn(segment);
    when(segment.writer()).thenReturn(Mockito.mock(SegmentWriter.class));

    final var segmentJournal = journalFactory.journal(segments);
    when(segments.getFirstSegment()).thenReturn(segment);
    when(segments.getTailSegments(anyLong())).thenAnswer(invocation -> sortedMap);

    // when
    assertThatThrownBy(segmentJournal::flush);

    // then
    assertThat(journalFactory.metaStore().loadLastFlushedIndex()).isEqualTo(15L);
  }

  @Test
  void shouldStoreLastFlushedIndexOnPartialFailedFlush() {
    // given
    final var error = new FlushException(new IOException("Cannot allocate memory"));
    final SortedMap<Long, FlushableSegment> sortedMap = new TreeMap<>();
    sortedMap.put(1L, new TestSegment(15));
    sortedMap.put(2L, new TestSegment(30, error));

    final var segments = Mockito.mock(SegmentsManager.class);
    final var segment = Mockito.mock(Segment.class);
    when(segment.index()).thenReturn(1L);
    when(segments.getLastSegment()).thenReturn(segment);
    when(segment.writer()).thenReturn(Mockito.mock(SegmentWriter.class));

    final var segmentJournal = journalFactory.journal(segments);
    when(segments.getFirstSegment()).thenReturn(segment);
    when(segments.getTailSegments(anyLong())).thenAnswer(invocation -> sortedMap);

    // when
    assertThatCode(segmentJournal::flush).isSameAs(error);
    assertThatThrownBy(segmentJournal::flush);

    // then
    assertThat(journalFactory.metaStore().loadLastFlushedIndex()).isEqualTo(15L);
  }
}
