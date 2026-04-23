/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.publish;

import io.camunda.eventbridge.protocol.EventBridgeBatch;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.util.List;
import org.agrona.MutableDirectBuffer;

/** Serializes multiple {@link InflightBatchEntry} instances into a contiguous memory buffer. */
public final class BatchBufferWriter implements BufferWriter {

  private List<InflightBatchEntry> entries;
  private int totalLength;
  private long firstBatchPosition;
  private long timestamp;

  void reset(
      final List<InflightBatchEntry> entries,
      final int totalLength,
      final long firstBatchPosition,
      final long timestamp) {
    this.entries = entries;
    this.totalLength = totalLength;
    this.firstBatchPosition = firstBatchPosition;
    this.timestamp = timestamp;
  }

  @Override
  public int getLength() {
    return totalLength;
  }

  @Override
  public int write(final MutableDirectBuffer buffer, final int offset) {
    int writePos = offset;
    long batchPosition = firstBatchPosition;

    for (int i = 0; i < entries.size(); i++) {
      final var entry = entries.get(i);

      buffer.putBytes(writePos, entry.requestBytes(), entry.batchOffset(), entry.batchLength());
      EventBridgeBatch.patchPosition(buffer, writePos, batchPosition);
      EventBridgeBatch.patchTimestamp(buffer, writePos, timestamp);

      batchPosition += entry.entryCount();
      writePos += entry.batchLength();
    }

    final int bytesWritten = writePos - offset;
    assert bytesWritten == totalLength
        : "BatchBufferWriter length mismatch: expected " + totalLength + ", wrote " + bytesWritten;

    return bytesWritten;
  }
}
