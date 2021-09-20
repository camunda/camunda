package io.atomix.cluster.messaging.grpc;

import io.atomix.cluster.messaging.MessagingConfig;
import io.atomix.cluster.messaging.grpc.service.Service;
import io.atomix.utils.net.Address;
import io.grpc.CompressorRegistry;
import io.grpc.Server;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

final class GrpcServer implements AutoCloseable {
  private final MessagingConfig config;
  private final List<Address> bindAddresses;
  private final Service messagingService;
  private final TransportFactory transportFactory;

  private Server server;

  GrpcServer(
      final MessagingConfig config,
      final List<Address> bindAddresses,
      final Service messagingService,
      final TransportFactory transportFactory) {
    this.config = config;
    this.bindAddresses = bindAddresses;
    this.messagingService = messagingService;
    this.transportFactory = transportFactory;
  }

  void start() throws IOException {
    if (server == null) {
      server = buildServer();
      server.start();
    }
  }

  void stop() throws InterruptedException {
    if (server == null) {
      return;
    }

    server.shutdown();
    server.awaitTermination(config.getShutdownTimeout().toNanos(), TimeUnit.NANOSECONDS);

    server = null;
  }

  @Override
  public void close() throws Exception {
    stop();
  }

  private Server buildServer() {
    final var firstAddress = bindAddresses.get(0);
    final var builder =
        transportFactory.createServerBuilder(firstAddress.resolve())
            .compressorRegistry(CompressorRegistry.getDefaultInstance())
            .addService(messagingService);
    return builder.build();
  }
}
