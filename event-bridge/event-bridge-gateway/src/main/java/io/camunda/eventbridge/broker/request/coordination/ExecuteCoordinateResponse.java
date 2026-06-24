/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.request.coordination;

import io.camunda.eventbridge.protocol.CoordinateRejectionType;
import io.camunda.eventbridge.protocol.ExecuteCoordinateResponseDecoder;
import io.camunda.eventbridge.protocol.ExecuteCoordinateResponseEncoder;
import io.camunda.eventbridge.protocol.MessageHeaderDecoder;
import io.camunda.eventbridge.protocol.MessageHeaderEncoder;
import io.camunda.zeebe.util.buffer.BufferReader;
import io.camunda.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class ExecuteCoordinateResponse implements BufferReader, BufferWriter {

  private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
  private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();

  private final ExecuteCoordinateResponseEncoder bodyEncoder =
      new ExecuteCoordinateResponseEncoder();
  private final ExecuteCoordinateResponseDecoder bodyDecoder =
      new ExecuteCoordinateResponseDecoder();

  private final DirectBuffer value = new UnsafeBuffer(0, 0);
  private CoordinateRejectionType rejectionType = CoordinateRejectionType.NONE;
  private String rejectionReason = "";

  public ExecuteCoordinateResponse() {
    reset();
  }

  public ExecuteCoordinateResponse reset() {
    value.wrap(0, 0);
    rejectionType = CoordinateRejectionType.NONE;
    rejectionReason = "";
    return this;
  }

  public DirectBuffer getValue() {
    return value;
  }

  /** {@code NONE} for a successful reply; anything else means the command was rejected. */
  public CoordinateRejectionType getRejectionType() {
    return rejectionType;
  }

  public String getRejectionReason() {
    return rejectionReason;
  }

  @Override
  public void wrap(final DirectBuffer buffer, final int offset, final int length) {
    reset();
    bodyDecoder.wrapAndApplyHeader(buffer, offset, headerDecoder);
    // Order matters: the fixed rejectionType first, then the var-data fields in declaration order
    // (value, then rejectionReason) — wrapValue advances the decoder past the value field.
    rejectionType = bodyDecoder.rejectionType();
    bodyDecoder.wrapValue(value);
    rejectionReason = bodyDecoder.rejectionReason();
  }

  @Override
  public int getLength() {
    return headerDecoder.encodedLength()
        + bodyDecoder.sbeBlockLength()
        + ExecuteCoordinateResponseDecoder.valueHeaderLength()
        + value.capacity()
        + ExecuteCoordinateResponseDecoder.rejectionReasonHeaderLength()
        + rejectionReason.length();
  }

  @Override
  public int write(final MutableDirectBuffer buffer, final int offset) {
    bodyEncoder
        .wrapAndApplyHeader(buffer, offset, headerEncoder)
        .rejectionType(rejectionType)
        .putValue(value, 0, value.capacity())
        .rejectionReason(rejectionReason);

    return headerEncoder.encodedLength() + bodyEncoder.encodedLength();
  }
}
