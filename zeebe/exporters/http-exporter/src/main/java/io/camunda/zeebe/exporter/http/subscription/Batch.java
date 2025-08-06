/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.http.subscription;

import java.util.ArrayList;
import java.util.List;

public class Batch {

  private final int size;
  private final List<BatchEntry> entries;
  private long lastTimeFlushed = -1;
  private final long flushInterval;

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

  int spaceLeft() {
    return size - entries.size();
  }

  boolean isTimeThresholdReached() {
    return lastTimeFlushed != -1 && System.currentTimeMillis() - lastTimeFlushed >= flushInterval;
  }

  public void addRecord(final BatchEntry batchEntry) {
    if (isFull()) {
      throw new IllegalStateException("Batch has too many entries. Drain first.");
    } else {
      if (entries.isEmpty() || batchEntry.logPosition() > entries.getLast().logPosition()) {
        entries.add(batchEntry);
      }
    }
  }

  public List<BatchEntry> getEntries() {
    return new ArrayList<>(entries);
  }

  public void flush() {
    if (entries.isEmpty()) {
      throw new IllegalStateException("Flushing empty batch not allowed.");
    }
    entries.clear();
    lastTimeFlushed = System.currentTimeMillis();
  }

  public int getSize() {
    return entries.size();
  }
}
