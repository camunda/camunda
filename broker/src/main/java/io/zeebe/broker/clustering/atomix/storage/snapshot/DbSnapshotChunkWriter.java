package io.zeebe.broker.clustering.atomix.storage.snapshot;

import io.atomix.protocols.raft.storage.snapshot.SnapshotChunkWriter;
import java.nio.ByteBuffer;

public class DbSnapshotChunkWriter implements SnapshotChunkWriter {

  @Override
  public void write(final ByteBuffer id, final ByteBuffer data) {

  }

  @Override
  public void close() {

  }
}
