/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.transport.stream.impl.messages;

import io.camunda.zeebe.util.buffer.BufferUtil;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class ErrorResponse implements StreamResponse {
  private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
  private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();

  private final ErrorResponseEncoder messageEncoder = new ErrorResponseEncoder();
  private final ErrorResponseDecoder messageDecoder = new ErrorResponseDecoder();

  private final DirectBuffer message = new UnsafeBuffer();
  private ErrorCode code;

  @Override
  public void wrap(final DirectBuffer buffer, final int offset, final int length) {
    messageDecoder.wrapAndApplyHeader(buffer, offset, headerDecoder);
    code = messageDecoder.code();
    messageDecoder.wrapMessage(message);
  }

  @Override
  public int getLength() {
    return headerEncoder.encodedLength()
        + messageEncoder.sbeBlockLength()
        + ErrorResponseEncoder.messageHeaderLength()
        + message.capacity();
  }

  @Override
  public void write(final MutableDirectBuffer buffer, final int offset) {
    messageEncoder
        .wrapAndApplyHeader(buffer, offset, headerEncoder)
        .code(code)
        .putMessage(message, 0, message.capacity());
  }

  @Override
  public int templateId() {
    return messageDecoder.sbeTemplateId();
  }

  public ErrorResponse code(final ErrorCode code) {
    this.code = code;
    return this;
  }

  public ErrorResponse message(final DirectBuffer message) {
    this.message.wrap(message);
    return this;
  }

  public ErrorResponse message(final String message) {
    final var bytes = message.getBytes(StandardCharsets.UTF_8);
    this.message.wrap(bytes);

    return this;
  }

  public ErrorCode code() {
    return code;
  }

  public String message() {
    return message.capacity() > 0 ? BufferUtil.bufferAsString(message) : "";
  }

  @Override
  public int hashCode() {
    return Objects.hash(message, code);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    final ErrorResponse that = (ErrorResponse) o;
    return Objects.equals(message, that.message) && code == that.code;
  }

  @Override
  public String toString() {
    return "ErrorResponse{" + "message=" + message() + ", code=" + code + '}';
  }
}
