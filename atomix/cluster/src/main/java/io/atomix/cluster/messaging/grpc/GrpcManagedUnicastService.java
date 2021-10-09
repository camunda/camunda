package io.atomix.cluster.messaging.grpc;

import io.atomix.cluster.messaging.ManagedUnicastService;
import io.atomix.cluster.messaging.MessagingConfig;
import io.atomix.cluster.messaging.UnicastService;
import io.atomix.utils.net.Address;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

final class GrpcManagedUnicastService implements ManagedUnicastService, AutoCloseable {
  private final GrpcMessagingService messagingService;
  private final GrpcMessagingServer server;
  private final MessagingConfig config;

  public GrpcManagedUnicastService(
      final GrpcMessagingService messagingService,
      final GrpcMessagingServer server,
      final MessagingConfig config) {
    this.messagingService = messagingService;
    this.server = server;
    this.config = config;
  }

  @Override
  public void unicast(final Address address, final String subject, final byte[] message) {
    messagingService.unicast(address, subject, message);
  }

  @Override
  public void addListener(
      final String subject, final BiConsumer<Address, byte[]> listener, final Executor executor) {
    messagingService.addListener(subject, listener, executor);
  }

  @Override
  public void removeListener(final String subject, final BiConsumer<Address, byte[]> listener) {
    messagingService.removeListener(subject, listener);
  }

  @Override
  public CompletableFuture<UnicastService> start() {
    return server.start().thenApply(ok -> this);
  }

  @Override
  public boolean isRunning() {
    return server.isRunning();
  }

  @Override
  public CompletableFuture<Void> stop() {
    return server.stop();
  }

  @Override
  public void close() throws Exception {
    stop().get(config.getShutdownTimeout().toNanos(), TimeUnit.NANOSECONDS);
  }
}
