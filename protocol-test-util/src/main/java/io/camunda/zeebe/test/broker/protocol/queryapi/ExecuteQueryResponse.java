/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.test.broker.protocol.queryapi;

import io.camunda.zeebe.protocol.record.ErrorResponseDecoder;
import io.camunda.zeebe.protocol.record.ExecuteQueryResponseDecoder;
import io.camunda.zeebe.protocol.record.MessageHeaderDecoder;
import io.camunda.zeebe.test.broker.protocol.MsgPackHelper;
import io.camunda.zeebe.test.broker.protocol.commandapi.ErrorResponse;
import io.camunda.zeebe.test.broker.protocol.commandapi.ErrorResponseException;
import io.camunda.zeebe.util.buffer.BufferReader;
import org.agrona.DirectBuffer;

public final class ExecuteQueryResponse implements BufferReader {
  private final MessageHeaderDecoder messageHeaderDecoder = new MessageHeaderDecoder();
  private final ExecuteQueryResponseDecoder responseDecoder = new ExecuteQueryResponseDecoder();
  private final ErrorResponse errorResponse = new ErrorResponse(new MsgPackHelper());

  public String getBpmnProcessId() {
    return responseDecoder.bpmnProcessId();
  }

  @Override
  public void wrap(final DirectBuffer responseBuffer, final int offset, final int length) {
    messageHeaderDecoder.wrap(responseBuffer, offset);

    if (messageHeaderDecoder.templateId() != responseDecoder.sbeTemplateId()) {
      if (messageHeaderDecoder.templateId() == ErrorResponseDecoder.TEMPLATE_ID) {
        errorResponse.wrap(responseBuffer, offset + messageHeaderDecoder.encodedLength(), length);
        throw new ErrorResponseException(errorResponse);
      } else {
        throw new RuntimeException(
            "Unexpected response from broker. Template id " + messageHeaderDecoder.templateId());
      }
    }

    responseDecoder.wrap(
        responseBuffer,
        offset + messageHeaderDecoder.encodedLength(),
        messageHeaderDecoder.blockLength(),
        messageHeaderDecoder.version());
  }
}
