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

/**
 * Writes multiple {@link InflightBatchEntry} references into LogStorage's buffer. Patches position
 * and timestamp during the write — copy and patch in one pass. The only copy in the pipeline.
 */
public final class BatchBufferWriter implements BufferWriter {

  private final List<InflightBatchEntry> entries;
  private final int totalLength;
  private final long firstBatchPosition;
  private final long timestamp;

  public BatchBufferWriter(
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

    for (final var entry : entries) {
      buffer.putBytes(writePos, entry.requestBytes(), entry.batchOffset(), entry.batchLength());

      EventBridgeBatch.patchBatchPosition(buffer, writePos, batchPosition);
      EventBridgeBatch.patchTimestamp(buffer, writePos, timestamp);

      batchPosition += entry.entryCount();
      writePos += entry.batchLength();
    }
    return getLength();
  }
}
