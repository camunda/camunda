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

import com.google.common.collect.Maps;
import io.atomix.cluster.messaging.MessagingException;
import io.camunda.zeebe.util.StringUtil;
import java.net.ConnectException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Base class for client-side connections. Manages request futures and timeouts. */
abstract class AbstractClientConnection implements ClientConnection {
  private final Logger log = LoggerFactory.getLogger(getClass());
  private final AtomicBoolean closed = new AtomicBoolean(false);

  // since all messages go through the same entry point, we keep a map of message IDs -> response
  // futures to allow dynamic dispatch of messages to the right response future
  private final Map<Long, CompletableFuture<byte[]>> responseFutures = Maps.newConcurrentMap();

  @Override
  public void dispatch(final ProtocolReply message) {
    final CompletableFuture<byte[]> responseFuture = responseFutures.remove(message.id());
    if (responseFuture != null) {
      if (message.status() == ProtocolReply.Status.OK) {
        responseFuture.complete(message.payload());
      } else if (message.status() == ProtocolReply.Status.ERROR_NO_HANDLER) {
        final String subject = extractMessage(message);
        responseFuture.completeExceptionally(new MessagingException.NoRemoteHandler(subject));
      } else if (message.status() == ProtocolReply.Status.ERROR_HANDLER_EXCEPTION) {
        final String exceptionMessage = extractMessage(message);
        responseFuture.completeExceptionally(
            new MessagingException.RemoteHandlerFailure(exceptionMessage));
      } else if (message.status() == ProtocolReply.Status.PROTOCOL_EXCEPTION) {
        responseFuture.completeExceptionally(new MessagingException.ProtocolException());
      }
    } else {
      log.debug(
          "Received a reply for message id:[{}] but was unable to locate the request handle",
          message.id());
    }
  }

  private String extractMessage(final ProtocolReply message) {
    final byte[] payload = message.payload();
    String exceptionMessage = null;

    if (payload != null && payload.length > 0) {
      exceptionMessage = StringUtil.fromBytes(payload);
    }
    return exceptionMessage;
  }

  @Override
  public void close() {
    if (closed.compareAndSet(false, true)) {
      for (final CompletableFuture<byte[]> responseFuture : responseFutures.values()) {
        responseFuture.completeExceptionally(
            new ConnectException(String.format("Connection %s was closed", this)));
      }
    }
  }

  /**
   * Registers a request to await a response. The future returned is already set up to remove itself
   * from the registry to ensure cleanup.
   *
   * <p>Will return the same future if there already exists one for a given ID.
   *
   * @param id the request ID
   * @return the response future for the given request ID
   */
  protected CompletableFuture<byte[]> awaitResponseForRequestWithId(final long id) {
    final CompletableFuture<byte[]> responseFuture =
        responseFutures.computeIfAbsent(id, ignored -> new CompletableFuture<>());
    responseFuture.whenComplete((result, error) -> responseFutures.remove(id));

    return responseFuture;
  }
}
