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
package io.atomix.primitive.session;

import io.atomix.primitive.PrimitiveState;
import io.atomix.primitive.PrimitiveType;
import io.atomix.primitive.event.EventType;
import io.atomix.primitive.event.PrimitiveEvent;
import io.atomix.primitive.operation.PrimitiveOperation;
import io.atomix.primitive.partition.PartitionId;
import io.atomix.utils.concurrent.ThreadContext;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Partition proxy. */
public interface SessionClient {

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
   * Returns the session state.
   *
   * @return The session state.
   */
  PrimitiveState getState();

  /**
   * Returns the proxy session identifier.
   *
   * @return The proxy session identifier
   */
  SessionId sessionId();

  /**
   * Returns the partition identifier.
   *
   * @return the partition identifier.
   */
  PartitionId partitionId();

  /**
   * Returns the partition thread context.
   *
   * @return the partition thread context
   */
  ThreadContext context();

  /**
   * Executes an operation to the cluster.
   *
   * @param operation the operation to execute
   * @return a future to be completed with the operation result
   * @throws NullPointerException if {@code operation} is null
   */
  CompletableFuture<byte[]> execute(PrimitiveOperation operation);

  /**
   * Adds an event listener.
   *
   * @param eventType the event type for which to add the listener
   * @param listener the event listener to add
   */
  void addEventListener(EventType eventType, Consumer<PrimitiveEvent> listener);

  /**
   * Removes an event listener.
   *
   * @param eventType the event type for which to remove the listener
   * @param listener the event listener to remove
   */
  void removeEventListener(EventType eventType, Consumer<PrimitiveEvent> listener);

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
   * Connects the proxy.
   *
   * @return a future to be completed once the proxy has been connected
   */
  CompletableFuture<SessionClient> connect();

  /**
   * Closes the proxy.
   *
   * @return a future to be completed once the proxy has been closed
   */
  CompletableFuture<Void> close();

  /**
   * Closes the session and deletes the service.
   *
   * @return a future to be completed once the service has been deleted
   */
  CompletableFuture<Void> delete();

  /** Partition proxy builder. */
  abstract class Builder implements io.atomix.utils.Builder<SessionClient> {}
}
