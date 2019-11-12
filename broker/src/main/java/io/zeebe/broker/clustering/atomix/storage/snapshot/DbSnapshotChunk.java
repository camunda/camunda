package io.zeebe.broker.clustering.atomix.storage.snapshot;

import io.atomix.protocols.raft.storage.snapshot.SnapshotChunk;
import java.nio.ByteBuffer;

public class DbSnapshotChunk implements SnapshotChunk {

  @Override
  public ByteBuffer id() {
    return null;
  }

  @Override
  public ByteBuffer data() {
    return null;
  }
}
