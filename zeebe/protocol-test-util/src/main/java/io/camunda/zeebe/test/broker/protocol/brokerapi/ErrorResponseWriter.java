/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test.broker.protocol.brokerapi;

import io.camunda.zeebe.protocol.record.ErrorCode;
import io.camunda.zeebe.protocol.record.ErrorResponseEncoder;
import io.camunda.zeebe.protocol.record.MessageHeaderEncoder;
import io.camunda.zeebe.test.broker.protocol.MsgPackHelper;
import java.nio.charset.StandardCharsets;
import org.agrona.MutableDirectBuffer;

public final class ErrorResponseWriter<R> extends AbstractMessageBuilder<R> {
  protected final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
  protected final ErrorResponseEncoder bodyEncoder = new ErrorResponseEncoder();
  protected final MsgPackHelper msgPackHelper;

  protected ErrorCode errorCode;
  protected byte[] errorData;

  public ErrorResponseWriter(final MsgPackHelper msgPackHelper) {
    this.msgPackHelper = msgPackHelper;
  }

  @Override
  public int getLength() {
    return MessageHeaderEncoder.ENCODED_LENGTH
        + ErrorResponseEncoder.BLOCK_LENGTH
        + ErrorResponseEncoder.errorDataHeaderLength()
        + errorData.length;
  }

  @Override
  public int write(final MutableDirectBuffer buffer, final int offset) {
    // protocol message
    bodyEncoder
        .wrapAndApplyHeader(buffer, offset, headerEncoder)
        .errorCode(errorCode)
        .putErrorData(errorData, 0, errorData.length);
    return bodyEncoder.encodedLength() + headerEncoder.encodedLength();
  }

  @Override
  public void initializeFrom(final R context) {}

  public void setErrorCode(final ErrorCode errorCode) {
    this.errorCode = errorCode;
  }

  public void setErrorData(final String errorData) {
    this.errorData = errorData.getBytes(StandardCharsets.UTF_8);
  }
}
