/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.messaging.watermark;

/**
 * A composable watermark used to track the progression of a specific partition.
 *
 * <p><b>Contract:</b> This interface acts as the bridge between the Write Path and the Read Path.
 * The Publisher uses it to report newly committed data, and the Fetcher uses it to safely evaluate
 * parked long-polling requests.
 */
public interface HighWatermark {

  /**
   * Retrieves an atomic, immutable snapshot of the partition's current progress.
   *
   * <p><b>Contract:</b> Used by the Fetcher to evaluate parked requests without risking interleaved
   * or inconsistent memory reads.
   *
   * @return the unified snapshot of position and bytes
   */
  PartitionWatermark get();

  /**
   * Advances the watermark based on a successful durable commit.
   *
   * <p><b>Contract:</b> Must be called by the Publisher when a batch's Raft commit future completes
   * successfully.
   *
   * @param commitPosition the Raft position of the last entry in the committed batch
   * @param committedBytes the physical byte size of the newly committed batch
   */
  void onCommitted(long commitPosition, int committedBytes);

  /**
   * Seeds the watermark to a recovered commit position on leader activation, so a newly elected
   * leader does not under-report the high watermark (and starve long-poll wake-ups) until its first
   * fresh append.
   *
   * <p><b>Contract:</b> Called once during partition leader startup with the last committed
   * position recovered from the log. A no-op if the position does not advance the current
   * watermark. The byte counter is left at zero — it only feeds relative (delta-since-park)
   * comparisons, which are established per parked request after activation.
   *
   * @param commitPosition the last committed record position recovered from the log
   */
  void seed(long commitPosition);
}
