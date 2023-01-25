/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.logstreams.impl.serializer;

import io.camunda.zeebe.logstreams.impl.log.SequencedBatch;
import io.camunda.zeebe.logstreams.log.LogAppendEntry;
import io.camunda.zeebe.protocol.Protocol;
import java.nio.ByteBuffer;
import java.util.Objects;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/** Accepts sequenced records, and serializes them with legacy dispatcher framing and alignment. */
public final class SequencedBatchSerializer {

  public static ByteBuffer serializeBatch(final SequencedBatch batch) {
    Objects.requireNonNull(batch, "must provide a batch to serialize");

    final var size = calculateBatchSize(batch);
    final var buffer = ByteBuffer.allocate(size).order(Protocol.ENDIANNESS);
    final var mutableBuffer = new UnsafeBuffer(buffer);

    serializeBatch(mutableBuffer, 0, batch);

    return buffer;
  }

  public static void serializeBatch(
      final MutableDirectBuffer buffer, final int offset, final SequencedBatch batch) {
    int currentOffset = offset;
    for (int i = 0; i < batch.entries().size(); i++) {
      final var entry = batch.entries().get(i);
      final var framedLength =
          LogAppendEntrySerializer.serialize(
              buffer,
              currentOffset,
              entry,
              batch.firstPosition() + i,
              getSourcePosition(batch, i, entry),
              batch.timestamp(),
              entry.needsProcessing());
      currentOffset += DataFrameDescriptor.alignedLength(framedLength);
    }
  }

  public static int calculateBatchSize(final SequencedBatch batch) {
    return batch.entries().stream()
        .mapToInt(
            entry ->
                DataFrameDescriptor.alignedLength(LogAppendEntrySerializer.framedLength(entry)))
        .sum();
  }

  private static long getSourcePosition(
      final SequencedBatch batch, final int i, final LogAppendEntry entry) {
    final long sourcePosition;
    if (entry.sourceIndex() >= 0 && entry.sourceIndex() < i) {
      sourcePosition = batch.firstPosition() + entry.sourceIndex();
    } else {
      sourcePosition = batch.sourcePosition();
    }
    return sourcePosition;
  }
}
