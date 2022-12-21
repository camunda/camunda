/*
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atomix.cluster.messaging.grpc;

import com.google.common.annotations.VisibleForTesting;
import io.atomix.cluster.ClusterConfig;
import io.atomix.cluster.messaging.MessagingConfig;
import io.atomix.cluster.messaging.grpc.client.ClientRegistry;
import io.atomix.cluster.messaging.grpc.service.Service;
import io.atomix.cluster.messaging.grpc.service.ServiceHandlerRegistry;
import io.atomix.utils.net.Address;

public final class GrpcMessagingFactory {
  private GrpcMessagingFactory() {}

  @VisibleForTesting
  static GrpcManagedServices create(final ClusterConfig config) {
    return create(
        config.getMessagingConfig(),
        config.getNodeConfig().getAddress(),
        config.getClusterId(),
        "default");
  }

  @SuppressWarnings("java:S2095")
  public static GrpcManagedServices create(
      final MessagingConfig config,
      final Address advertisedAddress,
      final String clusterId,
      final String serviceName) {
    final var bindAddresses =
        config.getInterfaces().stream()
            .map(hostname -> Address.from(hostname, config.getPort()))
            .toList();

    final var transportFactory = new NettyTransportFactory(config, advertisedAddress, serviceName);
    final var handlerRegistry = new ServiceHandlerRegistry();
    final var clientRegistry = new ClientRegistry(transportFactory);
    final var messagingService =
        new GrpcMessagingService(
            advertisedAddress, clusterId, bindAddresses, handlerRegistry, clientRegistry);
    final var server =
        new GrpcMessagingServer(
            config, bindAddresses, new Service(clusterId, handlerRegistry), transportFactory);

    final var managedMessagingService =
        new GrpcManagedMessagingService(messagingService, server, config);
    final var managedUnicastService =
        new GrpcManagedUnicastService(messagingService, server, config);

    return new GrpcManagedServices(managedMessagingService, managedUnicastService);
  }
}
