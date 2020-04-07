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

/** Local server-side connection. */
final class LocalServerConnection extends AbstractServerConnection {
  private static final byte[] EMPTY_PAYLOAD = new byte[0];

  private volatile LocalClientConnection clientConnection;

  LocalServerConnection(
      final HandlerRegistry handlers, final LocalClientConnection clientConnection) {
    super(handlers);
    this.clientConnection = clientConnection;
  }

  @Override
  public void reply(
      final ProtocolRequest message,
      final ProtocolReply.Status status,
      final Optional<byte[]> payload) {
    final LocalClientConnection clientConnection = this.clientConnection;
    if (clientConnection != null) {
      clientConnection.dispatch(
          new ProtocolReply(message.id(), payload.orElse(EMPTY_PAYLOAD), status));
    }
  }
}
