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
import io.camunda.zeebe.journal.util.MockJournalMetastore;
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
  private final DirectBuffer data = new UnsafeBuffer("test".getBytes(StandardCharsets.UTF_8));
  private final int entrySize = getSerializedSize(data);

  @Test
  void shouldDeleteFilesMarkedForDeletionsOnLoad() {
    // given
    final var segments = createSegmentsManager();
    segments.open(0);
    Objects.requireNonNull(segments.getFirstSegment()).createReader();
    segments.getFirstSegment().delete();

    // when
    // if we close the current journal, it will delete the files on closing. So we cannot test this
    // scenario.
    createSegmentsManager().open(0);

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
    final long index = journal.append(data).index();

    journal.close();
    final File dataFile = directory.resolve("data").toFile();
    final File logFile =
        Objects.requireNonNull(dataFile.listFiles(f -> f.getName().endsWith(".log")))[0];
    LogCorrupter.corruptDescriptor(logFile);

    // when/then
    final var segments = createSegmentsManager();
    assertThatThrownBy(() -> segments.open(index)).isInstanceOf(CorruptedJournalException.class);
  }

  @Test
  void shouldNotThrowExceptionWhenCorruptionAtNotAckEntries() throws Exception {
    // given
    final var journal = openJournal(1);
    final var index = journal.append(data).index();
    journal.append(data);

    journal.close();
    final File dataFile = directory.resolve("data").toFile();
    final File logFile =
        Objects.requireNonNull(dataFile.listFiles(f -> f.getName().endsWith("2.log")))[0];
    LogCorrupter.corruptDescriptor(logFile);

    // when/then
    final var segments = createSegmentsManager();
    assertThatNoException().isThrownBy(() -> segments.open(index));
    assertThat(Objects.requireNonNull(segments.getFirstSegment()).index()).isEqualTo(index);
    assertThat(Objects.requireNonNull(segments.getLastSegment()).index()).isEqualTo(index);
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
    final var segments = createSegmentsManager();
    assertThatNoException().isThrownBy(() -> segments.open(0));
    assertThat(Objects.requireNonNull(segments.getFirstSegment()).index()).isOne();
    assertThat(segments.getFirstSegment().lastIndex()).isZero();
  }

  @Test
  void shouldHandlePartiallyWrittenDescriptor() throws Exception {
    // given
    final File dataFile = directory.resolve("data").toFile();
    assertThat(dataFile.mkdirs()).isTrue();
    final File emptyLog = new File(dataFile, "journal-1.log");
    assertThat(emptyLog.createNewFile()).isTrue();

    // when
    final var segments = createSegmentsManager();

    // then
    assertThatNoException().isThrownBy(() -> segments.open(0));
    assertThat(Objects.requireNonNull(segments.getFirstSegment()).index()).isOne();
    assertThat(segments.getFirstSegment().lastIndex()).isZero();
  }

  private SegmentsManager createSegmentsManager() {
    final var journalIndex = new SparseJournalIndex(journalIndexDensity);
    return new SegmentsManager(
        journalIndex,
        entrySize + SegmentDescriptor.getEncodingLength(),
        directory.resolve("data").toFile(),
        JOURNAL_NAME,
        new SegmentLoader());
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
        .withMetaStore(new MockJournalMetastore())
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
