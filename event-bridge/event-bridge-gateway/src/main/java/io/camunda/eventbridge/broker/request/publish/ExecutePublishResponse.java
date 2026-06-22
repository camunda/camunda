/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.request.publish;

import io.camunda.eventbridge.protocol.ExecutePublishResponseDecoder;
import io.camunda.eventbridge.protocol.ExecutePublishResponseEncoder;
import io.camunda.eventbridge.protocol.MessageHeaderDecoder;
import io.camunda.eventbridge.protocol.MessageHeaderEncoder;
import io.camunda.eventbridge.protocol.PublishResponseStatus;
import io.camunda.eventbridge.protocol.request.PublishBatchResponse;
import io.camunda.zeebe.util.buffer.BufferReader;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.nio.charset.StandardCharsets;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

public class ExecutePublishResponse implements BufferReader, BufferWriter {

  private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
  private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();

  private final ExecutePublishResponseEncoder bodyEncoder = new ExecutePublishResponseEncoder();
  private final ExecutePublishResponseDecoder bodyDecoder = new ExecutePublishResponseDecoder();

  private final PublishBatchResponse response = new PublishBatchResponse();

  public ExecutePublishResponse() {
    reset();
  }

  public ExecutePublishResponse reset() {
    response.reset();
    return this;
  }

  public PublishBatchResponse getResponse() {
    return response;
  }

  @Override
  public void wrap(final DirectBuffer buffer, final int offset, final int length) {
    reset();
    bodyDecoder.wrapAndApplyHeader(buffer, offset, headerDecoder);
    response.setStatus(bodyDecoder.status());
    response.setRejectionReason(bodyDecoder.rejectionReason());
    response.setFirstPosition(bodyDecoder.firstPosition());
    response.setLastPosition(bodyDecoder.lastPosition());

    if (response.getStatus() != PublishResponseStatus.SUCCESS) {
      final var messageLength = bodyDecoder.rejectionMessageLength();
      if (messageLength > 0) {
        response.setRejectionMessage(bodyDecoder.rejectionMessage());
      } else {
        response.setRejectionMessage("");
      }
    }
  }

  @Override
  public int getLength() {
    final var rejectionMessageBytes =
        response.getRejectionMessage() != null
            ? response.getRejectionMessage().getBytes(StandardCharsets.UTF_8)
            : new byte[0];
    return MessageHeaderEncoder.ENCODED_LENGTH
        + ExecutePublishResponseEncoder.BLOCK_LENGTH
        + ExecutePublishResponseEncoder.rejectionMessageHeaderLength()
        + rejectionMessageBytes.length;
  }

  @Override
  public int write(final MutableDirectBuffer buffer, final int offset) {
    final var rejectionMessageBytes =
        response.getRejectionMessage() != null
            ? response.getRejectionMessage().getBytes(StandardCharsets.UTF_8)
            : new byte[0];

    bodyEncoder
        .wrapAndApplyHeader(buffer, offset, headerEncoder)
        .status(response.getStatus())
        .rejectionReason(response.getRejectionReason())
        .firstPosition(response.getFirstPosition())
        .lastPosition(response.getLastPosition())
        .putRejectionMessage(rejectionMessageBytes, 0, rejectionMessageBytes.length);

    return headerEncoder.encodedLength() + bodyEncoder.encodedLength();
  }
}
