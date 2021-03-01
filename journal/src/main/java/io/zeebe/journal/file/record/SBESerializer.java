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

public final class SBESerializer implements JournalRecordBufferWriter, JournalRecordBufferReader {

  protected final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
  private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();

  private final JournalIndexedRecordEncoder recordEncoder = new JournalIndexedRecordEncoder();
  private final JournalRecordMetadataEncoder metadataEncoder = new JournalRecordMetadataEncoder();

  private final JournalIndexedRecordDecoder recordDecoder = new JournalIndexedRecordDecoder();
  private final JournalRecordMetadataDecoder metadataDecoder = new JournalRecordMetadataDecoder();

  /* @Override
  public JournalRecord read(final ByteBuffer buffer) {
    final var recordPosition = buffer.position();
    try {
      final var record = new PersistedJournalRecord(buffer);
      buffer.position(recordPosition + record.getMetadataLength());
      final var expectedChecksum = computeChecksum(buffer, record.getIndexedRecordLength());
      if (record.checksum() != expectedChecksum) {
        buffer.position(recordPosition);
        return null; // TODO: throw an exception??
      }
      buffer.position(recordPosition + record.getLength());
      return record;
    } catch (final Exception e) {
      buffer.position(recordPosition);
      return null;
    }
  }*/

  /* @Override
  public JournalRecord write(final JournalRecord record, final ByteBuffer buffer) {
    final int recordStartPosition = buffer.position();
    buffer.mark();

    final PersistableJournalIndexedRecord indexedRecord =
        new PersistableJournalIndexedRecord(record.index(), record.asqn(), record.data());
    final PersistableJournalRecordMetadata recordMetadata = new PersistableJournalRecordMetadata();
    final var recordLength = recordMetadata.getLength() + indexedRecord.getLength();
    if (buffer.position() + recordLength > buffer.limit()) {
      throw new BufferOverflowException();
    }

    if (recordLength > maxEntrySize) {
      // Just reset the buffer. There's no need to zero the bytes since we haven't written anything
      buffer.reset();
      throw new StorageException.TooLarge(
          "Entry size " + recordLength + " exceeds maximum allowed bytes (" + maxEntrySize + ")");
    }

    // Write JournalIndexedRecord
    final MutableDirectBuffer bufferToWrite = new UnsafeBuffer();
    buffer.position(recordStartPosition + recordMetadata.getLength());
    bufferToWrite.wrap(buffer, buffer.position(), indexedRecord.getLength());
    indexedRecord.write(bufferToWrite, 0);

    // Calculate checksum and write JournalRecordMetadata
    buffer.position(recordStartPosition + recordMetadata.getLength());
    final long checksum = computeChecksum(buffer, indexedRecord.getLength());
    recordMetadata.setChecksum(checksum);
    buffer.position(recordStartPosition);
    bufferToWrite.wrap(buffer, recordStartPosition, recordLength);
    recordMetadata.write(bufferToWrite, 0);

    buffer.position(recordStartPosition);
    final var recordWritten = new PersistedJournalRecord(buffer);
    buffer.position(recordStartPosition + recordLength);
    return recordWritten;
  }*/

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
        .putApplicationRecord(record.data(), 0, record.data().capacity());

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
  public int metadataLength() {
    return headerEncoder.encodedLength() + metadataEncoder.sbeBlockLength();
  }

  @Override
  public int getSerializedLength(final JournalIndexedRecord record) {
    return headerEncoder.encodedLength()
        + recordEncoder.sbeBlockLength()
        + JournalIndexedRecordEncoder.applicationRecordHeaderLength()
        + record.data().capacity();
  }

  @Override
  public JournalRecordMetadata readMetadata(final DirectBuffer buffer) {
    return new PersistedJournalRecordMetadata(new UnsafeBuffer(buffer));
  }

  @Override
  public boolean hasMetadata(final DirectBuffer buffer) {
    headerDecoder.wrap(buffer, 0);
    return (headerDecoder.schemaId() == metadataDecoder.sbeSchemaId()
        && headerDecoder.templateId() == metadataDecoder.sbeTemplateId());
  }

  @Override
  public JournalIndexedRecord readRecord(final DirectBuffer buffer) {
    return new PersistedJournalIndexedRecord(buffer);
  }

  public int getMetadataLength(final DirectBuffer buffer) {
    headerDecoder.wrap(buffer, 0);
    return headerDecoder.encodedLength() + headerDecoder.blockLength();
  }

  /*
  @Override
  public void invalidate(final ByteBuffer buffer) {
    final var bufferToInvalidate = buffer.slice();
    while (bufferToInvalidate.position() <= MessageHeaderEncoder.ENCODED_LENGTH) {
      bufferToInvalidate.putInt(0);
    }
  }

  private long computeChecksum(final ByteBuffer buffer, final int length) {
    final var record = buffer.slice();
    record.limit(length);
    return checksumGenerator.compute(record);
  }*/
}
