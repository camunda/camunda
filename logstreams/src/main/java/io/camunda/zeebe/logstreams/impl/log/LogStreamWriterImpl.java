/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.logstreams.impl.log;

import static io.camunda.zeebe.dispatcher.impl.log.LogBufferAppender.RESULT_PADDING_AT_END_OF_PARTITION;
import static io.camunda.zeebe.logstreams.impl.log.LogEntryDescriptor.headerLength;
import static io.camunda.zeebe.logstreams.impl.log.LogEntryDescriptor.metadataOffset;
import static io.camunda.zeebe.logstreams.impl.log.LogEntryDescriptor.setKey;
import static io.camunda.zeebe.logstreams.impl.log.LogEntryDescriptor.setMetadataLength;
import static io.camunda.zeebe.logstreams.impl.log.LogEntryDescriptor.setPosition;
import static io.camunda.zeebe.logstreams.impl.log.LogEntryDescriptor.setSourceEventPosition;
import static io.camunda.zeebe.logstreams.impl.log.LogEntryDescriptor.setTimestamp;
import static io.camunda.zeebe.logstreams.impl.log.LogEntryDescriptor.valueOffset;

import io.camunda.zeebe.dispatcher.ClaimedFragment;
import io.camunda.zeebe.dispatcher.Dispatcher;
import io.camunda.zeebe.logstreams.log.LogAppendEntry;
import io.camunda.zeebe.logstreams.log.LogStreamWriter;
import io.camunda.zeebe.scheduler.clock.ActorClock;
import org.agrona.LangUtil;

final class LogStreamWriterImpl implements LogStreamWriter {
  private final ClaimedFragment claimedFragment = new ClaimedFragment();
  private final Dispatcher logWriteBuffer;

  private final int partitionId;

  LogStreamWriterImpl(final int partitionId, final Dispatcher logWriteBuffer) {
    this.logWriteBuffer = logWriteBuffer;
    this.partitionId = partitionId;
  }

  @Override
  public long tryWrite(final LogAppendEntry appendEntry, final long sourcePosition) {
    final var recordValue = appendEntry.recordValue();
    final var recordMetadata = appendEntry.recordMetadata();

    final int valueLength = recordValue.getLength();
    final int metadataLength = recordMetadata.getLength();

    if (valueLength == 0) {
      return 0;
    }

    // claim fragment in log write buffer
    final long claimedPosition = claimLogEntry(valueLength, metadataLength);
    if (claimedPosition >= 0) {
      try {
        writeEntry(appendEntry, claimedPosition, sourcePosition);
        claimedFragment.commit();
      } catch (final Exception e) {
        claimedFragment.abort();
        LangUtil.rethrowUnchecked(e);
      }
    }

    return claimedPosition;
  }

  private long claimLogEntry(final int valueLength, final int metadataLength) {
    final int framedLength = valueLength + headerLength(metadataLength);
    long claimedPosition;

    do {
      claimedPosition =
          logWriteBuffer.claimSingleFragment(claimedFragment, framedLength, partitionId);
    } while (claimedPosition == RESULT_PADDING_AT_END_OF_PARTITION);

    return claimedPosition;
  }

  private void writeEntry(
      final LogAppendEntry appendEntry, final long position, final long sourcePosition) {
    final var writeBuffer = claimedFragment.getBuffer();
    final var bufferOffset = claimedFragment.getOffset();
    final var metadataLength = (short) appendEntry.recordMetadata().getLength();

    setPosition(writeBuffer, bufferOffset, position);
    setSourceEventPosition(writeBuffer, bufferOffset, sourcePosition);
    setKey(writeBuffer, bufferOffset, appendEntry.key());
    setTimestamp(writeBuffer, bufferOffset, ActorClock.currentTimeMillis());
    setMetadataLength(writeBuffer, bufferOffset, metadataLength);

    if (metadataLength > 0) {
      appendEntry.recordMetadata().write(writeBuffer, metadataOffset(bufferOffset));
    }

    appendEntry.recordValue().write(writeBuffer, valueOffset(bufferOffset, metadataLength));
  }
}
