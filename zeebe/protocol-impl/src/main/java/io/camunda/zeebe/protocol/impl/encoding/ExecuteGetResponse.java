/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.encoding;

import io.camunda.zeebe.protocol.record.ExecuteGetResponseDecoder;
import io.camunda.zeebe.protocol.record.ExecuteGetResponseEncoder;
import io.camunda.zeebe.protocol.record.MessageHeaderDecoder;
import io.camunda.zeebe.protocol.record.MessageHeaderEncoder;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ResponseType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.util.buffer.BufferReader;
import io.camunda.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class ExecuteGetResponse implements BufferReader, BufferWriter {

  private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
  private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();

  private final ExecuteGetResponseEncoder bodyEncoder = new ExecuteGetResponseEncoder();
  private final ExecuteGetResponseDecoder bodyDecoder = new ExecuteGetResponseDecoder();
  private final DirectBuffer value = new UnsafeBuffer(0, 0);
  private final DirectBuffer rejectionReason = new UnsafeBuffer(0, 0);
  private int partitionId;
  private long key;
  private ResponseType responseType;
  private ValueType valueType;
  private Intent intent;
  private RejectionType rejectionType;

  public ExecuteGetResponse() {
    reset();
  }

  public ExecuteGetResponse reset() {
    partitionId = ExecuteGetResponseDecoder.partitionIdNullValue();
    key = ExecuteGetResponseDecoder.keyNullValue();
    responseType = ResponseType.NULL_VAL;
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

  public ExecuteGetResponse setPartitionId(final int partitionId) {
    this.partitionId = partitionId;
    return this;
  }

  public long getKey() {
    return key;
  }

  public ExecuteGetResponse setKey(final long key) {
    this.key = key;
    return this;
  }

  public ResponseType getResponseType() {
    return responseType;
  }

  public ExecuteGetResponse setResponseType(final ResponseType responseType) {
    this.responseType = responseType;
    return this;
  }

  public ValueType getValueType() {
    return valueType;
  }

  public ExecuteGetResponse setValueType(final ValueType valueType) {
    this.valueType = valueType;
    return this;
  }

  public Intent getIntent() {
    return intent;
  }

  public ExecuteGetResponse setIntent(final Intent intent) {
    this.intent = intent;
    return this;
  }

  public RejectionType getRejectionType() {
    return rejectionType;
  }

  public ExecuteGetResponse setRejectionType(final RejectionType rejectionType) {
    this.rejectionType = rejectionType;
    return this;
  }

  public DirectBuffer getValue() {
    return value;
  }

  public ExecuteGetResponse setValue(
      final DirectBuffer buffer, final int offset, final int length) {
    value.wrap(buffer, offset, length);
    return this;
  }

  public DirectBuffer getRejectionReason() {
    return rejectionReason;
  }

  public ExecuteGetResponse setRejectionReason(
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
    responseType = bodyDecoder.responseType();
    valueType = bodyDecoder.valueType();
    intent = Intent.fromProtocolValue(valueType, bodyDecoder.intent());
    rejectionType = bodyDecoder.rejectionType();

    offset += bodyDecoder.sbeBlockLength();

    final int valueLength = bodyDecoder.valueLength();
    offset += ExecuteGetResponseDecoder.valueHeaderLength();

    if (valueLength > 0) {
      value.wrap(buffer, offset, valueLength);
    }
    offset += valueLength;
    bodyDecoder.limit(offset);

    final int rejectionReasonLength = bodyDecoder.rejectionReasonLength();
    offset += ExecuteGetResponseDecoder.rejectionReasonHeaderLength();

    if (rejectionReasonLength > 0) {
      rejectionReason.wrap(buffer, offset, rejectionReasonLength);
    }
    offset += rejectionReasonLength;
    bodyDecoder.limit(offset);
    //
    //    assert bodyDecoder.limit() == frameEnd
    //        : "Decoder read only to position "
    //            + bodyDecoder.limit()
    //            + " but expected "
    //            + frameEnd
    //            + " as final position";
  }

  @Override
  public int getLength() {
    return MessageHeaderEncoder.ENCODED_LENGTH
        + ExecuteGetResponseEncoder.BLOCK_LENGTH
        + ExecuteGetResponseEncoder.valueHeaderLength()
        + value.capacity()
        + ExecuteGetResponseEncoder.rejectionReasonHeaderLength()
        + rejectionReason.capacity();
  }

  @Override
  public void write(final MutableDirectBuffer buffer, int offset) {
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
        .responseType(responseType)
        .valueType(valueType)
        .intent(intent.value())
        .rejectionType(rejectionType)
        .putValue(value, 0, value.capacity())
        .putRejectionReason(rejectionReason, 0, rejectionReason.capacity());
  }
}
