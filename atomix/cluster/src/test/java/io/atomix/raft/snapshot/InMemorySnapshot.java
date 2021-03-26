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
package io.atomix.raft.snapshot;

import io.atomix.utils.time.WallClockTimestamp;
import io.zeebe.snapshots.broker.SnapshotId;
import io.zeebe.snapshots.raft.PersistedSnapshot;
import io.zeebe.snapshots.raft.ReceivedSnapshot;
import io.zeebe.snapshots.raft.SnapshotChunk;
import io.zeebe.snapshots.raft.SnapshotChunkReader;
import io.zeebe.util.StringUtil;
import io.zeebe.util.buffer.BufferUtil;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;
import java.util.zip.CRC32;
import java.util.zip.Checksum;
import org.agrona.concurrent.UnsafeBuffer;

public class InMemorySnapshot implements PersistedSnapshot, ReceivedSnapshot {

  private final TestSnapshotStore testSnapshotStore;
  private final long index;
  private final long term;
  private final WallClockTimestamp timestamp;
  private final String id;
  private final NavigableMap<String, String> chunks = new TreeMap<>();
  private final Checksum checksumCalculator = new CRC32();

  private long checksum;

  InMemorySnapshot(final TestSnapshotStore testSnapshotStore, final String snapshotId) {
    this.testSnapshotStore = testSnapshotStore;
    id = snapshotId;
    final var parts = snapshotId.split("-");
    index = Long.parseLong(parts[0]);
    term = Long.parseLong(parts[1]);
    timestamp = WallClockTimestamp.from(Long.parseLong(parts[2]));
  }

  InMemorySnapshot(
      final TestSnapshotStore testSnapshotStore,
      final long index,
      final long term,
      final WallClockTimestamp timestamp) {
    this.testSnapshotStore = testSnapshotStore;
    this.index = index;
    this.term = term;
    this.timestamp = timestamp;
    id = String.format("%d-%d-%d", index, term, timestamp.unixTimestamp());
  }

  public static InMemorySnapshot newPersistedSnapshot(
      final long index, final long term, final int size, final TestSnapshotStore snapshotStore) {
    final var snapshot = new InMemorySnapshot(snapshotStore, index, term, new WallClockTimestamp());
    for (int i = 0; i < size; i++) {
      snapshot.writeChunks("chunk-" + i, "test".getBytes());
    }
    snapshot.persist();
    return snapshot;
  }

  void writeChunks(final String id, final byte[] chunk) {
    chunks.put(id, StringUtil.fromBytes(chunk));
    checksumCalculator.update(chunk);
  }

  @Override
  public WallClockTimestamp getTimestamp() {
    return timestamp;
  }

  @Override
  public int version() {
    return 1;
  }

  @Override
  public long getIndex() {
    return index;
  }

  @Override
  public long getTerm() {
    return term;
  }

  @Override
  public SnapshotChunkReader newChunkReader() {
    return new SnapshotChunkReader() {
      private NavigableMap<String, String> iterator = chunks;

      @Override
      public void seek(final ByteBuffer id) {
        final var chunkId = BufferUtil.bufferAsString(new UnsafeBuffer(id));
        iterator = chunks.tailMap(chunkId, true);
      }

      @Override
      public ByteBuffer nextId() {
        if (!hasNext()) {
          return null;
        }
        return ByteBuffer.wrap(iterator.firstEntry().getKey().getBytes());
      }

      @Override
      public void close() {
        iterator = null;
      }

      @Override
      public boolean hasNext() {
        return !iterator.isEmpty();
      }

      @Override
      public SnapshotChunk next() {
        final var nextEntry = iterator.firstEntry();
        iterator = chunks.tailMap(nextEntry.getKey(), false);
        return new TestSnapshotChunkImpl(
            id, nextEntry.getKey(), StringUtil.getBytes(nextEntry.getValue()), chunks.size());
      }
    };
  }

  @Override
  public void delete() {}

  @Override
  public Path getPath() {
    return null;
  }

  @Override
  public Path getChecksumPath() {
    return null;
  }

  @Override
  public long getCompactionBound() {
    return index;
  }

  @Override
  public String getId() {
    return id;
  }

  @Override
  public long getChecksum() {
    return checksum;
  }

  @Override
  public void close() {}

  @Override
  public long index() {
    return index;
  }

  @Override
  public ActorFuture<Boolean> apply(final SnapshotChunk chunk) throws IOException {
    chunks.put(chunk.getChunkName(), StringUtil.fromBytes(chunk.getContent()));
    return CompletableActorFuture.completed(true);
  }

  @Override
  public ActorFuture<Void> abort() {
    return CompletableActorFuture.completed(null);
  }

  @Override
  public ActorFuture<PersistedSnapshot> persist() {
    testSnapshotStore.newSnapshot(this);
    checksum = checksumCalculator.getValue();
    return CompletableActorFuture.completed(this);
  }

  @Override
  public SnapshotId snapshotId() {
    return new SnapshotId() {
      @Override
      public long getIndex() {
        return index;
      }

      @Override
      public long getTerm() {
        return term;
      }

      @Override
      public long getProcessedPosition() {
        return 0;
      }

      @Override
      public long getExportedPosition() {
        return 0;
      }

      @Override
      public WallClockTimestamp getTimestamp() {
        return timestamp;
      }

      @Override
      public String getSnapshotIdAsString() {
        return id;
      }
    };
  }

  @Override
  public int hashCode() {
    return Objects.hash(index, term, id);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final InMemorySnapshot that = (InMemorySnapshot) o;
    return index == that.index
        && term == that.term
        && id.equals(that.id)
        && chunks.equals(that.chunks);
  }
}
