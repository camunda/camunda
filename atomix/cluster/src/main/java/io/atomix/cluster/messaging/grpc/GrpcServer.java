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
        transportFactory
            .createServerBuilder(firstAddress.resolve())
            .compressorRegistry(CompressorRegistry.getDefaultInstance())
            .addService(messagingService);
    return builder.build();
  }
}
