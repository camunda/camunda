/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.journal.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.journal.record.RecordData;
import io.camunda.zeebe.journal.record.SBESerializer;
import io.camunda.zeebe.journal.util.MockJournalMetastore;
import io.camunda.zeebe.util.buffer.BufferWriter;
import io.camunda.zeebe.util.buffer.DirectBufferWriter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import org.agrona.CloseHelper;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

class SegmentedJournalCloseRaceTest {

  private static final int ENTRIES_PER_SEGMENT = 4;

  @TempDir Path directory;

  @AutoClose private final MeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final UnsafeBuffer data = new UnsafeBuffer("test".getBytes(StandardCharsets.UTF_8));
  private final BufferWriter recordDataWriter = new DirectBufferWriter(data);

  private SegmentedJournal journal;

  @BeforeEach
  void setup() {
    final int entrySize = FrameUtil.getLength() + getSerializedSize(data);
    journal =
        SegmentedJournal.builder(meterRegistry)
            .withDirectory(directory.resolve("data").toFile())
            .withMaxSegmentSize(
                entrySize * ENTRIES_PER_SEGMENT
                    + SegmentDescriptorSerializer.currentEncodingLength())
            .withJournalIndexDensity(ENTRIES_PER_SEGMENT / 2)
            .withMetaStore(new MockJournalMetastore())
            .build();
  }

  @AfterEach
  void afterEach() {
    CloseHelper.quietCloseAll(journal);
  }

  @Test
  void closeIsNotBlockedByAHeldReaderReadLock() {
    // given
    for (int i = 1; i <= ENTRIES_PER_SEGMENT * 5; i++) {
      journal.append(i, recordDataWriter);
    }

    // when
    final long readStamp = journal.acquireReadlock();
    try {
      final var close = CompletableFuture.runAsync(() -> journal.close());

      // then
      assertThat(close)
          .describedAs("close() bypasses the read/write lock that protects readers")
          .succeedsWithin(Duration.ofSeconds(5));
    } finally {
      journal.releaseReadlock(readStamp);
    }
  }

  @Test
  void readerSeekThrowsExceptionWhenSegmentClosesBetweenGuardAndSeek() throws Exception {
    // given - a reader positioned on the first segment
    for (int i = 1; i <= ENTRIES_PER_SEGMENT * 3; i++) {
      journal.append(i, recordDataWriter);
    }
    final var reader = (SegmentedJournalReader) journal.openReader();
    reader.seekToFirst();

    // a segment that reports open to the guard, then closed to checkSegmentOpen
    final var racingSegment = Mockito.mock(Segment.class);
    Mockito.when(racingSegment.isOpen()).thenReturn(true, false);

    // inject it as both the reader's "view" segment (the guarded one) and the delegate
    // SegmentReader's segment (the one checkSegmentOpen inspects)
    final var currentReaderField = SegmentedJournalReader.class.getDeclaredField("currentReader");
    currentReaderField.setAccessible(true);
    final var delegate = (SegmentReader) currentReaderField.get(reader);
    final long nextIndex = delegate.getNextIndex();

    final var currentSegmentField = SegmentedJournalReader.class.getDeclaredField("currentSegment");
    currentSegmentField.setAccessible(true);
    currentSegmentField.set(reader, racingSegment);

    final var segmentField = SegmentReader.class.getDeclaredField("segment");
    segmentField.setAccessible(true);
    segmentField.set(delegate, racingSegment);

    // when - seek to the current index
    // then - the exception is thrown
    assertThatThrownBy(() -> reader.seek(nextIndex))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Segment is already closed. Reader must reset to a valid index.");
  }

  private int getSerializedSize(final DirectBuffer data) {
    final var record = new RecordData(Long.MAX_VALUE, Long.MAX_VALUE, data);
    final var serializer = new SBESerializer();
    final var buffer = ByteBuffer.allocate(128);
    final var maybeWritten = serializer.writeData(record, new UnsafeBuffer(buffer), 0);
    return maybeWritten.get() + serializer.getMetadataLength();
  }
}
