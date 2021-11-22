/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.transport.queryapi;

import io.camunda.zeebe.broker.transport.ApiRequestHandler.RequestReader;
import io.camunda.zeebe.protocol.record.ExecuteQueryRequestDecoder;
import io.camunda.zeebe.protocol.record.MessageHeaderDecoder;
import org.agrona.DirectBuffer;

public class QueryRequestReader implements RequestReader<ExecuteQueryRequestDecoder> {
  private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
  private final ExecuteQueryRequestDecoder messageDecoder = new ExecuteQueryRequestDecoder();

  @Override
  public void reset() {
    // nothing to reset here, decoders are re-wrapped for every request
  }

  @Override
  public ExecuteQueryRequestDecoder getMessageDecoder() {
    return messageDecoder;
  }

  @Override
  public void wrap(final DirectBuffer buffer, final int offset, final int length) {
    messageDecoder.wrapAndApplyHeader(buffer, offset, headerDecoder);
  }
}
