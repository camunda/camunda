/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.transport.commandapi;

import static io.camunda.zeebe.protocol.record.ExecuteCommandRequestDecoder.TEMPLATE_ID;

import io.camunda.zeebe.broker.transport.AsyncApiRequestHandler.RequestReader;
import io.camunda.zeebe.broker.transport.RequestReaderException;
import io.camunda.zeebe.protocol.impl.encoding.AuthInfo;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.ExecuteCommandRequestDecoder;
import io.camunda.zeebe.protocol.record.MessageHeaderDecoder;
import org.agrona.DirectBuffer;

public class CommandApiRequestReader implements RequestReader<ExecuteCommandRequestDecoder> {

  private UnifiedRecordValue value;
  private final RecordMetadata metadata = new RecordMetadata();
  private final AuthInfo authInfo = new AuthInfo();
  private final MessageHeaderDecoder messageHeaderDecoder = new MessageHeaderDecoder();
  private final ExecuteCommandRequestDecoder commandRequestDecoder =
      new ExecuteCommandRequestDecoder();

  @Override
  public void reset() {
    if (value != null) {
      value.reset();
    }
    metadata.reset();
  }

  @Override
  public ExecuteCommandRequestDecoder getMessageDecoder() {
    return commandRequestDecoder;
  }

  @Override
  public void wrap(final DirectBuffer buffer, final int offset, final int length) {
    messageHeaderDecoder.wrap(buffer, offset);

    final int templateId = messageHeaderDecoder.templateId();
    if (TEMPLATE_ID != templateId) {
      throw new RequestReaderException.InvalidTemplateException(
          messageHeaderDecoder.templateId(), templateId);
    }

    commandRequestDecoder.wrap(
        buffer,
        offset + MessageHeaderDecoder.ENCODED_LENGTH,
        messageHeaderDecoder.blockLength(),
        messageHeaderDecoder.version());

    metadata.protocolVersion(messageHeaderDecoder.version());
    final var record = UnifiedRecordValue.fromValueType(commandRequestDecoder.valueType());
    if (record != null) {
      final int valueOffset =
          commandRequestDecoder.limit() + ExecuteCommandRequestDecoder.valueHeaderLength();
      final int valueLength = commandRequestDecoder.valueLength();
      value = record;
      value.wrap(buffer, valueOffset, valueLength);
    }

    commandRequestDecoder.skipValue();
    if (commandRequestDecoder.limit() < buffer.capacity()) {
      final int authOffset =
          commandRequestDecoder.limit() + ExecuteCommandRequestDecoder.authorizationHeaderLength();
      authInfo.wrap(buffer, authOffset, commandRequestDecoder.authorizationLength());
      metadata.authorization(authInfo);
    }
  }

  public UnifiedRecordValue value() {
    return value;
  }

  public RecordMetadata metadata() {
    return metadata;
  }
}
