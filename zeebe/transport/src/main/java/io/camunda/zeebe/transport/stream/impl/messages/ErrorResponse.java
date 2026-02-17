/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.transport.stream.impl.messages;

import io.camunda.zeebe.transport.stream.api.ClientStreamBlockedException;
import io.camunda.zeebe.transport.stream.api.NoSuchStreamException;
import io.camunda.zeebe.transport.stream.api.StreamExhaustedException;
import io.camunda.zeebe.transport.stream.api.StreamResponseException;
import io.camunda.zeebe.transport.stream.api.StreamResponseException.ErrorDetail;
import io.camunda.zeebe.transport.stream.impl.messages.ErrorResponseDecoder.DetailsDecoder;
import io.camunda.zeebe.transport.stream.impl.messages.ErrorResponseEncoder.DetailsEncoder;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class ErrorResponse implements StreamResponse {
  private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
  private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();

  private final ErrorResponseEncoder messageEncoder = new ErrorResponseEncoder();
  private final ErrorResponseDecoder messageDecoder = new ErrorResponseDecoder();

  private final List<ErrorDetailImpl> details = new ArrayList<>();
  private final DirectBuffer message = new UnsafeBuffer();
  private ErrorCode code;

  @Override
  public void wrap(final DirectBuffer buffer, final int offset, final int length) {
    messageDecoder.wrapAndApplyHeader(buffer, offset, headerDecoder);
    code = messageDecoder.code();
    messageDecoder.wrapMessage(message);

    details.clear();
    for (final DetailsDecoder decoder : messageDecoder.details()) {
      final var messageBuffer = new UnsafeBuffer();
      final var detail = new ErrorDetailImpl(decoder.code(), messageBuffer);
      decoder.wrapMessage(messageBuffer);
      details.add(detail);
    }
  }

  @Override
  public int getLength() {
    final var detailsLength =
        details.stream()
            .mapToInt(
                e ->
                    DetailsEncoder.sbeBlockLength()
                        + DetailsEncoder.messageHeaderLength()
                        + e.messageBuffer.capacity())
            .sum();

    return headerEncoder.encodedLength()
        + messageEncoder.sbeBlockLength()
        + DetailsEncoder.sbeHeaderSize()
        + detailsLength
        + ErrorResponseEncoder.messageHeaderLength()
        + message.capacity();
  }

  @Override
  public int write(final MutableDirectBuffer buffer, final int offset) {
    messageEncoder
        .wrapAndApplyHeader(buffer, offset, headerEncoder)
        .code(code)
        .putMessage(message, 0, message.capacity());
    final var detailsEncoder = messageEncoder.detailsCount(details.size());
    details.forEach(
        detail ->
            detailsEncoder
                .next()
                .code(detail.code())
                .putMessage(detail.messageBuffer(), 0, detail.messageBuffer().capacity()));
    return getLength();
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

  public ErrorResponse addDetail(final ErrorCode code, final String message) {
    details.add(
        new ErrorDetailImpl(code, new UnsafeBuffer(message.getBytes(StandardCharsets.UTF_8))));
    return this;
  }

  public List<? extends ErrorDetail> details() {
    return details;
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
    return "ErrorResponse{"
        + "message="
        + message()
        + ", code="
        + code
        + ", details="
        + details
        + '}';
  }

  public StreamResponseException asException() {
    return new StacklessException(this);
  }

  public static ErrorCode mapErrorToCode(final Throwable error) {
    return switch (error) {
      case final ClientStreamBlockedException ignored -> ErrorCode.BLOCKED;
      case final NoSuchStreamException ignored -> ErrorCode.NOT_FOUND;
      case final StreamExhaustedException ignored -> ErrorCode.EXHAUSTED;
      default -> ErrorCode.INTERNAL;
    };
  }

  private record ErrorDetailImpl(ErrorCode code, DirectBuffer messageBuffer)
      implements ErrorDetail {

    @Override
    public String message() {
      final var length = messageBuffer.capacity();
      return length == 0 ? "" : messageBuffer.getStringWithoutLengthUtf8(0, length);
    }

    @Override
    public String toString() {
      return "ErrorDetailImpl{" + "code=" + code + ", message=" + message() + "}";
    }
  }

  private static final class StacklessException extends StreamResponseException {

    private StacklessException(final ErrorResponse response) {
      super(response);
    }

    @Override
    public Throwable fillInStackTrace() {
      return this;
    }
  }
}
