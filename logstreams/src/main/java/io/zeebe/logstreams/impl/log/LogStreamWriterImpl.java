/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.logstreams.impl.log;

import static io.zeebe.dispatcher.impl.log.LogBufferAppender.RESULT_PADDING_AT_END_OF_PARTITION;
import static io.zeebe.logstreams.impl.log.LogEntryDescriptor.headerLength;
import static io.zeebe.logstreams.impl.log.LogEntryDescriptor.metadataOffset;
import static io.zeebe.logstreams.impl.log.LogEntryDescriptor.setKey;
import static io.zeebe.logstreams.impl.log.LogEntryDescriptor.setMetadataLength;
import static io.zeebe.logstreams.impl.log.LogEntryDescriptor.setSourceEventPosition;
import static io.zeebe.logstreams.impl.log.LogEntryDescriptor.setTimestamp;
import static io.zeebe.logstreams.impl.log.LogEntryDescriptor.valueOffset;

import io.atomix.raft.zeebe.ZeebeEntry;
import io.zeebe.dispatcher.ClaimedFragment;
import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.dispatcher.impl.log.DataFrameDescriptor;
import io.zeebe.logstreams.log.LogStreamRecordWriter;
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
import org.agrona.LangUtil;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class LogStreamWriterImpl implements LogStreamRecordWriter {
  private final DirectBufferWriter metadataWriterInstance = new DirectBufferWriter();
  private final DirectBufferWriter bufferWriterInstance = new DirectBufferWriter();
  private final ClaimedFragment claimedFragment = new ClaimedFragment();

  private final Dispatcher logWriteBuffer;
  private final int partitionId;
  private long key;
  private long sourceRecordPosition = -1L;
  private BufferWriter metadataWriter;
  private BufferWriter valueWriter;
  private CRC32C checksum = new CRC32C();
  private final LoggedEventImpl reader = new LoggedEventImpl();
  private final UnsafeBuffer view = new UnsafeBuffer(0, 0);
  private final Queue<Long> eventChecksums = new LinkedBlockingQueue<>();
  private final Map<Long, CompletableActorFuture<Long>> eventFutures = new ConcurrentHashMap<>();

  LogStreamWriterImpl(final int partitionId, final Dispatcher logWriteBuffer) {
    this.logWriteBuffer = logWriteBuffer;
    this.partitionId = partitionId;

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

  @Override
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
  public Optional<ActorFuture<Long>> tryWrite() {
    CompletableActorFuture<Long> future = null;
    if (valueWriter == null) {
      future = new CompletableActorFuture<>();
      future.complete(0L);
      return Optional.of(future);
    }

    final int valueLength = valueWriter.getLength();
    final int metadataLength = metadataWriter.getLength();

    // claim fragment in log write buffer
    final long claimedPosition = claimLogEntry(valueLength, metadataLength);

    if (claimedPosition >= 0) {
      try {
        final MutableDirectBuffer writeBuffer = claimedFragment.getBuffer();
        final int bufferOffset = claimedFragment.getOffset();

        // write log entry header
        setSourceEventPosition(writeBuffer, bufferOffset, sourceRecordPosition);
        setKey(writeBuffer, bufferOffset, key);
        setTimestamp(writeBuffer, bufferOffset, ActorClock.currentTimeMillis());
        setMetadataLength(writeBuffer, bufferOffset, (short) metadataLength);

        if (metadataLength > 0) {
          metadataWriter.write(writeBuffer, metadataOffset(bufferOffset));
        }

        // write log entry
        valueWriter.write(writeBuffer, valueOffset(bufferOffset, metadataLength));

        future = storeChecksum(writeBuffer, bufferOffset);
        claimedFragment.commit(claimedPosition, this::updateRecord);
      } catch (final Exception e) {
        claimedFragment.abort();
        LangUtil.rethrowUnchecked(e);
      } finally {
        reset();
      }
    }

    return Optional.ofNullable(future);
  }

  private CompletableActorFuture<Long> storeChecksum(
      final MutableDirectBuffer writeBuffer, final int bufferOffset) {
    final int length = valueWriter.getLength() + headerLength(metadataWriter.getLength());
    final byte[] buff = new byte[length];

    writeBuffer.getBytes(bufferOffset, buff);
    checksum.reset();
    checksum.update(buff);
    eventChecksums.offer(checksum.getValue());

    final CompletableActorFuture<Long> future = new CompletableActorFuture<>();
    eventFutures.put(checksum.getValue(), future);
    return future;
  }

  private void updateRecord(
      final ZeebeEntry entry, final Long position, final Integer fragmentOffset) {
    LogStreamBatchWriterImpl.updateRecord(
        view, reader, eventChecksums, eventFutures, entry, position, fragmentOffset);
  }

  private long claimLogEntry(final int valueLength, final int metadataLength) {
    final int framedLength = valueLength + headerLength(metadataLength);

    long claimedPosition;

    do {

      claimedPosition = logWriteBuffer.claim(claimedFragment, framedLength, partitionId);
    } while (claimedPosition == RESULT_PADDING_AT_END_OF_PARTITION);

    return claimedPosition - DataFrameDescriptor.alignedFramedLength(framedLength);
  }
}
