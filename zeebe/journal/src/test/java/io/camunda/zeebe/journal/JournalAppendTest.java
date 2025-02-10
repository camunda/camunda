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
import static org.assertj.core.api.Assertions.assertThatException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.journal.JournalException.InvalidAsqn;
import io.camunda.zeebe.journal.JournalException.InvalidChecksum;
import io.camunda.zeebe.journal.JournalException.InvalidIndex;
import io.camunda.zeebe.journal.file.SegmentedJournal;
import io.camunda.zeebe.journal.file.SegmentedJournalBuilder;
import io.camunda.zeebe.journal.util.MockJournalMetastore;
import io.camunda.zeebe.journal.util.TestJournalRecord;
import io.camunda.zeebe.test.util.junit.AutoCloseResources;
import io.camunda.zeebe.test.util.junit.AutoCloseResources.AutoCloseResource;
import io.camunda.zeebe.util.buffer.BufferUtil;
import io.camunda.zeebe.util.buffer.DirectBufferWriter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Path;
import java.util.function.Consumer;
import org.agrona.CloseHelper;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@AutoCloseResources
final class JournalAppendTest {
  @TempDir Path directory;
  final JournalMetaStore metaStore = new MockJournalMetastore();
  @AutoCloseResource private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

  private final DirectBufferWriter recordDataWriter = new DirectBufferWriter();
  private final DirectBufferWriter otherRecordDataWriter = new DirectBufferWriter();
  private Journal journal;

  @BeforeEach
  void setup() {
    final byte[] entry = "TestData".getBytes();
    recordDataWriter.wrap(new UnsafeBuffer(entry));

    final var entryOther = "TestData".getBytes();
    otherRecordDataWriter.wrap(new UnsafeBuffer(entryOther));

    journal = openJournal();
  }

  @AfterEach
  void teardown() {
    CloseHelper.quietClose(journal);
  }

  private SegmentedJournal openJournal() {
    return openJournal(b -> {});
  }

