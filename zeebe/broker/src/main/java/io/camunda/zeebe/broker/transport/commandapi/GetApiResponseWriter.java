/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.transport.commandapi;

import io.camunda.zeebe.broker.transport.AsyncApiRequestHandler.ResponseWriter;
import io.camunda.zeebe.protocol.record.ExecuteGetResponseEncoder;
import io.camunda.zeebe.protocol.record.MessageHeaderEncoder;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ResponseType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.transport.ServerOutput;
import io.camunda.zeebe.transport.impl.ServerResponseImpl;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Can write Get API responses
 *
 * <pre>
 *  <field name="partitionId" id="1" type="uint16"/>
 *  <field name="key" id="2" type="uint64"/>
 *  <field name="responseType" id="3" type="ResponseType"/>
 *  <!-- value type is usually request.ValueType but can also be ERROR if responseType is ERROR -->
 *  <field name="valueType" id="4" type="ValueType"/>
 *  <field name="intent" id="5" type="uint8"/>
 *  <!-- populated when responseType is ERROR -->
 *  <field name="rejectionType" id="6" type="RejectionType"/>
 *  <data name="value" id="7" type="varDataEncoding"/>
 *  <!-- populated when responseType is ERROR; UTF-8-encoded String -->
 *  <data name="rejectionReason" id="8" type="varDataEncoding"/>
 * </pre>
 */
public class GetApiResponseWriter implements ResponseWriter {

  private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
  private final ExecuteGetResponseEncoder responseEncoder = new ExecuteGetResponseEncoder();
  private final ServerResponseImpl response = new ServerResponseImpl();

  private final DirectBuffer value = new UnsafeBuffer();
  private final DirectBuffer rejectionReason = new UnsafeBuffer();

  private int partitionId;
  private long key;
  private ResponseType responseType;
  private ValueType valueType;
  private Intent intent;
  private RejectionType rejectionType;

  @Override
  public void tryWriteResponse(
      final ServerOutput output, final int partitionId, final long requestId) {

    try {
      response.reset().writer(this).setPartitionId(partitionId).setRequestId(requestId);
      output.sendResponse(response);
    } finally {
      reset();
    }
  }

  @Override
  public void reset() {
    partitionId = -1;
    key = -1;
    responseType = ResponseType.NULL_VAL;
    valueType = ValueType.NULL_VAL;
    intent = null;
    rejectionType = RejectionType.NULL_VAL;
    value.wrap(0, 0);
    rejectionReason.wrap(0, 0);
  }

  @Override
  public int getLength() {
    return MessageHeaderEncoder.ENCODED_LENGTH
        + ExecuteGetResponseEncoder.BLOCK_LENGTH
        + ExecuteGetResponseEncoder.partitionIdEncodingLength()
        + ExecuteGetResponseEncoder.keyEncodingLength()
        + ExecuteGetResponseEncoder.responseTypeEncodingLength()
        + ExecuteGetResponseEncoder.valueTypeEncodingLength()
        + ExecuteGetResponseEncoder.intentEncodingLength()
        + ExecuteGetResponseEncoder.rejectionTypeEncodingLength()
        + ExecuteGetResponseEncoder.valueHeaderLength()
        + value.capacity()
        + ExecuteGetResponseEncoder.rejectionReasonHeaderLength()
        + rejectionReason.capacity();
  }

  @Override
  public void write(final MutableDirectBuffer buffer, int offset) {
    // protocol header
    headerEncoder.wrap(buffer, offset);

    headerEncoder
        .blockLength(responseEncoder.sbeBlockLength())
        .templateId(responseEncoder.sbeTemplateId())
        .schemaId(responseEncoder.sbeSchemaId())
        .version(responseEncoder.sbeSchemaVersion());

    offset += headerEncoder.encodedLength();

    responseEncoder.wrap(buffer, offset);

    responseEncoder.partitionId(partitionId);
    responseEncoder.key(key);
    responseEncoder.responseType(responseType);
    responseEncoder.valueType(valueType);
    responseEncoder.intent(intent == null ? Intent.NULL_VAL : intent.value());
    responseEncoder.rejectionType(rejectionType);
    responseEncoder.putValue(value, 0, value.capacity());
    responseEncoder.putRejectionReason(rejectionReason, 0, rejectionReason.capacity());
  }

  public GetApiResponseWriter partitionId(final int partitionId) {
    this.partitionId = partitionId;
    return this;
  }

  public GetApiResponseWriter key(final long key) {
    this.key = key;
    return this;
  }

  public GetApiResponseWriter responseType(final ResponseType responseType) {
    this.responseType = responseType;
    return this;
  }

  public GetApiResponseWriter valueType(final ValueType valueType) {
    this.valueType = valueType;
    return this;
  }

  public GetApiResponseWriter intent(final Intent intent) {
    this.intent = intent;
    return this;
  }

  public GetApiResponseWriter rejectionType(final RejectionType rejectionType) {
    this.rejectionType = rejectionType;
    return this;
  }

  public GetApiResponseWriter value(final DirectBuffer value) {
    this.value.wrap(value);
    return this;
  }

  public GetApiResponseWriter rejectionReason(final DirectBuffer rejectionReason) {
    this.rejectionReason.wrap(rejectionReason);
    return this;
  }
}
