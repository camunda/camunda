/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.write;

import io.camunda.analytics.lake.model.ActivityRow;
import io.camunda.analytics.lake.model.InstanceRow;

/**
 * The lake's write seam: buffers finished rows and lands them as one atomic, offset-stamped commit
 * per flush.
 *
 * <p>Contract for exactly-once: {@link #flush(int, long)} must commit the buffered rows and the
 * {@code throughOffset} for that source partition <em>atomically</em> (the Iceberg implementation
 * stamps the offset into the snapshot summary of the same append commit). On restart the translator
 * asks {@link #committedOffset(int)} and resumes consumption there; a replayed flush whose {@code
 * throughOffset} is at or below the committed offset must be a no-op, which makes replay after a
 * crash idempotent without any dedup table.
 *
 * <p>Implementations own the physical form (Parquet files written via embedded DuckDB, registered
 * with iceberg-core). Callers never see files.
 */
public interface LakeWriter extends AutoCloseable {

  /** Buffers one finished instance row for the next flush. */
  void append(InstanceRow row);

  /** Buffers one finished activity row for the next flush. */
  void append(ActivityRow row);

  /**
   * Atomically commits all rows buffered since the last flush for {@code sourcePartition},
   * recording that the partition's stream has been fully translated through {@code throughOffset}
   * (inclusive). A no-op if {@code throughOffset} is at or below {@link #committedOffset(int)}.
   */
  void flush(int sourcePartition, long throughOffset);

  /**
   * The offset through which {@code sourcePartition} is durably committed in the lake, or {@code
   * -1} when nothing has been committed yet. Consumption resumes at the next position.
   */
  long committedOffset(int sourcePartition);

  @Override
  void close();
}
