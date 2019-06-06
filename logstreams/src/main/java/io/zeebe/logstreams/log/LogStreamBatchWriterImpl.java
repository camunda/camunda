/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.logstreams.log;

import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.alignedFramedLength;
import static io.zeebe.dispatcher.impl.log.LogBufferAppender.RESULT_PADDING_AT_END_OF_PARTITION;
import static io.zeebe.logstreams.impl.LogEntryDescriptor.HEADER_BLOCK_LENGTH;
import static io.zeebe.logstreams.impl.LogEntryDescriptor.headerLength;
import static io.zeebe.logstreams.impl.LogEntryDescriptor.metadataOffset;
import static io.zeebe.logstreams.impl.LogEntryDescriptor.setKey;
import static io.zeebe.logstreams.impl.LogEntryDescriptor.setMetadataLength;
import static io.zeebe.logstreams.impl.LogEntryDescriptor.setPosition;
import static io.zeebe.logstreams.impl.LogEntryDescriptor.setProducerId;
import static io.zeebe.logstreams.impl.LogEntryDescriptor.setSourceEventPosition;
import static io.zeebe.logstreams.impl.LogEntryDescriptor.setTimestamp;
import static io.zeebe.logstreams.impl.LogEntryDescriptor.valueOffset;
import static io.zeebe.util.EnsureUtil.ensureNotNull;
import static org.agrona.BitUtil.SIZE_OF_INT;
import static org.agrona.BitUtil.SIZE_OF_LONG;

import io.zeebe.dispatcher.ClaimedFragmentBatch;
import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.logstreams.impl.LogEntryDescriptor;
import io.zeebe.logstreams.log.LogStreamBatchWriter.LogEntryBuilder;
import io.zeebe.protocol.Protocol;
import io.zeebe.util.buffer.BufferWriter;
import io.zeebe.util.buffer.DirectBufferWriter;
import io.zeebe.util.sched.clock.ActorClock;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableDirectByteBuffer;
import org.agrona.LangUtil;
import org.agrona.MutableDirectBuffer;

public class LogStreamBatchWriterImpl implements LogStreamBatchWriter, LogEntryBuilder {
  private static final int INITIAL_BUFFER_CAPACITY = 1024 * 32;

  private final ClaimedFragmentBatch claimedBatch = new ClaimedFragmentBatch();

  private final MutableDirectBuffer eventBuffer =
      new ExpandableDirectByteBuffer(INITIAL_BUFFER_CAPACITY);

  private final DirectBufferWriter metadataWriterInstance = new DirectBufferWriter();
  private final DirectBufferWriter bufferWriterInstance = new DirectBufferWriter();

  private int eventBufferOffset;
  private int eventLength;
  private int eventCount;

  private Dispatcher logWriteBuffer;
  private int logId;

  private long key;

  private int producerId;

  private long sourceEventPosition;

  private BufferWriter metadataWriter;
  private BufferWriter valueWriter;

  public LogStreamBatchWriterImpl(final LogStream logStream) {
    wrap(logStream);
  }

  @Override
  public void wrap(final LogStream logStream) {
    this.logWriteBuffer = logStream.getWriteBuffer();
    this.logId = logStream.getPartitionId();

    reset();
  }

  @Override
  public LogStreamBatchWriter sourceRecordPosition(final long position) {
    this.sourceEventPosition = position;
    return this;
  }

  @Override
  public LogStreamBatchWriter producerId(final int producerId) {
    this.producerId = producerId;
    return this;
  }

  @Override
  public LogEntryBuilder event() {
    copyExistingEventToBuffer();
    resetEvent();
    return this;
  }

  @Override
  public LogEntryBuilder keyNull() {
    return key(LogEntryDescriptor.KEY_NULL_VALUE);
  }

  @Override
  public LogEntryBuilder key(final long key) {
    this.key = key;
    return this;
  }

  @Override
  public LogEntryBuilder metadata(final DirectBuffer buffer, final int offset, final int length) {
    metadataWriterInstance.wrap(buffer, offset, length);
    return this;
  }

  @Override
  public LogEntryBuilder metadata(final DirectBuffer buffer) {
    return metadata(buffer, 0, buffer.capacity());
  }

  @Override
  public LogEntryBuilder metadataWriter(final BufferWriter writer) {
    this.metadataWriter = writer;
    return this;
  }

  @Override
  public LogEntryBuilder value(
      final DirectBuffer value, final int valueOffset, final int valueLength) {
    return valueWriter(bufferWriterInstance.wrap(value, valueOffset, valueLength));
  }

  @Override
  public LogEntryBuilder value(final DirectBuffer value) {
    return value(value, 0, value.capacity());
  }

