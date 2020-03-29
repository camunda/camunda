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
package io.atomix.cluster.messaging;

import com.google.common.util.concurrent.MoreExecutors;
import io.atomix.utils.net.Address;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;

/**
 * Service for unreliable unicast messaging between nodes.
 *
 * <p>The broadcast service is an unreliable broadcast messaging service backed by multicast. This
 * service provides no guaranteed regarding reliability or order of messages.
 */
public interface UnicastService {

  /**
   * Broadcasts the given message to all listeners for the given subject.
   *
   * <p>The message will be broadcast to all listeners for the given {@code subject}. This service
   * makes no guarantee regarding the reliability or order of delivery of the message.
   *
   * @param address the address to which to unicast the message
   * @param subject the message subject
   * @param message the message to broadcast
   */
  void unicast(Address address, String subject, byte[] message);

  /**
   * Adds a broadcast listener for the given subject.
   *
   * <p>Messages broadcast to the given {@code subject} will be delivered to the provided listener.
   * This service provides no guarantee regarding the order in which messages arrive.
   *
   * @param subject the message subject
   * @param listener the broadcast listener to add
   */
  default void addListener(final String subject, final BiConsumer<Address, byte[]> listener) {
    addListener(subject, listener, MoreExecutors.directExecutor());
  }

  /**
   * Adds a broadcast listener for the given subject.
   *
   * <p>Messages broadcast to the given {@code subject} will be delivered to the provided listener.
   * This service provides no guarantee regarding the order in which messages arrive.
   *
   * @param subject the message subject
   * @param listener the broadcast listener to add
   * @param executor an executor with which to call the listener
   */
  void addListener(String subject, BiConsumer<Address, byte[]> listener, Executor executor);

  /**
   * Removes a broadcast listener for the given subject.
   *
   * @param subject the message subject
   * @param listener the broadcast listener to remove
   */
  void removeListener(String subject, BiConsumer<Address, byte[]> listener);

  /** Broadcast service builder. */
  interface Builder extends io.atomix.utils.Builder<UnicastService> {}
}
