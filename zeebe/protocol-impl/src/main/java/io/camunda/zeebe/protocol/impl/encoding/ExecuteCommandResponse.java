/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.encoding;

import static io.camunda.zeebe.protocol.record.ExecuteCommandResponseEncoder.keyNullValue;
import static io.camunda.zeebe.protocol.record.ExecuteCommandResponseEncoder.partitionIdNullValue;

import io.camunda.zeebe.protocol.record.ExecuteCommandResponseDecoder;
import io.camunda.zeebe.protocol.record.ExecuteCommandResponseEncoder;
import io.camunda.zeebe.protocol.record.MessageHeaderDecoder;
import io.camunda.zeebe.protocol.record.MessageHeaderEncoder;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.util.buffer.BufferReader;
import io.camunda.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class ExecuteCommandResponse implements BufferReader, BufferWriter {

  private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
  private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();

  private final ExecuteCommandResponseEncoder bodyEncoder = new ExecuteCommandResponseEncoder();
  private final ExecuteCommandResponseDecoder bodyDecoder = new ExecuteCommandResponseDecoder();
  private final DirectBuffer value = new UnsafeBuffer(0, 0);
  private final DirectBuffer rejectionReason = new UnsafeBuffer(0, 0);
  private int partitionId;
  private long key;
  private RecordType recordType;
  private ValueType valueType;
  private Intent intent;
  private RejectionType rejectionType;

  public ExecuteCommandResponse() {
    reset();
  }

  public ExecuteCommandResponse reset() {
    partitionId = partitionIdNullValue();
    key = keyNullValue();
    recordType = RecordType.NULL_VAL;
    valueType = ValueType.NULL_VAL;
    intent = Intent.UNKNOWN;
    rejectionType = RejectionType.NULL_VAL;
    value.wrap(0, 0);
    rejectionReason.wrap(0, 0);

    return this;
  }

  public int getPartitionId() {
    return partitionId;
  }

  public ExecuteCommandResponse setPartitionId(final int partitionId) {
    this.partitionId = partitionId;
    return this;
  }

  public long getKey() {
    return key;
  }

  public ExecuteCommandResponse setKey(final long key) {
    this.key = key;
    return this;
  }

  public RecordType getRecordType() {
    return recordType;
  }

  public ExecuteCommandResponse setRecordType(final RecordType recordType) {
    this.recordType = recordType;
    return this;
  }

  public ValueType getValueType() {
    return valueType;
  }

  public ExecuteCommandResponse setValueType(final ValueType valueType) {
    this.valueType = valueType;
    return this;
  }

  public Intent getIntent() {
    return intent;
  }

  public ExecuteCommandResponse setIntent(final Intent intent) {
    this.intent = intent;
    return this;
  }

  public RejectionType getRejectionType() {
    return rejectionType;
  }

  public ExecuteCommandResponse setRejectionType(final RejectionType rejectionType) {
    this.rejectionType = rejectionType;
    return this;
  }

  public DirectBuffer getValue() {
    return value;
  }

  public ExecuteCommandResponse setValue(
      final DirectBuffer buffer, final int offset, final int length) {
    value.wrap(buffer, offset, length);
    return this;
  }

  public DirectBuffer getRejectionReason() {
    return rejectionReason;
  }

  public ExecuteCommandResponse setRejectionReason(
      final DirectBuffer buffer, final int offset, final int length) {
    rejectionReason.wrap(buffer, offset, length);
    return this;
  }

  @Override
  public void wrap(final DirectBuffer buffer, int offset, final int length) {
    reset();

    final int frameEnd = offset + length;

    headerDecoder.wrap(buffer, offset);

    offset += headerDecoder.encodedLength();

    bodyDecoder.wrap(buffer, offset, headerDecoder.blockLength(), headerDecoder.version());

    partitionId = bodyDecoder.partitionId();
    key = bodyDecoder.key();
    recordType = bodyDecoder.recordType();
    valueType = bodyDecoder.valueType();
    intent = Intent.fromProtocolValue(valueType, bodyDecoder.intent());
    rejectionType = bodyDecoder.rejectionType();

    offset += bodyDecoder.sbeBlockLength();

    final int valueLength = bodyDecoder.valueLength();
    offset += ExecuteCommandResponseDecoder.valueHeaderLength();

    if (valueLength > 0) {
      value.wrap(buffer, offset, valueLength);
    }
    offset += valueLength;
    bodyDecoder.limit(offset);

    final int rejectionReasonLength = bodyDecoder.rejectionReasonLength();
    offset += ExecuteCommandResponseDecoder.rejectionReasonHeaderLength();

    if (rejectionReasonLength > 0) {
      rejectionReason.wrap(buffer, offset, rejectionReasonLength);
    }
    offset += rejectionReasonLength;
    bodyDecoder.limit(offset);

    assert bodyDecoder.limit() == frameEnd
        : "Decoder read only to position "
            + bodyDecoder.limit()
            + " but expected "
            + frameEnd
            + " as final position";
  }

  @Override
  public int getLength() {
    return MessageHeaderEncoder.ENCODED_LENGTH
        + ExecuteCommandResponseEncoder.BLOCK_LENGTH
        + ExecuteCommandResponseEncoder.valueHeaderLength()
        + value.capacity()
        + ExecuteCommandResponseEncoder.rejectionReasonHeaderLength()
        + rejectionReason.capacity();
  }

  @Override
  public int write(final MutableDirectBuffer buffer, int offset) {
    final int initialOffset = offset;
    headerEncoder
        .wrap(buffer, offset)
        .blockLength(bodyEncoder.sbeBlockLength())
        .templateId(bodyEncoder.sbeTemplateId())
        .schemaId(bodyEncoder.sbeSchemaId())
        .version(bodyEncoder.sbeSchemaVersion());

    offset += headerEncoder.encodedLength();

    bodyEncoder
        .wrap(buffer, offset)
        .partitionId(partitionId)
        .key(key)
        .recordType(recordType)
        .valueType(valueType)
        .intent(intent.value())
        .rejectionType(rejectionType)
        .putValue(value, 0, value.capacity())
        .putRejectionReason(rejectionReason, 0, rejectionReason.capacity());

    return bodyEncoder.limit() - initialOffset;
  }
}
