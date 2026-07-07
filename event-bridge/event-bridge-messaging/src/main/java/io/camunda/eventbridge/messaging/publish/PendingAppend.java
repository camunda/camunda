/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.messaging.publish;

import io.camunda.zeebe.logstreams.storage.LogStorage.AppendListener;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Reusable execution context for a single Raft append operation. */
final class PendingAppend implements AppendListener, Runnable {

  private final EventStreamPublisher publisher;
  private final BatchBufferWriter writer;

  private final List<InflightBatchEntry> entries;

  // Guards against LogStorage delivering more than one terminal callback for the same append; a
  // double completion would corrupt the publisher's in-flight accounting and the context pool.
  private final AtomicBoolean completionScheduled = new AtomicBoolean();

  private int capturedEntryCount;
  private int capturedBatchLength;
  private long firstBatchPosition;
  private Throwable commitError;

  PendingAppend(final EventStreamPublisher publisher, final int maxBatchesPerDrain) {
    this.publisher = publisher;
    writer = new BatchBufferWriter();
    entries = new ArrayList<>(maxBatchesPerDrain);
  }

  /** Clears previous state and prepares the context for a new append. */
  void reset(final long firstPosition) {
    entries.clear();
    capturedEntryCount = 0;
    capturedBatchLength = 0;
    commitError = null;
    firstBatchPosition = firstPosition;
    completionScheduled.set(false);
  }

  /** Encapsulates the accumulation of entries and their respective metrics. */
  void addEntry(final InflightBatchEntry entry) {
    entries.add(entry);
    capturedEntryCount += entry.entryCount();
    capturedBatchLength += entry.batchLength();
  }

  /** Readies the internal writer with the current state and returns it for serialization. */
  BatchBufferWriter getWriter(final long timestamp) {
    writer.reset(entries, capturedBatchLength, firstBatchPosition, timestamp);
    return writer;
  }

  // --- Accessors ---

  int entryCount() {
    return capturedEntryCount;
  }

  int batchLength() {
    return capturedBatchLength;
  }

  long firstPosition() {
    return firstBatchPosition;
  }

  long lastPosition() {
    return firstBatchPosition + capturedEntryCount - 1;
  }

  Throwable commitError() {
    return commitError;
  }

  int entriesSize() {
    return entries.size();
  }

  InflightBatchEntry getEntry(final int index) {
    return entries.get(index);
  }

  // --- AppendListener Callbacks ---

  @Override
  public void onWriteError(final Throwable error) {
    completeWith(error);
  }

  @Override
  public void onCommit(final long index, final long highestPosition) {
    completeWith(null);
  }

  @Override
  public void onCommitError(final long index, final Throwable error) {
    completeWith(error);
  }

  private void completeWith(final Throwable error) {
    if (completionScheduled.compareAndSet(false, true)) {
      commitError = error;
      publisher.submitAppendCompletion(this);
    }
  }

  @Override
  public void run() {
    publisher.onAppendCompleted(this);
  }
}
