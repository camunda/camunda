/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.logstreams.impl.serializer;

import io.camunda.zeebe.logstreams.impl.log.Sequencer.SequencedBatch;
import io.camunda.zeebe.logstreams.log.LogAppendEntry;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.scheduler.clock.ActorClock;
import java.nio.ByteBuffer;
import java.util.Objects;
import org.agrona.concurrent.UnsafeBuffer;

/** Accepts sequenced records, and serializes them with legacy dispatcher framing and alignment. */
public final class SequencedBatchSerializer {
  final LogAppendEntrySerializer entrySerializer = new LogAppendEntrySerializer();

  public ByteBuffer serializeBatch(final SequencedBatch batch) {
    Objects.requireNonNull(batch, "must provide a batch to serialize");

    final var size = calculateBatchSize(batch);
    final var buffer = ByteBuffer.allocate(size).order(Protocol.ENDIANNESS);
    final var mutableBuffer = new UnsafeBuffer(buffer);

    int bufferOffset = 0;
    for (int i = 0; i < batch.entries().size(); i++) {
      final var entry = batch.entries().get(i);
      final var framedLength =
          entrySerializer.serialize(
              mutableBuffer,
              bufferOffset,
              entry,
              batch.firstPosition() + i,
              getSourcePosition(batch, i, entry),
              ActorClock.currentTimeMillis());
      // Align the next entry like the dispatcher did
      bufferOffset += DataFrameDescriptor.alignedLength(framedLength);
    }

    return buffer;
  }

  private int calculateBatchSize(final SequencedBatch batch) {
    return batch.entries().stream()
        .mapToInt(entry -> DataFrameDescriptor.alignedLength(entrySerializer.framedLength(entry)))
        .sum();
  }

  private long getSourcePosition(
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
