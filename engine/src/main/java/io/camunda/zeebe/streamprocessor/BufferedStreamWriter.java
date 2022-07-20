/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.streamprocessor;

import static org.agrona.BitUtil.SIZE_OF_INT;
import static org.agrona.BitUtil.SIZE_OF_LONG;

import io.camunda.zeebe.logstreams.impl.log.LogEntryDescriptor;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.util.function.UnaryOperator;
import org.agrona.ExpandableDirectByteBuffer;
import org.agrona.MutableDirectBuffer;

/** Stream writer that writes into a byte buffer instead of writing directly to the stream */
final class BufferedStreamWriter {

  private static final int INITIAL_BUFFER_CAPACITY = 1024 * 32;

  // todo we can allocate one buffer with max message size here and then it would simplify this -
  // and limit checking
  private final MutableDirectBuffer recordBuffer =
      new ExpandableDirectByteBuffer(INITIAL_BUFFER_CAPACITY);

  private final RecordMetadata metadata = new RecordMetadata();

  private int recordBufferOffset;
  private int recordCount;
  private final UnaryOperator<Integer> capacityCalculator;

  /**
   * @param capacityCalculator function that takes current buffer size as input and returns free
   *     capacity
   */
  BufferedStreamWriter(final UnaryOperator<Integer> capacityCalculator) {
    reset();

    this.capacityCalculator = capacityCalculator;
  }

  MutableDirectBuffer getRecordBuffer() {
    return recordBuffer;
  }

  int getRecordBufferOffset() {
    return recordBufferOffset;
  }

  int getRecordCount() {
    return recordCount;
  }

  void appendRecord(
      final long key,
      final int sourceIndex,
      final RecordType type,
      final Intent intent,
      final RejectionType rejectionType,
      final String rejectionReason,
      final ValueType valueType,
      final BufferWriter valueWriter) {

    // copy record to buffer
    writeKey(key);
    writeSourceIndex(sourceIndex);

    initMetadata(type, intent, rejectionType, rejectionReason, valueType);
    final var metadataLength = metadata.getLength();
    writeMetadataLength(metadataLength);

    final var valueLength = valueWriter.getLength();
    writeValueLength(valueLength);

    writeMetadata(metadataLength);
    writeValue(valueWriter, valueLength);

    recordCount += 1;
  }

  boolean canWriteAdditionalEvent(final int length) {
    return length < capacityCalculator.apply(recordBufferOffset);
  }

  private void writeKey(long key) {
    if (key < 0) {
      key = LogEntryDescriptor.KEY_NULL_VALUE;
    }
    recordBuffer.putLong(recordBufferOffset, key, Protocol.ENDIANNESS);
    recordBufferOffset += SIZE_OF_LONG;
  }

  private void writeSourceIndex(final int sourceIndex) {
    recordBuffer.putInt(recordBufferOffset, sourceIndex, Protocol.ENDIANNESS);
    recordBufferOffset += SIZE_OF_INT;
  }

  private void initMetadata(
      final RecordType type,
      final Intent intent,
      final RejectionType rejectionType,
      final String rejectionReason,
      final ValueType valueType) {
    metadata.reset();

    metadata
        .recordType(type)
        .valueType(valueType)
        .intent(intent)
        .rejectionType(rejectionType)
        .rejectionReason(rejectionReason);
  }

  private void writeMetadataLength(final int metadataLength) {
    recordBuffer.putInt(recordBufferOffset, metadataLength, Protocol.ENDIANNESS);
    recordBufferOffset += SIZE_OF_INT;
  }

  private void writeValueLength(final int valueLength) {
    recordBuffer.putInt(recordBufferOffset, valueLength, Protocol.ENDIANNESS);
    recordBufferOffset += SIZE_OF_INT;
  }

  private void writeMetadata(final int metadataLength) {
    if (metadataLength > 0) {
      metadata.write(recordBuffer, recordBufferOffset);
      recordBufferOffset += metadataLength;
    }
  }

  private void writeValue(final BufferWriter valueWriter, final int valueLength) {
    valueWriter.write(recordBuffer, recordBufferOffset);
    recordBufferOffset += valueLength;
  }

  void reset() {
    recordBufferOffset = 0;
    recordCount = 0;
    metadata.reset();
  }
}
