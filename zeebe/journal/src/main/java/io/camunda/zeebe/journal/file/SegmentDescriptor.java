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

/**
 * The segment descriptor stores the metadata of a single segment {@link Segment} of a {@link
 * SegmentedJournal}. The descriptor is stored in the first bytes of the segment. The number of
 * bytes requires for the descriptor is dependent on the encoding used. The first byte of the
 * segment contains the version of the descriptor. The subsequent bytes contains the following
 * fields encoded using the SBE schema.
 *
 * <p>{@code maxSegmentSize}
 *
 * @param version
 *     <p>version in the header. Increment this version if there is non-backward compatible changes
 *     in the serialization format.
 * @param actingSchemaVersion
 *     <p>version of sbe schema. The version will be incremented if fields are added or removed from
 *     the sbe schema of descriptor. As long as these changes are backward compatible, there is no
 *     need to increment `CUR_VERSION`
 * @param id
 *     <p>(64-bit signed integer) - A unique segment identifier. This is a monotonically increasing
 *     number within each journal. Segments with in-sequence identifiers should contain in-sequence
 *     indices.
 * @param index
 *     <p>(64-bit signed integer) - The effective first index of the segment. This indicates the
 *     index at which the first entry should be written to the segment. Indices are monotonically
 *     increasing thereafter.
 * @param maxSegmentSize
 *     <p>(32-bit unsigned integer) - The maximum number of bytes allowed in the segment.
 * @param lastIndex
 *     <p>(64-bit unsigned integer) index of the last entry in this segment. Can be 0 if not set,
 *     even if an entry exists
 * @param lastPosition
 *     <p>(32-bit unsigned integer) position of the last entry in this segment. Can be 0 if not set,
 *     even if an entry exists
 */
record SegmentDescriptor(
    byte version,
    byte actingSchemaVersion,
    long id,
    long index,
    int maxSegmentSize,
    long lastIndex,
    int lastPosition,
    short encodingLength) {

  public SegmentDescriptor withUpdatedIndices(final long lastIndex, final int lastPosition) {
    return new SegmentDescriptor(
        version,
        actingSchemaVersion,
        id,
        index,
        maxSegmentSize,
        lastIndex,
        lastPosition,
        encodingLength);
  }

  public SegmentDescriptor reset() {
    return new SegmentDescriptor(
        version, actingSchemaVersion, id, index, maxSegmentSize, 0, 0, encodingLength);
  }

  /**
   * Returns a descriptor builder.
   *
   * @return The descriptor builder.
   */
  static Builder builder() {
    return new Builder();
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
          SegmentDescriptorSerializer.CUR_VERSION,
          (byte) SegmentDescriptorEncoder.SCHEMA_VERSION,
          id,
          index,
          maxSegmentSize,
          0,
          0,
          SegmentDescriptorSerializer.currentEncodingLength());
    }
  }
}
