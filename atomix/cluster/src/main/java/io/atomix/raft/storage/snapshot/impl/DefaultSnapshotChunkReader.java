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

import io.atomix.raft.storage.snapshot.SnapshotChunk;
import io.atomix.raft.storage.snapshot.SnapshotChunkReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.NoSuchElementException;

public class DefaultSnapshotChunkReader implements SnapshotChunkReader {
  private static final int MAX_CHUNK_SIZE = 32 * 1024;
  private final ByteBuffer chunkBuffer =
      ByteBuffer.allocateDirect(MAX_CHUNK_SIZE).order(ByteOrder.BIG_ENDIAN);
  private final ByteBuffer idBuffer =
      ByteBuffer.allocateDirect(Integer.BYTES).order(ByteOrder.BIG_ENDIAN);

  private final SnapshotReader reader;

  public DefaultSnapshotChunkReader(final SnapshotReader reader) {
    this.reader = reader;
  }

  @Override
  public void seek(final ByteBuffer id) {
    final int offset = id == null ? 0 : id.getInt(0);
    reader.skip(offset);
  }

  @Override
  public ByteBuffer nextId() {
    if (hasNext()) {
      return idBuffer.putInt(0, getOffset());
    }

    return null;
  }

  @Override
  public void close() {
    reader.close();
  }

  @Override
  public boolean hasNext() {
    return reader.hasRemaining();
  }

  @Override
  public SnapshotChunk next() {
    if (!hasNext()) {
      throw new NoSuchElementException();
    }

    final int offset = getOffset();
    chunkBuffer.clear().limit(Math.min(reader.remaining(), MAX_CHUNK_SIZE));
    reader.read(chunkBuffer);
    return new DefaultSnapshotChunk(offset, chunkBuffer.slice());
  }

  /** The reader is opened always at a specific offset, skipping the snapshot metadata. */
  private int getOffset() {
    return reader.position() - (DefaultSnapshotDescriptor.BYTES + Integer.BYTES);
  }
}
