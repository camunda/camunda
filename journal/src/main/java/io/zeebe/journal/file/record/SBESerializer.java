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

import io.zeebe.journal.file.MessageHeaderDecoder;
import io.zeebe.journal.file.MessageHeaderEncoder;
import io.zeebe.journal.file.RecordDataDecoder;
import io.zeebe.journal.file.RecordDataEncoder;
import io.zeebe.journal.file.RecordMetadataDecoder;
import io.zeebe.journal.file.RecordMetadataEncoder;
import java.nio.BufferOverflowException;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/** The serializer that writes and reads a journal record according to the SBE schema defined. */
public final class SBESerializer implements JournalRecordSerializer {

  private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
  private final RecordMetadataEncoder metadataEncoder = new RecordMetadataEncoder();
  private final RecordDataEncoder recordEncoder = new RecordDataEncoder();

  private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
  private final RecordMetadataDecoder metadataDecoder = new RecordMetadataDecoder();
  private final RecordDataDecoder recordDecoder = new RecordDataDecoder();

  @Override
  public int writeData(
      final RecordData record, final MutableDirectBuffer buffer, final int offset) {
    if (offset + getSerializedLength(record) > buffer.capacity()) {
      throw new BufferOverflowException();
    }

    headerEncoder
        .wrap(buffer, offset)
        .blockLength(recordEncoder.sbeBlockLength())
        .templateId(recordEncoder.sbeTemplateId())
        .schemaId(recordEncoder.sbeSchemaId())
        .version(recordEncoder.sbeSchemaVersion());

    recordEncoder.wrap(buffer, offset + headerEncoder.encodedLength());

    recordEncoder
        .index(record.index())
        .asqn(record.asqn())
        .putData(record.data(), 0, record.data().capacity());

    return headerEncoder.encodedLength() + recordEncoder.encodedLength();
  }

  @Override
  public int writeMetadata(
      final RecordMetadata metadata, final MutableDirectBuffer buffer, final int offset) {

    headerEncoder
        .wrap(buffer, offset)
        .blockLength(metadataEncoder.sbeBlockLength())
        .templateId(metadataEncoder.sbeTemplateId())
        .schemaId(metadataEncoder.sbeSchemaId())
        .version(metadataEncoder.sbeSchemaVersion());

    metadataEncoder.wrap(buffer, offset + headerEncoder.encodedLength());

    metadataEncoder.checksum(metadata.checksum()).length(metadata.length());

    return headerEncoder.encodedLength() + metadataEncoder.encodedLength();
  }

  @Override
  public int getMetadataLength() {
    return headerEncoder.encodedLength() + metadataEncoder.sbeBlockLength();
  }

  private boolean hasMetadata(final DirectBuffer buffer, final int offset) {
    headerDecoder.wrap(buffer, offset);
    return (headerDecoder.schemaId() == metadataDecoder.sbeSchemaId()
        && headerDecoder.templateId() == metadataDecoder.sbeTemplateId());
  }

  @Override
  public RecordMetadata readMetadata(final DirectBuffer buffer, final int offset) {
    if (!hasMetadata(buffer, offset)) {
      throw new CorruptedLogException("Cannot read metadata. Header does not match.");
    }
    metadataDecoder.wrap(
        buffer,
        offset + headerDecoder.encodedLength(),
        headerDecoder.blockLength(),
        headerDecoder.version());

    return new RecordMetadata(metadataDecoder.checksum(), metadataDecoder.length());
  }

  @Override
  public RecordData readData(final DirectBuffer buffer, final int offset, final int length) {
    headerDecoder.wrap(buffer, offset);
    if (headerDecoder.schemaId() != recordDecoder.sbeSchemaId()
        || headerDecoder.templateId() != recordDecoder.sbeTemplateId()) {
      throw new CorruptedLogException("Cannot read record. Header does not match.");
    }
    recordDecoder.wrap(
        buffer,
        offset + headerDecoder.encodedLength(),
        headerDecoder.blockLength(),
        headerDecoder.version());

    final DirectBuffer data = new UnsafeBuffer();
    recordDecoder.wrapData(data);
    return new RecordData(recordDecoder.index(), recordDecoder.asqn(), data);
  }

  public int getMetadataLength(final DirectBuffer buffer, final int offset) {
    headerDecoder.wrap(buffer, offset);
    return headerDecoder.encodedLength() + headerDecoder.blockLength();
  }

  private int getSerializedLength(final RecordData record) {
    return headerEncoder.encodedLength()
        + recordEncoder.sbeBlockLength()
        + RecordDataEncoder.dataHeaderLength()
        + record.data().capacity();
  }
}
