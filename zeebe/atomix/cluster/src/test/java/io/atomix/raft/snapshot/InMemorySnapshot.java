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

import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.snapshots.ImmutableChecksumsSFV;
import io.camunda.zeebe.snapshots.PersistedSnapshot;
import io.camunda.zeebe.snapshots.ReceivedSnapshot;
import io.camunda.zeebe.snapshots.SnapshotChunk;
import io.camunda.zeebe.snapshots.SnapshotChunkReader;
import io.camunda.zeebe.snapshots.SnapshotId;
import io.camunda.zeebe.snapshots.SnapshotMetadata;
import io.camunda.zeebe.snapshots.SnapshotReservation;
import io.camunda.zeebe.snapshots.impl.SfvChecksumImpl;
import io.camunda.zeebe.util.StringUtil;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.zip.CRC32C;
import java.util.zip.Checksum;
import org.agrona.concurrent.UnsafeBuffer;

public final class InMemorySnapshot implements PersistedSnapshot, ReceivedSnapshot {

  private final TestSnapshotStore testSnapshotStore;
  private final long index;
  private final long term;
  private final String id;
  private final NavigableMap<String, String> chunks = new TreeMap<>();
  private final Checksum checksumCalculator = new CRC32C();
  private final Set<SnapshotReservation> reservations = new CopyOnWriteArraySet<>();

  private ImmutableChecksumsSFV checksum;

  InMemorySnapshot(final TestSnapshotStore testSnapshotStore, final String snapshotId) {
    this.testSnapshotStore = testSnapshotStore;
    id = snapshotId;
    final var parts = snapshotId.split("-");
    index = Long.parseLong(parts[0]);
    term = Long.parseLong(parts[1]);
  }

  InMemorySnapshot(
      final TestSnapshotStore testSnapshotStore,
      final long index,
      final long term,
      final int nodeId) {
    this.testSnapshotStore = testSnapshotStore;
    this.index = index;
    this.term = term;
    id = String.format("%d-%d-%d", index, term, nodeId);
  }

  public static InMemorySnapshot newPersistedSnapshot(
      final int nodeId,
      final long index,
      final long term,
      final int size,
      final TestSnapshotStore snapshotStore) {
    final var snapshot = new InMemorySnapshot(snapshotStore, index, term, nodeId);
    for (int i = 0; i < size; i++) {
      snapshot.writeChunks("chunk-" + i, ("test-" + i).getBytes());
    }
    snapshot.persist();
    return snapshot;
  }

  void writeChunks(final String id, final byte[] chunk) {
    chunks.put(id, StringUtil.fromBytes(chunk));
    checksumCalculator.update(chunk);
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
      public void reset() {
        iterator = chunks;
      }

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
      public void setMaximumChunkSize(final int maximumChunkSize) {}

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
  public ImmutableChecksumsSFV getChecksums() {
    return checksum;
  }

  @Override
  public SnapshotMetadata getMetadata() {
    return null;
  }

  @Override
  public ActorFuture<SnapshotReservation> reserve() {
    final var reservation =
        new SnapshotReservation() {
          @Override
          public ActorFuture<Void> release() {
            reservations.remove(this);
            return CompletableActorFuture.completed(null);
          }
        };

    reservations.add(reservation);
    return CompletableActorFuture.completed(reservation);
  }

  @Override
  public boolean isReserved() {
    return !reservations.isEmpty();
  }

  @Override
  public long index() {
    return index;
  }

  @Override
  public ActorFuture<Void> apply(final SnapshotChunk chunk) {
    chunks.put(chunk.getChunkName(), StringUtil.fromBytes(chunk.getContent()));
    return CompletableActorFuture.completed(null);
  }

  @Override
  public ActorFuture<Void> abort() {
    return CompletableActorFuture.completed(null);
  }

  @Override
  public ActorFuture<PersistedSnapshot> persist() {
    testSnapshotStore.newSnapshot(this);
    checksum = new SfvChecksumImpl();
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

  @Override
  public String toString() {
    return "InMemorySnapshot{"
        + "index="
        + index
        + ", term="
        + term
        + ", id='"
        + id
        + '\''
        + ", checksum="
        + checksum
        + '}';
  }
}
