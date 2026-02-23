/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.common;

import java.time.Instant;
import java.time.InstantSource;

/**
 * Generates checkpoint IDs based on epoch milliseconds with an optional offset. Checkpoint IDs are
 * used to uniquely identify backups and are generated from timestamps.
 */
public final class CheckpointIdGenerator {

  private final InstantSource instantSource;
  private final long offset;

  public CheckpointIdGenerator(final InstantSource instantSource, final long offset) {
    this.instantSource = instantSource;
    this.offset = offset;
  }

  public CheckpointIdGenerator(final long offset) {
    this(InstantSource.system(), offset);
  }

  public CheckpointIdGenerator() {
    this(InstantSource.system(), 0L);
  }

  /**
   * Generates a checkpoint ID from the current time plus the configured offset.
   *
   * @return the generated checkpoint ID
   */
  public long generateCheckpointId() {
    return fromTimestamp(instantSource.instant().toEpochMilli());
  }

  /**
   * Converts a raw timestamp to a checkpoint ID by applying the configured offset.
   *
   * @param timestamp the raw timestamp in epoch milliseconds
   * @return the checkpoint ID (timestamp + offset)
   * @throws IllegalArgumentException if the timestamp is negative or if adding the offset would
   *     cause overflow
   */
  public long fromTimestamp(final long timestamp) {
    if (timestamp < 0) {
      throw new IllegalArgumentException(
          "Expected timestamp to be non-negative, but got %d".formatted(timestamp));
    }
    if (offset > 0) {
      // Check for overflow
      if (timestamp > Long.MAX_VALUE - offset) {
        throw new IllegalArgumentException(
            "Expected timestamp + offset to be <= %d, but got timestamp=%d and offset=%d"
                .formatted(Long.MAX_VALUE, timestamp, offset));
      }
      return timestamp + offset;
    }
    return timestamp;
  }

  public Instant toInstant(final long checkpointId) {
    return Instant.ofEpochMilli(checkpointId - offset);
  }
}
