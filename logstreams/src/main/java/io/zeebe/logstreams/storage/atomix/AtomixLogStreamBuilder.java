package io.zeebe.logstreams.storage.atomix;

import io.atomix.protocols.raft.partition.RaftPartition;
import io.zeebe.logstreams.impl.service.LogStreamService;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LogStreamBuilder;
import io.zeebe.util.ByteValue;
import io.zeebe.util.sched.channel.ActorConditions;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import org.agrona.concurrent.status.AtomicLongPosition;

public class AtomixLogStreamBuilder extends LogStreamBuilder<AtomixLogStreamBuilder> {
  private RaftPartition partition;

  public AtomixLogStreamBuilder withPartition(final RaftPartition partition) {
    this.partition = partition;
    return this;
  }

  @Override
  public ActorFuture<LogStream> buildAsync() {
    return CompletableActorFuture.completed(build());
  }

  @Override
  public LogStreamService build() {
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

    validate();

    return new LogStreamService(
        serviceContainer,
        new ActorConditions(),
        logName,
        partitionId,
        ByteValue.ofBytes(maxFragmentSize),
        new AtomicLongPosition(),
        logStorage);
  }
}
