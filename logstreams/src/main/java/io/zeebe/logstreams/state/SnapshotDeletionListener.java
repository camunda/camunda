package io.zeebe.logstreams.state;

/** Implementations will be called when snapshots have been purged. */
@FunctionalInterface
public interface SnapshotDeletionListener {

  /**
   * Called by a {@link SnapshotStorage} whenever snapshots are removed.
   *
   * @param storage the underlying snapshot storage
   * @param snapshot the oldest remaining snapshot
   */
  void onSnapshotDeleted(SnapshotStorage storage, Snapshot snapshot);
}
