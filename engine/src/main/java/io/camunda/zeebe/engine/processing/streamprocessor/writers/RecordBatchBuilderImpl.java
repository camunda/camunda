/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.streamprocessor.writers;

import static io.camunda.zeebe.logstreams.impl.log.LogEntryDescriptor.HEADER_BLOCK_LENGTH;
import static io.camunda.zeebe.util.EnsureUtil.ensureNotNull;
import static org.agrona.BitUtil.SIZE_OF_INT;
import static org.agrona.BitUtil.SIZE_OF_LONG;

import io.camunda.zeebe.logstreams.impl.log.LogEntryDescriptor;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.util.buffer.BufferWriter;
import io.camunda.zeebe.util.buffer.DirectBufferWriter;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableDirectByteBuffer;
import org.agrona.MutableDirectBuffer;

public final class RecordBatchBuilderImpl implements LogEntrysBuilder, BufferWriter {

  private static final int INITIAL_BUFFER_CAPACITY = 1024 * 32;
  // todo we can allocate one buffer with max message size here and then it would simplify this -
  // and limit checking
  private final MutableDirectBuffer eventBuffer =
      new ExpandableDirectByteBuffer(INITIAL_BUFFER_CAPACITY);
  private final DirectBufferWriter metadataWriterInstance = new DirectBufferWriter();
  private final DirectBufferWriter bufferWriterInstance = new DirectBufferWriter();
  private int eventBufferOffset;
  private int eventLength;
  private int eventCount;
  private long key;
  private int sourceIndex;
  private BufferWriter metadataWriter;
  private BufferWriter valueWriter;

  private void copyExistingEventToBuffer() {
    // validation
    if (valueWriter == null) {
      return;
    }

    // copy event to buffer
    final int metadataLength = metadataWriter.getLength();
    final int valueLength = valueWriter.getLength();

    eventBuffer.putLong(eventBufferOffset, key, Protocol.ENDIANNESS);
    eventBufferOffset += SIZE_OF_LONG;

    eventBuffer.putInt(eventBufferOffset, sourceIndex, Protocol.ENDIANNESS);
    eventBufferOffset += SIZE_OF_INT;

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

  private void resetEvent() {
    key = LogEntryDescriptor.KEY_NULL_VALUE;
    sourceIndex = -1;

    metadataWriter = metadataWriterInstance;
    valueWriter = null;

    bufferWriterInstance.reset();
    metadataWriterInstance.reset();
  }

  @Override
  public boolean canWriteAdditionalEvent(final int length) {
    final var count = eventCount + 1;
    final var batchLength = eventLength + length + (count * HEADER_BLOCK_LENGTH);
    return batchLength < getMaxFragmentLength();
  }

  @Override
  public int getMaxFragmentLength() {
    return (4 * 1024 * 1024); // hard coded for now - todo fix this
  }

  @Override
  public void reset() {
    eventBufferOffset = 0;
    eventLength = 0;
    eventCount = 0;
    resetEvent();
  }

  @Override
  public LogEntrysBuilder event() {
    copyExistingEventToBuffer();
    resetEvent();
    return this;
  }

  @Override
  public LogEntrysBuilder keyNull() {
    return key(LogEntryDescriptor.KEY_NULL_VALUE);
  }

  @Override
  public LogEntrysBuilder key(final long key) {
    this.key = key;
    return this;
  }

  @Override
  public LogEntrysBuilder sourceIndex(final int index) {
    sourceIndex = index;
    return this;
  }

  @Override
  public LogEntrysBuilder metadata(final DirectBuffer buffer, final int offset, final int length) {
    metadataWriterInstance.wrap(buffer, offset, length);
    return this;
  }

  @Override
  public LogEntrysBuilder metadata(final DirectBuffer buffer) {
    return metadata(buffer, 0, buffer.capacity());
  }

  @Override
  public LogEntrysBuilder metadataWriter(final BufferWriter writer) {
    metadataWriter = writer;
    return this;
  }

  @Override
  public LogEntrysBuilder value(
      final DirectBuffer value, final int valueOffset, final int valueLength) {
    return valueWriter(bufferWriterInstance.wrap(value, valueOffset, valueLength));
  }

  @Override
  public LogEntrysBuilder value(final DirectBuffer value) {
    return value(value, 0, value.capacity());
  }

  @Override
  public LogEntrysBuilder valueWriter(final BufferWriter writer) {
    valueWriter = writer;
    return this;
  }

  @Override
  public LogEntrysBuilder done() {
    ensureNotNull("value", valueWriter);
    copyExistingEventToBuffer();
    resetEvent();
    return this;
  }

  public int getEventLength() {
    return eventLength;
  }

  public int getEventCount() {
    return eventCount;
  }

  @Override
  public int getLength() {
    return eventLength;
  }

  @Override
  public void write(final MutableDirectBuffer buffer, final int offset) {
    eventBuffer.getBytes(0, buffer, offset, eventLength);
  }
}
