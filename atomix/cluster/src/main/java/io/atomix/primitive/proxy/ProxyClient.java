/*
 * Copyright 2018-present Open Networking Foundation
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
package io.atomix.primitive.proxy;

import io.atomix.primitive.PrimitiveState;
import io.atomix.primitive.PrimitiveType;
import io.atomix.primitive.operation.PrimitiveOperation;
import io.atomix.primitive.partition.PartitionId;
import io.atomix.primitive.protocol.PrimitiveProtocol;
import io.atomix.utils.concurrent.Futures;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

/** Primitive proxy. */
public interface ProxyClient<S> {

  /**
   * Returns the primitive name.
   *
   * @return the primitive name
   */
  String name();

  /**
   * Returns the client proxy type.
   *
   * @return The client proxy type.
   */
  PrimitiveType type();

  /**
   * Returns the client proxy protocol.
   *
   * @return the client proxy protocol
   */
  PrimitiveProtocol protocol();

  /**
   * Returns the session state.
   *
   * @return The session state.
   */
  PrimitiveState getState();

  /**
   * Returns the collection of all partition proxies.
   *
   * @return the collection of all partition proxies
   */
  Collection<ProxySession<S>> getPartitions();

  /**
   * Returns the collection of all partition IDs.
   *
   * @return the collection of all partition IDs
   */
  Collection<PartitionId> getPartitionIds();

  /**
   * Returns the proxy with the given identifier.
   *
   * @param partitionId the partition with the given identifier
   * @return the partition proxy with the given identifier
   */
  ProxySession<S> getPartition(PartitionId partitionId);

  /**
   * Returns the partition ID for the given key.
   *
   * @param key the key for which to return the partition ID
   * @return the partition ID for the given key
   */
  PartitionId getPartitionId(String key);

  /**
   * Returns the partition ID for the given key.
   *
   * @param key the key for which to return the partition ID
   * @return the partition ID for the given key
   */
  PartitionId getPartitionId(Object key);

  /**
   * Returns the partition proxy for the given key.
   *
   * @param key the key for which to return the partition proxy
   * @return the partition proxy for the given key
   */
  default ProxySession<S> getPartition(final String key) {
    return getPartition(getPartitionId(key));
  }

  /**
   * Returns the partition proxy for the given key.
   *
   * @param key the key for which to return the partition proxy
   * @return the partition proxy for the given key
   */
  default ProxySession<S> getPartition(final Object key) {
    return getPartition(getPartitionId(key));
  }

  /**
   * Registers a client proxy.
   *
   * @param client the client proxy to register
   */
  default void register(final Object client) {
    getPartitions().forEach(partition -> partition.register(client));
  }

  /**
   * Submits an empty operation to all partitions.
   *
   * @param operation the operation identifier
   * @return A completable future to be completed with the operation result. The future is
   *     guaranteed to be completed after all {@link PrimitiveOperation} submission futures that
   *     preceded it.
   * @throws NullPointerException if {@code operation} is null
   */
  default CompletableFuture<Void> acceptAll(final Consumer<S> operation) {
    return Futures.allOf(getPartitions().stream().map(proxy -> proxy.accept(operation)))
        .thenApply(v -> null);
  }

  /**
   * Submits an empty operation to all partitions.
   *
   * @param operation the operation identifier
   * @param <R> the operation result type
   * @return A completable future to be completed with the operation result. The future is
   *     guaranteed to be completed after all {@link PrimitiveOperation} submission futures that
   *     preceded it.
   * @throws NullPointerException if {@code operation} is null
   */
  default <R> CompletableFuture<Stream<R>> applyAll(final Function<S, R> operation) {
    return Futures.allOf(getPartitions().stream().map(proxy -> proxy.apply(operation)));
  }

  /**
   * Submits an empty operation to the given partition.
   *
   * @param partitionId the partition in which to execute the operation
   * @param operation the operation identifier
   * @return A completable future to be completed with the operation result. The future is
   *     guaranteed to be completed after all {@link PrimitiveOperation} submission futures that
   *     preceded it.
   * @throws NullPointerException if {@code operation} is null
   */
  default CompletableFuture<Void> acceptOn(
      final PartitionId partitionId, final Consumer<S> operation) {
    return getPartition(partitionId).accept(operation);
  }

  /**
   * Submits an empty operation to the given partition.
   *
   * @param partitionId the partition in which to execute the operation
   * @param operation the operation identifier
   * @param <R> the operation result type
   * @return A completable future to be completed with the operation result. The future is
   *     guaranteed to be completed after all {@link PrimitiveOperation} submission futures that
   *     preceded it.
   * @throws NullPointerException if {@code operation} is null
   */
  default <R> CompletableFuture<R> applyOn(
      final PartitionId partitionId, final Function<S, R> operation) {
    return getPartition(partitionId).apply(operation);
  }

  /**
   * Submits an empty operation to the owning partition for the given key.
   *
   * @param key the key for which to submit the operation
   * @param operation the operation
   * @return A completable future to be completed with the operation result. The future is
   *     guaranteed to be completed after all {@link PrimitiveOperation} submission futures that
   *     preceded it.
   * @throws NullPointerException if {@code operation} is null
   */
  default CompletableFuture<Void> acceptBy(final String key, final Consumer<S> operation) {
    return getPartition(key).accept(operation);
  }

  /**
   * Submits an empty operation to the owning partition for the given key.
   *
   * @param key the key for which to submit the operation
   * @param operation the operation
   * @param <R> the operation result type
   * @return A completable future to be completed with the operation result. The future is
   *     guaranteed to be completed after all {@link PrimitiveOperation} submission futures that
   *     preceded it.
   * @throws NullPointerException if {@code operation} is null
   */
  default <R> CompletableFuture<R> applyBy(final String key, final Function<S, R> operation) {
    return getPartition(key).apply(operation);
  }

  /**
   * Submits an empty operation to the owning partition for the given key.
   *
   * @param key the key for which to submit the operation
   * @param operation the operation
   * @param <R> the operation result type
   * @return A completable future to be completed with the operation result. The future is
   *     guaranteed to be completed after all {@link PrimitiveOperation} submission futures that
   *     preceded it.
   * @throws NullPointerException if {@code operation} is null
   */
  default <R> CompletableFuture<R> applyBy(final Object key, final Function<S, R> operation) {
    return getPartition(key).apply(operation);
  }

  /**
   * Registers a session state change listener.
   *
   * @param listener The callback to call when the session state changes.
   */
  void addStateChangeListener(Consumer<PrimitiveState> listener);

  /**
   * Removes a state change listener.
   *
   * @param listener the state change listener to remove
   */
  void removeStateChangeListener(Consumer<PrimitiveState> listener);

  /**
   * Connects the proxy client.
   *
   * @return a future to be completed once the proxy client has been connected
   */
  CompletableFuture<ProxyClient<S>> connect();

  /**
   * Closes the proxy client.
   *
   * @return a future to be completed once the proxy client has been closed
   */
  CompletableFuture<Void> close();

  /**
   * Deletes the proxy client.
   *
   * @return a future to be completed once the service has been deleted
   */
  CompletableFuture<Void> delete();
}
