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

import io.camunda.zeebe.journal.CorruptedJournalException;
import io.camunda.zeebe.journal.util.ChecksumGenerator;
import java.nio.ByteBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SegmentDescriptorTest {

  @Test
  void shouldWriteAndReadDescriptor() {
    // given
    final SegmentDescriptor descriptor =
        SegmentDescriptor.builder().withId(2).withIndex(100).withMaxSegmentSize(1024).build();
    descriptor.setLastPosition(100);
    descriptor.setLastIndex(10);
    final ByteBuffer buffer = ByteBuffer.allocate(SegmentDescriptor.getEncodingLength());
    descriptor.copyTo(buffer);

    // when
    final SegmentDescriptor descriptorRead = readDescriptor(buffer);

    // then
    assertThat(descriptorRead).isEqualTo(descriptor);
    assertThat(descriptorRead.id()).isEqualTo(2);
    assertThat(descriptorRead.index()).isEqualTo(100);
    assertThat(descriptorRead.maxSegmentSize()).isEqualTo(1024);
    assertThat(descriptorRead.lastIndex()).isEqualTo(10);
    assertThat(descriptorRead.lastPosition()).isEqualTo(100);
    assertThat(descriptorRead.length()).isEqualTo(SegmentDescriptor.getEncodingLength());
  }

  @ParameterizedTest
  @ValueSource(ints = {-1, 0, 100})
  void shouldValidateDescriptorHeader(final int invalidVersion) {
    // given
    final ByteBuffer buffer = ByteBuffer.allocate(SegmentDescriptor.getEncodingLength());
    buffer.put(0, (byte) invalidVersion);

    // when/then
    assertThatThrownBy(() -> readDescriptor(buffer)).isInstanceOf(UnknownVersionException.class);
  }

  @Test
  void shouldFailWithChecksumMismatch() {
    // given
    final ByteBuffer buffer = ByteBuffer.allocate(SegmentDescriptor.getEncodingLength());
    final SegmentDescriptor descriptor =
        SegmentDescriptor.builder().withId(123).withIndex(456).withMaxSegmentSize(789).build();
    descriptor.copyTo(buffer);

    // when
    final byte corruptByte = (byte) ~buffer.get(buffer.capacity() - 1);
    buffer.put(buffer.capacity() - 1, corruptByte);

    // then
    assertThatThrownBy(() -> readDescriptor(buffer)).isInstanceOf(CorruptedJournalException.class);
  }

  @Test
  void shouldReadV2WithSbeVersion1Message() {
    // given
    final SegmentDescriptor descriptor =
        SegmentDescriptor.builder().withId(2).withIndex(100).withMaxSegmentSize(1024).build();

    final ByteBuffer buffer = ByteBuffer.allocate(SegmentDescriptor.getEncodingLength());
    final UnsafeBuffer directBuffer = new UnsafeBuffer();
    directBuffer.wrap(buffer);
    writeDescriptorV2(descriptor, directBuffer, buffer);

    // when
    final SegmentDescriptor descriptorRead = readDescriptor(buffer);

    // then
    assertThat(descriptorRead).isEqualTo(descriptor);
    assertThat(descriptorRead.id()).isEqualTo(2);
    assertThat(descriptorRead.index()).isEqualTo(100);
    assertThat(descriptorRead.maxSegmentSize()).isEqualTo(1024);
    assertThat(descriptorRead.lastIndex()).isZero();
    assertThat(descriptorRead.lastPosition()).isZero();
  }

  @Test
  void shouldNotOverwriteV2WithSbeVersion1Descriptor() {
    // given
    final SegmentDescriptor descriptor =
        SegmentDescriptor.builder().withId(2).withIndex(100).withMaxSegmentSize(1024).build();

    final ByteBuffer buffer = ByteBuffer.allocate(SegmentDescriptor.getEncodingLength());
    final UnsafeBuffer directBuffer = new UnsafeBuffer();
    directBuffer.wrap(buffer);
    writeDescriptorV2(descriptor, directBuffer, buffer);

    // when
    final SegmentDescriptor descriptorToUpdate = readDescriptor(buffer);
    descriptorToUpdate.setLastIndex(100);
    descriptorToUpdate.setLastPosition(100);
    descriptorToUpdate.updateIfCurrentVersion(buffer);

    final SegmentDescriptor descriptorRead = readDescriptor(buffer);

    // then
    assertThat(descriptorRead).isEqualTo(descriptor);
    assertThat(descriptorRead.id()).isEqualTo(2);
    assertThat(descriptorRead.index()).isEqualTo(100);
    assertThat(descriptorRead.maxSegmentSize()).isEqualTo(1024);
    assertThat(descriptorRead.lastIndex()).isZero();
    assertThat(descriptorRead.lastPosition()).isZero();
  }

  private SegmentDescriptor readDescriptor(final ByteBuffer buffer) {
    return new SegmentDescriptorReader().readFrom(buffer);
  }

  private void writeDescriptorV2(
      final SegmentDescriptor descriptor,
      final UnsafeBuffer directBuffer,
      final ByteBuffer buffer) {

    final byte version = 2;
    directBuffer.putByte(0, version);

    // descriptor header
    final int versionLength = Byte.BYTES;
    final int descHeaderOffset =
        versionLength
            + MessageHeaderEncoder.ENCODED_LENGTH
            + DescriptorMetadataEncoder.BLOCK_LENGTH;

    final SegmentDescriptorEncoder segmentDescriptorEncoder = new SegmentDescriptorEncoder();
    final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();

    headerEncoder
        .wrap(directBuffer, descHeaderOffset)
        .blockLength(20)
        .templateId(SegmentDescriptorEncoder.TEMPLATE_ID)
        .schemaId(SegmentDescriptorEncoder.SCHEMA_ID)
        .version(1);

    segmentDescriptorEncoder.wrap(
        directBuffer, descHeaderOffset + MessageHeaderEncoder.ENCODED_LENGTH);
    segmentDescriptorEncoder
        .id(descriptor.id())
        .index(descriptor.index())
        .maxSegmentSize(descriptor.maxSegmentSize());

    final long checksum =
        new ChecksumGenerator()
            .compute(buffer, descHeaderOffset, headerEncoder.encodedLength() + 20);
    final DescriptorMetadataEncoder metadataEncoder = new DescriptorMetadataEncoder();

    metadataEncoder
        .wrapAndApplyHeader(directBuffer, versionLength, headerEncoder)
        .checksum(checksum);
  }
}
