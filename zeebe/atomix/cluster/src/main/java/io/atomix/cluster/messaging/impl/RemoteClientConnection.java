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

import static io.atomix.cluster.messaging.impl.MessagingMetrics.CHANNEL_ID_ATTRIBUTE;

import io.netty.channel.Channel;
import java.util.concurrent.CompletableFuture;

/** Client-side Netty remote connection. */
final class RemoteClientConnection extends AbstractClientConnection {
  private final Channel channel;
  private final MessagingMetrics messagingMetrics;

  RemoteClientConnection(final MessagingMetrics messagingMetrics, final Channel channel) {
    this.messagingMetrics = messagingMetrics;
    this.channel = channel;
  }

  @Override
  public CompletableFuture<Void> sendAsync(final ProtocolRequest message) {
    final CompletableFuture<Void> future = new CompletableFuture<>();
    countMessageMetrics(message);
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
    countReqResponseMetrics(message, responseFuture);
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

  private void countMessageMetrics(final ProtocolRequest message) {
    final String toAddress = channel.remoteAddress().toString();
    final String subject = message.subject();
    final String channelId = channel.attr(CHANNEL_ID_ATTRIBUTE).get();
    messagingMetrics.countMessage(
        channel.remoteAddress().toString(),
        message.subject(),
        channelId != null ? channelId : "anonymous");
    final byte[] payload = message.payload();
    messagingMetrics.observeRequestSize(toAddress, subject, payload == null ? 0 : payload.length);
  }

  private void countReqResponseMetrics(
      final ProtocolRequest message, final CompletableFuture<byte[]> responseFuture) {
    // TODO: Read our own channel id and use it to mark the metric
    final String channelId = channel.attr(CHANNEL_ID_ATTRIBUTE).get();
    final String toAddress = channel.remoteAddress().toString();
    final String subject = message.subject();
    messagingMetrics.countRequestResponse(
        toAddress, subject, channelId != null ? channelId : "anonymous");
    messagingMetrics.incInFlightRequests(toAddress, subject);
    final var timer = messagingMetrics.startRequestTimer(subject);
    final byte[] payload = message.payload();
    messagingMetrics.observeRequestSize(toAddress, subject, payload == null ? 0 : payload.length);

    responseFuture.whenComplete(
        (success, failure) -> {
          timer.close();
          messagingMetrics.decInFlightRequests(toAddress, subject);
          if (failure != null) {
            messagingMetrics.countFailureResponse(toAddress, subject, failure.getClass().getName());
          } else {
            messagingMetrics.countSuccessResponse(toAddress, subject);
          }
        });
  }

  @Override
  public String toString() {
    return "RemoteClientConnection{channel=" + channel + "}";
  }
}
