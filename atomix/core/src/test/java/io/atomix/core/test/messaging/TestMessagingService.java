/*
 * Copyright 2017-present Open Networking Foundation
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atomix.core.test.messaging;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.Sets;
import io.atomix.cluster.messaging.ManagedMessagingService;
import io.atomix.cluster.messaging.MessagingException.NoRemoteHandler;
import io.atomix.cluster.messaging.MessagingService;
import io.atomix.utils.concurrent.ComposableFuture;
import io.atomix.utils.concurrent.Futures;
import io.atomix.utils.net.Address;
import java.net.ConnectException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

/** Test messaging service. */
public class TestMessagingService implements ManagedMessagingService {
  private final Address address;
  private final Map<Address, TestMessagingService> services;
  private final Map<String, BiFunction<Address, byte[], CompletableFuture<byte[]>>> handlers =
      new ConcurrentHashMap<>();
  private final AtomicBoolean started = new AtomicBoolean();
  private final Set<Address> partitions = Sets.newConcurrentHashSet();

  public TestMessagingService(
      final Address address, final Map<Address, TestMessagingService> services) {
    this.address = address;
    this.services = services;
  }

  /** Returns the test service for the given address or {@code null} if none has been created. */
  private TestMessagingService getService(final Address address) {
    checkNotNull(address);
    return services.get(address);
  }

  /** Returns the given handler for the given address. */
  private BiFunction<Address, byte[], CompletableFuture<byte[]>> getHandler(
      final Address address, final String type) {
    final TestMessagingService service = getService(address);
    if (service == null) {
      return (e, p) -> Futures.exceptionalFuture(new NoRemoteHandler());
    }
    final BiFunction<Address, byte[], CompletableFuture<byte[]>> handler =
        service.handlers.get(checkNotNull(type));
    if (handler == null) {
      return (e, p) -> Futures.exceptionalFuture(new NoRemoteHandler());
    }
    return handler;
  }

  /** Partitions the node from the given address. */
  void partition(final Address address) {
    partitions.add(address);
  }

  /** Heals the partition from the given address. */
  void heal(final Address address) {
    partitions.remove(address);
  }

  /**
   * Returns a boolean indicating whether this node is partitioned from the given address.
   *
   * @param address the address to check
   * @return whether this node is partitioned from the given address
   */
  boolean isPartitioned(final Address address) {
    return partitions.contains(address);
  }

  @Override
  public Address address() {
    return address;
  }

  @Override
  public CompletableFuture<Void> sendAsync(
      final Address address, final String type, final byte[] payload, final boolean keepAlive) {
    if (isPartitioned(address)) {
      return Futures.exceptionalFuture(new ConnectException());
    }
    return getHandler(address, type).apply(this.address, payload).thenApply(v -> null);
  }

  @Override
  public CompletableFuture<byte[]> sendAndReceive(
      final Address address, final String type, final byte[] payload, final boolean keepAlive) {
    if (isPartitioned(address)) {
      return Futures.exceptionalFuture(new ConnectException());
    }
    return getHandler(address, type).apply(this.address, payload);
  }

  @Override
  public CompletableFuture<byte[]> sendAndReceive(
      final Address address,
      final String type,
      final byte[] payload,
      final boolean keepAlive,
      final Executor executor) {
    if (isPartitioned(address)) {
      return Futures.exceptionalFuture(new ConnectException());
    }
    final ComposableFuture<byte[]> future = new ComposableFuture<>();
    sendAndReceive(address, type, payload).whenCompleteAsync(future, executor);
    return future;
  }

  @Override
  public CompletableFuture<byte[]> sendAndReceive(
      final Address address,
      final String type,
      final byte[] payload,
      final boolean keepAlive,
      final Duration timeout) {
    if (isPartitioned(address)) {
      return Futures.exceptionalFuture(new ConnectException());
    }
    return getHandler(address, type).apply(this.address, payload);
  }

  @Override
  public CompletableFuture<byte[]> sendAndReceive(
      final Address address,
      final String type,
      final byte[] payload,
      final boolean keepAlive,
      final Duration timeout,
      final Executor executor) {
    if (isPartitioned(address)) {
      return Futures.exceptionalFuture(new ConnectException());
    }
    final ComposableFuture<byte[]> future = new ComposableFuture<>();
    sendAndReceive(address, type, payload).whenCompleteAsync(future, executor);
    return future;
  }

  @Override
  public void registerHandler(
      final String type, final BiConsumer<Address, byte[]> handler, final Executor executor) {
    checkNotNull(type);
    checkNotNull(handler);
    handlers.put(
        type,
        (e, p) -> {
          try {
            executor.execute(() -> handler.accept(e, p));
            return CompletableFuture.completedFuture(new byte[0]);
          } catch (final RejectedExecutionException e2) {
            return Futures.exceptionalFuture(e2);
          }
        });
  }

  @Override
  public void registerHandler(
      final String type,
      final BiFunction<Address, byte[], byte[]> handler,
      final Executor executor) {
    checkNotNull(type);
    checkNotNull(handler);
    handlers.put(
        type,
        (e, p) -> {
          final CompletableFuture<byte[]> future = new CompletableFuture<>();
          try {
            executor.execute(() -> future.complete(handler.apply(e, p)));
          } catch (final RejectedExecutionException e2) {
            future.completeExceptionally(e2);
          }
          return future;
        });
  }

  @Override
  public void registerHandler(
      final String type, final BiFunction<Address, byte[], CompletableFuture<byte[]>> handler) {
    checkNotNull(type);
    checkNotNull(handler);
    handlers.put(type, handler);
  }

  @Override
  public void unregisterHandler(final String type) {
    handlers.remove(checkNotNull(type));
  }

  @Override
  public CompletableFuture<MessagingService> start() {
    services.put(address, this);
    started.set(true);
    return CompletableFuture.completedFuture(this);
  }

  @Override
  public boolean isRunning() {
    return started.get();
  }

  @Override
  public CompletableFuture<Void> stop() {
    services.remove(address);
    started.set(false);
    return CompletableFuture.completedFuture(null);
  }
}
