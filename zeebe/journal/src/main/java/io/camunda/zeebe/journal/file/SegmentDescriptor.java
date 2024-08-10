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

import io.camunda.zeebe.journal.util.ChecksumGenerator;
import java.nio.ByteBuffer;
import java.util.Objects;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
final class SegmentDescriptor {
  // current descriptor version containing: header, metadata, header and descriptor. descriptor
  // contains lastIndex and lastPosition. Version 2 with sbeSchemaVersion 1 does not contain
  // lastIndex and lastPosition.
  static final byte CUR_VERSION = 2;
  // First version containing: header and descriptor. We remove support for VERSION 1 as this was
  // introduced long ago.
  static final byte META_VERSION = 2;
  static final int VERSION_LENGTH = Byte.BYTES;
  private static final Logger LOG = LoggerFactory.getLogger(SegmentDescriptor.class);
  private final DescriptorMetadataEncoder metadataEncoder = new DescriptorMetadataEncoder();
  private final SegmentDescriptorEncoder segmentDescriptorEncoder = new SegmentDescriptorEncoder();
  private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
  private final ChecksumGenerator checksumGen = new ChecksumGenerator();
  // version in the header. Increment this version if there is non-backward compatible changes in
  // the serialization format.
  private final byte version;

  // version of sbe schema. The version will be incremented if fields are added or removed from the
  // sbe schema of descriptor. As long as these changes are backward compatible, there is no need to
  // increment `CUR_VERSION`
  private final int actingSchemaVersion;
  private final long id;
  private final long index;
  private final int maxSegmentSize;
  private final int encodedLength;
  // index of the last entry in this segment. Can be 0 if not set, even if an entry exists.
  private long lastIndex;
  // position of the last entry in this segment. Can be 0 if not set, even if an entry exists.
  private int lastPosition;

  SegmentDescriptor(
      final byte version,
      final int actingSchemaVersion,
      final long id,
      final long index,
      final int maxSegmentSize,
      final long lastIndex,
      final int lastPosition,
      final int encodedLength) {
    this.version = version;
    this.actingSchemaVersion = actingSchemaVersion;
    this.id = id;
    this.index = index;
    this.maxSegmentSize = maxSegmentSize;
    this.lastIndex = lastIndex;
    this.lastPosition = lastPosition;
    this.encodedLength = encodedLength;
  }

  /**
   * The number of bytes taken by the descriptor in the segment is dependent on the encoding used.
   * The length represents this number of bytes.
   *
   * @return the number of bytes taken by this descriptor in the segment.
   */
  int length() {
    return encodedLength;
  }

  /**
   * The number of bytes required to write a descriptor to the segment.
   *
   * @return the encoding length
   */
  static int getEncodingLength() {
    return VERSION_LENGTH
        + MessageHeaderEncoder.ENCODED_LENGTH * 2
        + DescriptorMetadataEncoder.BLOCK_LENGTH
        + SegmentDescriptorEncoder.BLOCK_LENGTH;
  }

  /**
   * Returns a descriptor builder.
   *
   * @return The descriptor builder.
   */
  static Builder builder() {
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
  long id() {
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
  long index() {
    return index;
  }

  /**
   * Returns the maximum allowed number of bytes in the segment.
   *
   * @return The maximum allowed number of bytes in the segment.
   */
  int maxSegmentSize() {
    return maxSegmentSize;
  }

  /**
   * Copies the descriptor to a new buffer. The number of bytes written will be equal to {@link
   * SegmentDescriptor#getEncodingLength()}
   */
  SegmentDescriptor copyTo(final ByteBuffer buffer) {
    final MutableDirectBuffer directBuffer = new UnsafeBuffer();
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
        .maxSegmentSize(maxSegmentSize)
        .lastIndex(lastIndex)
        .lastPosition(lastPosition);

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
    return "SegmentDescriptor{"
        + "id="
        + id
        + ", index="
        + index
        + ", maxSegmentSize="
        + maxSegmentSize
        + ", lastIndex="
        + lastIndex
        + ", lastPosition="
        + lastPosition
        + '}';
  }

  int lastPosition() {
    return lastPosition;
  }

  void setLastPosition(final int lastPosition) {
    this.lastPosition = lastPosition;
  }

  void setLastIndex(final long lastIndex) {
    this.lastIndex = lastIndex;
  }

  void updateIfCurrentVersion(final ByteBuffer buffer) {
    if (version >= CUR_VERSION
        && actingSchemaVersion == segmentDescriptorEncoder.sbeSchemaVersion()) {
      copyTo(buffer);
    } else {
      // Do not overwrite the descriptor for older versions. The new version has a higher length and
      // will overwrite the first entry.
      LOG.trace(
          "Segment descriptor version is {}, and sbe schema version is {}, which is different from current version {}, and current sbe schema version {}."
              + "Skipping update to the descriptor.",
          version,
          actingSchemaVersion,
          CUR_VERSION,
          segmentDescriptorEncoder.sbeSchemaVersion());
    }
  }

  long lastIndex() {
    return lastIndex;
  }

  /** Segment descriptor builder. */
  static final class Builder {

    private long id;
    private long index;
    private int maxSegmentSize;

    /**
     * Sets the segment identifier.
     *
     * @param id The segment identifier.
     * @return The segment descriptor builder.
     */
    Builder withId(final long id) {
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
    Builder withIndex(final long index) {
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
    Builder withMaxSegmentSize(final int maxSegmentSize) {
      checkArgument(maxSegmentSize > 0, "maxSegmentSize must be positive");
      this.maxSegmentSize = maxSegmentSize;
      return this;
    }

    /**
     * Builds the segment descriptor.
     *
     * @return The built segment descriptor.
     */
    SegmentDescriptor build() {
      return new SegmentDescriptor(
          CUR_VERSION,
          SegmentDescriptorEncoder.SCHEMA_VERSION,
          id,
          index,
          maxSegmentSize,
          0,
          0,
          getEncodingLength());
    }
  }
}
