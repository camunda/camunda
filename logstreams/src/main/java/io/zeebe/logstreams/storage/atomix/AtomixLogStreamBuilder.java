package io.zeebe.logstreams.storage.atomix;

import io.atomix.protocols.raft.partition.RaftPartition;
import io.zeebe.logstreams.log.LogStreamBuilder;

public class AtomixLogStreamBuilder extends LogStreamBuilder<AtomixLogStreamBuilder> {
  private RaftPartition partition;

  public AtomixLogStreamBuilder withPartition(final RaftPartition partition) {
    this.partition = partition;
    return this;
  }

  @Override
  protected void applyDefaults() {
    if (partition != null) {
      if (logStorage == null) {
        final var server = new AtomixRaftServer(partition.getServer());
        logStorage = new AtomixLogStorage(server, server, server);
      }

      if (partitionId < 0) {
        partitionId = partition.id().id();
      }

      if (logName == null) {
        logName = partition.name();
      }
    }
  }
}
