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
import java.util.concurrent.atomic.AtomicReference;

public final class InFlightEntry {
  final LogStreamMetrics metrics;
  LogAppendEntryMetadata entryMetadata;
  final AtomicReference<Listener> requestListener;
  final AtomicReference<CloseableSilently> writeTimer;
  final AtomicReference<CloseableSilently> commitTimer;

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
    this.requestListener = new AtomicReference<>(requestListener);
    writeTimer = new AtomicReference<>(null);
    commitTimer = new AtomicReference<>(null);
  }

  public void onAppend() {
    writeTimer.set(metrics.startWriteTimer());
    commitTimer.set(metrics.startCommitTimer());
    if (requestListener.get() != null) {
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
    closeIfPossible(writeTimer);
  }

  public void onCommit() {
    closeIfPossible(commitTimer);
  }

  public void onProcessed() {
    final var requestListener = this.requestListener.getAndSet(null);
    if (requestListener != null) {
      requestListener.onSuccess();
      metrics.decreaseInflightRequests();
    }
  }

  public void cleanup() {
    final var requestListener = this.requestListener.getAndSet(null);
    if (requestListener != null) {
      requestListener.onIgnore();
      metrics.decreaseInflightRequests();
    }

    closeIfPossible(writeTimer);
    closeIfPossible(commitTimer);
  }

  private void closeIfPossible(final AtomicReference<CloseableSilently> closeableRef) {
    final var closeable = closeableRef.getAndSet(null);
    if (closeable != null) {
      closeable.close();
    }
  }
}
