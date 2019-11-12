package io.zeebe.broker.clustering.atomix.storage.snapshot;

import io.atomix.protocols.raft.storage.snapshot.Snapshot;
import io.atomix.protocols.raft.storage.snapshot.SnapshotChunkReader;
import io.atomix.protocols.raft.storage.snapshot.impl.SnapshotReader;
import io.atomix.protocols.raft.storage.snapshot.impl.SnapshotWriter;
import io.atomix.utils.time.WallClockTimestamp;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import org.agrona.concurrent.UnsafeBuffer;

public class DbSnapshot implements Snapshot {
  // version currently hardcoded, could be used for backwards compatibility
  private static final int VERSION = 1;

  private final Path directory;
  private final long index;
  private final long term;
  private final WallClockTimestamp timestamp;
  private final long position;

  @Override
  public WallClockTimestamp timestamp() {
    return timestamp;
  }

  @Override
  public int version() {
    return VERSION;
  }

  @Override
  public long index() {
    return index;
  }

  @Override
  public long term() {
    return term;
  }

  @Override
  public SnapshotChunkReader newChunkReader() {
    return new DbSnapshotChunkReader(this);
  }

  @Override
  public Snapshot complete() {
    return null;
  }

  @Override
  public void close() {}

  @Override
  public void delete() {}

  @Override
  public SnapshotWriter openWriter() {
    throw new UnsupportedOperationException("Deprecated operation, use DbPendingSnapshot");
  }

  @Override
  public SnapshotReader openReader() {
    throw new UnsupportedOperationException("Deprecated operation, use SnapshotChunkReader");
  }

  @Override
  public void closeReader(final SnapshotReader reader) {
    throw new UnsupportedOperationException("Deprecated operation, use SnapshotChunkReader");
  }

  @Override
  public void closeWriter(final SnapshotWriter writer) {
    throw new UnsupportedOperationException("Deprecated operation, use DbPendingSnapshot");
  }

  public Path getDirectory() {
    return directory;
  }

  Path getChunk(final ByteBuffer id) {
    final var view = new UnsafeBuffer(id);
    final String filename = view.getStringWithoutLengthUtf8(0, id.remaining());
    return directory.resolve(filename);
  }
}
