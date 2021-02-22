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

import io.zeebe.journal.StorageException;
import io.zeebe.journal.file.ChecksumGenerator;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SBESerializerTest {

  private PersistableJournalRecord firstRecord;
  private PersistableJournalRecord secondRecord;
  private final int maxEntrySize = 2048;

  @BeforeEach
  public void setup() {
    final DirectBuffer data = new UnsafeBuffer();
    data.wrap("Test".getBytes());
    firstRecord = new PersistableJournalRecord(1, 2, data);
    secondRecord = new PersistableJournalRecord(2, 4, data);
  }

  @Test
  public void shouldWriteRecord() {
    // given
    final ByteBuffer buffer = ByteBuffer.allocate(firstRecord.getLength());
    final SBESerializer serializer = new SBESerializer(new ChecksumGenerator(), maxEntrySize);

    // when
    final var recordWritten = serializer.write(firstRecord, buffer);

    // then
    assertThat(recordWritten.index()).isEqualTo(1);
    assertThat(recordWritten.asqn()).isEqualTo(2);
  }

  @Test
  public void shouldReadRecord() {
    // given
    final ByteBuffer buffer = ByteBuffer.allocate(firstRecord.getLength());
    final SBESerializer serializer = new SBESerializer(new ChecksumGenerator(), maxEntrySize);
    final var recordWritten = serializer.write(firstRecord, buffer);

    // when
    buffer.position(0);
    final var recordRead = serializer.read(buffer);

    // then
    assertThat(recordRead).isEqualTo(recordWritten);
  }

  @Test
  public void shouldAdvancePositionAfterWritingAndReading() {
    // given
    final ByteBuffer buffer = ByteBuffer.allocate(firstRecord.getLength() * 2);
    final SBESerializer serializer = new SBESerializer(new ChecksumGenerator(), maxEntrySize);
    final var firstRecordWritten = serializer.write(firstRecord, buffer);
    assertThat(firstRecordWritten.index()).isEqualTo(1);

    // when
    final var secondRecordWritten = serializer.write(secondRecord, buffer);
    assertThat(secondRecordWritten.index()).isEqualTo(2);

    // then
    buffer.position(0);
    final var recordRead = serializer.read(buffer);
    assertThat(recordRead).isEqualTo(firstRecordWritten);

    final var recordRead2 = serializer.read(buffer);
    assertThat(recordRead2).isEqualTo(secondRecordWritten);
  }

  @Test
  public void shouldRightAndReadRecordAtAnyPosition() {
    // given
    final int initialOffset = 8;
    final ByteBuffer buffer = ByteBuffer.allocate(initialOffset + firstRecord.getLength() * 2);
    final SBESerializer serializer = new SBESerializer(new ChecksumGenerator(), maxEntrySize);

    // when
    buffer.position(initialOffset); // should start writing from a non-zero start position

    final var firstRecordWritten = serializer.write(firstRecord, buffer);
    assertThat(firstRecordWritten.index()).isEqualTo(1);

    final var secondRecordWritten = serializer.write(secondRecord, buffer);
    assertThat(secondRecordWritten.index()).isEqualTo(2);

    // then
    buffer.position(initialOffset);
    final var recordRead = serializer.read(buffer);
    assertThat(recordRead).isEqualTo(firstRecordWritten);

    final var recordRead2 = serializer.read(buffer);
    assertThat(recordRead2).isEqualTo(secondRecordWritten);
  }

  @Test
  public void shouldRejectRecordBiggerThanMaxEntrySize() {
    // given
    final ByteBuffer buffer = ByteBuffer.allocate(firstRecord.getLength());
    final SBESerializer serializer =
        new SBESerializer(new ChecksumGenerator(), firstRecord.getLength() - 1);

    // when - then
    assertThatThrownBy(() -> serializer.write(firstRecord, buffer))
        .isInstanceOf(StorageException.TooLarge.class);
    assertThat(buffer.position()).isEqualTo(0);
  }

  @Test
  public void shouldNotWriteWhenNotEnoughSpace() {
    // given
    final ByteBuffer buffer = ByteBuffer.allocate(firstRecord.getLength() - 1);
    final SBESerializer serializer = new SBESerializer(new ChecksumGenerator(), maxEntrySize);

    // when - then
    assertThatThrownBy(() -> serializer.write(firstRecord, buffer))
        .isInstanceOf(BufferOverflowException.class);
    assertThat(buffer.position()).isEqualTo(0);
  }
}
