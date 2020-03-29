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
package io.atomix.raft.storage.snapshot.impl;

import com.google.common.base.MoreObjects;
import io.atomix.raft.storage.snapshot.PendingSnapshot;
import io.atomix.raft.storage.snapshot.Snapshot;
import io.atomix.utils.time.WallClockTimestamp;
import java.nio.ByteBuffer;
import java.nio.file.Path;

public class DefaultPendingSnapshot implements PendingSnapshot {

  private final Snapshot snapshot;
  private int nextOffset;

  public DefaultPendingSnapshot(final Snapshot snapshot) {
    this.snapshot = snapshot;
  }

  @Override
  public long index() {
    return snapshot.index();
  }

  @Override
  public long term() {
    return snapshot.term();
  }

  @Override
  public WallClockTimestamp timestamp() {
    return snapshot.timestamp();
  }

  @Override
  public boolean containsChunk(final ByteBuffer chunkId) {
    return chunkId.getInt(0) < nextOffset;
  }

  @Override
  public boolean isExpectedChunk(final ByteBuffer chunkId) {
    return chunkId.getInt(0) == nextOffset;
  }

  @Override
  public void write(final ByteBuffer chunkId, final ByteBuffer chunkData) {
    try (final SnapshotWriter writer = snapshot.openWriter()) {
      writer.write(chunkData.asReadOnlyBuffer());
    }
  }

  @Override
  public void setNextExpected(final ByteBuffer nextChunkId) {
    nextOffset = nextChunkId.getInt(0);
  }

  @Override
  public void commit() {
    snapshot.complete();
  }

  @Override
  public void abort() {
    snapshot.close();
  }

  @Override
  public Path getPath() {
    return snapshot.getPath();
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("snapshot", snapshot)
        .add("nextOffset", nextOffset)
        .toString();
  }
}
