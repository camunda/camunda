package io.atomix.raft.storage.snapshot;

import java.nio.file.Path;

/**
 * Creates a snapshot store which should store its {@link Snapshot} and {@link PendingSnapshot}
 * instances in the given directory.
 */
@FunctionalInterface
public interface SnapshotStoreFactory {

  /**
   * Creates a snapshot store operating in the given {@code directory}.
   *
   * @param directory the root directory where snapshots should be stored
   * @param partitionName the partition name for this store
   * @return a new {@link SnapshotStore}
   */
  SnapshotStore createSnapshotStore(Path directory, String partitionName);
}
