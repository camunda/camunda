/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.http.subscription;

import java.util.ArrayList;
import java.util.List;

public class Batch<T> {

  private final int size;
  private final List<BatchEntry<T>> entries;
  private final long flushInterval;
  private long lastTimeFlushed = System.currentTimeMillis();
  private long lastLogPosition = -1;

  public Batch(final int size, final long flushInterval) {
    this.size = size;
    this.flushInterval = flushInterval;
    entries = new ArrayList<>(size);
  }

  boolean shouldFlush() {
    return !entries.isEmpty() && (isFull() || isTimeThresholdReached());
  }

  boolean isFull() {
    return size == entries.size();
  }

  boolean isEmpty() {
    return entries.isEmpty();
  }

  int spaceLeft() {
    return size - entries.size();
  }

  boolean isTimeThresholdReached() {
    return System.currentTimeMillis() - lastTimeFlushed >= flushInterval;
  }

  public boolean addRecord(final BatchEntry<T> batchEntry) {
    if (isFull()) {
      throw new IllegalStateException("Batch has too many entries. Drain first.");
    } else {
      if (lastLogPosition == -1 || batchEntry.logPosition() > lastLogPosition) {
        lastLogPosition = batchEntry.logPosition();
        return entries.add(batchEntry);
      }
    }
    return false;
  }

  public List<BatchEntry<T>> getEntries() {
    return new ArrayList<>(entries);
  }

  public long flush() {
    if (entries.isEmpty()) {
      throw new IllegalStateException("Flushing empty batch not allowed.");
    }
    lastTimeFlushed = System.currentTimeMillis();
    entries.clear();
    return lastLogPosition;
  }

  public int getSize() {
    return entries.size();
  }

  public long getLastLogPosition() {
    return lastLogPosition;
  }
}
