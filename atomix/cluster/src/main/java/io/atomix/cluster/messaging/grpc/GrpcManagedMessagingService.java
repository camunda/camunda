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

import io.atomix.cluster.messaging.ManagedMessagingService;
import io.atomix.cluster.messaging.MessagingService;
import io.atomix.utils.net.Address;
import io.camunda.zeebe.util.CloseableSilently;
import java.time.Duration;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import org.agrona.CloseHelper;

public final class GrpcManagedMessagingService
    implements ManagedMessagingService, CloseableSilently {
  private final AtomicBoolean isRunning = new AtomicBoolean();

  private final GrpcMessagingService messagingService;
  private final GrpcServer server;

  public GrpcManagedMessagingService(
      final GrpcMessagingService messagingService, final GrpcServer server) {
    this.messagingService = messagingService;
    this.server = server;
  }

  @Override
  public CompletableFuture<MessagingService> start() {
    if (!isRunning.compareAndSet(false, true)) {
      return CompletableFuture.completedFuture(this);
    }

    try {
      server.start();
      return CompletableFuture.completedFuture(this);
    } catch (final Exception e) {
      return CompletableFuture.failedFuture(e);
    }
  }

  @Override
  public CompletableFuture<Void> stop() {
    if (!isRunning.compareAndSet(true, false)) {
      return CompletableFuture.completedFuture(null);
    }

    final Queue<Throwable> errors = new LinkedList<>();
    CloseHelper.closeAll(errors::add, server, messagingService);
    final var error = errors.poll();
    if (error != null) {
      errors.forEach(error::addSuppressed);
      return CompletableFuture.failedFuture(error);
    }

    return CompletableFuture.completedFuture(null);
  }

  @Override
  public void close() {
    stop().join();
  }

  @Override
  public Address address() {
    return messagingService.address();
  }

  @Override
  public Collection<Address> bindingAddresses() {
    return messagingService.bindingAddresses();
  }

  @Override
  public CompletableFuture<Void> sendAsync(
      final Address address, final String type, final byte[] payload, final boolean keepAlive) {
    if (!isRunning.get()) {
      return CompletableFuture.failedFuture(
          new IllegalStateException("MessagingService is closed."));
    }

    return messagingService.sendAsync(address, type, payload, keepAlive);
  }

  @Override
  public CompletableFuture<byte[]> sendAndReceive(
      final Address address, final String type, final byte[] payload, final boolean keepAlive) {
    if (!isRunning.get()) {
      return CompletableFuture.failedFuture(
          new IllegalStateException("MessagingService is closed."));
    }

    return messagingService.sendAndReceive(address, type, payload, keepAlive);
  }

  @Override
  public CompletableFuture<byte[]> sendAndReceive(
      final Address address,
      final String type,
      final byte[] payload,
      final boolean keepAlive,
      final Executor executor) {
    if (!isRunning.get()) {
      return CompletableFuture.failedFuture(
          new IllegalStateException("MessagingService is closed."));
    }

    return messagingService.sendAndReceive(address, type, payload, keepAlive, executor);
  }

  @Override
  public CompletableFuture<byte[]> sendAndReceive(
      final Address address,
      final String type,
      final byte[] payload,
      final boolean keepAlive,
      final Duration timeout) {
    if (!isRunning.get()) {
      return CompletableFuture.failedFuture(
          new IllegalStateException("MessagingService is closed."));
    }

    return messagingService.sendAndReceive(address, type, payload, keepAlive, timeout);
  }

  @Override
  public CompletableFuture<byte[]> sendAndReceive(
      final Address address,
      final String type,
      final byte[] payload,
      final boolean keepAlive,
      final Duration timeout,
      final Executor executor) {
    if (!isRunning.get()) {
      return CompletableFuture.failedFuture(
          new IllegalStateException("MessagingService is closed."));
    }

    return messagingService.sendAndReceive(address, type, payload, keepAlive, timeout, executor);
  }

  @Override
  public void registerHandler(
      final String type, final BiConsumer<Address, byte[]> handler, final Executor executor) {
    messagingService.registerHandler(type, handler, executor);
  }

  @Override
  public void registerHandler(
      final String type,
      final BiFunction<Address, byte[], byte[]> handler,
      final Executor executor) {
    messagingService.registerHandler(type, handler, executor);
  }

  @Override
  public void registerHandler(
      final String type, final BiFunction<Address, byte[], CompletableFuture<byte[]>> handler) {
    messagingService.registerHandler(type, handler);
  }

  @Override
  public void unregisterHandler(final String type) {
    messagingService.unregisterHandler(type);
  }

  @Override
  public boolean isRunning() {
    return isRunning.get();
  }
}
