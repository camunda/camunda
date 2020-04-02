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

import java.util.function.Consumer;

/**
 * Service for broadcast messaging between nodes.
 *
 * <p>The broadcast service is an unreliable broadcast messaging service backed by multicast. This
 * service provides no guaranteed regarding reliability or order of messages.
 */
public interface BroadcastService {

  /**
   * Broadcasts the given message to all listeners for the given subject.
   *
   * <p>The message will be broadcast to all listeners for the given {@code subject}. This service
   * makes no guarantee regarding the reliability or order of delivery of the message.
   *
   * @param subject the message subject
   * @param message the message to broadcast
   */
  void broadcast(String subject, byte[] message);

  /**
   * Adds a broadcast listener for the given subject.
   *
   * <p>Messages broadcast to the given {@code subject} will be delivered to the provided listener.
   * This service provides no guarantee regarding the order in which messages arrive.
   *
   * @param subject the message subject
   * @param listener the broadcast listener to add
   */
  void addListener(String subject, Consumer<byte[]> listener);

  /**
   * Removes a broadcast listener for the given subject.
   *
   * @param subject the message subject
   * @param listener the broadcast listener to remove
   */
  void removeListener(String subject, Consumer<byte[]> listener);

  /** Broadcast service builder. */
  interface Builder extends io.atomix.utils.Builder<BroadcastService> {}
}
