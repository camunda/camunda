/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.journal.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.journal.CorruptedJournalException;
import io.camunda.zeebe.journal.record.RecordData;
import io.camunda.zeebe.journal.record.SBESerializer;
import io.camunda.zeebe.util.buffer.BufferWriter;
import io.camunda.zeebe.util.buffer.DirectBufferWriter;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Objects;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SegmentsManagerTest {

  private static final String JOURNAL_NAME = "journal";

  @TempDir Path directory;
  private final int journalIndexDensity = 1;
  private final UnsafeBuffer data = new UnsafeBuffer("test".getBytes(StandardCharsets.UTF_8));
  private final BufferWriter recordDataWriter = new DirectBufferWriter().wrap(data);
  private final int entrySize = getSerializedSize(data);

  @Test
  void shouldDeleteFilesMarkedForDeletionsOnLoad() {
    // given
    final var segments = createSegmentsManager(0);
    segments.open();
    segments.getFirstSegment().createReader();
    segments.getFirstSegment().delete();

    // when
    // if we close the current journal, it will delete the files on closing. So we cannot test this
    // scenario.
    createSegmentsManager(0).open();

    // then
    final File logDirectory = directory.resolve("data").toFile();
    assertThat(logDirectory)
        .isDirectoryNotContaining(
            file -> SegmentFile.isDeletedSegmentFile(JOURNAL_NAME, file.getName()))
        .isDirectoryContaining(file -> SegmentFile.isSegmentFile(JOURNAL_NAME, file.getName()));
  }

  @Test
  void shouldDetectCorruptionAtDescriptorWithAckedEntries() throws Exception {
    // given
    final var journal = openJournal(1);
    final long index = journal.append(recordDataWriter).index();

    journal.close();
    final File dataFile = directory.resolve("data").toFile();
    final File logFile =
        Objects.requireNonNull(dataFile.listFiles(f -> f.getName().endsWith(".log")))[0];
    LogCorrupter.corruptDescriptor(logFile);

    // when/then
    final var segments = createSegmentsManager(index);
    assertThatThrownBy(segments::open).isInstanceOf(CorruptedJournalException.class);
  }

  @Test
  void shouldNotThrowExceptionWhenCorruptionAtNotAckEntries() throws Exception {
    // given
    final var journal = openJournal(1);
    final var index = journal.append(recordDataWriter).index();
    journal.append(recordDataWriter);

    journal.close();
    final File dataFile = directory.resolve("data").toFile();
    final File logFile =
        Objects.requireNonNull(dataFile.listFiles(f -> f.getName().endsWith("2.log")))[0];
    LogCorrupter.corruptDescriptor(logFile);

    // when/then
    final var segments = createSegmentsManager(index);
    assertThatNoException().isThrownBy(segments::open);
    assertThat(segments.getFirstSegment().index()).isEqualTo(index);
    assertThat(segments.getLastSegment().index()).isEqualTo(index);
  }

  @Test
  void shouldNotThrowExceptionWhenCorruptionAtDescriptorWithoutAckedEntries() throws Exception {
    // given
    final var journal = openJournal(1);
    journal.close();
    final File dataFile = directory.resolve("data").toFile();
    final File logFile =
        Objects.requireNonNull(dataFile.listFiles(f -> f.getName().endsWith(".log")))[0];
    LogCorrupter.corruptDescriptor(logFile);

    // when/then
    final var segments = createSegmentsManager(0);
    assertThatNoException().isThrownBy(segments::open);
    assertThat(segments.getFirstSegment().index()).isEqualTo(1);
    assertThat(segments.getFirstSegment().lastIndex()).isEqualTo(0);
  }

  @Test
  void shouldHandlePartiallyWrittenDescriptor() throws Exception {
    // given
    final File dataFile = directory.resolve("data").toFile();
    assertThat(dataFile.mkdirs()).isTrue();
    final File emptyLog = new File(dataFile, "journal-1.log");
    assertThat(emptyLog.createNewFile()).isTrue();

    // when
    final var segments = createSegmentsManager(0);

    // then
    assertThatNoException().isThrownBy(segments::open);
    assertThat(segments.getFirstSegment().index()).isEqualTo(1);
    assertThat(segments.getFirstSegment().lastIndex()).isEqualTo(0);
  }

  private SegmentsManager createSegmentsManager(final long lastWrittenIndex) {
    final var journalIndex = new SparseJournalIndex(journalIndexDensity);
    final var maxSegmentSize = entrySize + SegmentDescriptor.getEncodingLength();
    return new SegmentsManager(
        journalIndex,
        maxSegmentSize,
        directory.resolve("data").toFile(),
        lastWrittenIndex,
        JOURNAL_NAME,
        new SegmentLoader(2 * maxSegmentSize, new JournalMetrics("1")));
  }

  private SegmentedJournal openJournal(final float entriesPerSegment) {
    return openJournal(entriesPerSegment, entrySize);
  }

  private SegmentedJournal openJournal(final float entriesPerSegment, final int entrySize) {
    return SegmentedJournal.builder()
        .withDirectory(directory.resolve("data").toFile())
        .withMaxSegmentSize(
            (int) (entrySize * entriesPerSegment) + SegmentDescriptor.getEncodingLength())
        .withJournalIndexDensity(journalIndexDensity)
        .withName(JOURNAL_NAME)
        .build();
  }

  private int getSerializedSize(final DirectBuffer data) {
    final var record = new RecordData(1, 1, data);
    final var serializer = new SBESerializer();
    final ByteBuffer buffer = ByteBuffer.allocate(128);
    return serializer.writeData(record, new UnsafeBuffer(buffer), 0).get()
        + FrameUtil.getLength()
        + serializer.getMetadataLength();
  }
}
