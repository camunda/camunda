/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.messaging.publish;

import io.atomix.cluster.messaging.InboundPayload;
import io.camunda.eventbridge.protocol.EventBridgeBatch;
import java.nio.ByteOrder;

/**
 * Immutable reference to a batch inside the original transport payload. No data copying. Reads
 * {@code entryCount} once at creation.
 *
 * <p>Once accepted by the pipeline (a successful {@link
 * io.camunda.eventbridge.messaging.stream.EventStreamWriter#tryWrite}), the pipeline owns the
 * payload and must {@link #release()} it exactly once — after the batch was copied into the log, or
 * on any failure/shutdown path.
 *
 * @param requestId unique ID for response correlation
 * @param payload the original transport payload holding the request bytes
 * @param batchOffset offset of the EventBridgeBatch within the payload
 * @param batchLength total length of the EventBridgeBatch
 * @param entryCount number of entries in the batch (read once from the payload)
 */
public record InflightBatchEntry(
    long requestId, InboundPayload payload, int batchOffset, int batchLength, int entryCount) {

  public static InflightBatchEntry of(
      final long requestId,
      final InboundPayload payload,
      final int batchOffset,
      final int batchLength) {
    final var entryCount =
        payload
            .view()
            .getInt(batchOffset + EventBridgeBatch.ENTRY_COUNT_OFFSET, ByteOrder.LITTLE_ENDIAN);
    return new InflightBatchEntry(requestId, payload, batchOffset, batchLength, entryCount);
  }

  /** Releases the underlying transport payload; the batch bytes must not be read afterwards. */
  void release() {
    payload.release();
  }
}
