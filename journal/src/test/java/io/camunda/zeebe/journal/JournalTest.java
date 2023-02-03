/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.journal;

import static io.camunda.zeebe.journal.file.SegmentedJournal.ASQN_IGNORE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.journal.JournalException.InvalidASqn;
import io.camunda.zeebe.journal.JournalException.InvalidChecksum;
import io.camunda.zeebe.journal.JournalException.InvalidIndex;
import io.camunda.zeebe.journal.file.LogCorrupter;
import io.camunda.zeebe.journal.file.SegmentedJournal;
import io.camunda.zeebe.journal.file.SegmentedJournalBuilder;
import io.camunda.zeebe.journal.record.PersistedJournalRecord;
import io.camunda.zeebe.journal.record.RecordData;
import io.camunda.zeebe.journal.record.RecordMetadata;
import io.camunda.zeebe.journal.util.TestJournalRecord;
import io.camunda.zeebe.util.buffer.BufferUtil;
import io.camunda.zeebe.util.buffer.DirectBufferWriter;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class JournalTest {

  @TempDir Path directory;

  private byte[] entry = "TestData".getBytes();
  private final DirectBufferWriter recordDataWriter =
      new DirectBufferWriter().wrap(new UnsafeBuffer(entry));
  private final DirectBufferWriter otherRecordDataWriter =
      new DirectBufferWriter().wrap(new UnsafeBuffer("TestData".getBytes()));

  @Test
  void shouldBeEmpty() {
    // given
    final var journal = openJournal();

    // when-then
    assertThat(journal.isEmpty()).isTrue();

    journal.close();
  }

  @Test
  void shouldNotBeEmpty() {
    // given
    final var journal = openJournal();
    journal.append(1, recordDataWriter);

    // when-then
    assertThat(journal.isEmpty()).isFalse();

    journal.close();
  }

  @Test
  void shouldAppendData() {
    // given
    final var journal = openJournal();

    // when
    final var recordAppended = journal.append(1, recordDataWriter);

    // then
    assertThat(recordAppended.index()).isEqualTo(1);
    assertThat(recordAppended.asqn()).isEqualTo(1);

    journal.close();
  }

  @Test
  void shouldReadRecord() {
    // given
    final var journal = openJournal();
    final var recordAppended = journal.append(1, recordDataWriter);

    // when
    final var reader = journal.openReader();
    final var recordRead = reader.next();

    // then
    assertThat(recordRead).isEqualTo(recordAppended);

    journal.close();
  }

  @Test
  void shouldAppendMultipleData() {
    // given
    final var journal = openJournal();

    // when
    final var firstRecord = journal.append(10, recordDataWriter);
    final var secondRecord = journal.append(20, otherRecordDataWriter);

    // then
    assertThat(firstRecord.index()).isEqualTo(1);
    assertThat(firstRecord.asqn()).isEqualTo(10);

    assertThat(secondRecord.index()).isEqualTo(2);
    assertThat(secondRecord.asqn()).isEqualTo(20);

    journal.close();
  }

  @Test
  void shouldReadMultipleRecord() {
    // given
    final var journal = openJournal();
    final var firstRecord = journal.append(1, recordDataWriter);
    final var secondRecord = journal.append(20, otherRecordDataWriter);

    // when
    final var reader = journal.openReader();
    final var firstRecordRead = reader.next();
    final var secondRecordRead = reader.next();

    // then
    assertThat(firstRecordRead).isEqualTo(firstRecord);
    assertThat(secondRecordRead).isEqualTo(secondRecord);

    journal.close();
  }

  @Test
  void shouldAppendAndReadMultipleRecordsInOrder() {
    // given
    final var journal = openJournal();

    // when
    for (int i = 0; i < 10; i++) {
      final var recordAppended = journal.append(i + 10, recordDataWriter);
      assertThat(recordAppended.index()).isEqualTo(i + 1);
    }

    // then
    final var reader = journal.openReader();
    for (int i = 0; i < 10; i++) {
      assertThat(reader.hasNext()).isTrue();
      final var recordRead = reader.next();
      assertThat(recordRead.index()).isEqualTo(i + 1);
      final byte[] data = new byte[recordRead.data().capacity()];
      recordRead.data().getBytes(0, data);
      assertThat(recordRead.asqn()).isEqualTo(i + 10);
      assertThat(data).containsExactly(entry);
    }

    journal.close();
  }

  @Test
  void shouldAppendAndReadMultipleRecords() {
    // given
    final var journal = openJournal();
    final var reader = journal.openReader();

    for (int i = 0; i < 10; i++) {
      entry = ("TestData" + i).getBytes();
      recordDataWriter.wrap(new UnsafeBuffer(entry));

      // when
      final var recordAppended = journal.append(i + 10, recordDataWriter);
      assertThat(recordAppended.index()).isEqualTo(i + 1);

      // then
      assertThat(reader.hasNext()).isTrue();
      final var recordRead = reader.next();
      assertThat(recordRead.index()).isEqualTo(i + 1);
      final byte[] data = new byte[recordRead.data().capacity()];
      recordRead.data().getBytes(0, data);
      assertThat(recordRead.asqn()).isEqualTo(i + 10);
      assertThat(data).containsExactly(entry);
    }

    journal.close();
  }

  @Test
  void shouldReset() {
    // given
    final var journal = openJournal();
    long asqn = 1;
    assertThat(journal.getLastIndex()).isEqualTo(0);
    journal.append(asqn++, recordDataWriter);
    journal.append(asqn++, recordDataWriter);

    // when
    journal.reset(2);

    // then
    assertThat(journal.isEmpty()).isTrue();
    assertThat(journal.getLastIndex()).isEqualTo(1);
    final var record = journal.append(asqn, recordDataWriter);
    assertThat(record.index()).isEqualTo(2);

    journal.close();
  }

  @Test
  void shouldNotReadAfterJournalResetWithoutReaderReset() {
    // given
    final var journal = openJournal();
    final var reader = journal.openReader();
    long asqn = 1;
    assertThat(journal.getLastIndex()).isEqualTo(0);
    journal.append(asqn++, recordDataWriter);
    journal.append(asqn++, recordDataWriter);
    final var record1 = reader.next();
    assertThat(record1.index()).isEqualTo(1);

    // when
    journal.reset(2);
    journal.append(asqn, recordDataWriter);

    // then
    assertThat(reader.hasNext()).isFalse();

    journal.close();
  }

  @Test
  void shouldWriteToTruncatedIndex() {
    // given
    final var journal = openJournal();
    final var reader = journal.openReader();
    assertThat(journal.getLastIndex()).isEqualTo(0);
    journal.append(1, recordDataWriter);
    journal.append(2, recordDataWriter);
    journal.append(3, recordDataWriter);
    final var record1 = reader.next();
    assertThat(record1.index()).isEqualTo(1);

    // when
    journal.deleteAfter(1);

    // then
    assertThat(journal.getLastIndex()).isEqualTo(1);
    final var record = journal.append(4, recordDataWriter);
    assertThat(record.index()).isEqualTo(2);
    assertThat(record.asqn()).isEqualTo(4);
    assertThat(reader.hasNext()).isTrue();

    final var newRecord = reader.next();
    assertThat(newRecord).isEqualTo(record);

    journal.close();
  }

  @Test
  void shouldTruncate() {
    // given
    final var journal = openJournal();
    final var reader = journal.openReader();
    assertThat(journal.getLastIndex()).isEqualTo(0);
    journal.append(1, recordDataWriter);
    journal.append(2, recordDataWriter);
    journal.append(3, recordDataWriter);
    final var record1 = reader.next();
    assertThat(record1.index()).isEqualTo(1);

    // when
    journal.deleteAfter(1);

    // then
    assertThat(journal.getLastIndex()).isEqualTo(1);
    assertThat(reader.hasNext()).isFalse();

    journal.close();
  }

  @Test
  void shouldNotReadTruncatedEntries() {
    // given
    final var journal = openJournal();
    final int totalWrites = 10;
    final int truncateIndex = 5;
    int asqn = 1;
    final Map<Integer, JournalRecord> written = new HashMap<>();

    final var reader = journal.openReader();

    int writerIndex;
    for (writerIndex = 1; writerIndex <= totalWrites; writerIndex++) {
      final var record = journal.append(asqn++, recordDataWriter);
      assertThat(record.index()).isEqualTo(writerIndex);
      written.put(writerIndex, record);
    }

    int readerIndex;
    for (readerIndex = 1; readerIndex <= truncateIndex; readerIndex++) {
      assertThat(reader.hasNext()).isTrue();
      final var record = reader.next();
      assertThat(record).isEqualTo(written.get(readerIndex));
    }

    // when
    journal.deleteAfter(truncateIndex);

    for (writerIndex = truncateIndex + 1; writerIndex <= totalWrites; writerIndex++) {
      final var record = journal.append(asqn++, recordDataWriter);
      assertThat(record.index()).isEqualTo(writerIndex);
      written.put(writerIndex, record);
    }

    // then
    for (; readerIndex <= totalWrites; readerIndex++) {
      assertThat(reader.hasNext()).isTrue();
      final var record = reader.next();
      assertThat(record).isEqualTo(written.get(readerIndex));
    }

    journal.close();
  }

  @Test
  void shouldNotReadTruncatedEntriesWhenReaderPastTruncateIndex() {
    // given
    final var journal = openJournal();
    final var reader = journal.openReader();
    assertThat(journal.getLastIndex()).isEqualTo(0);
    journal.append(1, recordDataWriter);
    journal.append(2, recordDataWriter);
    journal.append(3, recordDataWriter);
    reader.next();
    reader.next();
    assertThat(reader.hasNext()).isTrue();

    // when
    journal.deleteAfter(1);

    // then
    assertThat(journal.getLastIndex()).isEqualTo(1);
    assertThat(reader.hasNext()).isFalse();

    journal.close();
  }

  @Test
  void shouldNotReadTruncatedEntriesWhenReaderAtTruncateIndex() {
    // given
    final var journal = openJournal();
    final var reader = journal.openReader();
    assertThat(journal.getLastIndex()).isEqualTo(0);
    journal.append(1, recordDataWriter);
    journal.append(2, recordDataWriter);
    journal.append(3, recordDataWriter);
    reader.next();
    reader.next();
    assertThat(reader.hasNext()).isTrue();

    // when
    journal.deleteAfter(2);

    // then
    assertThat(journal.getLastIndex()).isEqualTo(2);
    assertThat(reader.hasNext()).isFalse();

    journal.close();
  }

  @Test
  void shouldNotReadTruncatedEntriesWhenReaderBeforeTruncateIndex() {
    // given
    final var journal = openJournal();
    final var reader = journal.openReader();
    assertThat(journal.getLastIndex()).isEqualTo(0);
    journal.append(1, recordDataWriter);
    journal.append(2, recordDataWriter);
    journal.append(3, recordDataWriter);
    reader.next();
    assertThat(reader.hasNext()).isTrue();

    // when
    journal.deleteAfter(2);

    // then
    assertThat(journal.getLastIndex()).isEqualTo(2);
    reader.next();
    assertThat(reader.hasNext()).isFalse();

    journal.close();
  }

  @Test
  void shouldAppendJournalRecord() {
    // given
    final var journal = openJournal();
    final var receiverJournal =
        SegmentedJournal.builder()
            .withDirectory(directory.resolve("data-2").toFile())
            .withJournalIndexDensity(5)
            .build();
    final var expected = journal.append(10, recordDataWriter);

    // when
    receiverJournal.append(expected);

    // then
    final var reader = receiverJournal.openReader();
    assertThat(reader.hasNext()).isTrue();
    final var actual = reader.next();
    assertThat(expected).isEqualTo(actual);

    journal.close();
  }

  @Test
  void shouldNotAppendRecordWithAlreadyAppendedIndex() {
    // given
    final var journal = openJournal();
    final var record = journal.append(1, recordDataWriter);
    journal.append(recordDataWriter);

    // when/then
    assertThatThrownBy(() -> journal.append(record)).isInstanceOf(InvalidIndex.class);

    journal.close();
  }

  @Test
  void shouldNotAppendRecordWithGapInIndex() {
    // given
    final var journal = openJournal();
    final var receiverJournal =
        SegmentedJournal.builder()
            .withDirectory(directory.resolve("data-2").toFile())
            .withJournalIndexDensity(5)
            .build();
    journal.append(1, recordDataWriter);
    final var record = journal.append(2, recordDataWriter);

    // when/then
    assertThatThrownBy(() -> receiverJournal.append(record)).isInstanceOf(InvalidIndex.class);

    journal.close();
  }

  @Test
  void shouldNotAppendLastRecord() {
    // given
    final var journal = openJournal();
    final var record = journal.append(1, recordDataWriter);

    // when/then
    assertThatThrownBy(() -> journal.append(record)).isInstanceOf(InvalidIndex.class);

    journal.close();
  }

  @Test
  void shouldAppendRecordWithASqnToIgnore() {
    // given
    final var journal = openJournal();
    journal.append(1, recordDataWriter);

    // when/then
    journal.append(ASQN_IGNORE, recordDataWriter);

    journal.close();
  }

  @Test
  void shouldNotAppendRecordWithInvalidChecksum() {
    // given
    final var journal = openJournal();
    final var receiverJournal =
        SegmentedJournal.builder()
            .withDirectory(directory.resolve("data-2").toFile())
            .withJournalIndexDensity(5)
            .build();
    final var record = journal.append(1, recordDataWriter);

    // when
    final var invalidChecksumRecord =
        new TestJournalRecord(record.index(), record.asqn(), -1, record.data());

    // then
    assertThatThrownBy(() -> receiverJournal.append(invalidChecksumRecord))
        .isInstanceOf(InvalidChecksum.class);

    journal.close();
  }

  @Test
  void shouldNotAppendRecordWithTooLowASqn() {
    // given
    final var journal = openJournal();
    journal.append(1, recordDataWriter);

    // when/then
    assertThatThrownBy(() -> journal.append(0, recordDataWriter)).isInstanceOf(InvalidASqn.class);
    assertThatThrownBy(() -> journal.append(1, recordDataWriter)).isInstanceOf(InvalidASqn.class);

    journal.close();
  }

  @Test
  void shouldNotAppendRecordWithTooLowASqnIfPreviousRecordIsIgnoreASqn() {
    // given
    final var journal = openJournal();
    journal.append(1, recordDataWriter);

    // when
    journal.append(ASQN_IGNORE, recordDataWriter);

    // then
    assertThatThrownBy(() -> journal.append(0, recordDataWriter)).isInstanceOf(InvalidASqn.class);
    assertThatThrownBy(() -> journal.append(1, recordDataWriter)).isInstanceOf(InvalidASqn.class);

    journal.close();
  }

  @Test
  void shouldReturnFirstIndex() {
    // given
    final var journal = openJournal();

    // when
    final long firstIndex = journal.append(recordDataWriter).index();
    journal.append(recordDataWriter);

    // then
    assertThat(journal.getFirstIndex()).isEqualTo(firstIndex);

    journal.close();
  }

  @Test
  void shouldReturnLastIndex() {
    // given
    final var journal = openJournal();

    // when
    journal.append(recordDataWriter);
    final long lastIndex = journal.append(recordDataWriter).index();

    // then
    assertThat(journal.getLastIndex()).isEqualTo(lastIndex);

    journal.close();
  }

  @Test
  void shouldOpenAndClose() {
    // given
    final var journal = openJournal();

    // when/then
    assertThat(journal.isOpen()).isTrue();
    journal.close();
    assertThat(journal.isOpen()).isFalse();
  }

  @Test
  void shouldReopenJournalWithExistingRecords() throws Exception {
    // given
    final long lastIndexBeforeClose;
    try (var journal = openJournal()) {
      journal.append(recordDataWriter);
      journal.append(recordDataWriter);
      lastIndexBeforeClose = journal.getLastIndex();
      assertThat(lastIndexBeforeClose).isEqualTo(2);
    }

    // when
    final var journal = openJournal();

    // then
    assertThat(journal.isOpen()).isTrue();
    assertThat(journal.getLastIndex()).isEqualTo(lastIndexBeforeClose);

    journal.close();
  }

  @Test
  void shouldReadReopenedJournal() throws Exception {
    // given
    final PersistedJournalRecord appendedRecord;
    final JournalReader reader;
    try (var journal = openJournal()) {
      appendedRecord = copyRecord(journal.append(recordDataWriter));
    }

    // when
    final var journal = openJournal();
    reader = journal.openReader();

    // then
    assertThat(journal.isOpen()).isTrue();

    assertThat(reader.hasNext()).isTrue();
    assertThat(reader.next()).isEqualTo(appendedRecord);

    journal.close();
  }

  @Test
  void shouldWriteToReopenedJournalAtNextIndex() throws Exception {
    // given
    final PersistedJournalRecord firstRecord;
    try (var journal = openJournal()) {
      firstRecord = copyRecord(journal.append(recordDataWriter));
    }

    // when
    final var journal = openJournal();
    final var secondRecord = journal.append(recordDataWriter);

    // then
    assertThat(secondRecord.index()).isEqualTo(2);

    final JournalReader reader = journal.openReader();

    assertThat(reader.hasNext()).isTrue();
    assertThat(reader.next()).isEqualTo(firstRecord);

    assertThat(reader.hasNext()).isTrue();
    assertThat(reader.next()).isEqualTo(secondRecord);

    journal.close();
  }

  @Test
  void shouldNotReadDeletedEntries() {
    // given
    final var journal = openJournal();
    final var firstRecord = journal.append(recordDataWriter);
    journal.append(recordDataWriter);
    journal.append(recordDataWriter);

    // when
    journal.deleteAfter(firstRecord.index());
    final var newSecondRecord = journal.append(recordDataWriter);

    // then
    final JournalReader reader = journal.openReader();
    assertThat(newSecondRecord.index()).isEqualTo(2);

    assertThat(reader.hasNext()).isTrue();
    assertThat(reader.next()).isEqualTo(firstRecord);

    assertThat(reader.hasNext()).isTrue();
    assertThat(reader.next()).isEqualTo(newSecondRecord);

    assertThat(reader.hasNext()).isFalse();

    journal.close();
  }

  @Test
  void shouldInvalidateAllEntries() {
    // given
    final var journal = openJournal();
    recordDataWriter.wrap(new UnsafeBuffer("000".getBytes(StandardCharsets.UTF_8)));
    final var firstRecord = copyRecord(journal.append(recordDataWriter));

    journal.append(recordDataWriter);
    journal.append(recordDataWriter);

    // when
    journal.deleteAfter(firstRecord.index());
    recordDataWriter.wrap(new UnsafeBuffer("111".getBytes(StandardCharsets.UTF_8)));
    final var secondRecord = copyRecord(journal.append(recordDataWriter));

    journal.close();
    final var newJournal = openJournal();

    // then
    final var reader = newJournal.openReader();
    assertThat(reader.next()).isEqualTo(firstRecord);
    assertThat(reader.next()).isEqualTo(secondRecord);
    assertThat(reader.hasNext()).isFalse();
    newJournal.close();
  }

  @Test
  void shouldDetectCorruptedEntry() throws Exception {
    // given
    final var journal = openJournal();
    recordDataWriter.wrap(new UnsafeBuffer("000".getBytes(StandardCharsets.UTF_8)));
    journal.append(recordDataWriter);
    final var secondRecord = copyRecord(journal.append(recordDataWriter));
    final File dataFile = Objects.requireNonNull(directory.toFile().listFiles())[0];
    final File log = Objects.requireNonNull(dataFile.listFiles())[0];

    // when
    journal.close();
    assertThat(LogCorrupter.corruptRecord(log, secondRecord.index())).isTrue();

    // then
    //noinspection resource
    assertThatThrownBy(() -> openJournal(b -> b.withLastWrittenIndex(secondRecord.index())))
        .isInstanceOf(CorruptedJournalException.class);
  }

  @Test
  void shouldDeletePartiallyWrittenEntry() throws Exception {
    // given
    final var journal = openJournal();
    recordDataWriter.wrap(new UnsafeBuffer("000".getBytes(StandardCharsets.UTF_8)));
    final var firstRecord = copyRecord(journal.append(recordDataWriter));
    final var secondRecord = copyRecord(journal.append(recordDataWriter));
    final File dataFile = Objects.requireNonNull(directory.toFile().listFiles())[0];
    final File log = Objects.requireNonNull(dataFile.listFiles())[0];

    // when
    journal.close();
    assertThat(LogCorrupter.corruptRecord(log, secondRecord.index())).isTrue();
    final var newJournal = openJournal(b -> b.withLastWrittenIndex(firstRecord.index()));
    recordDataWriter.wrap(new UnsafeBuffer("111".getBytes(StandardCharsets.UTF_8)));
    final var lastRecord = newJournal.append(recordDataWriter);
    final var reader = newJournal.openReader();

    // then
    assertThat(reader.next()).isEqualTo(firstRecord);
    assertThat(reader.next()).isEqualTo(lastRecord);

    newJournal.close();
  }

  // TODO: do not rely on implementation detail to compare records
  private PersistedJournalRecord copyRecord(final JournalRecord record) {
    final RecordData data =
        new RecordData(record.index(), record.asqn(), BufferUtil.cloneBuffer(record.data()));

    if (record instanceof PersistedJournalRecord p) {
      return new PersistedJournalRecord(p.metadata(), data);
    }

    return new PersistedJournalRecord(
        new RecordMetadata(record.checksum(), data.data().capacity()), data);
  }

  private SegmentedJournal openJournal() {
    return openJournal(b -> {});
  }

  private SegmentedJournal openJournal(final Consumer<SegmentedJournalBuilder> option) {
    final var builder =
        SegmentedJournal.builder()
            .withDirectory(directory.resolve("data").toFile())
            .withJournalIndexDensity(5);
    option.accept(builder);

    return builder.build();
  }
}
