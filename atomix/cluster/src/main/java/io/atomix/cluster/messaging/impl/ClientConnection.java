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

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/** Client-side connection interface which handles sending messages. */
interface ClientConnection extends Connection<ProtocolReply> {

  /**
   * Sends a message to the other side of the connection.
   *
   * @param message the message to send
   * @return a completable future to be completed once the message has been sent
   */
  CompletableFuture<Void> sendAsync(ProtocolRequest message);

  /**
   * Sends a message to the other side of the connection, awaiting a reply.
   *
   * @param message the message to send
   * @param timeout the response timeout
   * @return a completable future to be completed once a reply is received or the request times out
   */
  CompletableFuture<byte[]> sendAndReceive(ProtocolRequest message, Duration timeout);

  /** Closes the connection. */
  default void close() {}
}
