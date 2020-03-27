package io.atomix.raft.storage.snapshot;

import java.nio.ByteBuffer;

/** Represents a serializable snapshot chunk */
public interface SnapshotChunk {

  /**
   * Returns the snapshot chunk identifier; the identifier is implementation dependent and its
   * semantics are dictated by its producer/consumer
   *
   * @return the snapshot chunk ID
   */
  ByteBuffer id();

  /**
   * Returns the snapshot chunk data.
   *
   * @return the snapshot chunk data
   */
  ByteBuffer data();
}
