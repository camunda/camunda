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

import io.netty.channel.Channel;
import java.util.concurrent.CompletableFuture;

/** Client-side Netty remote connection. */
final class RemoteClientConnection extends AbstractClientConnection {
  private final Channel channel;

  RemoteClientConnection(final Channel channel) {
    this.channel = channel;
  }

  @Override
  public CompletableFuture<Void> sendAsync(final ProtocolRequest message) {
    final CompletableFuture<Void> future = new CompletableFuture<>();
    channel
        .writeAndFlush(message)
        .addListener(
            channelFuture -> {
              if (!channelFuture.isSuccess()) {
                future.completeExceptionally(channelFuture.cause());
              } else {
                future.complete(null);
              }
            });
    return future;
  }

  @Override
  public CompletableFuture<byte[]> sendAndReceive(final ProtocolRequest message) {
    final CompletableFuture<byte[]> responseFuture = awaitResponseForRequestWithId(message.id());
    channel
        .writeAndFlush(message)
        .addListener(
            channelFuture -> {
              if (!channelFuture.isSuccess()) {
                responseFuture.completeExceptionally(channelFuture.cause());
              }
            });
    return responseFuture;
  }

  @Override
  public String toString() {
    return "RemoteClientConnection{channel=" + channel + "}";
  }
}
