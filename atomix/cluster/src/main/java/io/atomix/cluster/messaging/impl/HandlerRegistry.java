/*
 * Copyright 2018-present Open Networking Foundation
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
package io.atomix.cluster.messaging.impl;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/** Messaging handler registry. */
final class HandlerRegistry {
  private final Map<String, BiConsumer<ProtocolRequest, ServerConnection>> handlers =
      new ConcurrentHashMap<>();

  /**
   * Registers a message type handler.
   *
   * @param type the message type
   * @param handler the message handler
   */
  void register(final String type, final BiConsumer<ProtocolRequest, ServerConnection> handler) {
    handlers.put(type, handler);
  }

  /**
   * Unregisters a message type handler.
   *
   * @param type the message type
   */
  void unregister(final String type) {
    handlers.remove(type);
  }

  /**
   * Looks up a message type handler.
   *
   * @param type the message type
   * @return the message handler or {@code null} if no handler of the given type is registered
   */
  BiConsumer<ProtocolRequest, ServerConnection> get(final String type) {
    return handlers.get(type);
  }
}
