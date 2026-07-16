/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.journal.file;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.journal.JournalReader;
import io.camunda.zeebe.journal.JournalRecord;
import io.camunda.zeebe.journal.record.RecordData;
import io.camunda.zeebe.journal.record.SBESerializer;
import io.camunda.zeebe.journal.util.MockJournalMetastore;
import io.camunda.zeebe.util.buffer.BufferUtil;
import io.camunda.zeebe.util.buffer.BufferWriter;
import io.camunda.zeebe.util.buffer.DirectBufferWriter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.agrona.CloseHelper;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SegmentedJournalReaderTest {

  private static final int ENTRIES_PER_SEGMENT = 4;

  @TempDir Path directory;

  @AutoClose private final MeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final UnsafeBuffer data = new UnsafeBuffer("test".getBytes(StandardCharsets.UTF_8));
  private final BufferWriter recordDataWriter = new DirectBufferWriter(data);

  private JournalReader reader;
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
    reader = journal.openReader();
  }

  @AfterEach
  void afterEach() {
    CloseHelper.quietCloseAll(reader, journal);
  }

  @Test
  void shouldReadAfterCompact() {
    // given
    for (int i = 1; i <= ENTRIES_PER_SEGMENT * 5; i++) {
      assertThat(journal.append(i, recordDataWriter).index()).isEqualTo(i);
    }
    assertThat(reader.hasNext()).isTrue();

    // when - compact up to the first index of segment 3
    final int indexToCompact = ENTRIES_PER_SEGMENT * 2 + 1;
    journal.deleteUntil(indexToCompact);
    reader.seekToFirst();

    // then
    assertThat(reader.hasNext()).isTrue();
    assertThat(reader.next().index()).isEqualTo(indexToCompact);
  }

  @Test
  void shouldSeekToAnyIndexInMultipleSegments() {
    // given
    long asqn = 1;

    for (int i = 1; i <= ENTRIES_PER_SEGMENT * 2; i++) {
      journal.append(asqn++, recordDataWriter).index();
    }

    for (int i = 1; i < ENTRIES_PER_SEGMENT * 2; i++) {
      // when
      reader.seek(i);

      // then
      assertThat(reader.hasNext()).isTrue();
      assertThat(reader.next().index()).isEqualTo(i);
    }
  }

  @Test
  void shouldSeekToAnyAsqnInMultipleSegments() {
    // given

    for (int i = 1; i <= ENTRIES_PER_SEGMENT * 2; i++) {
      journal.append(i, recordDataWriter).index();
    }

    for (int i = 1; i <= ENTRIES_PER_SEGMENT * 2; i++) {
      // when
      reader.seekToAsqn(i);

      // then
      assertThat(reader.hasNext()).isTrue();
      assertThat(reader.next().asqn()).isEqualTo(i);
    }
  }

  @Test
  void shouldNotReadWhenAccessingDeletedSegment() {
    // given
    journal.append(recordDataWriter);
    final var reader = journal.openReader();

    // when
    journal.reset(100);

    // then
    assertThat(reader.hasNext()).isFalse();
  }

  @Test
  void shouldReadAfterReset() {
    // given
    journal.append(recordDataWriter);
    final var reader = journal.openReader();
    final int resetIndex = 100;
    journal.reset(resetIndex);
    journal.append(recordDataWriter);

    // when
    reader.seekToFirst();

    // then
    assertThat(reader.next().index()).isEqualTo(resetIndex);
  }

  @Test
  void shouldBuiltIndexOnDemandWhileSeek() {
    // given
    for (int i = 1; i <= ENTRIES_PER_SEGMENT; i++) {
      assertThat(journal.append(i, recordDataWriter).index()).isEqualTo(i);
    }

    // simulate restart with no index
    journal.getJournalIndex().clear();
    assertThat(journal.getJournalIndex().lookup(journal.getLastIndex())).isNull();

    // when
    reader.seekToLast();

    // then
    assertThat(journal.getJournalIndex().lookup(journal.getLastIndex()))
        .describedAs("Index must be built during seek")
        .isNotNull();
  }

  @Test
  @Disabled(
      "The test is crashing the JVM w/ SIGSEV as the record is referencing a pointer that has been unmapped.")
  void shouldResetBufferWhenClosing() {
    // given
    for (int i = 1; i <= ENTRIES_PER_SEGMENT + 1; i++) {
      assertThat(journal.append(i, recordDataWriter).index()).isEqualTo(i);
    }
    final JournalRecord record;
    record = reader.next();
    assertThat(record).isNotNull().returns("test", r -> BufferUtil.bufferAsString(r.data()));
    journal.getFirstSegment().delete();

    // when - reader is closed
    reader.close();

    // then
    assertThat(BufferUtil.bufferAsString(record.data())).isEqualTo("");
  }

  private int getSerializedSize(final DirectBuffer data) {
    final var record = new RecordData(Long.MAX_VALUE, Long.MAX_VALUE, data);
    final var serializer = new SBESerializer();
    final ByteBuffer buffer = ByteBuffer.allocate(128);
    return serializer.writeData(record, new UnsafeBuffer(buffer), 0).get()
        + serializer.getMetadataLength();
  }
}
