/*
 * Copyright 2017-present Open Networking Foundation
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
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
package io.camunda.zeebe.journal.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.journal.file.record.CorruptedLogException;
import java.nio.ByteBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

class JournalSegmentDescriptorTest {

  @Test
  void shouldWriteAndReadDescriptor() {
    // given
    JournalSegmentDescriptor descriptor =
        JournalSegmentDescriptor.builder()
            .withId(2)
            .withIndex(100)
            .withMaxSegmentSize(1024)
            .build();
    final ByteBuffer buffer = ByteBuffer.allocate(JournalSegmentDescriptor.getEncodingLength());
    descriptor = descriptor.copyTo(buffer);

    // when
    final JournalSegmentDescriptor descriptorRead = new JournalSegmentDescriptor(buffer);

    // then
    assertThat(descriptorRead).isEqualTo(descriptor);
    assertThat(descriptorRead.id()).isEqualTo(2);
    assertThat(descriptorRead.index()).isEqualTo(100);
    assertThat(descriptorRead.maxSegmentSize()).isEqualTo(1024);
    assertThat(descriptorRead.length()).isEqualTo(JournalSegmentDescriptor.getEncodingLength());
  }

  @Test
  void shouldValidateDescriptorHeader() {
    // given
    final ByteBuffer buffer = ByteBuffer.allocate(JournalSegmentDescriptor.getEncodingLength());

    // when/then
    assertThatThrownBy(() -> new JournalSegmentDescriptor(buffer))
        .isInstanceOf(CorruptedLogException.class);
  }

  @Test
  void shouldReadV1Message() {
    // given
    final ByteBuffer buffer = ByteBuffer.allocate(JournalSegmentDescriptor.getEncodingLength());
    final MutableDirectBuffer directBuffer = new UnsafeBuffer(buffer);
    final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    final SegmentDescriptorEncoder descriptorEncoder = new SegmentDescriptorEncoder();

    directBuffer.putByte(0, (byte) 1);
    descriptorEncoder
        .wrapAndApplyHeader(directBuffer, 1, headerEncoder)
        .id(123)
        .index(456)
        .maxSegmentSize(789);

    // when
    final JournalSegmentDescriptor descriptor = new JournalSegmentDescriptor(buffer);

    // then
    assertThat(descriptor.id()).isEqualTo(123);
    assertThat(descriptor.index()).isEqualTo(456);
    assertThat(descriptor.maxSegmentSize()).isEqualTo(789);
  }

  @Test
  void shouldFailWithChecksumMismatch() {
    // given
    final ByteBuffer buffer = ByteBuffer.allocate(JournalSegmentDescriptor.getEncodingLength());
    final JournalSegmentDescriptor descriptor =
        JournalSegmentDescriptor.builder()
            .withId(123)
            .withIndex(456)
            .withMaxSegmentSize(789)
            .build();
    descriptor.copyTo(buffer);

    // when
    final byte corruptByte = (byte) ~buffer.get(buffer.capacity() - 1);
    buffer.put(buffer.capacity() - 1, corruptByte);

    // then
    assertThatThrownBy(() -> new JournalSegmentDescriptor(buffer))
        .isInstanceOf(CorruptedLogException.class);
  }
}
