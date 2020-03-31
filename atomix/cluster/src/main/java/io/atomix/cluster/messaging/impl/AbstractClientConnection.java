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
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.stat.descriptive.SynchronizedDescriptiveStatistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Base class for client-side connections. Manages request futures and timeouts. */
abstract class AbstractClientConnection implements ClientConnection {
  private static final int WINDOW_SIZE = 10;
  private static final int MIN_SAMPLES = 50;
  private static final int TIMEOUT_FACTOR = 5;
  private static final long MIN_TIMEOUT_MILLIS = 100;
  private static final long MAX_TIMEOUT_MILLIS = 5000;

  private final Logger log = LoggerFactory.getLogger(getClass());

  private final ScheduledExecutorService executorService;
  private final Map<Long, Callback> callbacks = Maps.newConcurrentMap();

  private final Map<String, DescriptiveStatistics> replySamples = new ConcurrentHashMap<>();

  private final AtomicBoolean closed = new AtomicBoolean(false);

  AbstractClientConnection(final ScheduledExecutorService executorService) {
    this.executorService = executorService;
  }

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

  /**
   * Adds a reply time to the history.
   *
   * @param type the message type
   * @param replyTime the reply time to add to the history
   */
  private void addReplyTime(final String type, final long replyTime) {
    DescriptiveStatistics samples = replySamples.get(type);
    if (samples == null) {
      samples =
          replySamples.computeIfAbsent(
              type, t -> new SynchronizedDescriptiveStatistics(WINDOW_SIZE));
    }
    samples.addValue(replyTime);
  }

  /**
   * Returns the timeout in milliseconds for the given timeout duration
   *
   * @param type the message type
   * @param timeout the timeout duration or {@code null} if the timeout is dynamic
   * @return the timeout in milliseconds
   */
  private long getTimeoutMillis(final String type, final Duration timeout) {
    return timeout != null ? timeout.toMillis() : computeTimeoutMillis(type);
  }

  /**
   * Computes the timeout for the next request.
   *
   * @param type the message type
   * @return the computed timeout for the next request
   */
  private long computeTimeoutMillis(final String type) {
    final DescriptiveStatistics samples = replySamples.get(type);
    if (samples == null || samples.getN() < MIN_SAMPLES) {
      return MAX_TIMEOUT_MILLIS;
    }
    return Math.min(
        Math.max((long) samples.getMax() * TIMEOUT_FACTOR, MIN_TIMEOUT_MILLIS), MAX_TIMEOUT_MILLIS);
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
    private final String type;
    private final long time = System.currentTimeMillis();
    private final long timeout;
    private final ScheduledFuture<?> scheduledFuture;
    private final CompletableFuture<byte[]> replyFuture;

    Callback(
        final long id,
        final String type,
        final Duration timeout,
        final CompletableFuture<byte[]> future) {
      this.id = id;
      this.type = type;
      this.timeout = getTimeoutMillis(type, timeout);
      this.scheduledFuture =
          executorService.schedule(this::timeout, this.timeout, TimeUnit.MILLISECONDS);
      this.replyFuture = future;
      future.thenRun(() -> addReplyTime(type, System.currentTimeMillis() - time));
      callbacks.put(id, this);
    }

    /**
     * Returns the callback message type.
     *
     * @return the message type
     */
    String type() {
      return type;
    }

    /** Fails the callback future with a timeout exception. */
    private void timeout() {
      replyFuture.completeExceptionally(
          new TimeoutException(
              "Request type " + type + " timed out in " + timeout + " milliseconds"));
      callbacks.remove(id);
    }

    /**
     * Completes the callback with the given value.
     *
     * @param value the value with which to complete the callback
     */
    void complete(final byte[] value) {
      scheduledFuture.cancel(false);
      replyFuture.complete(value);
    }

    /**
     * Completes the callback exceptionally.
     *
     * @param error the callback exception
     */
    void completeExceptionally(final Throwable error) {
      scheduledFuture.cancel(false);
      replyFuture.completeExceptionally(error);
      callbacks.remove(id);
    }
  }
}
