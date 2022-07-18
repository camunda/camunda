/*
 * Copyright 2017-present Open Networking Foundation
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.zeebe.journal.file;

import static com.google.common.base.Preconditions.checkArgument;

import io.camunda.zeebe.journal.file.record.CorruptedLogException;
import java.nio.ByteBuffer;
import java.util.Objects;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * The segment descriptor stores the metadata of a single segment {@link Segment} of a {@link
 * SegmentedJournal}. The descriptor is stored in the first bytes of the segment. The number of
 * bytes requires for the descriptor is dependent on the encoding used. The first byte of the
 * segment contains the version of the descriptor. The subsequent bytes contains the following
 * fields encoded using the SBE schema.
 *
 * <p>{@code id} (64-bit signed integer) - A unique segment identifier. This is a monotonically
 * increasing number within each journal. Segments with in-sequence identifiers should contain
 * in-sequence indices.
 *
 * <p><{@code index} (64-bit signed integer) - The effective first index of the segment. This
 * indicates the index at which the first entry should be written to the segment. Indices are
 * monotonically increasing thereafter.
 *
 * <p>{@code maxSegmentSize} (32-bit unsigned integer) - The maximum number of bytes allowed in the
 * segment.
 */
public final class SegmentDescriptor {

  private static final int VERSION_LENGTH = Byte.BYTES;
  // current descriptor version containing: header, metadata, header and descriptor
  private static final byte CUR_VERSION = 2;
  // previous descriptor version containing: header and descriptor
  private static final byte NO_META_VERSION = 1;
  // the combined length for each version of the descriptor (starting at version 1)
  // V1 - 29: version byte (1) + header (8) + descriptor (20)
  private static final int[] VERSION_LENGTHS = {29, getEncodingLength()};

  private long id;
  private long index;
  private int maxSegmentSize;
  private int encodedLength;
  private long checksum;

  private final DescriptorMetadataEncoder metadataEncoder = new DescriptorMetadataEncoder();
  private final DescriptorMetadataDecoder metadataDecoder = new DescriptorMetadataDecoder();

  private final SegmentDescriptorDecoder segmentDescriptorDecoder = new SegmentDescriptorDecoder();
  private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
  private final MutableDirectBuffer directBuffer = new UnsafeBuffer();

  private final SegmentDescriptorEncoder segmentDescriptorEncoder = new SegmentDescriptorEncoder();
  private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
  private final ChecksumGenerator checksumGen = new ChecksumGenerator();

  public SegmentDescriptor(final ByteBuffer buffer) {
    directBuffer.wrap(buffer);

    final byte version = directBuffer.getByte(0);
    if (version == CUR_VERSION) {
      readV2Descriptor(directBuffer);
    } else if (version == NO_META_VERSION) {
      readV1Descriptor(directBuffer);
    } else {
      throw new CorruptedLogException(
          String.format(
              "Expected version to be one [%d %d] but read %d instead.",
              NO_META_VERSION, CUR_VERSION, version));
    }
  }

  private SegmentDescriptor(final long id, final long index, final int maxSegmentSize) {
    this.id = id;
    this.index = index;
    this.maxSegmentSize = maxSegmentSize;
    encodedLength = getEncodingLength();
  }

  /** Validates the header's schema and template ids before loading the descriptor's fields. */
  private void readV1Descriptor(final MutableDirectBuffer buffer) {
    validateHeader(
        buffer,
        VERSION_LENGTH,
        segmentDescriptorDecoder.sbeSchemaId(),
        segmentDescriptorDecoder.sbeTemplateId());

    readDescriptor(buffer, VERSION_LENGTH);
  }

  /**
   * Validates the headers' schema and template ids, as well as the metadata's checksum, before
   * loading the descriptor's fields.
   */
  private void readV2Descriptor(final MutableDirectBuffer buffer) {
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
    final int totalLength = readDescriptor(buffer, descHeaderOffset);

    // length of the header + descriptor
    final int descriptorLength = totalLength - descHeaderOffset;
    validateChecksum(buffer, descHeaderOffset, descriptorLength);
  }

  private void validateChecksum(
      final MutableDirectBuffer buffer, final int descHeaderOffset, final int descriptorLength) {
    final ByteBuffer slice = ByteBuffer.allocate(descriptorLength);
    buffer.getBytes(descHeaderOffset, slice, descriptorLength);
    final long computedChecksum = checksumGen.compute(slice, 0, descriptorLength);

    if (computedChecksum != checksum) {
      throw new CorruptedLogException(
          "Descriptor doesn't match checksum (possibly due to corruption).");
    }
  }

  /**
   * Loads the descriptor's fields.
   *
   * @param offset offset where the descriptor's header starts
   * @return offset after reading the descriptor
   */
  private int readDescriptor(final MutableDirectBuffer buffer, final int offset) {
    headerDecoder.wrap(buffer, offset);
    segmentDescriptorDecoder.wrap(
        directBuffer,
        offset + headerDecoder.encodedLength(),
        headerDecoder.blockLength(),
        headerDecoder.version());

    id = segmentDescriptorDecoder.id();
    index = segmentDescriptorDecoder.index();
    maxSegmentSize = segmentDescriptorDecoder.maxSegmentSize();
    encodedLength =
        offset + headerDecoder.encodedLength() + segmentDescriptorDecoder.encodedLength();

    return encodedLength;
  }

