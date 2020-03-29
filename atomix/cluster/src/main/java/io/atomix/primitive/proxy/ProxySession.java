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
import io.atomix.utils.concurrent.ThreadContext;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

/** Proxy session. */
public interface ProxySession<S> {

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
   * Returns the proxy partition ID.
   *
   * @return the partition ID
   */
  PartitionId partitionId();

  /**
   * Returns the session thread context.
   *
   * @return the session thread context
   */
  ThreadContext context();

  /**
   * Returns the session state.
   *
   * @return The session state.
   */
  PrimitiveState getState();

  /**
   * Registers a client listener.
   *
   * @param client the client listener to register
   */
  void register(Object client);

  /**
   * Submits an empty operation to the given partition.
   *
   * @param operation the operation identifier
   * @return A completable future to be completed with the operation result. The future is
   *     guaranteed to be completed after all {@link PrimitiveOperation} submission futures that
   *     preceded it.
   * @throws NullPointerException if {@code operation} is null
   */
  CompletableFuture<Void> accept(Consumer<S> operation);

  /**
   * Submits an empty operation to the given partition.
   *
   * @param operation the operation identifier
   * @param <R> the operation result type
   * @return A completable future to be completed with the operation result. The future is
   *     guaranteed to be completed after all {@link PrimitiveOperation} submission futures that
   *     preceded it.
   * @throws NullPointerException if {@code operation} is null
   */
  <R> CompletableFuture<R> apply(Function<S, R> operation);

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
   * Connects the proxy session.
   *
   * @return a future to be completed once the proxy session has been connected
   */
  CompletableFuture<ProxySession<S>> connect();

  /**
   * Closes the proxy session.
   *
   * @return a future to be completed once the proxy session has been closed
   */
  CompletableFuture<Void> close();

  /**
   * Closes the proxy session and deletes the service.
   *
   * @return a future to be completed once the service has been deleted
   */
  CompletableFuture<Void> delete();
}
