/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.logstreams.impl.log;

import static io.camunda.zeebe.dispatcher.impl.log.LogBufferAppender.RESULT_PADDING_AT_END_OF_PARTITION;
import static io.camunda.zeebe.logstreams.impl.log.LogEntryDescriptor.HEADER_BLOCK_LENGTH;
import static io.camunda.zeebe.logstreams.impl.log.LogEntryDescriptor.headerLength;

import io.camunda.zeebe.dispatcher.ClaimedFragmentBatch;
import io.camunda.zeebe.dispatcher.Dispatcher;
import io.camunda.zeebe.logstreams.log.LogAppendEntry;
import io.camunda.zeebe.logstreams.log.LogStreamBatchWriter;
import io.camunda.zeebe.scheduler.clock.ActorClock;
import org.agrona.LangUtil;

final class LogStreamBatchWriterImpl implements LogStreamBatchWriter {

  private final ClaimedFragmentBatch claimedBatch = new ClaimedFragmentBatch();
  private final LogAppendEntrySerializer serializer = new LogAppendEntrySerializer();
  private final Dispatcher logWriteBuffer;
  private final int logId;

  LogStreamBatchWriterImpl(final int partitionId, final Dispatcher dispatcher) {
    logWriteBuffer = dispatcher;
    logId = partitionId;
  }

  @Override
  public boolean canWriteEvents(final int eventCount, final int batchSize) {
    return logWriteBuffer.canClaimFragmentBatch(eventCount, batchSize);
  }

  @Override
  public long tryWrite(
      final Iterable<? extends LogAppendEntry> appendEntries, final long sourcePosition) {
    int batchBytes = 0;
    int batchCount = 0;

    for (final var entry : appendEntries) {
      batchBytes += entry.getLength();
      batchCount++;
    }

    if (batchCount == 0) {
      return 0;
    }

    final long position = claimBatchForEvents(batchCount, batchBytes);
    if (position >= 0) {
      try {
        // return position of last event
        writeEntries(appendEntries, position, sourcePosition);
        claimedBatch.commit();
      } catch (final Exception e) {
        claimedBatch.abort();
        LangUtil.rethrowUnchecked(e);
      }
    }
    return position + batchCount - 1;
  }

  private long claimBatchForEvents(final int batchCount, final int batchBytes) {
    final var batchLength = computeBatchLength(batchCount, batchBytes);
    long claimedPosition;

    do {
      claimedPosition = logWriteBuffer.claimFragmentBatch(claimedBatch, batchCount, batchLength);
    } while (claimedPosition == RESULT_PADDING_AT_END_OF_PARTITION);

    return claimedPosition;
  }

  private void writeEntries(
      final Iterable<? extends LogAppendEntry> appendEntries,
      final long firstPosition,
      final long sourcePosition) {
    final var entryTimestamp = ActorClock.currentTimeMillis();
    int index = 0;

    for (final var entry : appendEntries) {
      final var sourceIndex = entry.sourceIndex();
      final var entrySourcePosition =
          (sourceIndex >= 0 && sourceIndex < index) ? firstPosition + sourceIndex : sourcePosition;
      final var position = firstPosition + index;

      writeEntry(entry, position, entrySourcePosition, entryTimestamp);
      index++;
    }
  }

  private void writeEntry(
      final LogAppendEntry entry,
      final long position,
      final long sourcePosition,
      final long entryTimestamp) {
    final var fragmentLength =
        headerLength(entry.recordMetadata().getLength()) + entry.recordValue().getLength();
    claimedBatch.nextFragment(fragmentLength, logId);

    serializer.serialize(
        claimedBatch.getBuffer(),
        claimedBatch.getFragmentOffset(),
        entry,
        position,
        sourcePosition,
        entryTimestamp);
  }

  private int computeBatchLength(final int eventsCount, final int eventsLength) {
    return eventsLength + (eventsCount * HEADER_BLOCK_LENGTH);
  }
}
