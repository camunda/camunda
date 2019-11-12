package io.zeebe.broker.clustering.atomix.storage.snapshot;

import io.atomix.protocols.raft.storage.snapshot.PendingSnapshot;
import io.atomix.protocols.raft.storage.snapshot.Snapshot;
import io.atomix.protocols.raft.storage.snapshot.SnapshotStore;
import io.atomix.utils.time.WallClockTimestamp;
import java.util.NavigableMap;

// TODO: does this class need to be thread-safe?
public class DbSnapshotStore implements SnapshotStore {
  // if thread-safe is a must, then switch to ConcurrentNavigableMap
  private final NavigableMap<Long, DbSnapshot> snapshots;

  @Override
  public Snapshot getSnapshot(final long index) {
    return null;
  }

  @Override
  public void close() {}

  @Override
  public long getCurrentSnapshotIndex() {
    return snapshots.lastKey();
  }

  @Override
  public Snapshot getCurrentSnapshot() {
    return snapshots.lastEntry().getValue();
  }

  @Override
  public void delete() {
    // if thread-safe is a must, then the following must be atomic
    snapshots.values().forEach(DbSnapshot::delete);
    snapshots.clear();
  }

  @Override
  public PendingSnapshot newPendingSnapshot(
      final long index, final long term, final WallClockTimestamp timestamp) {
    return new DbPendingSnapshot(index, term, timestamp);
  }

  @Override
  public Snapshot newSnapshot(
      final long index, final long term, final WallClockTimestamp timestamp) {
    throw new UnsupportedOperationException(
        "Deprecated operation, use PendingSnapshot to create new snapshots");
  }
}
