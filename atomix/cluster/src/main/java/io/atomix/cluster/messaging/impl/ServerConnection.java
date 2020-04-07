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

import java.util.Optional;

/** Server-side connection interface which handles replying to messages. */
interface ServerConnection extends Connection<ProtocolRequest> {

  /**
   * Sends a reply to the other side of the connection.
   *
   * @param message the message to which to reply
   * @param status the reply status
   * @param payload the response payload
   */
  void reply(ProtocolRequest message, ProtocolReply.Status status, Optional<byte[]> payload);

  /** Closes the connection. */
  default void close() {}
}
