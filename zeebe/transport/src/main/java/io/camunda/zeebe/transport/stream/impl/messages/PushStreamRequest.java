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

public final class PushStreamRequest implements BufferReader, BufferWriter {
  private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
  private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();

  private final PushStreamRequestEncoder messageEncoder = new PushStreamRequestEncoder();
  private final PushStreamRequestDecoder messageDecoder = new PushStreamRequestDecoder();

  private final DirectBuffer payloadReader = new UnsafeBuffer();
  private BufferWriter payloadWriter = new DirectBufferWriter().wrap(payloadReader);
  private UUID streamId;

  @Override
  public void wrap(final DirectBuffer buffer, final int offset, final int length) {
    messageDecoder.wrapAndApplyHeader(buffer, 0, headerDecoder);
    streamId = new UUID(messageDecoder.id().high(), messageDecoder.id().low());
    messageDecoder.wrapPayload(payloadReader);
    payloadWriter = new DirectBufferWriter().wrap(payloadReader);
  }

  @Override
  public int getLength() {
    return headerEncoder.encodedLength()
        + messageEncoder.sbeBlockLength()
        + PushStreamRequestEncoder.payloadHeaderLength()
        + payloadWriter.getLength();
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

    SbeUtil.writeNested(
        payloadWriter,
        PushStreamRequestEncoder.payloadHeaderLength(),
        messageEncoder,
        PushStreamRequestEncoder.BYTE_ORDER);
    return getLength();
  }

  /** May return null if it was never read or set. */
  public UUID streamId() {
    return streamId;
  }

  public PushStreamRequest streamId(final UUID streamId) {
    this.streamId = streamId;
    return this;
  }

  /**
   * Returns the payload after a call to {@link #wrap(DirectBuffer, int, int)} or {@link
   * #payload(DirectBuffer)}. Otherwise, may return old or invalid data (e.g. empty buffer)
   */
  public DirectBuffer payload() {
    return payloadReader;
  }

  /**
   * May return null if {@link #payload(DirectBuffer)}, {@link #payload(BufferWriter)}, or {@link
   * #wrap(DirectBuffer, int, int)} were never called.
   */
  public BufferWriter payloadWriter() {
    return payloadWriter;
  }

  public PushStreamRequest payload(final BufferWriter payloadWriter) {
    this.payloadWriter = payloadWriter;
    payloadReader.wrap(0, 0);
    return this;
  }

  public PushStreamRequest payload(final DirectBuffer payload) {
    payloadReader.wrap(payload);
    payloadWriter = new DirectBufferWriter().wrap(payload);
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(streamId, payloadReader, payloadWriter);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    final PushStreamRequest that = (PushStreamRequest) o;
    return Objects.equals(streamId, that.streamId)
        && Objects.equals(payloadReader, that.payloadReader)
        && Objects.equals(payloadWriter, that.payloadWriter);
  }

  @Override
  public String toString() {
    return "PushStreamRequest{"
        + "streamId="
        + streamId
        + "payload=byte['"
        + payloadWriter.getLength()
        + "]'}";
  }
}
