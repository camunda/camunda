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
import java.util.List;
import java.util.Objects;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/** Accepts sequenced records, and serializes them with legacy dispatcher framing and alignment. */
public final class SequencedBatchSerializer {

  public static ByteBuffer serializeBatch(final SequencedBatch batch) {
    Objects.requireNonNull(batch, "must provide a batch to serialize");

    final var buffer = ByteBuffer.allocate(batch.length()).order(Protocol.ENDIANNESS);
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
              batch.timestamp());
      currentOffset += DataFrameDescriptor.alignedLength(framedLength);
    }
  }

  public static int calculateBatchSize(final List<LogAppendEntry> entries) {
    return entries.stream().mapToInt(SequencedBatchSerializer::calculateEntrySize).sum();
  }

  private static int calculateEntrySize(final LogAppendEntry entry) {
    return DataFrameDescriptor.alignedLength(LogAppendEntrySerializer.framedLength(entry));
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
