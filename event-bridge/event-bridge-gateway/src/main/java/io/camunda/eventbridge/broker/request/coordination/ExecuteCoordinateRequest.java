/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.request.coordination;

import io.camunda.eventbridge.protocol.CoordinateRequestType;
import io.camunda.eventbridge.protocol.ExecuteCoordinateRequestDecoder;
import io.camunda.eventbridge.protocol.ExecuteCoordinateRequestEncoder;
import io.camunda.eventbridge.protocol.MessageHeaderDecoder;
import io.camunda.eventbridge.protocol.MessageHeaderEncoder;
import io.camunda.zeebe.util.buffer.BufferReader;
import io.camunda.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class ExecuteCoordinateRequest implements BufferReader, BufferWriter {

  private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
  private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();

  private final ExecuteCoordinateRequestEncoder bodyEncoder = new ExecuteCoordinateRequestEncoder();
  private final ExecuteCoordinateRequestDecoder bodyDecoder = new ExecuteCoordinateRequestDecoder();

  private final DirectBuffer value = new UnsafeBuffer(0, 0);
  private CoordinateRequestType type;

  public ExecuteCoordinateRequest() {
    reset();
  }

  public ExecuteCoordinateRequest reset() {
    type = CoordinateRequestType.NULL_VAL;
    value.wrap(0, 0);
    return this;
  }

  public DirectBuffer getValue() {
    return value;
  }

  public ExecuteCoordinateRequest wrapValue(
      final DirectBuffer buffer, final int offset, final int length) {
    value.wrap(buffer, offset, length);
    return this;
  }

  public CoordinateRequestType getType() {
    return type;
  }

  public ExecuteCoordinateRequest setType(final CoordinateRequestType type) {
    this.type = type;
    return this;
  }

  @Override
  public void wrap(final DirectBuffer buffer, final int offset, final int length) {
    reset();
    bodyDecoder.wrapAndApplyHeader(buffer, offset, headerDecoder);
    type = bodyDecoder.type();

    final var valueOffset =
        offset
            + headerDecoder.encodedLength()
            + bodyDecoder.sbeBlockLength()
            + ExecuteCoordinateRequestDecoder.valueHeaderLength();
    final var valueLength = bodyDecoder.valueLength();
    value.wrap(buffer, valueOffset, valueLength);
  }

  @Override
  public int getLength() {
    return headerDecoder.encodedLength()
        + bodyDecoder.sbeBlockLength()
        + ExecuteCoordinateRequestDecoder.valueHeaderLength()
        + value.capacity();
  }

  @Override
  public int write(final MutableDirectBuffer buffer, final int offset) {
    bodyEncoder
        .wrapAndApplyHeader(buffer, offset, headerEncoder)
        .type(type)
        .putValue(value, 0, value.capacity());

    return headerEncoder.encodedLength() + bodyEncoder.encodedLength();
  }
}