  private SegmentedJournal openJournal(final Consumer<SegmentedJournalBuilder> option) {
    final var builder =
        SegmentedJournal.builder(meterRegistry)
            .withDirectory(directory.resolve("data").toFile())
            .withMaxSegmentSize(1024 * 1024) // speeds up certain tests, e.g. shouldCompact
            .withMetaStore(metaStore)
            .withJournalIndexDensity(5);
    option.accept(builder);

    return builder.build();
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
  void shouldAppendJournalRecord() {
    // given
    try (final var receiverJournal =
        SegmentedJournal.builder(meterRegistry)
            .withDirectory(directory.resolve("data-2").toFile())
            .withJournalIndexDensity(5)
            .withMetaStore(new MockJournalMetastore())
            .build()) {
      final var expected = journal.append(10, recordDataWriter);

      // when
      receiverJournal.append(
          expected.checksum(), BufferUtil.bufferAsArray(expected.serializedRecord()));

      // then
      final var reader = receiverJournal.openReader();
      assertThat(reader.hasNext()).isTrue();
      final var actual = reader.next();
      assertThat(expected).isEqualTo(actual);
    }
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
  void shouldNotAppendRecordWithAlreadyAppendedIndex() {
    // given
    final var record = journal.append(recordDataWriter);
    journal.append(recordDataWriter);

    // when/then
    assertThatException()
        .isThrownBy(
            () ->
                journal.append(
                    record.checksum(), BufferUtil.bufferAsArray(record.serializedRecord())))
        .isInstanceOf(InvalidIndex.class);
  }

  @Test
  void shouldNotAppendRecordWithGapInIndex() {
    // given
    try (final var receiverJournal =
        SegmentedJournal.builder(meterRegistry)
            .withDirectory(directory.resolve("data-2").toFile())
            .withJournalIndexDensity(5)
            .withMetaStore(new MockJournalMetastore())
            .build()) {
      journal.append(1, recordDataWriter);
      final var record = journal.append(2, recordDataWriter);

      // when/then
      assertThatException()
          .isThrownBy(
              () ->
                  receiverJournal.append(
                      record.checksum(), BufferUtil.bufferAsArray(record.serializedRecord())))
          .isInstanceOf(InvalidIndex.class);
    }
  }

  @Test
  void shouldNotAppendLastRecord() {
    // given
    final var record = journal.append(recordDataWriter);

    // when/then
    assertThatException()
        .isThrownBy(
            () ->
                journal.append(
                    record.checksum(), BufferUtil.bufferAsArray(record.serializedRecord())))
        .isInstanceOf(InvalidIndex.class);
  }

  @Test
  void shouldAppendRecordWithASQNToIgnore() {
    // given
    final var firstIndex = journal.append(1, recordDataWriter).index();

    // when
    final var appended = journal.append(ASQN_IGNORE, recordDataWriter);

    // then
    assertThat(appended.index()).isEqualTo(firstIndex + 1);
  }

  @Test
  void shouldNotAppendRecordWithInvalidChecksum() {
    // given
    try (final var receiverJournal =
        SegmentedJournal.builder(meterRegistry)
            .withDirectory(directory.resolve("data-2").toFile())
            .withJournalIndexDensity(5)
            .withMetaStore(new MockJournalMetastore())
            .build()) {
      final var record = journal.append(1, recordDataWriter);

      // when
      final var invalidChecksumRecord =
          new TestJournalRecord(
              record.index(), record.asqn(), -1, record.data(), record.serializedRecord());

      // then
      assertThatThrownBy(() -> receiverJournal.append(invalidChecksumRecord))
          .isInstanceOf(InvalidChecksum.class);
    }
  }

  @Test
  void shouldNotAppendRecordWithTooLowASQN() {
    // given
    journal.append(1, recordDataWriter);

    // when/then
    assertThatThrownBy(() -> journal.append(0, recordDataWriter)).isInstanceOf(InvalidAsqn.class);
    assertThatThrownBy(() -> journal.append(1, recordDataWriter)).isInstanceOf(InvalidAsqn.class);
  }

  @Test
  void shouldNotAppendRecordWithTooLowASQNIfPreviousRecordIsIgnoreASQN() {
    // given
    journal.append(1, recordDataWriter);

    // when
    journal.append(ASQN_IGNORE, recordDataWriter);

    // then
    assertThatThrownBy(() -> journal.append(0, recordDataWriter)).isInstanceOf(InvalidAsqn.class);
    assertThatThrownBy(() -> journal.append(1, recordDataWriter)).isInstanceOf(InvalidAsqn.class);
  }

  @Test
  void shouldAppendSerializedJournalRecord() {
    // given
    try (final var receiverJournal =
        SegmentedJournal.builder(meterRegistry)
            .withDirectory(directory.resolve("data-2").toFile())
            .withJournalIndexDensity(5)
            .withMetaStore(new MockJournalMetastore())
            .build()) {
      final var expected = journal.append(10, recordDataWriter);

      // when
      final byte[] serializedRecord = getSerializedBytes(expected);
      receiverJournal.append(expected.checksum(), serializedRecord);

      // then
      final var reader = receiverJournal.openReader();
      assertThat(reader.hasNext()).isTrue();
      final var actual = reader.next();
      assertThat(expected).isEqualTo(actual);
    }
  }

  @Test
  void shouldAppendSerializedJournalRecordReturnedByReader() {
    // given
    try (final var receiverJournal =
        SegmentedJournal.builder(meterRegistry)
            .withDirectory(directory.resolve("data-2").toFile())
            .withJournalIndexDensity(5)
            .withMetaStore(new MockJournalMetastore())
            .build()) {
      final var expected = journal.append(10, recordDataWriter);
      final var recordToWrite = journal.openReader().next();

      // when
      final byte[] serializedRecord = getSerializedBytes(recordToWrite);
      receiverJournal.append(recordToWrite.checksum(), serializedRecord);

      // then
      final var reader = receiverJournal.openReader();
      assertThat(reader.hasNext()).isTrue();
      final var actual = reader.next();
      assertThat(expected).isEqualTo(actual);
    }
  }

  @Test
  void shouldNotAppendSerializedRecordWithAlreadyAppendedIndex() {
    // given
    final var record = journal.append(1, recordDataWriter);
    journal.append(recordDataWriter);

    // when
    final byte[] serializedRecord = getSerializedBytes(record);

    assertThatException()
        .isThrownBy(() -> journal.append(record.checksum(), serializedRecord))
        .describedAs("Should fail to append index 1 after index 2")
        .isInstanceOf(InvalidIndex.class);
  }

  @Test
  void shouldNotAppendSerializedRecordWithGapInIndex() {
    // given
    try (final var receiverJournal =
        SegmentedJournal.builder(meterRegistry)
            .withDirectory(directory.resolve("data-2").toFile())
            .withJournalIndexDensity(5)
            .withMetaStore(new MockJournalMetastore())
            .build()) {
      journal.append(1, recordDataWriter);
      final var record = journal.append(2, recordDataWriter);

      // when/then
      final byte[] serializedRecord = getSerializedBytes(record);
      assertThatException()
          .isThrownBy(() -> receiverJournal.append(record.checksum(), serializedRecord))
          .describedAs("Should fail to append index 2 without index 1")
          .isInstanceOf(InvalidIndex.class);
    }
  }

  @Test
  void shouldNotAppendDuplicateSerializedRecord() {
    // given
    final var record = journal.append(1, recordDataWriter);

    // when/then
    final byte[] serializedRecord = getSerializedBytes(record);
    assertThatException()
        .isThrownBy(() -> journal.append(record.checksum(), serializedRecord))
        .describedAs("Should fail to append index 1 again")
        .isInstanceOf(InvalidIndex.class);
  }

  @Test
  void shouldNotAppendSerializedRecordWithInvalidChecksum() {
    // given
    try (final var receiverJournal =
        SegmentedJournal.builder(meterRegistry)
            .withDirectory(directory.resolve("data-2").toFile())
            .withJournalIndexDensity(5)
            .withMetaStore(new MockJournalMetastore())
            .build()) {
      final var record = journal.append(recordDataWriter);

      // when/then
      final var serializedRecord = getSerializedBytes(record);
      assertThatException()
          .isThrownBy(() -> receiverJournal.append(record.checksum() - 1, serializedRecord))
          .isInstanceOf(InvalidChecksum.class);
    }
  }

  private static byte[] getSerializedBytes(final JournalRecord record) {
    final byte[] serializedRecord = new byte[record.serializedRecord().capacity()];
    record.serializedRecord().getBytes(0, serializedRecord);
    return serializedRecord;
  }
}
