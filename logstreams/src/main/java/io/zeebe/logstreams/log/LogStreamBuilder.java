package io.zeebe.logstreams.log;

import io.zeebe.logstreams.spi.LogStorage;
import io.zeebe.servicecontainer.ServiceContainer;
import io.zeebe.util.sched.future.ActorFuture;
import java.util.Objects;

@SuppressWarnings("unchecked")
public abstract class LogStreamBuilder<SELF extends LogStreamBuilder<SELF>> {
  private static final int MINIMUM_FRAGMENT_SIZE = 32 * 1024;
  protected ServiceContainer serviceContainer;
  protected int maxFragmentSize = 1024 * 1024 * 4;
  protected LogStorage logStorage;
  protected int partitionId;

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

  public abstract ActorFuture<LogStream> buildAsync();

  public LogStream build() {
    return buildAsync().join();
  }

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
