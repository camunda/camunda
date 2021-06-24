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
import java.net.ConnectException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Base class for client-side connections. Manages request futures and timeouts. */
abstract class AbstractClientConnection implements ClientConnection {
  private final Logger log = LoggerFactory.getLogger(getClass());
  private final Map<Long, Callback> callbacks = Maps.newConcurrentMap();
  private final AtomicBoolean closed = new AtomicBoolean(false);

  @Override
  public void dispatch(final ProtocolReply message) {
    final Callback callback = callbacks.remove(message.id());
    if (callback != null) {
      if (message.status() == ProtocolReply.Status.OK) {
        callback.complete(message.payload());
      } else if (message.status() == ProtocolReply.Status.ERROR_NO_HANDLER) {
        callback.completeExceptionally(new MessagingException.NoRemoteHandler());
      } else if (message.status() == ProtocolReply.Status.ERROR_HANDLER_EXCEPTION) {
        callback.completeExceptionally(new MessagingException.RemoteHandlerFailure());
      } else if (message.status() == ProtocolReply.Status.PROTOCOL_EXCEPTION) {
        callback.completeExceptionally(new MessagingException.ProtocolException());
      }
    } else {
      log.debug(
          "Received a reply for message id:[{}] but was unable to locate the request handle",
          message.id());
    }
  }

  @Override
  public void close() {
    if (closed.compareAndSet(false, true)) {
      for (final Callback callback : callbacks.values()) {
        callback.completeExceptionally(new ConnectException());
      }
    }
  }

  /** Client connection callback. */
  final class Callback {
    private final long id;
    private final CompletableFuture<byte[]> replyFuture;

    Callback(final long id, final CompletableFuture<byte[]> future) {
      this.id = id;
      replyFuture = future;
      callbacks.put(id, this);
    }

    /**
     * Completes the callback with the given value.
     *
     * @param value the value with which to complete the callback
     */
    void complete(final byte[] value) {
      replyFuture.complete(value);
    }

    /**
     * Completes the callback exceptionally.
     *
     * @param error the callback exception
     */
    void completeExceptionally(final Throwable error) {
      replyFuture.completeExceptionally(error);
      callbacks.remove(id);
    }
  }
}
