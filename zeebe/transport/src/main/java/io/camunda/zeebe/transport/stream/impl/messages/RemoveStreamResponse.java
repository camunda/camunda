/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.transport.stream.impl.messages;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

public final class RemoveStreamResponse implements StreamResponse {
  private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
  private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();

  private final RemoveStreamResponseEncoder messageEncoder = new RemoveStreamResponseEncoder();
  private final RemoveStreamResponseDecoder messageDecoder = new RemoveStreamResponseDecoder();

  @Override
  public void wrap(final DirectBuffer buffer, final int offset, final int length) {
    messageDecoder.wrapAndApplyHeader(buffer, offset, headerDecoder);
  }

  @Override
  public int getLength() {
    return headerEncoder.encodedLength() + messageEncoder.sbeBlockLength();
  }

  @Override
  public int write(final MutableDirectBuffer buffer, final int offset) {
    messageEncoder.wrapAndApplyHeader(buffer, offset, headerEncoder);
    return getLength();
  }

  @Override
  public int templateId() {
    return messageDecoder.sbeTemplateId();
  }

  @Override
  public String toString() {
    return "RemoveStreamResponse{}";
  }
}
