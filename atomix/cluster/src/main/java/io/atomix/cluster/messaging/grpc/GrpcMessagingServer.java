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
import io.atomix.cluster.messaging.grpc.codec.CompressingInterceptor;
import io.atomix.cluster.messaging.grpc.codec.SnappyCodec;
import io.atomix.cluster.messaging.grpc.service.Service;
import io.atomix.utils.Managed;
import io.atomix.utils.net.Address;
import io.grpc.CompressorRegistry;
import io.grpc.Server;
import io.grpc.protobuf.services.ChannelzService;
import io.grpc.protobuf.services.ProtoReflectionService;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class GrpcMessagingServer implements Managed<Server>, AutoCloseable {
  private static final Logger LOGGER = LoggerFactory.getLogger(GrpcMessagingServer.class);

  private final MessagingConfig config;
  private final List<Address> bindAddresses;
  private final Service messagingService;
  private final TransportFactory transportFactory;
  private final AtomicBoolean isRunning = new AtomicBoolean();

  private Server server;

  GrpcMessagingServer(
      final MessagingConfig config,
      final List<Address> bindAddresses,
      final Service messagingService,
      final TransportFactory transportFactory) {
    this.config = config;
    this.bindAddresses = bindAddresses;
    this.messagingService = messagingService;
    this.transportFactory = transportFactory;
  }

  @Override
  public CompletableFuture<Server> start() {
    if (!isRunning.compareAndSet(false, true)) {
      return CompletableFuture.completedFuture(server);
    }

    try {
      server = buildServer();
    } catch (final Exception e) {
      isRunning.set(false);
      server = null;
      return CompletableFuture.failedFuture(e);
    }

    try {
      server.start();
      LOGGER.debug(
          "Started gRPC messaging server on {} (TLS enabled: {}, compression: {})",
          server.getListenSockets(),
          config.isTlsEnabled(),
          config.getCompressionAlgorithm());
      return CompletableFuture.completedFuture(server);
    } catch (final Exception e) {
      return CompletableFuture.failedFuture(e);
    }
  }

  @Override
  public boolean isRunning() {
    return isRunning.getOpaque();
  }

  @Override
  public CompletableFuture<Void> stop() {
    if (!isRunning.compareAndSet(true, false)) {
      return CompletableFuture.completedFuture(null);
    }

    final var oldServer = server;
    server.shutdownNow();
    server = null;

    try {
      oldServer.awaitTermination(config.getShutdownTimeout().toNanos(), TimeUnit.NANOSECONDS);
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      return CompletableFuture.failedFuture(e);
    }

    return CompletableFuture.completedFuture(null);
  }

  @Override
  public void close() throws Exception {
    stop().get(config.getShutdownTimeout().toNanos(), TimeUnit.NANOSECONDS);
  }

  private Server buildServer() {
    final var firstAddress = bindAddresses.get(0);
    final var builder =
        transportFactory
            .createServerBuilder(firstAddress)
            .compressorRegistry(CompressorRegistry.getDefaultInstance())
            .addService(messagingService)
            // debug services
            .addService(ChannelzService.newInstance(100))
            .addService(ProtoReflectionService.newInstance());

    if (config.isTlsEnabled()) {
      builder.useTransportSecurity(config.getCertificateChain(), config.getPrivateKey());
    }

    switch (config.getCompressionAlgorithm()) {
      case GZIP -> builder.intercept(new CompressingInterceptor("gzip"));
      case SNAPPY -> {
        CompressorRegistry.getDefaultInstance().register(new SnappyCodec());
        builder.intercept(new CompressingInterceptor("snappy"));
      }
      default -> {}
    }

    return builder.build();
  }
}
