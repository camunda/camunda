/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.transport.fetch;

import io.camunda.zeebe.util.IndexEntry;
import io.camunda.zeebe.util.IndexScanResult;
import java.nio.channels.FileChannel;
import java.util.List;

/**
 * Response to a fetch request. Contains zero or more complete {@code EventBridgeBatch} instances
 * packed contiguously in the data array.
 *
 * <p>Batches are always returned whole — never split. If the consumer requested an offset that
 * falls in the middle of a batch, the entire batch is returned and the consumer SDK skips entries
 * before the requested offset using {@link
 * io.camunda.eventbridge.protocol.EventBridgeBatchIterator#skipTo(long)}.
 *
 * <p>The consumer uses {@code highWatermark} to track how far behind it is. The difference {@code
 * highWatermark - lastBatchPosition} is the consumer lag.
 */
public record FetchResponse(
    long firstBatchPosition,
    long lastBatchPosition,
    long highWatermark,
    IndexScanResult.Success scanResult,
    int dataLength) {

  private static final FetchResponse EMPTY_RESPONSE = new FetchResponse(0, -1, 0, null, 0);

  /** Empty response — no data available. */
  public static FetchResponse empty() {
    return EMPTY_RESPONSE;
  }

  public boolean isEmpty() {
    return dataLength == 0;
  }

  /**
   * Safely closes the lease. Used when discarding partial reads or completing network transfers.
   */
  public void releaseLease() {
    if (scanResult != null && scanResult.lease() != null) {
      scanResult.lease().close();
    }
  }

  public FileChannel channel() {
    return scanResult.channel();
  }

  public List<IndexEntry> entries() {
    return scanResult.entries();
  }
}
