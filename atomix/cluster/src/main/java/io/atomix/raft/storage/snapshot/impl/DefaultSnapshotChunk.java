package io.atomix.raft.storage.snapshot.impl;

import io.atomix.raft.storage.snapshot.SnapshotChunk;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class DefaultSnapshotChunk implements SnapshotChunk {
  private final ByteBuffer id;
  private final ByteBuffer data;

  public DefaultSnapshotChunk(final int offset, final ByteBuffer data) {
    this.id =
        ByteBuffer.allocateDirect(Integer.BYTES).putInt(0, offset).order(ByteOrder.BIG_ENDIAN);
    this.data = data;
  }

  @Override
  public ByteBuffer id() {
    return id;
  }

  @Override
  public ByteBuffer data() {
    return data;
  }
}
