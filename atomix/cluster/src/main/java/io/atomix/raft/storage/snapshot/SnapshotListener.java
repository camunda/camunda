package io.atomix.raft.storage.snapshot;

/** Represents a lifecycle listener for a {@link SnapshotStore}'s {@link Snapshot} collection. */
public interface SnapshotListener {

  /**
   * Called whenever a new snapshot is committed to the snapshot store.
   *
   * @param snapshot the newly committed snapshot
   * @param store the snapshot store to which it was added
   */
  void onNewSnapshot(Snapshot snapshot, SnapshotStore store);

  /**
   * Called whenever a committed snapshot has been deleted.
   *
   * @param snapshot the snapshot that was deleted
   * @param store the store from which it was removed
   */
  void onSnapshotDeletion(Snapshot snapshot, SnapshotStore store);
}
