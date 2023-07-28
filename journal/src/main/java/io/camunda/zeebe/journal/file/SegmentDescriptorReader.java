/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.journal.file;

import io.camunda.zeebe.journal.CorruptedJournalException;
import io.camunda.zeebe.journal.util.ChecksumGenerator;
import java.nio.ByteBuffer;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

final class SegmentDescriptorReader {
  private final DescriptorMetadataDecoder metadataDecoder = new DescriptorMetadataDecoder();
  private final SegmentDescriptorDecoder segmentDescriptorDecoder = new SegmentDescriptorDecoder();
  private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
  private final ChecksumGenerator checksumGen = new ChecksumGenerator();
  private final DirectBuffer directBuffer = new UnsafeBuffer();
  private int actingSchemaVersion = segmentDescriptorDecoder.sbeSchemaVersion();
  private long id;
  private long index;
  private int maxSegmentSize;
  // index of the last entry in this segment. Can be 0 if not set, even if an entry exists.
  private long lastIndex;
  // position of the last entry in this segment. Can be 0 if not set, even if an entry exists.
  private int lastPosition;
  private int encodedLength;
  private long checksum;

  SegmentDescriptor readFrom(final ByteBuffer buffer) {
    directBuffer.wrap(buffer);
    final byte version;
    try {
      version = directBuffer.getByte(0);
      if (version >= SegmentDescriptor.META_VERSION && version <= SegmentDescriptor.CUR_VERSION) {
        readV2Descriptor(directBuffer);
      } else {
        throw new UnknownVersionException(
            String.format(
                "Expected version to be one (%d %d] but read %d instead.",
                SegmentDescriptor.META_VERSION, SegmentDescriptor.CUR_VERSION, version));
      }
    } catch (final IndexOutOfBoundsException error) {
      // Previously SegmentLoader checks if the file has sufficient size for the descriptor. But
      // it
      // is not checked anymore because the encoded length is not known before reading.
      throw new CorruptedJournalException("Failed to read segment descriptor", error);
    }
    return new SegmentDescriptor(
        version,
        actingSchemaVersion,
        id,
        index,
        maxSegmentSize,
        lastIndex,
        lastPosition,
        encodedLength);
  }

  /**
   * Validates the headers' schema and template ids, as well as the metadata's checksum, before
   * loading the descriptor's fields.
   */
  private void readV2Descriptor(final DirectBuffer buffer) {
    // validate metadata header
    validateHeader(
        buffer,
        SegmentDescriptor.VERSION_LENGTH,
        metadataDecoder.sbeSchemaId(),
        metadataDecoder.sbeTemplateId());
    final int descHeaderOffset = readChecksum(buffer, SegmentDescriptor.VERSION_LENGTH);

    // validate descriptor header
    validateHeader(
        buffer,
        descHeaderOffset,
        segmentDescriptorDecoder.sbeSchemaId(),
        segmentDescriptorDecoder.sbeTemplateId());
    final int totalLength = readDescriptor(buffer, descHeaderOffset);

    // length of the header + descriptor
    final int descriptorLength = totalLength - descHeaderOffset;
    validateChecksum(buffer, descHeaderOffset, descriptorLength);
  }

  private void validateChecksum(
      final DirectBuffer buffer, final int descHeaderOffset, final int descriptorLength) {
    final ByteBuffer slice = ByteBuffer.allocate(descriptorLength);
    buffer.getBytes(descHeaderOffset, slice, descriptorLength);
    final long computedChecksum = checksumGen.compute(slice, 0, descriptorLength);

    if (computedChecksum != checksum) {
      throw new CorruptedJournalException(
          "Descriptor doesn't match checksum (possibly due to corruption).");
    }
  }

  /**
   * Loads the descriptor's fields.
   *
   * @param offset offset where the descriptor's header starts
   * @return offset after reading the descriptor
   */
  private int readDescriptor(final DirectBuffer buffer, final int offset) {
    headerDecoder.wrap(buffer, offset);
    actingSchemaVersion = headerDecoder.version();
    segmentDescriptorDecoder.wrap(
        directBuffer,
        offset + headerDecoder.encodedLength(),
        headerDecoder.blockLength(),
        actingSchemaVersion);

    id = segmentDescriptorDecoder.id();
    index = segmentDescriptorDecoder.index();
    maxSegmentSize = segmentDescriptorDecoder.maxSegmentSize();
    lastIndex = Math.max(0, segmentDescriptorDecoder.lastIndex());
    lastPosition = Math.max(0, (int) segmentDescriptorDecoder.lastPosition());
    encodedLength =
        offset + headerDecoder.encodedLength() + segmentDescriptorDecoder.encodedLength();

    return encodedLength;
  }

  /**
   * Loads the metadata's checksum field.
   *
   * @return offset after the metadata
   */
  private int readChecksum(final DirectBuffer buffer, final int offset) {
    headerDecoder.wrap(buffer, offset);
    metadataDecoder.wrap(
        buffer,
        offset + headerDecoder.encodedLength(),
        headerDecoder.blockLength(),
        headerDecoder.version());

    checksum = metadataDecoder.checksum();
    return offset + headerDecoder.encodedLength() + metadataDecoder.encodedLength();
  }

  /** Validate that the header's schema and template ids match the expected ones. */
  private void validateHeader(
      final DirectBuffer buffer, final int offset, final int schemaId, final int templateId) {
    headerDecoder.wrap(buffer, offset);

    if (headerDecoder.schemaId() != schemaId || headerDecoder.templateId() != templateId) {
      throw new CorruptedJournalException(
          String.format(
              "Cannot read header. Read schema and template ids ('%d' and '%d') don't match expected '%d' and %d'.",
              headerDecoder.schemaId(), headerDecoder.templateId(), schemaId, templateId));
    }
  }
}
