package io.zeebe.logstreams.storage.memory;

import io.zeebe.logstreams.impl.service.LogStreamService;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LogStreamBuilder;
import io.zeebe.util.ByteValue;
import io.zeebe.util.sched.channel.ActorConditions;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import java.nio.ByteBuffer;
import java.util.Objects;
import org.agrona.concurrent.status.AtomicLongPosition;

public class MemoryLogStreamBuilder extends LogStreamBuilder<MemoryLogStreamBuilder> {
  private static final int DEFAULT_CAPACITY = 512 * 1024 * 1024;
  private int capacity = DEFAULT_CAPACITY;
  private ByteBuffer buffer;

  public MemoryLogStreamBuilder withCapacity(final int capacity) {
    this.capacity = capacity;
    return this;
  }

  public MemoryLogStreamBuilder withBuffer(final ByteBuffer buffer) {
    this.buffer = buffer;
    return this;
  }

  @Override
  public ActorFuture<LogStream> buildAsync() {
    return CompletableActorFuture.completed(build());
  }

  @Override
  public LogStreamService build() {
    if (logStorage == null) {
      if (buffer == null && capacity > 0) {
        buffer = ByteBuffer.allocate(capacity);
      }

      Objects.requireNonNull(buffer, "Backing buffer cannot be null");
      logStorage = new MemoryLogStorage(buffer);
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
