/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.client.impl;

import io.camunda.zeebe.protocol.record.ErrorCode;
import io.camunda.zeebe.protocol.record.ErrorResponseDecoder;
import io.camunda.zeebe.protocol.record.MessageHeaderDecoder;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;

public final class ErrorResponseHandler {
  private final ErrorResponseDecoder decoder = new ErrorResponseDecoder();

  private DirectBuffer errorMessage;

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
