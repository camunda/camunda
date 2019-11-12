package io.zeebe.broker.clustering.atomix.storage.snapshot;

import io.atomix.protocols.raft.storage.snapshot.SnapshotChunk;
import io.atomix.protocols.raft.storage.snapshot.SnapshotChunkReader;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.List;

public class DbSnapshotChunkReader implements SnapshotChunkReader {
  private final List<Path> chunks;

  public DbSnapshotChunkReader(final DbSnapshot snapshot) {
    this.chunks = snapshot.getDirectory();
  }

  @Override
  public void seek(final ByteBuffer id) {
    final
  }

  @Override
  public ByteBuffer nextId() {
    return null;
  }

  @Override
  public void close() {

  }

  @Override
  public boolean hasNext() {
    return false;
  }

  @Override
  public SnapshotChunk next() {
    return null;
  }
}
