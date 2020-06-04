/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.logstreams.impl.log;

import static io.zeebe.dispatcher.impl.log.LogBufferAppender.RESULT_PADDING_AT_END_OF_PARTITION;
import static io.zeebe.logstreams.impl.log.LogEntryDescriptor.HEADER_BLOCK_LENGTH;
import static io.zeebe.logstreams.impl.log.LogEntryDescriptor.headerLength;
import static io.zeebe.logstreams.impl.log.LogEntryDescriptor.metadataOffset;
import static io.zeebe.logstreams.impl.log.LogEntryDescriptor.setKey;
import static io.zeebe.logstreams.impl.log.LogEntryDescriptor.setMetadataLength;
import static io.zeebe.logstreams.impl.log.LogEntryDescriptor.setSourceEventPosition;
import static io.zeebe.logstreams.impl.log.LogEntryDescriptor.setTimestamp;
import static io.zeebe.logstreams.impl.log.LogEntryDescriptor.valueOffset;
import static io.zeebe.util.EnsureUtil.ensureNotNull;
import static org.agrona.BitUtil.SIZE_OF_INT;
import static org.agrona.BitUtil.SIZE_OF_LONG;

import io.atomix.raft.zeebe.ZeebeEntry;
import io.zeebe.dispatcher.ClaimedFragmentBatch;
import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.dispatcher.impl.log.DataFrameDescriptor;
import io.zeebe.logstreams.log.LogStreamBatchWriter;
import io.zeebe.logstreams.log.LogStreamBatchWriter.LogEntryBuilder;
import io.zeebe.protocol.Protocol;
import io.zeebe.util.buffer.BufferWriter;
import io.zeebe.util.buffer.DirectBufferWriter;
import io.zeebe.util.sched.clock.ActorClock;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.zip.CRC32C;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableDirectByteBuffer;
import org.agrona.LangUtil;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class LogStreamBatchWriterImpl implements LogStreamBatchWriter, LogEntryBuilder {

  private static final int INITIAL_BUFFER_CAPACITY = 1024 * 32;

  private final ClaimedFragmentBatch claimedBatch = new ClaimedFragmentBatch();

  private final MutableDirectBuffer eventBuffer =
      new ExpandableDirectByteBuffer(INITIAL_BUFFER_CAPACITY);

  private final DirectBufferWriter metadataWriterInstance = new DirectBufferWriter();
  private final DirectBufferWriter bufferWriterInstance = new DirectBufferWriter();

  private int eventBufferOffset;
  private int eventLength;
  private int eventCount;

  private final Dispatcher logWriteBuffer;
  private final int logId;

  private long key;

  private long sourceEventPosition;

  private BufferWriter metadataWriter;
  private BufferWriter valueWriter;
  private final Queue<Long> eventChecksums = new LinkedBlockingQueue<>();
  private final Map<Long, CompletableActorFuture<Long>> eventFutures = new ConcurrentHashMap<>();

  private CRC32C checksum = new CRC32C();
  private final LoggedEventImpl reader = new LoggedEventImpl();
  private final UnsafeBuffer view = new UnsafeBuffer(0, 0);

  LogStreamBatchWriterImpl(final int partitionId, final Dispatcher dispatcher) {
    this.logWriteBuffer = dispatcher;
    this.logId = partitionId;

    reset();
  }

  @Override
  public LogStreamBatchWriter sourceRecordPosition(final long position) {
    this.sourceEventPosition = position;
    return this;
  }

  @Override
  public LogEntryBuilder event() {
    copyExistingEventToBuffer();
    resetEvent();

    return this;
  }

  @Override
  public int getMaxFragmentLength() {
    return logWriteBuffer.getMaxFragmentLength();
  }

  @Override
  public void reset() {
    eventBufferOffset = 0;
    eventLength = 0;
    eventCount = 0;
    sourceEventPosition = -1L;
    resetEvent();
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
  public Optional<ActorFuture<Long>> tryWrite() {
    CompletableActorFuture<Long> future = null;
    if (eventCount == 0) {
      if (valueWriter == null) {
        future = new CompletableActorFuture<>();
        future.complete(0L);
        return Optional.of(future);
      }

      copyExistingEventToBuffer();
    }

    if (claimBatchForEvents() >= 0) {
      try {
        future = writeEventsToBuffer(claimedBatch.getBuffer());
        claimedBatch.commit(this::updateRecord);
      } catch (final Exception e) {
        claimedBatch.abort();
        LangUtil.rethrowUnchecked(e);
      } finally {
        reset();
      }
    }

    return Optional.ofNullable(future);
  }

  private void updateRecord(
      final ZeebeEntry entry, final Long position, final Integer fragmentOffset) {
    updateRecord(view, reader, eventChecksums, eventFutures, entry, position, fragmentOffset);
  }

  static void updateRecord(
      final UnsafeBuffer view,
      final LoggedEventImpl reader,
      final Queue<Long> eventChecksums,
      final Map<Long, CompletableActorFuture<Long>> eventFutures,
      final ZeebeEntry entry,
      final Long position,
      final Integer fragmentOffset) {
    view.wrap(entry.data());
    reader.wrap(view, fragmentOffset);

    final CRC32C checksum = new CRC32C();
    checksum.reset();
    checksum.update(
        reader.getValueBuffer().byteArray(), reader.getMessageOffset(), reader.getMessageLength());

    final Long storedChecksum = eventChecksums.poll();
    if (storedChecksum == null || checksum.getValue() != storedChecksum) {
      final IllegalStateException exception =
          new IllegalStateException(
              String.format(
                  "Record %d in entry with index %d is not in expected order.",
                  position & 0xFF, position >> 8));

      eventFutures.entrySet().stream()
          .sorted(Map.Entry.comparingByKey())
          .forEachOrdered(e -> e.getValue().completeExceptionally(exception));
      eventFutures.clear();
      eventChecksums.clear();

      throw exception;
    }

    LogEntryDescriptor.setPosition(
        view, DataFrameDescriptor.messageOffset(fragmentOffset), position);
    final CompletableActorFuture<Long> future = eventFutures.remove(storedChecksum);
    if (future != null) {
      future.complete(position);
    }
  }

  private long claimBatchForEvents() {
    final int batchLength = eventLength + (eventCount * HEADER_BLOCK_LENGTH);

    long claimedPosition;
    do {
      claimedPosition = logWriteBuffer.claim(claimedBatch, eventCount, batchLength);
    } while (claimedPosition == RESULT_PADDING_AT_END_OF_PARTITION);

    return claimedPosition;
  }

  private CompletableActorFuture<Long> writeEventsToBuffer(final MutableDirectBuffer writeBuffer) {
    long lastChecksum = -1L;
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
      claimedBatch.nextFragment(fragmentLength, logId);
      final int bufferOffset = claimedBatch.getFragmentOffset();

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

      lastChecksum = storeChecksum(writeBuffer, bufferOffset, fragmentLength);
    }

    final CompletableActorFuture<Long> future = new CompletableActorFuture<>();
    eventFutures.put(lastChecksum, future);
    return future;
  }

  private long storeChecksum(
      final MutableDirectBuffer writeBuffer, final int bufferOffset, final int fragmentLength) {
    final byte[] buff = new byte[fragmentLength];
    writeBuffer.getBytes(bufferOffset, buff);

    checksum.reset();
    checksum.update(buff);
    eventChecksums.offer(checksum.getValue());

    return checksum.getValue();
  }

  private void resetEvent() {
    key = LogEntryDescriptor.KEY_NULL_VALUE;

    metadataWriter = metadataWriterInstance;
    valueWriter = null;

    bufferWriterInstance.reset();
    metadataWriterInstance.reset();
  }
}
