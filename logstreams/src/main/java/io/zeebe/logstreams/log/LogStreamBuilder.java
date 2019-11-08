package io.zeebe.logstreams.log;

import io.zeebe.logstreams.impl.service.LogStreamService;
import io.zeebe.logstreams.spi.LogStorage;
import io.zeebe.servicecontainer.ServiceContainer;
import io.zeebe.util.ByteValue;
import io.zeebe.util.sched.channel.ActorConditions;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import java.util.Objects;
import org.agrona.concurrent.status.AtomicLongPosition;

@SuppressWarnings("unchecked")
public class LogStreamBuilder<SELF extends LogStreamBuilder<SELF>> {
  private static final int MINIMUM_FRAGMENT_SIZE = 32 * 1024;
  protected int maxFragmentSize = 1024 * 1024 * 4;
  protected int partitionId = -1;
  protected ServiceContainer serviceContainer;
  protected LogStorage logStorage;
  protected String logName;

  public SELF withServiceContainer(final ServiceContainer serviceContainer) {
    this.serviceContainer = serviceContainer;
    return (SELF) this;
  }

  public SELF withMaxFragmentSize(final int maxFragmentSize) {
    this.maxFragmentSize = maxFragmentSize;
    return (SELF) this;
  }

  public SELF withLogStorage(final LogStorage logStorage) {
    this.logStorage = logStorage;
    return (SELF) this;
  }

  public SELF withPartitionId(final int partitionId) {
    this.partitionId = partitionId;
    return (SELF) this;
  }

  public SELF withLogName(final String logName) {
    this.logName = logName;
    return (SELF) this;
  }

  public ActorFuture<LogStream> buildAsync() {
    return CompletableActorFuture.completed(build());
  }

  public LogStreamService build() {
    applyDefaults();
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

  protected void applyDefaults() {}

  protected void validate() {
    Objects.requireNonNull(serviceContainer, "Must specify a service container");
    Objects.requireNonNull(logStorage, "Must specify a log storage");

    if (maxFragmentSize < MINIMUM_FRAGMENT_SIZE) {
      throw new IllegalArgumentException(
          String.format(
              "Expected fragment size to be at least '%d', but was '%d'",
              MINIMUM_FRAGMENT_SIZE, maxFragmentSize));
    }

    // TODO: storage should validate the max fragment size to ensure we don't attempt
    // to write blocks that are too large
  }
}
