/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.transport.stream.impl.messages;

import io.camunda.zeebe.util.buffer.BufferReader;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.util.Objects;
import java.util.UUID;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

public final class RemoveStreamRequest implements BufferReader, BufferWriter {
  private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
  private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();

  private final RemoveStreamRequestEncoder messageEncoder = new RemoveStreamRequestEncoder();
  private final RemoveStreamRequestDecoder messageDecoder = new RemoveStreamRequestDecoder();

  private UUID streamId;

  @Override
  public void wrap(final DirectBuffer buffer, final int offset, final int length) {
    messageDecoder.wrapAndApplyHeader(buffer, 0, headerDecoder);
    streamId = new UUID(messageDecoder.id().high(), messageDecoder.id().low());
  }

  @Override
  public int getLength() {
    return headerEncoder.encodedLength() + messageEncoder.sbeBlockLength();
  }

  @Override
  public int write(final MutableDirectBuffer buffer, final int offset) {
    messageEncoder.wrapAndApplyHeader(buffer, offset, headerEncoder);

    if (streamId != null) {
      messageEncoder
          .id()
          .high(streamId.getMostSignificantBits())
          .low(streamId.getLeastSignificantBits());
    }
    return getLength();
  }

  public UUID streamId() {
    return streamId;
  }

  public RemoveStreamRequest streamId(final UUID streamId) {
    this.streamId = streamId;
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(streamId);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    final RemoveStreamRequest that = (RemoveStreamRequest) o;
    return Objects.equals(streamId, that.streamId);
  }

  @Override
  public String toString() {
    return "RemoveStreamRequest{" + "streamId=" + streamId + '}';
  }
}
