/*
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
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
package io.atomix.raft.storage.snapshot;

import io.atomix.utils.time.WallClockTimestamp;
import java.nio.ByteBuffer;
import java.nio.file.Path;

/** Represents a snapshot being written, e.g. during snapshot replication */
public interface PendingSnapshot {

  /** @return the snapshot's index */
  long index();

  /** @return the snapshot's term */
  long term();

  /** @return the snapshot's timestamp */
  WallClockTimestamp timestamp();

  /**
   * Returns true if the chunk identified by the given ID has already been written to the snapshot.
   *
   * @param chunkId the chunk ID to check for
   * @return true if already written, false otherwise
   */
  boolean containsChunk(ByteBuffer chunkId);

  /**
   * Returns true if the chunk identified by chunkId is the expected next chunk, false otherwise.
   *
   * @param chunkId the ID of the new chunk
   * @return true if is expected, false otherwise
   */
  boolean isExpectedChunk(ByteBuffer chunkId);

  /**
   * Writes the chunk data {@code chunkData} as identified by {@code chunkId}.
   *
   * @param chunkId the new chunk ID
   * @param chunkData the new chunk data
   */
  void write(ByteBuffer chunkId, ByteBuffer chunkData);

  /**
   * Sets that the next expected chunk ID is the one with the given {@code nextChunkId}.
   *
   * @param nextChunkId the next expected chunk ID
   */
  void setNextExpected(ByteBuffer nextChunkId);

  /** Marks the snapshot as complete and valid. */
  void commit();

  /**
   * Aborts the pending snapshot, closing all allocated resources and removing any partial files.
   */
  void abort();

  /** @return the working directory of the pending snapshot */
  Path getPath();
}
