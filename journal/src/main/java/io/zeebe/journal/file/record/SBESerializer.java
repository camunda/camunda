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

import io.zeebe.journal.file.JournalIndexedRecordDecoder;
import io.zeebe.journal.file.JournalIndexedRecordEncoder;
import io.zeebe.journal.file.JournalRecordMetadataDecoder;
import io.zeebe.journal.file.JournalRecordMetadataEncoder;
import io.zeebe.journal.file.MessageHeaderDecoder;
import io.zeebe.journal.file.MessageHeaderEncoder;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * The serializer that writes and reads a journal record according to the SBE schema defined. A
 * journal record consists of two parts - a metadata and an indexed record.
 *
 * <p>Metadata consists of the checksum and the length of the record. The record consists of index,
 * asqn and the data.
 */
public final class SBESerializer implements JournalRecordSerializer {

  private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
  private final JournalRecordMetadataEncoder metadataEncoder = new JournalRecordMetadataEncoder();
  private final JournalIndexedRecordEncoder recordEncoder = new JournalIndexedRecordEncoder();

  private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
  private final JournalRecordMetadataDecoder metadataDecoder = new JournalRecordMetadataDecoder();
  private final JournalIndexedRecordDecoder recordDecoder = new JournalIndexedRecordDecoder();

  @Override
  public int write(final JournalIndexedRecord record, final MutableDirectBuffer buffer) {

    headerEncoder
        .wrap(buffer, 0)
        .blockLength(recordEncoder.sbeBlockLength())
        .templateId(recordEncoder.sbeTemplateId())
        .schemaId(recordEncoder.sbeSchemaId())
        .version(recordEncoder.sbeSchemaVersion());

    recordEncoder.wrap(buffer, headerEncoder.encodedLength());

    recordEncoder
        .index(record.index())
        .asqn(record.asqn())
        .putData(record.data(), 0, record.data().capacity());

    return headerEncoder.encodedLength() + recordEncoder.encodedLength();
  }

  @Override
  public int write(final JournalRecordMetadata metadata, final MutableDirectBuffer buffer) {

    headerEncoder
        .wrap(buffer, 0)
        .blockLength(metadataEncoder.sbeBlockLength())
        .templateId(metadataEncoder.sbeTemplateId())
        .schemaId(metadataEncoder.sbeSchemaId())
        .version(metadataEncoder.sbeSchemaVersion());

    metadataEncoder.wrap(buffer, headerEncoder.encodedLength());

    metadataEncoder.checksum(metadata.checksum()).length(metadata.length());

    return headerEncoder.encodedLength() + metadataEncoder.encodedLength();
  }

  @Override
  public int getMetadataLength() {
    return headerEncoder.encodedLength() + metadataEncoder.sbeBlockLength();
  }

  @Override
  public int getSerializedLength(final JournalIndexedRecord record) {
    return headerEncoder.encodedLength()
        + recordEncoder.sbeBlockLength()
        + JournalIndexedRecordEncoder.dataHeaderLength()
        + record.data().capacity();
  }

  @Override
  public boolean hasMetadata(final DirectBuffer buffer) {
    headerDecoder.wrap(buffer, 0);
    return (headerDecoder.schemaId() == metadataDecoder.sbeSchemaId()
        && headerDecoder.templateId() == metadataDecoder.sbeTemplateId());
  }

  @Override
  public JournalRecordMetadata readMetadata(final DirectBuffer buffer) {
    if (!hasMetadata(buffer)) {
      throw new InvalidRecordException("Cannot read buffer. Header does not match.");
    }
    metadataDecoder.wrap(
        buffer,
        headerDecoder.encodedLength(),
        headerDecoder.blockLength(),
        headerDecoder.version());

    return new JournalRecordMetadataImpl(
        metadataDecoder.checksum(), metadataDecoder.length()); // TODO: int <-> long
  }

  @Override
  public JournalIndexedRecord readRecord(final DirectBuffer buffer) {
    headerDecoder.wrap(buffer, 0);
    if (!(headerDecoder.schemaId() == recordDecoder.sbeSchemaId()
        && headerDecoder.templateId() == recordDecoder.sbeTemplateId())) {
      throw new InvalidRecordException("Cannot read buffer. Header does not match.");
    }
    recordDecoder.wrap(
        buffer,
        headerDecoder.encodedLength(),
        headerDecoder.blockLength(),
        headerDecoder.version());

    final DirectBuffer data = new UnsafeBuffer();
    recordDecoder.wrapData(data);
    return new JournalIndexedRecordImpl(recordDecoder.index(), recordDecoder.asqn(), data);
  }

  public int getMetadataLength(final DirectBuffer buffer) {
    headerDecoder.wrap(buffer, 0);
    return headerDecoder.encodedLength() + headerDecoder.blockLength();
  }
}
