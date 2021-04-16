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
package io.zeebe.journal.file;

import static com.google.common.base.Preconditions.checkArgument;

import java.nio.ByteBuffer;
import java.util.Objects;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * The segment descriptor stores the metadata of a single segment {@link JournalSegment} of a {@link
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
public final class JournalSegmentDescriptor {

  private static final byte VERSION = 1;
  private static final int VERSION_LENGTH = Byte.BYTES;

  private final long id;
  private final long index;
  private final int maxSegmentSize;
  private final int encodedLength;

  private final SegmentDescriptorDecoder segmentDescriptorDecoder = new SegmentDescriptorDecoder();
  private final MessageHeaderDecoder messageHeaderDecoder = new MessageHeaderDecoder();
  private final MutableDirectBuffer directBuffer = new UnsafeBuffer();

  private final SegmentDescriptorEncoder segmentDescriptorEncoder = new SegmentDescriptorEncoder();
  private final MessageHeaderEncoder messageHeaderEncoder = new MessageHeaderEncoder();

  public JournalSegmentDescriptor(final ByteBuffer buffer) {
    directBuffer.wrap(buffer);

    /* The first byte of the buffer contains the version at which the descriptor is written.
     Currently we have only one version, so we can ignore it. We can use this version in future
    when needed. Note that we can add fields to SBE schema of the descriptor without changing this version. */
    messageHeaderDecoder.wrap(directBuffer, VERSION_LENGTH);
    segmentDescriptorDecoder.wrap(
        directBuffer,
        VERSION_LENGTH + messageHeaderDecoder.encodedLength(),
        messageHeaderDecoder.blockLength(),
        messageHeaderDecoder.version());

    id = segmentDescriptorDecoder.id();
    index = segmentDescriptorDecoder.index();
    maxSegmentSize = segmentDescriptorDecoder.maxSegmentSize();
    encodedLength =
        VERSION_LENGTH
            + messageHeaderDecoder.encodedLength()
            + segmentDescriptorDecoder.encodedLength();
  }

  private JournalSegmentDescriptor(final long id, final long index, final int maxSegmentSize) {
    this.id = id;
    this.index = index;
    this.maxSegmentSize = maxSegmentSize;
    encodedLength = getEncodingLength();
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
        + MessageHeaderEncoder.ENCODED_LENGTH
        + SegmentDescriptorEncoder.BLOCK_LENGTH;
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
   * JournalSegmentDescriptor#getEncodingLength()}
   */
  JournalSegmentDescriptor copyTo(final ByteBuffer buffer) {
    directBuffer.wrap(buffer);
    directBuffer.putByte(0, VERSION);

    messageHeaderEncoder
        .wrap(directBuffer, VERSION_LENGTH)
        .blockLength(segmentDescriptorEncoder.sbeBlockLength())
        .templateId(segmentDescriptorEncoder.sbeTemplateId())
        .schemaId(segmentDescriptorEncoder.sbeSchemaId())
        .version(segmentDescriptorEncoder.sbeSchemaVersion());

    segmentDescriptorEncoder
        .wrap(directBuffer, VERSION_LENGTH + messageHeaderEncoder.encodedLength())
        .id(id)
        .index(index)
        .maxSegmentSize(maxSegmentSize);

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
    final JournalSegmentDescriptor that = (JournalSegmentDescriptor) o;
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
    public JournalSegmentDescriptor build() {
      return new JournalSegmentDescriptor(id, index, maxSegmentSize);
    }
  }
}
