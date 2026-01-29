/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.encoding;

import io.camunda.zeebe.protocol.record.ErrorCode;
import io.camunda.zeebe.protocol.record.ErrorResponseDecoder;
import io.camunda.zeebe.protocol.record.ErrorResponseEncoder;
import io.camunda.zeebe.protocol.record.MessageHeaderDecoder;
import io.camunda.zeebe.protocol.record.MessageHeaderEncoder;
import io.camunda.zeebe.util.buffer.BufferReader;
import io.camunda.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class ErrorResponse implements BufferWriter, BufferReader {

  private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
  private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();

  private final ErrorResponseEncoder bodyEncoder = new ErrorResponseEncoder();
  private final ErrorResponseDecoder bodyDecoder = new ErrorResponseDecoder();
  private final DirectBuffer errorData = new UnsafeBuffer();
  private ErrorCode errorCode;

  public ErrorResponse() {
    reset();
  }

  public ErrorResponse reset() {
    errorCode = ErrorCode.NULL_VAL;
    errorData.wrap(0, 0);

    return this;
  }

  public ErrorCode getErrorCode() {
    return errorCode;
  }

  public ErrorResponse setErrorCode(final ErrorCode errorCode) {
    this.errorCode = errorCode;
    return this;
  }

  public DirectBuffer getErrorData() {
    return errorData;
  }

  public ErrorResponse setErrorData(final DirectBuffer errorData) {
    this.errorData.wrap(errorData, 0, errorData.capacity());
    return this;
  }

  public boolean tryWrap(final DirectBuffer buffer) {
    return tryWrap(buffer, 0, buffer.capacity());
  }

  public boolean tryWrap(final DirectBuffer buffer, final int offset, final int length) {
    headerDecoder.wrap(buffer, offset);

    return headerDecoder.schemaId() == bodyDecoder.sbeSchemaId()
        && headerDecoder.templateId() == bodyDecoder.sbeTemplateId();
  }

  @Override
  public void wrap(final DirectBuffer buffer, int offset, final int length) {
    reset();

    final int frameEnd = offset + length;

    headerDecoder.wrap(buffer, offset);

    offset += headerDecoder.encodedLength();

    bodyDecoder.wrap(buffer, offset, headerDecoder.blockLength(), headerDecoder.version());

    errorCode = bodyDecoder.errorCode();

    offset += bodyDecoder.sbeBlockLength();

    final int errorDataLength = bodyDecoder.errorDataLength();
    offset += ErrorResponseDecoder.errorDataHeaderLength();

    if (errorDataLength > 0) {
      errorData.wrap(buffer, offset, errorDataLength);
      offset += errorDataLength;
    }

    bodyDecoder.limit(offset);

    assert bodyDecoder.limit() == frameEnd
        : "Decoder read only to position "
            + bodyDecoder.limit()
            + " but expected "
            + frameEnd
            + " as final position";
  }

  @Override
  public int getLength() {
    return MessageHeaderEncoder.ENCODED_LENGTH
        + ErrorResponseEncoder.BLOCK_LENGTH
        + ErrorResponseEncoder.errorDataHeaderLength()
        + errorData.capacity();
  }

  @Override
  public int write(final MutableDirectBuffer buffer, int offset) {
    final int initialOffset = offset;
    headerEncoder
        .wrap(buffer, offset)
        .blockLength(bodyEncoder.sbeBlockLength())
        .templateId(bodyEncoder.sbeTemplateId())
        .schemaId(bodyEncoder.sbeSchemaId())
        .version(bodyEncoder.sbeSchemaVersion());

    offset += headerEncoder.encodedLength();

    bodyEncoder
        .wrap(buffer, offset)
        .errorCode(errorCode)
        .putErrorData(errorData, 0, errorData.capacity());

    return bodyEncoder.limit() - initialOffset;
  }

  public byte[] toBytes() {
    final byte[] bytes = new byte[getLength()];
    final MutableDirectBuffer buffer = new UnsafeBuffer(bytes);
    write(buffer, 0);
    return bytes;
  }

  @Override
  public String toString() {
    return "ErrorResponse{" + "errorCode=" + errorCode + '}';
  }
}
