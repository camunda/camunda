/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.test.broker.protocol.commandapi;

import io.zeebe.protocol.record.ErrorResponseDecoder;
import io.zeebe.protocol.record.ExecuteCommandResponseDecoder;
import io.zeebe.protocol.record.MessageHeaderDecoder;
import io.zeebe.protocol.record.RecordType;
import io.zeebe.protocol.record.RejectionType;
import io.zeebe.protocol.record.ValueType;
import io.zeebe.protocol.record.intent.Intent;
import io.zeebe.test.broker.protocol.MsgPackHelper;
import io.zeebe.util.buffer.BufferReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import org.agrona.DirectBuffer;
import org.agrona.LangUtil;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.io.DirectBufferInputStream;

public final class ExecuteCommandResponse implements BufferReader {
  protected final ErrorResponse errorResponse;
  protected final MsgPackHelper msgPackHelper;
  protected Map<String, Object> value;
  private final MessageHeaderDecoder messageHeaderDecoder = new MessageHeaderDecoder();
  private final ExecuteCommandResponseDecoder responseDecoder = new ExecuteCommandResponseDecoder();
  private final DirectBuffer responseBuffer = new UnsafeBuffer();
  private int valueLengthOffset;
  private String rejectionReason;

  public ExecuteCommandResponse(final MsgPackHelper msgPackHelper) {
    this.msgPackHelper = msgPackHelper;
    errorResponse = new ErrorResponse(msgPackHelper);
  }

  public Map<String, Object> getValue() {
    return value;
  }

  public DirectBuffer getRawValue() {
    responseDecoder.limit(valueLengthOffset);
    final int valueLength = responseDecoder.valueLength();
    final int valueOffset = valueLengthOffset + ExecuteCommandResponseDecoder.valueHeaderLength();

    final UnsafeBuffer buf = new UnsafeBuffer(responseDecoder.buffer(), valueOffset, valueLength);
    return buf;
  }

  public long getKey() {
    return responseDecoder.key();
  }

  public int getPartitionId() {
    return responseDecoder.partitionId();
  }

  public ValueType getValueType() {
    return responseDecoder.valueType();
  }

  public Intent getIntent() {
    return Intent.fromProtocolValue(responseDecoder.valueType(), responseDecoder.intent());
  }

  public RecordType getRecordType() {
    return responseDecoder.recordType();
  }

  public RejectionType getRejectionType() {
    return responseDecoder.rejectionType();
  }

  public String getRejectionReason() {
    return rejectionReason;
  }

  @Override
  public void wrap(final DirectBuffer responseBuffer, final int offset, final int length) {
    messageHeaderDecoder.wrap(responseBuffer, offset);

    if (messageHeaderDecoder.templateId() != responseDecoder.sbeTemplateId()) {
      if (messageHeaderDecoder.templateId() == ErrorResponseDecoder.TEMPLATE_ID) {
        errorResponse.wrap(responseBuffer, offset + messageHeaderDecoder.encodedLength(), length);
        throw new RuntimeException(
            "Unexpected error response from broker: "
                + errorResponse.getErrorCode()
                + " - "
                + errorResponse.getErrorData());
      } else {
        throw new RuntimeException(
            "Unexpected response from broker. Template id " + messageHeaderDecoder.templateId());
      }
    }

    responseDecoder.wrap(
        responseBuffer,
        offset + messageHeaderDecoder.encodedLength(),
        messageHeaderDecoder.blockLength(),
        messageHeaderDecoder.version());

    valueLengthOffset = responseDecoder.limit();
    final int valueLength = responseDecoder.valueLength();
    final int valueOffset = valueLengthOffset + ExecuteCommandResponseDecoder.valueHeaderLength();
    this.responseBuffer.wrap(responseBuffer, valueOffset, valueLength);

    try (final InputStream is =
        new DirectBufferInputStream(responseBuffer, valueOffset, valueLength)) {
      value = msgPackHelper.readMsgPack(is);
    } catch (final IOException e) {
      LangUtil.rethrowUnchecked(e);
    }

    responseDecoder.limit(valueOffset + valueLength);
    rejectionReason = responseDecoder.rejectionReason();
  }

  public <T extends BufferReader> T readInto(final T record) {
    record.wrap(responseBuffer, 0, responseBuffer.capacity());
    return record;
  }
}
