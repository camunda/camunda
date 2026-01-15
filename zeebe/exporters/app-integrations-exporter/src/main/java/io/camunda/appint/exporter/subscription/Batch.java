/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.appint.exporter.subscription;

import io.camunda.zeebe.protocol.record.Record;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class Batch<T> {

  private final int size;
  private final List<T> entries;
  private final long flushInterval;
  private long lastTimeFlushed = System.currentTimeMillis();
  private long lastLogPosition = -1;

  public Batch(final int size, final long flushInterval) {
    this.size = size;
    this.flushInterval = flushInterval;
    entries = new ArrayList<>(size);
  }

  protected boolean shouldFlush() {
    return !entries.isEmpty() && (isFull() || isTimeThresholdReached());
  }

  protected boolean isFull() {
    return size == entries.size();
  }

  protected boolean isEmpty() {
    return entries.isEmpty();
  }

  protected int spaceLeft() {
    return size - entries.size();
  }

  protected boolean isTimeThresholdReached() {
    final var elapsedTime = System.currentTimeMillis() - lastTimeFlushed;
    return elapsedTime >= flushInterval;
  }

  public boolean addRecord(final Record<?> record, final Function<Record<?>, T> entryMapper) {
    if (isFull()) {
      throw new IllegalStateException("Batch has too many entries. Drain first.");
    } else {
      if (lastLogPosition == -1 || record.getPosition() > lastLogPosition) {
        lastLogPosition = record.getPosition();
        return entries.add(entryMapper.apply(record));
      }
    }
    return false;
  }

  public List<T> getEntries() {
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
