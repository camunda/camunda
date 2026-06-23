/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.pubsub.publish;

import io.camunda.eventbridge.protocol.EventBridgeBatch;

/**
 * Immutable reference to a batch from the original Netty byte array. No data copying. Reads {@code
 * entryCount} once at creation.
 *
 * @param requestId unique ID for response correlation
 * @param requestBytes the original Netty byte array
 * @param batchOffset offset of the EventBridgeBatch within the byte array
 * @param batchLength total length of the EventBridgeBatch
 * @param entryCount number of entries in the batch (read once from the byte array)
 */
public record InflightBatchEntry(
    long requestId, byte[] requestBytes, int batchOffset, int batchLength, int entryCount) {

  public static InflightBatchEntry of(
      final long requestId,
      final byte[] requestBytes,
      final int batchOffset,
      final int batchLength) {
    final var entryCount =
        ByteUtil.readInt(requestBytes, batchOffset + EventBridgeBatch.ENTRY_COUNT_OFFSET);
    return new InflightBatchEntry(requestId, requestBytes, batchOffset, batchLength, entryCount);
  }
}