  @Override
  public LogEntryBuilder valueWriter(final BufferWriter writer) {
    this.valueWriter = writer;
    return this;
  }

  @Override
  public LogStreamBatchWriter done() {
    ensureNotNull("value", valueWriter);
    copyExistingEventToBuffer();
    resetEvent();
    return this;
  }

  public void copyExistingEventToBuffer() {
    // validation
    if (valueWriter == null) {
      return;
    }

    // copy event to buffer
    final int metadataLength = metadataWriter.getLength();
    final int valueLength = valueWriter.getLength();

    eventBuffer.putLong(eventBufferOffset, key, Protocol.ENDIANNESS);
    eventBufferOffset += SIZE_OF_LONG;

    eventBuffer.putInt(eventBufferOffset, metadataLength, Protocol.ENDIANNESS);
    eventBufferOffset += SIZE_OF_INT;

    eventBuffer.putInt(eventBufferOffset, valueLength, Protocol.ENDIANNESS);
    eventBufferOffset += SIZE_OF_INT;

    if (metadataLength > 0) {
      metadataWriter.write(eventBuffer, eventBufferOffset);
      eventBufferOffset += metadataLength;
    }

    valueWriter.write(eventBuffer, eventBufferOffset);
    eventBufferOffset += valueLength;

    eventLength += metadataLength + valueLength;
    eventCount += 1;
  }

  @Override
  public long tryWrite() {
    if (eventCount == 0) {
      if (valueWriter == null) {
        return 0;
      }

      copyExistingEventToBuffer();
    }

    long result = claimBatchForEvents();
    if (result >= 0) {
      try {
        // return position of last event
        result = writeEventsToBuffer(claimedBatch.getBuffer());

        claimedBatch.commit();
      } catch (final Exception e) {
        claimedBatch.abort();
        LangUtil.rethrowUnchecked(e);
      } finally {
        reset();
      }
    }
    return result;
  }

  private long claimBatchForEvents() {
    final int batchLength = eventLength + (eventCount * HEADER_BLOCK_LENGTH);

    long claimedPosition = -1;
    do {
      claimedPosition = logWriteBuffer.claim(claimedBatch, eventCount, batchLength);
    } while (claimedPosition == RESULT_PADDING_AT_END_OF_PARTITION);

    return claimedPosition;
  }

  private long writeEventsToBuffer(final MutableDirectBuffer writeBuffer) {
    long lastEventPosition = -1L;
    eventBufferOffset = 0;

    for (int i = 0; i < eventCount; i++) {
      final long key = eventBuffer.getLong(eventBufferOffset, Protocol.ENDIANNESS);
      eventBufferOffset += SIZE_OF_LONG;

      final int metadataLength = eventBuffer.getInt(eventBufferOffset, Protocol.ENDIANNESS);
      eventBufferOffset += SIZE_OF_INT;

      final int valueLength = eventBuffer.getInt(eventBufferOffset, Protocol.ENDIANNESS);
      eventBufferOffset += SIZE_OF_INT;

      final int fragmentLength = headerLength(metadataLength) + valueLength;

      // allocate fragment for log entry
      final long nextFragmentPosition = claimedBatch.nextFragment(fragmentLength, logId);
      final int bufferOffset = claimedBatch.getFragmentOffset();

      final long position = nextFragmentPosition - alignedFramedLength(fragmentLength);

      // write log entry header
      setPosition(writeBuffer, bufferOffset, position);
      setProducerId(writeBuffer, bufferOffset, producerId);
      setSourceEventPosition(writeBuffer, bufferOffset, sourceEventPosition);
      setKey(writeBuffer, bufferOffset, key);
      setTimestamp(writeBuffer, bufferOffset, ActorClock.currentTimeMillis());
      setMetadataLength(writeBuffer, bufferOffset, (short) metadataLength);

      if (metadataLength > 0) {
        writeBuffer.putBytes(
            metadataOffset(bufferOffset), eventBuffer, eventBufferOffset, metadataLength);
        eventBufferOffset += metadataLength;
      }

      // write log entry value
      writeBuffer.putBytes(
          valueOffset(bufferOffset, metadataLength), eventBuffer, eventBufferOffset, valueLength);
      eventBufferOffset += valueLength;

      lastEventPosition = position;
    }
    return lastEventPosition;
  }

  @Override
  public void reset() {
    eventBufferOffset = 0;
    eventLength = 0;
    eventCount = 0;
    sourceEventPosition = -1L;
    producerId = -1;
    resetEvent();
  }

  private void resetEvent() {
    key = LogEntryDescriptor.KEY_NULL_VALUE;

    metadataWriter = metadataWriterInstance;
    valueWriter = null;

    bufferWriterInstance.reset();
    metadataWriterInstance.reset();
  }
}
