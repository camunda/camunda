/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.zeebe.journal.file.record;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.util.Either;
import java.nio.ByteBuffer;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SBESerializerTest {

  private RecordData record;
  private RecordMetadata metadata;
  private SBESerializer serializer;
  private MutableDirectBuffer writeBuffer;

  @BeforeEach
  public void setup() {
    serializer = new SBESerializer();

    final DirectBuffer data = new UnsafeBuffer();
    data.wrap("firstData".getBytes());
    record = new RecordData(1, 2, data);

    metadata = new RecordMetadata(1L, 2);

    final ByteBuffer buffer = ByteBuffer.allocate(256);
    writeBuffer = new UnsafeBuffer(buffer);
  }

  @Test
  public void shouldWriteRecord() {
    // given - when
    final var recordWrittenLength = serializer.writeData(record, writeBuffer, 0).get();

    // then
    assertThat(recordWrittenLength).isPositive();
  }

  @Test
  public void shouldReadRecord() {
    // given
    final var length = serializer.writeData(record, writeBuffer, 0).get();

    // when
    final var recordRead = serializer.readData(writeBuffer, 0, length);

    // then
    assertThat(recordRead.index()).isEqualTo(record.index());
    assertThat(recordRead.asqn()).isEqualTo(record.asqn());
    assertThat(recordRead.data()).isEqualTo(record.data());
  }

  @Test
  public void shouldWriteMetadata() {
    // given - when
    final var metadataLength = serializer.writeMetadata(metadata, writeBuffer, 0);

    // then
    assertThat(metadataLength).isEqualTo(serializer.getMetadataLength());
  }

  @Test
  public void shouldReadMetadata() {
    // given
    serializer.writeMetadata(metadata, writeBuffer, 0);

    // when
    final var metadataRead = serializer.readMetadata(writeBuffer, 0);

    // then
    assertThat(metadataRead.checksum()).isEqualTo(metadata.checksum());
    assertThat(metadataRead.length()).isEqualTo(metadata.length());
  }

  @Test
  public void shouldThrowCorruptLogExceptionIfMetadataIsInvalid() {
    // given
    serializer.writeMetadata(metadata, writeBuffer, 0);
    writeBuffer.putLong(0, 0);

    // when - then
    assertThatThrownBy(() -> serializer.readMetadata(writeBuffer, 0))
        .isInstanceOf(CorruptedLogException.class);
  }

  @Test
  public void shouldThrowExceptionWhenInvalidRecord() {
    // given
    writeBuffer.putLong(0, 0);

    // when - then
    assertThatThrownBy(() -> serializer.readData(writeBuffer, 0, 1))
        .isInstanceOf(CorruptedLogException.class);
  }

  @Test
  public void shouldReadLengthEqualToActualLength() {
    // given
    final int actualMetadataLength = serializer.writeMetadata(metadata, writeBuffer, 0);

    // when
    final int readMetadataLength = serializer.getMetadataLength(writeBuffer, 0);

    // then
    assertThat(readMetadataLength).isEqualTo(actualMetadataLength);
  }

  @Test
  public void shouldWriteRecordAtAnyOffset() {
    // given
    final int offset = 10;

    // when
    final var recordWrittenLength = serializer.writeData(record, writeBuffer, offset).get();
    final var readData = serializer.readData(writeBuffer, offset, recordWrittenLength);

    // then
    assertThat(readData).isEqualTo(record);
  }

  @Test
  public void shouldWriteMetadataAtAnyOffset() {
    // given
    final int offset = 10;

    // when
    serializer.writeMetadata(metadata, writeBuffer, offset);
    final var readMetadata = serializer.readMetadata(writeBuffer, offset);

    // then
    assertThat(readMetadata).isEqualTo(metadata);
  }

  @Test
  public void shouldThrowBufferOverFlowWhenNotEnoughSpace() {
    // given
    final int offset = writeBuffer.capacity() - 1;

    // when - then
    assertThat(serializer.writeData(record, writeBuffer, offset)).matches(Either::isLeft);
  }
}
