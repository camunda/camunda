/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.watermark;

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
}
