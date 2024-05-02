/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.journal.record;

import io.camunda.zeebe.journal.CorruptedJournalException;
import io.camunda.zeebe.journal.file.MessageHeaderDecoder;
import io.camunda.zeebe.journal.file.MessageHeaderEncoder;
import io.camunda.zeebe.journal.file.RecordDataDecoder;
import io.camunda.zeebe.journal.file.RecordDataEncoder;
import io.camunda.zeebe.journal.file.RecordMetadataDecoder;
import io.camunda.zeebe.journal.file.RecordMetadataEncoder;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.buffer.BufferWriter;
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
  public Either<BufferOverflowException, Integer> writeData(
      final long index,
      final long asqn,
      final BufferWriter recordDataWriter,
      final MutableDirectBuffer writeBuffer,
      final int offset) {
    return writeDataAtVersion(
        recordEncoder.sbeSchemaVersion(), index, asqn, recordDataWriter, writeBuffer, offset);
  }

  @Override
  public Either<BufferOverflowException, Integer> writeDataAtVersion(
      final int version,
      final long index,
      final long asqn,
      final BufferWriter recordDataWriter,
      final MutableDirectBuffer writeBuffer,
      final int offset) {
    final int entryLength = recordDataWriter.getLength();
    final int serializedLength = getSerializedLength(entryLength);
    if (offset + serializedLength > writeBuffer.capacity()) {
      return Either.left(new BufferOverflowException());
    }

    headerEncoder
        .wrap(writeBuffer, offset)
        .blockLength(recordEncoder.sbeBlockLength())
        .templateId(recordEncoder.sbeTemplateId())
        .schemaId(recordEncoder.sbeSchemaId())
        .version(version);

    recordEncoder.wrap(writeBuffer, offset + headerEncoder.encodedLength());

    recordEncoder.index(index).asqn(asqn);
    final int headerLength = RecordDataEncoder.dataHeaderLength();
    final int limit = recordEncoder.limit();
    recordEncoder.limit(limit + headerLength + entryLength);
    writeBuffer.putInt(limit, entryLength, java.nio.ByteOrder.LITTLE_ENDIAN);
    recordDataWriter.write(writeBuffer, limit + headerLength);

    final var writtenBytes = headerEncoder.encodedLength() + recordEncoder.encodedLength();
    return Either.right(writtenBytes);
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

  @Override
  public RecordMetadata readMetadata(final DirectBuffer buffer, final int offset) {
    if (!hasMetadata(buffer, offset)) {
      throw new CorruptedJournalException("Cannot read metadata. Header does not match.");
    }
    metadataDecoder.wrap(
        buffer,
        offset + headerDecoder.encodedLength(),
        headerDecoder.blockLength(),
        headerDecoder.version());

    return new RecordMetadata(metadataDecoder.checksum(), metadataDecoder.length());
  }

  @Override
  public RecordData readData(final DirectBuffer buffer, final int offset) {
    headerDecoder.wrap(buffer, offset);
    if (headerDecoder.schemaId() != recordDecoder.sbeSchemaId()
        || headerDecoder.templateId() != recordDecoder.sbeTemplateId()) {
      throw new CorruptedJournalException("Cannot read record. Header does not match.");
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

  @Override
  public int getMetadataLength(final DirectBuffer buffer, final int offset) {
    headerDecoder.wrap(buffer, offset);
    return headerDecoder.encodedLength() + headerDecoder.blockLength();
  }

  private boolean hasMetadata(final DirectBuffer buffer, final int offset) {
    headerDecoder.wrap(buffer, offset);
    return (headerDecoder.schemaId() == metadataDecoder.sbeSchemaId()
        && headerDecoder.templateId() == metadataDecoder.sbeTemplateId());
  }

  private int getSerializedLength(final int entryLength) {
    return headerEncoder.encodedLength()
        + recordEncoder.sbeBlockLength()
        + RecordDataEncoder.dataHeaderLength()
        + entryLength;
  }
}
