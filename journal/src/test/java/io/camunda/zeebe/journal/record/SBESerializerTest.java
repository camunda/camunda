/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.journal.record;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.journal.CorruptedJournalException;
import io.camunda.zeebe.util.Either;
import java.nio.ByteBuffer;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class SBESerializerTest {

  private RecordData record;
  private RecordMetadata metadata;
  private SBESerializer serializer;
  private MutableDirectBuffer writeBuffer;

  @BeforeEach
  void setup() {
    serializer = new SBESerializer();

    final DirectBuffer data = new UnsafeBuffer();
    data.wrap("firstData".getBytes());
    record = new RecordData(1, 2, data);

    metadata = new RecordMetadata(1L, 2);

    final ByteBuffer buffer = ByteBuffer.allocate(256);
    writeBuffer = new UnsafeBuffer(buffer);
  }

  @Test
  void shouldWriteRecord() {
    // given - when
    final var recordWrittenLength = serializer.writeData(record, writeBuffer, 0).get();

    // then
    assertThat(recordWrittenLength).isPositive();
  }

  @Test
  void shouldReadRecord() {
    // given
    serializer.writeData(record, writeBuffer, 0).get();

    // when
    final var recordRead = serializer.readData(writeBuffer, 0);

    // then
    assertThat(recordRead.index()).isEqualTo(record.index());
    assertThat(recordRead.asqn()).isEqualTo(record.asqn());
    assertThat(recordRead.data()).isEqualTo(record.data());
  }

  @Test
  void shouldWriteMetadata() {
    // given - when
    final var metadataLength = serializer.writeMetadata(metadata, writeBuffer, 0);

    // then
    assertThat(metadataLength).isEqualTo(serializer.getMetadataLength());
  }

  @Test
  void shouldReadMetadata() {
    // given
    serializer.writeMetadata(metadata, writeBuffer, 0);

    // when
    final var metadataRead = serializer.readMetadata(writeBuffer, 0);

    // then
    assertThat(metadataRead.checksum()).isEqualTo(metadata.checksum());
    assertThat(metadataRead.length()).isEqualTo(metadata.length());
  }

  @Test
  void shouldThrowCorruptLogExceptionIfMetadataIsInvalid() {
    // given
    serializer.writeMetadata(metadata, writeBuffer, 0);
    writeBuffer.putLong(0, 0);

    // when - then
    assertThatThrownBy(() -> serializer.readMetadata(writeBuffer, 0))
        .isInstanceOf(CorruptedJournalException.class);
  }

  @Test
  void shouldThrowExceptionWhenInvalidRecord() {
    // given
    writeBuffer.putLong(0, 0);

    // when - then
    assertThatThrownBy(() -> serializer.readData(writeBuffer, 0))
        .isInstanceOf(CorruptedJournalException.class);
  }

  @Test
  void shouldReadLengthEqualToActualLength() {
    // given
    final int actualMetadataLength = serializer.writeMetadata(metadata, writeBuffer, 0);

    // when
    final int readMetadataLength = serializer.getMetadataLength(writeBuffer, 0);

    // then
    assertThat(readMetadataLength).isEqualTo(actualMetadataLength);
  }

  @Test
  void shouldWriteRecordAtAnyOffset() {
    // given
    final int offset = 10;

    // when
    serializer.writeData(record, writeBuffer, offset).get();
    final var readData = serializer.readData(writeBuffer, offset);

    // then
    assertThat(readData).isEqualTo(record);
  }

  @Test
  void shouldWriteMetadataAtAnyOffset() {
    // given
    final int offset = 10;

    // when
    serializer.writeMetadata(metadata, writeBuffer, offset);
    final var readMetadata = serializer.readMetadata(writeBuffer, offset);

    // then
    assertThat(readMetadata).isEqualTo(metadata);
  }

  @Test
  void shouldThrowBufferOverFlowWhenNotEnoughSpace() {
    // given
    final int offset = writeBuffer.capacity() - 1;

    // when - then
    assertThat(serializer.writeData(record, writeBuffer, offset)).matches(Either::isLeft);
  }
}
