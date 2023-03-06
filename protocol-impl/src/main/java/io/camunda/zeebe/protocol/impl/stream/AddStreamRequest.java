/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.protocol.impl.stream;

import io.camunda.zeebe.protocol.record.AddStreamRequestDecoder;
import io.camunda.zeebe.protocol.record.AddStreamRequestEncoder;
import io.camunda.zeebe.protocol.record.MessageHeaderDecoder;
import io.camunda.zeebe.protocol.record.MessageHeaderEncoder;
import io.camunda.zeebe.util.buffer.BufferReader;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.util.Objects;
import java.util.UUID;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class AddStreamRequest implements BufferReader, BufferWriter {
  private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
  private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();

  private final AddStreamRequestEncoder messageEncoder = new AddStreamRequestEncoder();
  private final AddStreamRequestDecoder messageDecoder = new AddStreamRequestDecoder();

  private final DirectBuffer streamType = new UnsafeBuffer();
  private final DirectBuffer metadata = new UnsafeBuffer();

  private UUID streamId;

  @Override
  public void wrap(final DirectBuffer buffer, final int offset, final int length) {
    messageDecoder.wrapAndApplyHeader(buffer, offset, headerDecoder);
    messageDecoder.wrapStreamType(streamType);
    messageDecoder.wrapMetadata(metadata);
    streamId = new UUID(messageDecoder.id().high(), messageDecoder.id().low());
  }

  @Override
  public int getLength() {
    return headerEncoder.encodedLength()
        + messageEncoder.sbeBlockLength()
        + AddStreamRequestEncoder.streamTypeHeaderLength()
        + streamType.capacity()
        + AddStreamRequestEncoder.metadataHeaderLength()
        + metadata.capacity();
  }

  @Override
  public void write(final MutableDirectBuffer buffer, final int offset) {
    messageEncoder
        .wrapAndApplyHeader(buffer, offset, headerEncoder)
        .putStreamType(streamType, 0, streamType.capacity())
        .putMetadata(metadata, 0, metadata.capacity());

    if (streamId != null) {
      messageEncoder
          .id()
          .high(streamId.getMostSignificantBits())
          .low(streamId.getLeastSignificantBits());
    }
  }

  public DirectBuffer streamType() {
    return streamType;
  }

  public AddStreamRequest streamType(final DirectBuffer streamType) {
    this.streamType.wrap(streamType);
    return this;
  }

  public DirectBuffer metadata() {
    return metadata;
  }

  public AddStreamRequest metadata(final DirectBuffer metadata) {
    this.metadata.wrap(metadata);
    return this;
  }

  public UUID streamId() {
    return streamId;
  }

  public AddStreamRequest streamId(final UUID streamId) {
    this.streamId = streamId;
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(streamType, metadata, streamId);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    final AddStreamRequest that = (AddStreamRequest) o;
    return streamType.equals(that.streamType)
        && metadata.equals(that.metadata)
        && Objects.equals(streamId, that.streamId);
  }

  @Override
  public String toString() {
    return "AddStreamRequest{"
        + "streamType="
        + streamType
        + ", metadata="
        + metadata
        + ", streamId="
        + streamId
        + '}';
  }
}
