package io.atomix.raft.storage.snapshot.impl;

import io.atomix.raft.storage.snapshot.SnapshotChunk;
import io.atomix.raft.storage.snapshot.SnapshotChunkReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.NoSuchElementException;

public class DefaultSnapshotChunkReader implements SnapshotChunkReader {
  private static final int MAX_CHUNK_SIZE = 32 * 1024;
  private final ByteBuffer chunkBuffer =
      ByteBuffer.allocateDirect(MAX_CHUNK_SIZE).order(ByteOrder.BIG_ENDIAN);
  private final ByteBuffer idBuffer =
      ByteBuffer.allocateDirect(Integer.BYTES).order(ByteOrder.BIG_ENDIAN);

  private final SnapshotReader reader;

  public DefaultSnapshotChunkReader(final SnapshotReader reader) {
    this.reader = reader;
  }

  @Override
  public void seek(final ByteBuffer id) {
    final int offset = id == null ? 0 : id.getInt(0);
    reader.skip(offset);
  }

  @Override
  public ByteBuffer nextId() {
    if (hasNext()) {
      return idBuffer.putInt(0, getOffset());
    }

    return null;
  }

  @Override
  public void close() {
    reader.close();
  }

  @Override
  public boolean hasNext() {
    return reader.hasRemaining();
  }

  @Override
  public SnapshotChunk next() {
    if (!hasNext()) {
      throw new NoSuchElementException();
    }

    final int offset = getOffset();
    chunkBuffer.clear().limit(Math.min(reader.remaining(), MAX_CHUNK_SIZE));
    reader.read(chunkBuffer);
    return new DefaultSnapshotChunk(offset, chunkBuffer.slice());
  }

  /** The reader is opened always at a specific offset, skipping the snapshot metadata. */
  private int getOffset() {
    return reader.position() - (DefaultSnapshotDescriptor.BYTES + Integer.BYTES);
  }
}
