/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.gateway.impl;

import io.zeebe.protocol.record.ErrorCode;
import io.zeebe.protocol.record.ErrorResponseDecoder;
import io.zeebe.protocol.record.MessageHeaderDecoder;
import io.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;

public final class ErrorResponseHandler {
  protected final ErrorResponseDecoder decoder = new ErrorResponseDecoder();

  protected DirectBuffer errorMessage;

  public boolean handlesResponse(final MessageHeaderDecoder responseHeader) {
    return ErrorResponseDecoder.SCHEMA_ID == responseHeader.schemaId()
        && ErrorResponseDecoder.TEMPLATE_ID == responseHeader.templateId();
  }

  public void wrap(final DirectBuffer body, final int offset, final int length, final int version) {
    decoder.wrap(body, offset, length, version);

    final int errorDataLength = decoder.errorDataLength();
    final byte[] errorData = new byte[errorDataLength];
    decoder.getErrorData(errorData, 0, errorDataLength);
    errorMessage = BufferUtil.wrapArray(errorData);
  }

  public ErrorCode getErrorCode() {
    return decoder.errorCode();
  }

  public DirectBuffer getErrorMessage() {
    return errorMessage;
  }
}
