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
