package io.atomix.raft.storage.snapshot;

import java.nio.ByteBuffer;
import java.util.Iterator;

public interface SnapshotChunkReader extends Iterator<SnapshotChunk>, AutoCloseable {

  /**
   * Skips all chunks up to the one with the given ID, inclusively, such that the next chunk would
   * be the chunk right after it (if any). If no ID is given then should not do anything.
   *
   * @param id the chunk ID to seek to; maybe null
   */
  void seek(ByteBuffer id);

  /**
   * Returns the next chunk ID; if {@link #hasNext()} should return false, then this will return
   * null.
   *
   * @return the next chunk ID
   */
  ByteBuffer nextId();

  @Override
  void close();
}
