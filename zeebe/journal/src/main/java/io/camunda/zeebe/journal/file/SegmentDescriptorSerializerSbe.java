/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.journal.file;

import io.camunda.zeebe.journal.CorruptedJournalException;
import io.camunda.zeebe.journal.util.ChecksumGenerator;
import java.nio.ByteBuffer;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class SegmentDescriptorSerializerSbe implements SegmentDescriptorSerializer {

  // VERSIONS
  // First version containing: header and descriptor. We remove support for VERSION 1 as this was
  // introduced long ago.
  private static final byte META_VERSION = 2;
  private static final byte VERSION = 2;
  // LENGTHS
  private static final int VERSION_LENGTH = Byte.BYTES;
  static final int ENCODING_LENGTH =
      VERSION_LENGTH
          + MessageHeaderEncoder.ENCODED_LENGTH * 2
          + DescriptorMetadataEncoder.BLOCK_LENGTH
          + SegmentDescriptorEncoder.BLOCK_LENGTH;

  private static final Logger LOG = LoggerFactory.getLogger(SegmentDescriptorSerializer.class);

  private static final int ACTING_SCHEMA_VERSION = SegmentDescriptorDecoder.SCHEMA_VERSION;

  private static final int DESCRIPTOR_HEADER_OFFSET =
      VERSION_LENGTH + MessageHeaderEncoder.ENCODED_LENGTH + DescriptorMetadataEncoder.BLOCK_LENGTH;

  private final DescriptorMetadataEncoder metadataEncoder = new DescriptorMetadataEncoder();
  private final SegmentDescriptorEncoder segmentDescriptorEncoder = new SegmentDescriptorEncoder();
  private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
  private final ChecksumGenerator checksumGen = new ChecksumGenerator();
  private final DescriptorMetadataDecoder metadataDecoder = new DescriptorMetadataDecoder();
  private final SegmentDescriptorDecoder segmentDescriptorDecoder = new SegmentDescriptorDecoder();
  private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
  private final MutableDirectBuffer directBuffer = new UnsafeBuffer();

  private short encodedLength;
  private long checksum;

  public SegmentDescriptorSerializerSbe() {
    clear();
  }

  @Override
  public byte majorVersion() {
    return VERSION;
  }

  /**
   *
   *
   * <ol>
   *   <li>id, index, maxSegmentSize
   *   <li>add lastIndex, lastPosition
   * </ol>
   */
  @Override
  public byte minorVersion() {
    return SegmentDescriptorEncoder.SCHEMA_VERSION;
  }

  @Override
  public int encodingLength() {
    return ENCODING_LENGTH;
  }

  @Override
  public void writeTo(final SegmentDescriptor segmentDescriptor, final ByteBuffer buffer) {
    clear();
    if (segmentDescriptor.version() >= VERSION
        && segmentDescriptor.actingSchemaVersion() == segmentDescriptorEncoder.sbeSchemaVersion()) {
      writeSegmentDescriptor(segmentDescriptor, buffer);
    } else {
      // Do not overwrite the descriptor for older versions. The new version has a higher length and
      // will overwrite the first entry.
      LOG.trace(
          "Segment descriptor version is {}, and sbe schema version is {}, which is different from current version {}, and current sbe schema version {}."
              + "Skipping update to the descriptor.",
          segmentDescriptor.version(),
          ACTING_SCHEMA_VERSION,
          VERSION,
          segmentDescriptorEncoder.sbeSchemaVersion());
    }
  }

  @Override
  public SegmentDescriptor readFrom(final ByteBuffer buffer) {
    clear();
    directBuffer.wrap(buffer);
    final byte version;
    try {
      version = directBuffer.getByte(0);
      if (version >= META_VERSION && version <= VERSION) {
        return readV2Descriptor(directBuffer, version);
      } else {
        throw new UnknownVersionException(
            String.format(
                "Expected version to be one (%d %d] but read %d instead.",
                META_VERSION, CUR_VERSION, version));
      }
    } catch (final IndexOutOfBoundsException error) {
      // Previously SegmentLoader checked if the file had sufficient size for the descriptor, but
      // it is not checked anymore because the encoded length is not known before reading.
      throw new CorruptedJournalException("Failed to read segment descriptor", error);
    }
  }

  private void writeSegmentDescriptor(
      final SegmentDescriptor segmentDescriptor, final ByteBuffer buffer) {
    directBuffer.wrap(buffer);
    directBuffer.putByte(0, CUR_VERSION);

    // descriptor header
    segmentDescriptorEncoder
        .wrapAndApplyHeader(directBuffer, DESCRIPTOR_HEADER_OFFSET, headerEncoder)
        .id(segmentDescriptor.id())
        .index(segmentDescriptor.index())
        .maxSegmentSize(segmentDescriptor.maxSegmentSize())
        .lastIndex(segmentDescriptor.lastIndex())
        .lastPosition(segmentDescriptor.lastPosition());

    final long checksum =
        checksumGen.compute(
            buffer,
            DESCRIPTOR_HEADER_OFFSET,
            headerEncoder.encodedLength() + segmentDescriptorEncoder.encodedLength());
    metadataEncoder
        .wrapAndApplyHeader(directBuffer, VERSION_LENGTH, headerEncoder)
        .checksum(checksum);
  }

  /**
   * Validates the headers' schema and template ids, as well as the metadata's checksum, before
   * loading the descriptor's fields.
   */
  private SegmentDescriptor readV2Descriptor(final DirectBuffer buffer, final byte version) {
    // validate metadata header
    validateHeader(
        buffer, VERSION_LENGTH, metadataDecoder.sbeSchemaId(), metadataDecoder.sbeTemplateId());
    final int descHeaderOffset = readChecksum(buffer, VERSION_LENGTH);

    // validate descriptor header
    validateHeader(
        buffer,
        descHeaderOffset,
        segmentDescriptorDecoder.sbeSchemaId(),
        segmentDescriptorDecoder.sbeTemplateId());
    final SegmentDescriptor descriptor = readDescriptor(buffer, descHeaderOffset, version);

    // length of the header + descriptor
    final int descriptorLength = encodedLength - descHeaderOffset;
    validateChecksum(buffer, descHeaderOffset, descriptorLength);
    return descriptor;
  }

  private void validateChecksum(
      final DirectBuffer buffer, final int descHeaderOffset, final int descriptorLength) {
    final long computedChecksum = checksumGen.compute(buffer, descHeaderOffset, descriptorLength);

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
  private SegmentDescriptor readDescriptor(
      final DirectBuffer buffer, final int offset, final byte version) {
    headerDecoder.wrap(buffer, offset);
    final var actingSchemaVersion = (byte) headerDecoder.version();
    segmentDescriptorDecoder.wrap(
        directBuffer,
        offset + headerDecoder.encodedLength(),
        headerDecoder.blockLength(),
        actingSchemaVersion);

    final var id = segmentDescriptorDecoder.id();
    final var index = segmentDescriptorDecoder.index();
    final var maxSegmentSize = segmentDescriptorDecoder.maxSegmentSize();
    final var lastIndex = Math.max(0, segmentDescriptorDecoder.lastIndex());
    final var lastPosition = Math.max(0, (int) segmentDescriptorDecoder.lastPosition());
    encodedLength =
        (short) (offset + headerDecoder.encodedLength() + segmentDescriptorDecoder.encodedLength());

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

  private void clear() {
    encodedLength = 0;
    checksum = 0;
  }
}