  /**
   * Loads the metadata's checksum field.
   *
   * @return offset after the metadata
   */
  private int readChecksum(final MutableDirectBuffer buffer, final int offset) {
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
      final MutableDirectBuffer buffer,
      final int offset,
      final int schemaId,
      final int templateId) {
    headerDecoder.wrap(buffer, offset);

    if (headerDecoder.schemaId() != schemaId || headerDecoder.templateId() != templateId) {
      throw new CorruptedLogException(
          String.format(
              "Cannot read header. Read schema and template ids ('%d' and '%d') don't match expected '%d' and %d'.",
              headerDecoder.schemaId(), headerDecoder.templateId(), schemaId, templateId));
    }
  }

  /**
   * The number of bytes taken by the descriptor in the segment is dependent on the encoding used.
   * The length represents this number of bytes.
   *
   * @return the number of bytes taken by this descriptor in the segment.
   */
  public int length() {
    return encodedLength;
  }

  /**
   * The number of bytes required to write a descriptor to the segment.
   *
   * @return the encoding length
   */
  public static int getEncodingLength() {
    return VERSION_LENGTH
        + MessageHeaderEncoder.ENCODED_LENGTH * 2
        + DescriptorMetadataEncoder.BLOCK_LENGTH
        + SegmentDescriptorEncoder.BLOCK_LENGTH;
  }

  /** The number of bytes required to read and write a descriptor of a given version. */
  public static int getEncodingLengthForVersion(final byte version) {
    if (version == 0 || version > VERSION_LENGTHS.length) {
      throw new UnknownVersionException(
          String.format(
              "Expected version byte to be one [%d %d] but got %d instead.",
              NO_META_VERSION, CUR_VERSION, version));
    }

    return VERSION_LENGTHS[version - 1];
  }

  /**
   * Returns a descriptor builder.
   *
   * @return The descriptor builder.
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Returns the segment identifier.
   *
   * <p>The segment ID is a monotonically increasing number within each log. Segments with
   * in-sequence identifiers should contain in-sequence indexes.
   *
   * @return The segment identifier.
   */
  public long id() {
    return id;
  }

  /**
   * Returns the segment index.
   *
   * <p>The index indicates the index at which the first entry should be written to the segment.
   * Indexes are monotonically increasing thereafter.
   *
   * @return The segment index.
   */
  public long index() {
    return index;
  }

  /**
   * Returns the maximum allowed number of bytes in the segment.
   *
   * @return The maximum allowed number of bytes in the segment.
   */
  public int maxSegmentSize() {
    return maxSegmentSize;
  }

  /**
   * Copies the descriptor to a new buffer. The number of bytes written will be equal to {@link
   * SegmentDescriptor#getEncodingLength()}
   */
  SegmentDescriptor copyTo(final ByteBuffer buffer) {
    directBuffer.wrap(buffer);
    directBuffer.putByte(0, CUR_VERSION);

    // descriptor header
    final int descHeaderOffset =
        VERSION_LENGTH
            + MessageHeaderEncoder.ENCODED_LENGTH
            + DescriptorMetadataEncoder.BLOCK_LENGTH;
    segmentDescriptorEncoder
        .wrapAndApplyHeader(directBuffer, descHeaderOffset, headerEncoder)
        .id(id)
        .index(index)
        .maxSegmentSize(maxSegmentSize);

    final long checksum =
        checksumGen.compute(
            buffer,
            descHeaderOffset,
            headerEncoder.encodedLength() + segmentDescriptorEncoder.encodedLength());
    metadataEncoder
        .wrapAndApplyHeader(directBuffer, VERSION_LENGTH, headerEncoder)
        .checksum(checksum);

    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, index, maxSegmentSize);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final SegmentDescriptor that = (SegmentDescriptor) o;
    return id == that.id && index == that.index && maxSegmentSize == that.maxSegmentSize;
  }

  @Override
  public String toString() {
    return "JournalSegmentDescriptor{"
        + "id="
        + id
        + ", index="
        + index
        + ", maxSegmentSize="
        + maxSegmentSize
        + '}';
  }

  /** Segment descriptor builder. */
  public static final class Builder {

    private long id;
    private long index;
    private int maxSegmentSize;

    /**
     * Sets the segment identifier.
     *
     * @param id The segment identifier.
     * @return The segment descriptor builder.
     */
    public Builder withId(final long id) {
      checkArgument(id > 0, "id must be positive");
      this.id = id;
      return this;
    }

    /**
     * Sets the segment index.
     *
     * @param index The segment starting index.
     * @return The segment descriptor builder.
     */
    public Builder withIndex(final long index) {
      checkArgument(index > 0, "index must be positive");
      this.index = index;
      return this;
    }

    /**
     * Sets maximum number of bytes of the segment.
     *
     * @param maxSegmentSize The maximum count of the segment.
     * @return The segment descriptor builder.
     */
    public Builder withMaxSegmentSize(final int maxSegmentSize) {
      checkArgument(maxSegmentSize > 0, "maxSegmentSize must be positive");
      this.maxSegmentSize = maxSegmentSize;
      return this;
    }

    /**
     * Builds the segment descriptor.
     *
     * @return The built segment descriptor.
     */
    public SegmentDescriptor build() {
      return new SegmentDescriptor(id, index, maxSegmentSize);
    }
  }
}
