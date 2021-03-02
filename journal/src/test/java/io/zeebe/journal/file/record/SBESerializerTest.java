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
package io.zeebe.journal.file.record;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.ByteBuffer;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SBESerializerTest {

  private JournalIndexedRecord record;
  private JournalRecordMetadata metadata;
  private SBESerializer serializer;
  private int recordLength;
  private MutableDirectBuffer writeBuffer;

  @BeforeEach
  public void setup() {
    serializer = new SBESerializer();

    final DirectBuffer data = new UnsafeBuffer();
    data.wrap("firstData".getBytes());
    record = new JournalIndexedRecordImpl(1, 2, data);
    recordLength = serializer.getSerializedLength(record);

    metadata = new JournalRecordMetadataImpl(1L, 2);

    final ByteBuffer buffer = ByteBuffer.allocate(recordLength);
    writeBuffer = new UnsafeBuffer(buffer);
  }

  @Test
  public void shouldWriteRecord() {
    // given - when
    final var recordWrittenLength = serializer.write(record, writeBuffer);

    // then
    assertThat(recordWrittenLength).isEqualTo(recordLength);
  }

  @Test
  public void shouldReadRecord() {
    // given
    serializer.write(record, writeBuffer);

    // when
    final var recordRead = serializer.readRecord(writeBuffer);

    // then
    assertThat(recordRead.asqn()).isEqualTo(record.asqn());
    assertThat(recordRead.index()).isEqualTo(record.index());
    assertThat(recordRead.data()).isEqualTo(record.data());
  }

  @Test
  public void shouldWriteMetadata() {
    // given - when
    final var recordWrittenLength = serializer.write(metadata, writeBuffer);

    // then
    assertThat(recordWrittenLength).isEqualTo(serializer.getMetadataLength());
  }

  @Test
  public void shouldReadMetadata() {
    // given
    serializer.write(metadata, writeBuffer);

    // when
    final var recordRead = serializer.readMetadata(writeBuffer);

    // then
    assertThat(recordRead.checksum()).isEqualTo(metadata.checksum());
    assertThat(recordRead.length()).isEqualTo(metadata.length());
  }

  @Test
  public void shouldCheckMetadata() {
    // given
    writeBuffer.putLong(0, 0);

    // when - then
    assertThat(serializer.hasMetadata(writeBuffer)).isFalse();
  }

  @Test
  public void shouldThrowExceptionWhenInvalidMetadata() {
    // given
    writeBuffer.putLong(0, 0);

    // when - then
    assertThatThrownBy(() -> serializer.readMetadata(writeBuffer))
        .isInstanceOf(InvalidRecordException.class);
  }

  @Test
  public void shouldThrowExceptionWhenInvalidRecord() {
    // given
    writeBuffer.putLong(0, 0);

    // when - then
    assertThatThrownBy(() -> serializer.readRecord(writeBuffer))
        .isInstanceOf(InvalidRecordException.class);
  }

  @Test
  public void shouldEstimatedLengthEqualToActualLength() {
    // given
    final int estimatedRecordLength = serializer.getSerializedLength(record);
    final int estimatedMetadataLength = serializer.getMetadataLength();

    // when
    final int actualRecordLength = serializer.write(record, writeBuffer);
    final int actualMetadataLength = serializer.write(metadata, writeBuffer);

    // then
    assertThat(estimatedRecordLength).isEqualTo(actualRecordLength);
    assertThat(estimatedMetadataLength).isEqualTo(actualMetadataLength);
  }

  @Test
  public void shouldReadLengthEqualToActualLength() {
    // given
    final int actualMetadataLength = serializer.write(metadata, writeBuffer);

    // when
    final int readMetadataLength = serializer.getMetadataLength(writeBuffer);

    // then
    assertThat(readMetadataLength).isEqualTo(actualMetadataLength);
  }
}
