/*
 * Copyright 2015-present Open Networking Foundation
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
 * limitations under the License
 */
package io.atomix.raft.storage.snapshot.impl;

import static com.google.common.base.Preconditions.checkNotNull;

import io.atomix.storage.buffer.Buffer;
import io.atomix.storage.buffer.FileBuffer;
import io.atomix.storage.buffer.HeapBuffer;

/**
 * Stores information about a {@link DefaultSnapshot} of the state machine.
 *
 * <p>Snapshot descriptors represent the header of a snapshot file which stores metadata about the
 * snapshot contents. This API provides methods for reading and a builder for writing snapshot
 * headers/descriptors.
 */
public final class DefaultSnapshotDescriptor implements AutoCloseable {

  public static final int BYTES = 64;

  // Current snapshot version.
  private static final int VERSION = 1;

  // The lengths of each field in the header.
  private static final int INDEX_LENGTH = Long.BYTES; // 64-bit signed integer
  private static final int TIMESTAMP_LENGTH = Long.BYTES; // 64-bit signed integer
  private static final int VERSION_LENGTH = Integer.BYTES; // 32-bit signed integer
  private static final int LOCKED_LENGTH = 1; // 8-bit signed byte
  private static final int TERM_LENGTH = Long.BYTES; // 64-bit signed integer

  // The positions of each field in the header.
  private static final int INDEX_POSITION = 0; // 0
  private static final int TIMESTAMP_POSITION = INDEX_POSITION + INDEX_LENGTH; // 8
  private static final int VERSION_POSITION = TIMESTAMP_POSITION + TIMESTAMP_LENGTH; // 16
  private static final int LOCKED_POSITION = VERSION_POSITION + VERSION_LENGTH; // 20
  private static final int TERM_POSITION = LOCKED_POSITION + LOCKED_LENGTH; // 21
  private final long index;
  private final long timestamp;
  private final long term;
  private Buffer buffer;
  private boolean locked;
  private final int version;

  /** @throws NullPointerException if {@code buffer} is null */
  public DefaultSnapshotDescriptor(final Buffer buffer) {
    this.buffer = checkNotNull(buffer, "buffer cannot be null");
    this.index = buffer.readLong();
    this.timestamp = buffer.readLong();
    this.version = buffer.readInt();
    this.locked = buffer.readBoolean();
    this.term = buffer.readLong();
    buffer.skip(BYTES - buffer.position());
  }

  /**
   * Returns a descriptor builder.
   *
   * <p>The descriptor builder will write segment metadata to a {@code 48} byte in-memory buffer.
   *
   * @return The descriptor builder.
   */
  public static Builder builder() {
    return new Builder(HeapBuffer.allocate(BYTES));
  }

  /**
   * Returns a descriptor builder for the given descriptor buffer.
   *
   * @param buffer The descriptor buffer.
   * @return The descriptor builder.
   * @throws NullPointerException if {@code buffer} is null
   */
  public static Builder builder(final Buffer buffer) {
    return new Builder(buffer);
  }

  /**
   * Returns the snapshot index.
   *
   * @return The snapshot index.
   */
  public long index() {
    return index;
  }

  /**
   * Returns the snapshot timestamp.
   *
   * @return The snapshot timestamp.
   */
  public long timestamp() {
    return timestamp;
  }

  /**
   * Returns the snapshot version number.
   *
   * @return the snapshot version number
   */
  public int version() {
    return version;
  }

  public long term() {
    return term;
  }

  /** Locks the segment. */
  public void lock() {
    buffer.flush().writeBoolean(LOCKED_POSITION, true).flush();
    locked = true;
  }

  /** Copies the snapshot to a new buffer. */
  DefaultSnapshotDescriptor copyTo(final Buffer buffer) {
    this.buffer =
        buffer
            .writeLong(index)
            .writeLong(timestamp)
            .writeInt(version)
            .writeBoolean(locked)
            .writeLong(term)
            .skip(BYTES - buffer.position())
            .flush();
    return this;
  }

  @Override
  public void close() {
    buffer.close();
  }

  /** Deletes the descriptor. */
  public void delete() {
    if (buffer instanceof FileBuffer) {
      ((FileBuffer) buffer).delete();
    }
  }

  /**
   * Returns whether the snapshot has been locked by commitment.
   *
   * <p>A snapshot will be locked once it has been fully written.
   *
   * @return Indicates whether the snapshot has been locked.
   */
  public boolean isLocked() {
    return locked;
  }

  /** Snapshot descriptor builder. */
  public static final class Builder {

    private final Buffer buffer;

    private Builder(final Buffer buffer) {
      this.buffer = checkNotNull(buffer, "buffer cannot be null");
    }

    /**
     * Sets the snapshot index.
     *
     * @param index The snapshot index.
     * @return The snapshot builder.
     */
    public Builder withIndex(final long index) {
      buffer.writeLong(INDEX_POSITION, index);
      return this;
    }

    public Builder withTerm(final long term) {
      buffer.writeLong(TERM_POSITION, term);
      return this;
    }

    /**
     * Sets the snapshot timestamp.
     *
     * @param timestamp The snapshot timestamp.
     * @return The snapshot builder.
     */
    public Builder withTimestamp(final long timestamp) {
      buffer.writeLong(TIMESTAMP_POSITION, timestamp);
      return this;
    }

    /**
     * Builds the snapshot descriptor.
     *
     * @return The built snapshot descriptor.
     */
    public DefaultSnapshotDescriptor build() {
      return new DefaultSnapshotDescriptor(buffer.writeInt(VERSION_POSITION, VERSION));
    }
  }
}
