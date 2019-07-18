/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.logstreams.log;

import static io.zeebe.dispatcher.impl.log.LogBufferAppender.RESULT_PADDING_AT_END_OF_PARTITION;
import static io.zeebe.logstreams.impl.LogEntryDescriptor.headerLength;
import static io.zeebe.logstreams.impl.LogEntryDescriptor.metadataOffset;
import static io.zeebe.logstreams.impl.LogEntryDescriptor.setKey;
import static io.zeebe.logstreams.impl.LogEntryDescriptor.setMetadataLength;
import static io.zeebe.logstreams.impl.LogEntryDescriptor.setPosition;
import static io.zeebe.logstreams.impl.LogEntryDescriptor.setSourceEventPosition;
import static io.zeebe.logstreams.impl.LogEntryDescriptor.setTimestamp;
import static io.zeebe.logstreams.impl.LogEntryDescriptor.valueOffset;

import io.zeebe.dispatcher.ClaimedFragment;
import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.dispatcher.impl.log.DataFrameDescriptor;
import io.zeebe.logstreams.impl.LogEntryDescriptor;
import io.zeebe.util.buffer.BufferWriter;
import io.zeebe.util.buffer.DirectBufferWriter;
import io.zeebe.util.sched.clock.ActorClock;
import org.agrona.DirectBuffer;
import org.agrona.LangUtil;
import org.agrona.MutableDirectBuffer;

public class LogStreamWriterImpl implements LogStreamRecordWriter {
  protected final DirectBufferWriter metadataWriterInstance = new DirectBufferWriter();
  protected final DirectBufferWriter bufferWriterInstance = new DirectBufferWriter();
  protected final ClaimedFragment claimedFragment = new ClaimedFragment();
  protected long key;
  protected long sourceRecordPosition = -1L;
  protected BufferWriter metadataWriter;
  protected BufferWriter valueWriter;
  private LogStream logStream;

  public LogStreamWriterImpl() {}

  public LogStreamWriterImpl(final LogStream log) {
    wrap(log);
  }

  @Override
  public void wrap(final LogStream log) {
    this.logStream = log;
    reset();
  }

  @Override
  public LogStreamRecordWriter keyNull() {
    return key(LogEntryDescriptor.KEY_NULL_VALUE);
  }

  @Override
  public LogStreamRecordWriter key(final long key) {
    this.key = key;
    return this;
  }

  public LogStreamRecordWriter sourceRecordPosition(final long position) {
    this.sourceRecordPosition = position;
    return this;
  }

  @Override
  public LogStreamRecordWriter metadata(
      final DirectBuffer buffer, final int offset, final int length) {
    metadataWriterInstance.wrap(buffer, offset, length);
    return this;
  }

  @Override
  public LogStreamRecordWriter metadata(final DirectBuffer buffer) {
    return metadata(buffer, 0, buffer.capacity());
  }

  @Override
  public LogStreamRecordWriter metadataWriter(final BufferWriter writer) {
    this.metadataWriter = writer;
    return this;
  }

  @Override
  public LogStreamRecordWriter value(
      final DirectBuffer value, final int valueOffset, final int valueLength) {
    return valueWriter(bufferWriterInstance.wrap(value, valueOffset, valueLength));
  }

  @Override
  public LogStreamRecordWriter value(final DirectBuffer value) {
    return value(value, 0, value.capacity());
  }

  @Override
  public LogStreamRecordWriter valueWriter(final BufferWriter writer) {
    this.valueWriter = writer;
    return this;
  }

  @Override
  public void reset() {
    key = LogEntryDescriptor.KEY_NULL_VALUE;
    metadataWriter = metadataWriterInstance;
    valueWriter = null;
    sourceRecordPosition = -1L;

    bufferWriterInstance.reset();
    metadataWriterInstance.reset();
  }

  @Override
  public long tryWrite() {
    if (valueWriter == null) {
      return 0;
    }

    long result = -1;

    final int valueLength = valueWriter.getLength();
    final int metadataLength = metadataWriter.getLength();

    // claim fragment in log write buffer
    final long claimedPosition = claimLogEntry(valueLength, metadataLength);

    if (claimedPosition >= 0) {
      try {
        final MutableDirectBuffer writeBuffer = claimedFragment.getBuffer();
        final int bufferOffset = claimedFragment.getOffset();

        // write log entry header
        setPosition(writeBuffer, bufferOffset, claimedPosition);
        setSourceEventPosition(writeBuffer, bufferOffset, sourceRecordPosition);
        setKey(writeBuffer, bufferOffset, key);
        setTimestamp(writeBuffer, bufferOffset, ActorClock.currentTimeMillis());
        setMetadataLength(writeBuffer, bufferOffset, (short) metadataLength);

        if (metadataLength > 0) {
          metadataWriter.write(writeBuffer, metadataOffset(bufferOffset));
        }

        // write log entry
        valueWriter.write(writeBuffer, valueOffset(bufferOffset, metadataLength));

        result = claimedPosition;
        claimedFragment.commit();
      } catch (final Exception e) {
        claimedFragment.abort();
        LangUtil.rethrowUnchecked(e);
      } finally {
        reset();
      }
    }

    return result;
  }

  private long claimLogEntry(final int valueLength, final int metadataLength) {
    final int framedLength = valueLength + headerLength(metadataLength);

    long claimedPosition = -1;
    final Dispatcher logWriteBuffer = logStream.getWriteBuffer();
    final int logId = logStream.getPartitionId();

    do {

      claimedPosition = logWriteBuffer.claim(claimedFragment, framedLength, logId);
    } while (claimedPosition == RESULT_PADDING_AT_END_OF_PARTITION);

    return claimedPosition - DataFrameDescriptor.alignedFramedLength(framedLength);
  }
}
