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

  @BeforeEach
  public void setup() {
    serializer = new SBESerializer();

    final DirectBuffer data = new UnsafeBuffer();
    data.wrap("firstData".getBytes());
    record = new PersistableJournalIndexedRecord(1, 2, data);
    recordLength = serializer.getSerializedLength(record);

    metadata = new JournalRecordMetadataImpl(1L, 2);
  }

  @Test
  public void shouldWriteRecord() {
    // given
    final ByteBuffer buffer = ByteBuffer.allocate(recordLength);
    final MutableDirectBuffer directBuffer = new UnsafeBuffer(buffer);

    // when
    final var recordWrittenLength = serializer.write(record, directBuffer);

    // then
    assertThat(recordWrittenLength).isEqualTo(recordLength);
  }

  @Test
  public void shouldReadRecord() {
    // given
    final ByteBuffer buffer = ByteBuffer.allocate(recordLength);
    final MutableDirectBuffer directBuffer = new UnsafeBuffer(buffer);

    serializer.write(record, directBuffer);

    // when
    buffer.position(0);
    final var recordRead = serializer.readRecord(directBuffer);

    // then
    assertThat(recordRead.asqn()).isEqualTo(record.asqn());
    assertThat(recordRead.index()).isEqualTo(record.index());
    assertThat(recordRead.data()).isEqualTo(record.data());
  }

  @Test
  public void shouldWriteMetadata() {
    // given
    final ByteBuffer buffer = ByteBuffer.allocate(recordLength);
    final MutableDirectBuffer directBuffer = new UnsafeBuffer(buffer);

    // when
    final var recordWrittenLength = serializer.write(metadata, directBuffer);

    // then
    assertThat(recordWrittenLength).isEqualTo(serializer.metadataLength());
  }

  @Test
  public void shouldReadMetadata() {
    // given
    final ByteBuffer buffer = ByteBuffer.allocate(recordLength);
    final MutableDirectBuffer directBuffer = new UnsafeBuffer(buffer);

    serializer.write(metadata, directBuffer);

    // when
    buffer.position(0);
    final var recordRead = serializer.readMetadata(directBuffer);

    // then
    assertThat(recordRead.checksum()).isEqualTo(metadata.checksum());
    assertThat(recordRead.length()).isEqualTo(metadata.length());
  }
}
