/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.transport.commandapi;

import static io.camunda.zeebe.protocol.record.ExecuteGetRequestDecoder.TEMPLATE_ID;

import io.camunda.zeebe.broker.transport.AsyncApiRequestHandler.RequestReader;
import io.camunda.zeebe.broker.transport.RequestReaderException;
import io.camunda.zeebe.protocol.impl.encoding.AuthInfo;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.record.ExecuteGetRequestDecoder;
import io.camunda.zeebe.protocol.record.MessageHeaderDecoder;
import org.agrona.DirectBuffer;

public class GetApiRequestReader implements RequestReader<ExecuteGetRequestDecoder> {

  private final RecordMetadata metadata = new RecordMetadata();
  private final AuthInfo authInfo = new AuthInfo();
  private final MessageHeaderDecoder messageHeaderDecoder = new MessageHeaderDecoder();
  private final ExecuteGetRequestDecoder getRequestDecoder = new ExecuteGetRequestDecoder();

  @Override
  public void reset() {
    metadata.reset();
  }

  @Override
  public ExecuteGetRequestDecoder getMessageDecoder() {
    return getRequestDecoder;
  }

  @Override
  public void wrap(final DirectBuffer buffer, final int offset, final int length) {
    messageHeaderDecoder.wrap(buffer, offset);

    final int templateId = messageHeaderDecoder.templateId();
    if (TEMPLATE_ID != templateId) {
      throw new RequestReaderException.InvalidTemplateException(
          messageHeaderDecoder.templateId(), templateId);
    }

    getRequestDecoder.wrap(
        buffer,
        offset + MessageHeaderDecoder.ENCODED_LENGTH,
        messageHeaderDecoder.blockLength(),
        messageHeaderDecoder.version());

    metadata.protocolVersion(messageHeaderDecoder.version());

    if (getRequestDecoder.limit() < buffer.capacity()) {
      final int authOffset =
          getRequestDecoder.limit() + ExecuteGetRequestDecoder.authorizationHeaderLength();
      authInfo.wrap(buffer, authOffset, getRequestDecoder.authorizationLength());
      metadata.authorization(authInfo);
    }
  }

  public RecordMetadata metadata() {
    return metadata;
  }
}
