package io.zeebe.logstreams.storage.atomix;

import io.atomix.protocols.raft.partition.impl.RaftPartitionServer;
import io.atomix.protocols.raft.storage.log.RaftLogReader;
import io.atomix.protocols.raft.zeebe.ZeebeLogAppender;
import io.atomix.storage.journal.JournalReader.Mode;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class AtomixRaftServer
    implements AtomixLogCompactor, AtomixReaderFactory, AtomixAppenderSupplier {
  private final RaftPartitionServer server;

  public AtomixRaftServer(final RaftPartitionServer server) {
    this.server = server;
  }

  @Override
  public Optional<ZeebeLogAppender> getAppender() {
    return server.getAppender();
  }

  @Override
  public CompletableFuture<Void> compact(final long index) {
    server.setCompactablePosition(index, 0); // todo: remove term
    // actually just delegates compact to the state machine
    return server.snapshot();
  }

  @Override
  public RaftLogReader create(final long index, final Mode mode) {
    return server.openReader(index, mode);
  }
}
