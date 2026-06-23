/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.pubsub.stream;

import io.camunda.eventbridge.pubsub.publish.InboundQueue;
import io.camunda.eventbridge.pubsub.publish.InflightBatchEntry;
import java.util.concurrent.atomic.AtomicBoolean;

/** Writes complete EventBridgeBatch bytes into the inbound ring buffer. Thread-safe. */
public final class EventStreamWriter {

  private final AtomicBoolean closed = new AtomicBoolean(false);
  private final InboundQueue inbound;
  private final Runnable drainNotifier;

  public EventStreamWriter(final InboundQueue inbound, final Runnable drainNotifier) {
    this.inbound = inbound;
    this.drainNotifier = drainNotifier;
  }

  public boolean tryWrite(
      final long requestId,
      final byte[] requestBytes,
      final int batchOffset,
      final int batchLength) {

    if (closed.get()) {
      return false;
    }

    final var entry = InflightBatchEntry.of(requestId, requestBytes, batchOffset, batchLength);

    if (!inbound.offer(entry)) {
      return false;
    }

    if (inbound.scheduleDrain()) {
      drainNotifier.run();
    }

    return true;
  }

  /** Stops accepting new writes. Already-queued entries remain for the appender to drain. */
  public void close() {
    closed.set(true);
  }

  public boolean isClosed() {
    return closed.get();
  }
}
