/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.test.broker.protocol.commandapi;

import io.zeebe.protocol.record.ErrorCode;
import io.zeebe.protocol.record.ErrorResponseDecoder;
import io.zeebe.protocol.record.MessageHeaderDecoder;
import io.zeebe.test.broker.protocol.MsgPackHelper;
import io.zeebe.util.buffer.BufferReader;
import org.agrona.DirectBuffer;

public final class ErrorResponse implements BufferReader {
  protected final MsgPackHelper msgPackHelper;
  protected String errorData;
  private final MessageHeaderDecoder messageHeaderDecoder = new MessageHeaderDecoder();
  private final ErrorResponseDecoder bodyDecoder = new ErrorResponseDecoder();

  public ErrorResponse(final MsgPackHelper msgPackHelper) {
    this.msgPackHelper = msgPackHelper;
  }

  public ErrorCode getErrorCode() {
    return bodyDecoder.errorCode();
  }

  public String getErrorData() {
    return errorData;
  }

  @Override
  public void wrap(final DirectBuffer responseBuffer, final int offset, final int length) {
    messageHeaderDecoder.wrap(responseBuffer, 0);

    if (messageHeaderDecoder.templateId() != bodyDecoder.sbeTemplateId()) {
      throw new RuntimeException("Unexpected response from broker.");
    }

    bodyDecoder.wrap(
        responseBuffer,
        messageHeaderDecoder.encodedLength(),
        messageHeaderDecoder.blockLength(),
        messageHeaderDecoder.version());

    final int errorDataLength = bodyDecoder.errorDataLength();
    final int errorDataOffset =
        messageHeaderDecoder.encodedLength()
            + messageHeaderDecoder.blockLength()
            + ErrorResponseDecoder.errorDataHeaderLength();

    errorData = responseBuffer.getStringWithoutLengthUtf8(errorDataOffset, errorDataLength);

    bodyDecoder.limit(errorDataOffset + errorDataLength);
  }
}
