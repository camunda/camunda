package io.atomix.cluster.messaging.grpc;

import io.atomix.cluster.ClusterConfig;
import io.atomix.cluster.messaging.ManagedMessagingService;
import io.atomix.cluster.messaging.grpc.client.ClientRegistry;
import io.atomix.cluster.messaging.grpc.netty.NettyTransportFactory;
import io.atomix.cluster.messaging.grpc.service.Service;
import io.atomix.cluster.messaging.grpc.service.ServiceHandlerRegistry;
import io.atomix.utils.net.Address;
import io.netty.util.concurrent.DefaultThreadFactory;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public final class GrpcFactory {
  private GrpcFactory() {}

  @SuppressWarnings("java:S2095")
  public static GrpcManagedMessagingService create(final ClusterConfig config) {
    final var messagingConfig = config.getMessagingConfig();
    final var bindAddresses =
        messagingConfig.getInterfaces().stream()
            .map(hostname -> Address.from(hostname, messagingConfig.getPort()))
            .collect(Collectors.toUnmodifiableList());

    final var transportFactory = createTransportFactory(config);
    final var handlerRegistry = new ServiceHandlerRegistry();
    final var clientRegistry = new ClientRegistry(transportFactory);
    final var messagingService =
        new GrpcMessagingService(
            config.getNodeConfig().getAddress(),
            config.getClusterId(),
            bindAddresses,
            handlerRegistry,
            clientRegistry);
    final var server =
        new GrpcServer(
            messagingConfig,
            bindAddresses,
            new Service(config.getClusterId(), handlerRegistry),
            transportFactory);

    return new GrpcManagedMessagingService(messagingService, server);
  }

  private static TransportFactory createTransportFactory(final ClusterConfig config) {
    final var executorService =
        new ThreadPoolExecutor(
            1,
            2,
            60,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(),
            new DefaultThreadFactory("grpc-messaging-service-worker"));
    return new NettyTransportFactory(config.getMessagingConfig(), executorService);
  }
}
