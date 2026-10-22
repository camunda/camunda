/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.consumer;

/** An entry's application data that isn't a batch of the log stream's logged events. */
public final class InvalidRecordBatchException extends RuntimeException {

  private final long index;
  private final int offset;
  private final int length;

  InvalidRecordBatchException(
      final long index, final int offset, final int length, final int capacity) {
    super(
        ("Expected a logged event of 1 to %d bytes at byte %d of entry %d's application data, but"
                + " got %d")
            .formatted(capacity - offset, offset, index, length));
    this.index = index;
    this.offset = offset;
    this.length = length;
  }

  /** The entry's Raft index. */
  public long index() {
    return index;
  }

  /** Where the logged event starts in the entry's application data. */
  public int offset() {
    return offset;
  }

  /** The length the logged event's header says it has. */
  public int length() {
    return length;
  }
}
