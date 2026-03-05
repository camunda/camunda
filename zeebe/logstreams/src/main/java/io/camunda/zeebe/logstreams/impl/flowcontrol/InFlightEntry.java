/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.logstreams.impl.flowcontrol;

import com.netflix.concurrency.limits.Limiter.Listener;
import io.camunda.zeebe.logstreams.impl.LogStreamMetrics;
import io.camunda.zeebe.logstreams.impl.log.LogAppendEntryMetadata;
import io.camunda.zeebe.util.CloseableSilently;

public final class InFlightEntry {
  final LogStreamMetrics metrics;
  LogAppendEntryMetadata entryMetadata;
  Listener requestListener;
  CloseableSilently writeTimer;
  CloseableSilently commitTimer;

  /**
   * Request identification for user commands. A value of {@code -1} indicates no request metadata
   * (e.g., for internal or processing-result entries). A non-negative value indicates a user
   * command whose commit error should be reported back to the client.
   */
  long requestId = -1;

  int requestStreamId = -1;

  /**
   * The position this entry was registered at in the ring buffer. Set by {@link RingBuffer#put}
   * before the entry reference is published into the array. Used by {@link RingBuffer#get} to
   * verify that the entry in a slot actually belongs to the requested position (guards against
   * wraparound collisions).
   *
   * <p>This field is intentionally <em>not</em> volatile. Visibility is guaranteed by the {@link
   * java.util.concurrent.atomic.AtomicReferenceArray} used in the ring buffer: {@code put} sets
   * this field before the volatile write ({@code getAndSet}) that publishes the entry reference.
   * Readers obtain the reference via a volatile read ({@code get}) on the same array slot, which
   * establishes a happens-before edge that carries this plain write.
   */
  long position;

  public InFlightEntry(
      final LogStreamMetrics metrics,
      final LogAppendEntryMetadata entryMetadata,
      final Listener requestListener) {
    this.metrics = metrics;
    this.entryMetadata = entryMetadata;
    this.requestListener = requestListener;
    writeTimer = null;
    commitTimer = null;
  }

  public void onAppend() {
    writeTimer = metrics.startWriteTimer();
    commitTimer = metrics.startCommitTimer();
    if (requestListener != null) {
      metrics.increaseInflightRequests();
    }
  }

  public void onWrite() {
    final var entryMetadata = this.entryMetadata;
    if (entryMetadata != null) {
      for (int i = 0; i < entryMetadata.size(); i++) {
        metrics.recordAppendedEntry(
            1, entryMetadata.recordType(i), entryMetadata.valueType(i), entryMetadata.intent(i));
      }
      this.entryMetadata = null;
    }
    final var writeTimer = this.writeTimer;
    if (writeTimer != null) {
      writeTimer.close();
      this.writeTimer = null;
    }
  }

  public void onCommit() {
    final var commitTimer = this.commitTimer;
    if (commitTimer != null) {
      commitTimer.close();
      this.commitTimer = null;
    }
  }

  public void onProcessed() {
    final var requestListener = this.requestListener;
    if (requestListener != null) {
      requestListener.onSuccess();
      metrics.decreaseInflightRequests();
      this.requestListener = null;
    }
  }

  public void cleanup() {
    final var requestListener = this.requestListener;
    if (requestListener != null) {
      requestListener.onIgnore();
      metrics.decreaseInflightRequests();
      this.requestListener = null;
    }
    final var writeTimer = this.writeTimer;
    if (writeTimer != null) {
      writeTimer.close();
      this.writeTimer = null;
    }
    final var commitTimer = this.commitTimer;
    if (commitTimer != null) {
      commitTimer.close();
      this.commitTimer = null;
    }
  }
}
