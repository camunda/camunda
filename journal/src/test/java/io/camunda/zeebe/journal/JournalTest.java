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

import io.camunda.zeebe.journal.JournalException.InvalidAsqn;
import io.camunda.zeebe.journal.JournalException.InvalidChecksum;
import io.camunda.zeebe.journal.JournalException.InvalidIndex;
import io.camunda.zeebe.journal.file.LogCorrupter;
import io.camunda.zeebe.journal.file.SegmentedJournal;
import io.camunda.zeebe.journal.file.SegmentedJournalBuilder;
import io.camunda.zeebe.journal.record.PersistedJournalRecord;
import io.camunda.zeebe.journal.record.RecordData;
import io.camunda.zeebe.journal.record.RecordMetadata;
import io.camunda.zeebe.journal.util.MockJournalMetastore;
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
import org.agrona.CloseHelper;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class JournalTest {

  @TempDir Path directory;
  final JournalMetaStore metaStore = new MockJournalMetastore();
  private byte[] entry;
  private final DirectBufferWriter recordDataWriter = new DirectBufferWriter();
  private final DirectBufferWriter otherRecordDataWriter = new DirectBufferWriter();
  private Journal journal;

  @BeforeEach
  void setup() {
    entry = "TestData".getBytes();
    recordDataWriter.wrap(new UnsafeBuffer(entry));

    final var entryOther = "TestData".getBytes();
    otherRecordDataWriter.wrap(new UnsafeBuffer(entryOther));

    journal = openJournal();
  }

  @AfterEach
  void teardown() {
    CloseHelper.quietClose(journal);
  }

  @Test
  void shouldBeEmpty() {
    // when-then
    assertThat(journal.isEmpty()).isTrue();
  }

  @Test
  void shouldNotBeEmpty() {
    // given
    journal.append(1, recordDataWriter);

    // when-then
    assertThat(journal.isEmpty()).isFalse();
  }

  @Test
  void shouldAppendData() {
    // when
    final var recordAppended = journal.append(1, recordDataWriter);

    // then
    assertThat(recordAppended.index()).isEqualTo(1);
    assertThat(recordAppended.asqn()).isEqualTo(1);
  }

  @Test
  void shouldReadRecord() {
    // given
    final var recordAppended = journal.append(1, recordDataWriter);

    // when
    final var reader = journal.openReader();
    final var recordRead = reader.next();

    // then
    assertThat(recordRead).isEqualTo(recordAppended);
  }

  @Test
  void shouldAppendMultipleData() {
    // when
    final var firstRecord = journal.append(10, recordDataWriter);
    final var secondRecord = journal.append(20, otherRecordDataWriter);

    // then
    assertThat(firstRecord.index()).isEqualTo(1);
    assertThat(firstRecord.asqn()).isEqualTo(10);

    assertThat(secondRecord.index()).isEqualTo(2);
    assertThat(secondRecord.asqn()).isEqualTo(20);
  }

  @Test
  void shouldReadMultipleRecord() {
    // given
    final var firstRecord = journal.append(1, recordDataWriter);
    final var secondRecord = journal.append(20, otherRecordDataWriter);

    // when
    final var reader = journal.openReader();
    final var firstRecordRead = reader.next();
    final var secondRecordRead = reader.next();

    // then
    assertThat(firstRecordRead).isEqualTo(firstRecord);
    assertThat(secondRecordRead).isEqualTo(secondRecord);
  }

  @Test
  void shouldAppendAndReadMultipleRecordsInOrder() {
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
  }

  @Test
  void shouldAppendAndReadMultipleRecords() {
    final var reader = journal.openReader();
    for (int i = 0; i < 10; i++) {
      // given
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
  }

  @Test
  void shouldReset() {
    // given
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
  }

  @Test
  void shouldNotReadAfterJournalResetWithoutReaderReset() {
    // given
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
  }

  @Test
  void shouldWriteToTruncatedIndex() {
    // given
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
  }

  @Test
  void shouldTruncate() {
    // given
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
  }

  @Test
  void shouldCompact() {
    // given
    final var reader = journal.openReader();
    final var firstIndex = journal.getFirstIndex();

    // when - fill out until something can be deleted
    long currentIndex = 0;
    long position = 1;
    while (!journal.deleteUntil(currentIndex)) {
      currentIndex = journal.append(position++, recordDataWriter).index();
    }

    // then
    assertThat(reader.seekToFirst())
        .as(
            "compacted if current index %d greater than first ever index %d",
            currentIndex, firstIndex)
        .isEqualTo(currentIndex)
        .isNotEqualTo(firstIndex);
  }

  @Test
  void shouldNotReadTruncatedEntries() {
    // given
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
  }

  @Test
  void shouldNotReadTruncatedEntriesWhenReaderPastTruncateIndex() {
    // given
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
  }

  @Test
  void shouldNotReadTruncatedEntriesWhenReaderAtTruncateIndex() {
    // given
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
  }

  @Test
  void shouldNotReadTruncatedEntriesWhenReaderBeforeTruncateIndex() {
    // given
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
  }

  @Test
  void shouldAppendJournalRecord() {
    // given
    final var receiverJournal =
        SegmentedJournal.builder()
            .withDirectory(directory.resolve("data-2").toFile())
            .withJournalIndexDensity(5)
            .withMetaStore(new MockJournalMetastore())
            .build();
    final var expected = journal.append(10, recordDataWriter);

    // when
    receiverJournal.append(expected);

    // then
    final var reader = receiverJournal.openReader();
    assertThat(reader.hasNext()).isTrue();
    final var actual = reader.next();
    assertThat(expected).isEqualTo(actual);
  }

  @Test
  void shouldNotAppendRecordWithAlreadyAppendedIndex() {
    // given
    final var record = journal.append(1, recordDataWriter);
    journal.append(recordDataWriter);

    // when/then
    assertThatThrownBy(() -> journal.append(record)).isInstanceOf(InvalidIndex.class);
  }

  @Test
  void shouldNotAppendRecordWithGapInIndex() {
    // given
    final var receiverJournal =
        SegmentedJournal.builder()
            .withDirectory(directory.resolve("data-2").toFile())
            .withJournalIndexDensity(5)
            .withMetaStore(new MockJournalMetastore())
            .build();
    journal.append(1, recordDataWriter);
    final var record = journal.append(2, recordDataWriter);

    // when/then
    assertThatThrownBy(() -> receiverJournal.append(record)).isInstanceOf(InvalidIndex.class);
  }

  @Test
  void shouldNotAppendLastRecord() {
    // given
    final var record = journal.append(1, recordDataWriter);

    // when/then
    assertThatThrownBy(() -> journal.append(record)).isInstanceOf(InvalidIndex.class);
  }

  @Test
  void shouldAppendRecordWithASqnToIgnore() {
    // given
    journal.append(1, recordDataWriter);

    // when/then
    journal.append(ASQN_IGNORE, recordDataWriter);
  }

  @Test
  void shouldNotAppendRecordWithInvalidChecksum() {
    // given
    final var receiverJournal =
        SegmentedJournal.builder()
            .withDirectory(directory.resolve("data-2").toFile())
            .withJournalIndexDensity(5)
            .withMetaStore(new MockJournalMetastore())
            .build();
    final var record = journal.append(1, recordDataWriter);

    // when
    final var invalidChecksumRecord =
        new TestJournalRecord(record.index(), record.asqn(), -1, record.data());

    // then
    assertThatThrownBy(() -> receiverJournal.append(invalidChecksumRecord))
        .isInstanceOf(InvalidChecksum.class);
  }

  @Test
  void shouldNotAppendRecordWithTooLowASqn() {
    // given
    journal.append(1, recordDataWriter);

    // when/then
    assertThatThrownBy(() -> journal.append(0, recordDataWriter)).isInstanceOf(InvalidAsqn.class);
    assertThatThrownBy(() -> journal.append(1, recordDataWriter)).isInstanceOf(InvalidAsqn.class);
  }

  @Test
  void shouldNotAppendRecordWithTooLowASqnIfPreviousRecordIsIgnoreASqn() {
    // given
    journal.append(1, recordDataWriter);

    // when
    journal.append(ASQN_IGNORE, recordDataWriter);

    // then
    assertThatThrownBy(() -> journal.append(0, recordDataWriter)).isInstanceOf(InvalidAsqn.class);
    assertThatThrownBy(() -> journal.append(1, recordDataWriter)).isInstanceOf(InvalidAsqn.class);
  }

  @Test
  void shouldReturnFirstIndex() {
    // when
    final long firstIndex = journal.append(recordDataWriter).index();
    journal.append(recordDataWriter);

    // then
    assertThat(journal.getFirstIndex()).isEqualTo(firstIndex);
  }

  @Test
  void shouldReturnLastIndex() {
    // when
    journal.append(recordDataWriter);
    final long lastIndex = journal.append(recordDataWriter).index();

    // then
    assertThat(journal.getLastIndex()).isEqualTo(lastIndex);
  }

  @Test
  void shouldOpenAndClose() throws Exception {
    // when/then
    assertThat(journal.isOpen()).isTrue();
    journal.close();
    assertThat(journal.isOpen()).isFalse();
  }

  @Test
  void shouldReopenJournalWithExistingRecords() throws Exception {
    // given
    journal.append(recordDataWriter);
    journal.append(recordDataWriter);
    final long lastIndexBeforeClose = journal.getLastIndex();
    assertThat(lastIndexBeforeClose).isEqualTo(2);
    journal.close();

    // when
    journal = openJournal();

    // then
    assertThat(journal.isOpen()).isTrue();
    assertThat(journal.getLastIndex()).isEqualTo(lastIndexBeforeClose);
  }

  @Test
  void shouldReadReopenedJournal() throws Exception {
    // given
    final var appendedRecord = copyRecord(journal.append(recordDataWriter));
    journal.close();

    // when
    journal = openJournal();
    final JournalReader reader = journal.openReader();

    // then
    assertThat(journal.isOpen()).isTrue();
    assertThat(reader.hasNext()).isTrue();
    assertThat(reader.next()).isEqualTo(appendedRecord);
  }

  @Test
  void shouldWriteToReopenedJournalAtNextIndex() throws Exception {
    // given
    final var firstRecord = copyRecord(journal.append(recordDataWriter));
    journal.close();

    // when
    journal = openJournal();
    final var secondRecord = journal.append(recordDataWriter);

    // then
    assertThat(secondRecord.index()).isEqualTo(2);

    final JournalReader reader = journal.openReader();

    assertThat(reader.hasNext()).isTrue();
    assertThat(reader.next()).isEqualTo(firstRecord);

    assertThat(reader.hasNext()).isTrue();
    assertThat(reader.next()).isEqualTo(secondRecord);
  }

  @Test
  void shouldNotReadDeletedEntries() {
    // given
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
  }

  @Test
  void shouldInvalidateAllEntries() throws Exception {
    // given
    recordDataWriter.wrap(new UnsafeBuffer("000".getBytes(StandardCharsets.UTF_8)));
    final var firstRecord = copyRecord(journal.append(recordDataWriter));

    journal.append(recordDataWriter);
    journal.append(recordDataWriter);

    // when
    journal.deleteAfter(firstRecord.index());
    recordDataWriter.wrap(new UnsafeBuffer("111".getBytes(StandardCharsets.UTF_8)));
    final var secondRecord = copyRecord(journal.append(recordDataWriter));

    journal.close();
    journal = openJournal();

    // then
    final var reader = journal.openReader();
    assertThat(reader.next()).isEqualTo(firstRecord);
    assertThat(reader.next()).isEqualTo(secondRecord);
    assertThat(reader.hasNext()).isFalse();
  }

  @Test
  void shouldDetectCorruptedEntry() throws Exception {
    // given
    recordDataWriter.wrap(new UnsafeBuffer("000".getBytes(StandardCharsets.UTF_8)));
    journal.append(recordDataWriter);
    final var secondRecord = copyRecord(journal.append(recordDataWriter));
    final File dataFile = Objects.requireNonNull(directory.toFile().listFiles())[0];
    final File log = Objects.requireNonNull(dataFile.listFiles())[0];

    // when
    journal.close();
    assertThat(LogCorrupter.corruptRecord(log, secondRecord.index())).isTrue();

    // then
    metaStore.storeLastFlushedIndex(secondRecord.index());
    assertThatThrownBy(() -> journal = openJournal(b -> b.withMetaStore(metaStore)))
        .isInstanceOf(CorruptedJournalException.class);
  }

  @Test
  void shouldDeletePartiallyWrittenEntry() throws Exception {
    // given
    recordDataWriter.wrap(new UnsafeBuffer("000".getBytes(StandardCharsets.UTF_8)));
    final var firstRecord = copyRecord(journal.append(recordDataWriter));
    final var secondRecord = copyRecord(journal.append(recordDataWriter));
    final File dataFile = Objects.requireNonNull(directory.toFile().listFiles())[0];
    final File log = Objects.requireNonNull(dataFile.listFiles())[0];

    // when
    journal.close();
    assertThat(LogCorrupter.corruptRecord(log, secondRecord.index())).isTrue();
    metaStore.storeLastFlushedIndex(firstRecord.index());
    journal = openJournal();
    recordDataWriter.wrap(new UnsafeBuffer("111".getBytes(StandardCharsets.UTF_8)));
    final var lastRecord = journal.append(recordDataWriter);
    final var reader = journal.openReader();

    // then
    assertThat(reader.next()).isEqualTo(firstRecord);
    assertThat(reader.next()).isEqualTo(lastRecord);
  }

  @Test
  void shouldUpdateMetastoreAfterFlush() {
    journal = openJournal();
    journal.append(1, recordDataWriter);
    final var lastWrittenIndex = journal.append(2, recordDataWriter).index();

    // when
    journal.flush();

    // then
    assertThat(metaStore.loadLastFlushedIndex()).isEqualTo(lastWrittenIndex);
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
            .withMaxSegmentSize(1024 * 1024) // speeds up certain tests, e.g. shouldCompact
            .withMetaStore(metaStore)
            .withJournalIndexDensity(5);
    option.accept(builder);

    return builder.build();
  }
}
