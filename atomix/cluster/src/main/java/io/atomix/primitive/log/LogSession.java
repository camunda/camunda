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
package io.atomix.primitive.log;

import io.atomix.primitive.PrimitiveState;
import io.atomix.primitive.partition.PartitionId;
import io.atomix.primitive.session.SessionId;
import io.atomix.utils.concurrent.ThreadContext;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Log session. */
public interface LogSession {

  /**
   * Returns the session partition ID.
   *
   * @return the session partition ID
   */
  PartitionId partitionId();

  /**
   * Returns the session identifier.
   *
   * @return the session identifier
   */
  SessionId sessionId();

  /**
   * Returns the partition thread context.
   *
   * @return the partition thread context
   */
  ThreadContext context();

  /**
   * Returns the log producer.
   *
   * @return the log producer
   */
  LogProducer producer();

  /**
   * Returns the log consumer.
   *
   * @return the log consumer
   */
  LogConsumer consumer();

  /**
   * Returns the current session state.
   *
   * @return the current session state
   */
  PrimitiveState getState();

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
   * Connects the log session.
   *
   * @return a future to be completed once the log session has been connected
   */
  CompletableFuture<LogSession> connect();

  /**
   * Closes the log session.
   *
   * @return a future to be completed once the log session has been closed
   */
  CompletableFuture<Void> close();

  /** Log session builder. */
  abstract class Builder implements io.atomix.utils.Builder<LogSession> {}
}
