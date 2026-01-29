/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.transport.stream.impl.messages;

import io.camunda.zeebe.util.SbeUtil;
import io.camunda.zeebe.util.buffer.BufferReader;
import io.camunda.zeebe.util.buffer.BufferWriter;
import io.camunda.zeebe.util.buffer.DirectBufferWriter;
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

  private UUID streamId;
  private final DirectBuffer metadataReader = new UnsafeBuffer();
  private BufferWriter metadataWriter = new DirectBufferWriter().wrap(metadataReader);

  @Override
  public void wrap(final DirectBuffer buffer, final int offset, final int length) {
    messageDecoder.wrapAndApplyHeader(buffer, offset, headerDecoder);
    messageDecoder.wrapStreamType(streamType);
    messageDecoder.wrapMetadata(metadataReader);
    metadataWriter = new DirectBufferWriter().wrap(metadataReader);
    streamId = new UUID(messageDecoder.id().high(), messageDecoder.id().low());
  }

  @Override
  public int getLength() {
    return headerEncoder.encodedLength()
        + messageEncoder.sbeBlockLength()
        + AddStreamRequestEncoder.streamTypeHeaderLength()
        + streamType.capacity()
        + AddStreamRequestEncoder.metadataHeaderLength()
        + metadataWriter.getLength();
  }

  @Override
  public int write(final MutableDirectBuffer buffer, final int offset) {
    messageEncoder
        .wrapAndApplyHeader(buffer, offset, headerEncoder)
        .putStreamType(streamType, 0, streamType.capacity());

    SbeUtil.writeNested(
        metadataWriter,
        AddStreamRequestEncoder.metadataHeaderLength(),
        messageEncoder,
        AddStreamRequestEncoder.BYTE_ORDER);

    if (streamId != null) {
      messageEncoder
          .id()
          .high(streamId.getMostSignificantBits())
          .low(streamId.getLeastSignificantBits());
    }
    return getLength();
  }

  public DirectBuffer streamType() {
    return streamType;
  }

  public AddStreamRequest streamType(final DirectBuffer streamType) {
    this.streamType.wrap(streamType);
    return this;
  }

  public DirectBuffer metadata() {
    return metadataReader;
  }

  public AddStreamRequest metadata(final DirectBuffer metadata) {
    metadataReader.wrap(metadata);
    metadataWriter = new DirectBufferWriter().wrap(metadata);
    return this;
  }

  public AddStreamRequest metadata(final BufferWriter metadataWriter) {
    this.metadataWriter = metadataWriter;
    metadataReader.wrap(0, 0);
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
    return Objects.hash(streamType, metadataReader, streamId);
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
        && metadataReader.equals(that.metadataReader)
        && Objects.equals(streamId, that.streamId);
  }

  @Override
  public String toString() {
    return "AddStreamRequest{"
        + "streamType="
        + streamType
        + ", metadata="
        + metadataReader
        + ", streamId="
        + streamId
        + '}';
  }
}
