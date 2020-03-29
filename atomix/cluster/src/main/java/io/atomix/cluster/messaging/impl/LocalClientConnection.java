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
import java.util.concurrent.ScheduledExecutorService;

/** Local client-side connection. */
final class LocalClientConnection extends AbstractClientConnection {
  private final LocalServerConnection serverConnection;

  LocalClientConnection(
      final ScheduledExecutorService executorService, final HandlerRegistry handlers) {
    super(executorService);
    this.serverConnection = new LocalServerConnection(handlers, this);
  }

  @Override
  public CompletableFuture<Void> sendAsync(final ProtocolRequest message) {
    serverConnection.dispatch(message);
    return CompletableFuture.completedFuture(null);
  }

  @Override
  public CompletableFuture<byte[]> sendAndReceive(
      final ProtocolRequest message, final Duration timeout) {
    final CompletableFuture<byte[]> future = new CompletableFuture<>();
    new Callback(message.id(), message.subject(), timeout, future);
    serverConnection.dispatch(message);
    return future;
  }

  @Override
  public void close() {
    super.close();
    serverConnection.close();
  }
}
