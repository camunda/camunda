/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.messaging.watermark;

/**
 * An immutable snapshot of a partition's durably committed progress.
 *
 * <p><b>Thread Safety & Consistency:</b> By bundling position and bytes together in a single
 * record, we completely eliminate the risk of interleaved memory reads. Threads evaluating this
 * watermark will always see a perfectly consistent state.
 */
public record PartitionWatermark(long commitPosition, long committedBytes) {

  /**
   * Safely evaluates whether this watermark satisfies a requested offset and byte deficit.
   *
   * @param requestedOffset the target offset the consumer wants to read from
   * @param parkedAtBytesSnapshot the byte watermark at the exact moment the consumer was parked
   * @param requiredByteDelta the number of newly committed bytes required to wake up the consumer
   * @return {@code true} if the requested offset exists AND enough new bytes have arrived
   */
  public boolean satisfies(
      final long requestedOffset, final long parkedAtBytesSnapshot, final long requiredByteDelta) {

    // 1. FUTURE OFFSET PROTECTION
    // If the log hasn't reached the requested offset yet, ignore byte accumulations.
    // This prevents spin-loops when clients ask for data that doesn't exist yet.
    if (commitPosition < requestedOffset) {
      return false;
    }

    // 2. BYTE DEFICIT CHECK
    // The offset exists. Have enough bytes accumulated since the snapshot was taken?
    final long deltaSincePark = committedBytes - parkedAtBytesSnapshot;
    return deltaSincePark >= requiredByteDelta;
  }
}
