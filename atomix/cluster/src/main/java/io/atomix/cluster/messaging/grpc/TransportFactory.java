package io.atomix.cluster.messaging.grpc;

import io.camunda.zeebe.util.CloseableSilently;
import io.grpc.ManagedChannelBuilder;
import io.grpc.ServerBuilder;
import java.net.SocketAddress;

@SuppressWarnings("java:S1452")
public interface TransportFactory extends CloseableSilently {
  ServerBuilder<?> createServerBuilder(final SocketAddress address);

  ManagedChannelBuilder<?> createClientBuilder(final SocketAddress address);
}
