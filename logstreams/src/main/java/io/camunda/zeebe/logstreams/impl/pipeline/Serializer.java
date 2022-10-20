/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.logstreams.impl.pipeline;

import static io.camunda.zeebe.logstreams.impl.log.LogEntryDescriptor.metadataOffset;
import static io.camunda.zeebe.logstreams.impl.log.LogEntryDescriptor.setSourceEventPosition;
import static io.camunda.zeebe.logstreams.impl.log.LogEntryDescriptor.valueOffset;

import io.camunda.zeebe.dispatcher.impl.log.DataFrameDescriptor;
import io.camunda.zeebe.logstreams.impl.log.LogEntryDescriptor;
import io.camunda.zeebe.logstreams.impl.pipeline.Sequencer.SequencedBatchEntry;
import io.camunda.zeebe.logstreams.impl.pipeline.Sequencer.SequencedRecordBatch;
import io.camunda.zeebe.scheduler.clock.ActorClock;
import java.nio.ByteBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/** Accepts sequenced records, and serializes them. */
public class Serializer {

  public ByteBuffer serializeBatch(final SequencedRecordBatch batch) {
    final var size = calculateBatchSize(batch);
    final var buffer = ByteBuffer.allocate(size);
    final var mutableBuffer = new UnsafeBuffer(buffer);

    int bufferOffset = 0;
    for (int i = 0; i < batch.entries().size(); i++) {
      final var entry = batch.entries().get(i);
      final var entryLength = calculateEntrySize(entry);

      // Write frame length

      buffer.putInt(
          DataFrameDescriptor.lengthOffset(bufferOffset),
          DataFrameDescriptor.HEADER_LENGTH + entryLength);
      bufferOffset += DataFrameDescriptor.HEADER_LENGTH;

      LogEntryDescriptor.setPosition(mutableBuffer, bufferOffset, entry.position());
      final var sourceIndex = entry.entry().sourceIndex();
      if (sourceIndex >= 0 && sourceIndex < i) {
        setSourceEventPosition(mutableBuffer, bufferOffset, batch.lowestPosition() + sourceIndex);
      } else {
        setSourceEventPosition(mutableBuffer, bufferOffset, batch.sourceEventPosition());
      }

      LogEntryDescriptor.setKey(mutableBuffer, bufferOffset, entry.entry().key());
      LogEntryDescriptor.setTimestamp(mutableBuffer, bufferOffset, ActorClock.currentTimeMillis());
      final var metadataLength = entry.entry().recordMetadata().getLength();
      LogEntryDescriptor.setMetadataLength(mutableBuffer, bufferOffset, (short) metadataLength);

      assert entry.entry().recordMetadata().getLength() > 0;
      assert entry.entry().recordValue().getLength() > 0;

      entry.entry().recordMetadata().write(mutableBuffer, metadataOffset(bufferOffset));
      entry.entry().recordValue().write(mutableBuffer, valueOffset(bufferOffset, metadataLength));
      bufferOffset += entryLength;
    }

    return buffer;
  }

  private int calculateBatchSize(final SequencedRecordBatch batch) {
    return batch.entries().stream().mapToInt(this::calculateEntrySize).sum();
  }

  private int calculateEntrySize(final SequencedBatchEntry entry) {
    return DataFrameDescriptor.alignedLength(
        LogEntryDescriptor.headerLength(entry.entry().recordMetadata().getLength())
            + entry.entry().recordValue().getLength()
            + DataFrameDescriptor.HEADER_LENGTH);
  }
}
