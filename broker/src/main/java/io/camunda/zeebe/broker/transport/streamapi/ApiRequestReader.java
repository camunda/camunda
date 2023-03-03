/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.transport.streamapi;

import io.camunda.zeebe.protocol.record.AddGatewayStreamRequestDecoder;
import io.camunda.zeebe.protocol.record.MessageHeaderDecoder;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class ApiRequestReader {
  private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
  private final AddGatewayStreamRequestDecoder messageDecoder =
      new AddGatewayStreamRequestDecoder();

  private final DirectBuffer streamType = new UnsafeBuffer();
  private final DirectBuffer metadata = new UnsafeBuffer();

  public void wrap(final DirectBuffer buffer) {
    messageDecoder.wrapAndApplyHeader(buffer, 0, headerDecoder);
    messageDecoder.wrapStreamType(streamType);
    messageDecoder.wrapMetadata(metadata);
  }

  long id() {
    return messageDecoder.id();
  }

  DirectBuffer streamType() {
    return streamType;
  }

  DirectBuffer metadata() {
    return metadata;
  }
}
