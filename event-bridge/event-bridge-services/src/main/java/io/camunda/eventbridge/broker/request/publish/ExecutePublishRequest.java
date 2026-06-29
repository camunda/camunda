/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.request.publish;

import io.camunda.eventbridge.protocol.ExecutePublishRequestDecoder;
import io.camunda.eventbridge.protocol.ExecutePublishRequestEncoder;
import io.camunda.eventbridge.protocol.MessageHeaderDecoder;
import io.camunda.eventbridge.protocol.MessageHeaderEncoder;
import io.camunda.zeebe.util.buffer.BufferReader;
import io.camunda.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class ExecutePublishRequest implements BufferReader, BufferWriter {

  private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
  private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();

  private final ExecutePublishRequestEncoder bodyEncoder = new ExecutePublishRequestEncoder();
  private final ExecutePublishRequestDecoder bodyDecoder = new ExecutePublishRequestDecoder();

  private final DirectBuffer value = new UnsafeBuffer(0, 0);

  public ExecutePublishRequest wrapValue(
      final DirectBuffer buffer, final int offset, final int length) {
    value.wrap(buffer, offset, length);
    return this;
  }

  public ExecutePublishRequest reset() {
    value.wrap(0, 0);
    return this;
  }

  public DirectBuffer getValue() {
    return value;
  }

  @Override
  public void wrap(final DirectBuffer buffer, final int offset, final int length) {
    reset();
    bodyDecoder.wrapAndApplyHeader(buffer, offset, headerDecoder);

    final var valueOffset =
        offset
            + headerDecoder.encodedLength()
            + bodyDecoder.sbeBlockLength()
            + ExecutePublishRequestDecoder.entryBatchHeaderLength();
    final var valueLength = bodyDecoder.entryBatchLength();
    value.wrap(buffer, valueOffset, valueLength);
  }

  @Override
  public int getLength() {
    return headerDecoder.encodedLength()
        + bodyDecoder.sbeBlockLength()
        + ExecutePublishRequestDecoder.entryBatchHeaderLength()
        + value.capacity();
  }

  @Override
  public int write(final MutableDirectBuffer buffer, final int offset) {
    bodyEncoder
        .wrapAndApplyHeader(buffer, offset, headerEncoder)
        .putEntryBatch(value, 0, value.capacity());

    return headerEncoder.encodedLength() + bodyEncoder.encodedLength();
  }
}
