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
import io.atomix.primitive.protocol.LogProtocol;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Log client. */
public interface LogClient {

  /**
   * Returns the client log protocol.
   *
   * @return the client log protocol
   */
  LogProtocol protocol();

  /**
   * Returns the log client state.
   *
   * @return the log client state
   */
  PrimitiveState state();

  /**
   * Returns the collection of all partitions.
   *
   * @return the collection of all partitions
   */
  Collection<LogSession> getPartitions();

  /**
   * Returns the collection of all partition IDs.
   *
   * @return the collection of all partition IDs
   */
  Collection<PartitionId> getPartitionIds();

  /**
   * Returns the partition with the given identifier.
   *
   * @param partitionId the partition with the given identifier
   * @return the partition with the given identifier
   */
  LogSession getPartition(PartitionId partitionId);

  /**
   * Returns the partition ID for the given key.
   *
   * @param key the key for which to return the partition ID
   * @return the partition ID for the given key
   */
  PartitionId getPartitionId(String key);

  /**
   * Returns the partition for the given key.
   *
   * @param key the key for which to return the partition
   * @return the partition for the given key
   */
  default LogSession getPartition(final String key) {
    return getPartition(getPartitionId(key));
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
   * Connects the log client.
   *
   * @return a future to be completed once the log client has been connected
   */
  CompletableFuture<LogClient> connect();

  /**
   * Closes the log client.
   *
   * @return a future to be completed once the log client has been closed
   */
  CompletableFuture<Void> close();
}
