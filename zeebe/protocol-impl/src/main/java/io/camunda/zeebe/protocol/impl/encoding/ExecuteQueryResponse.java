/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.encoding;

import io.camunda.zeebe.protocol.record.ExecuteQueryResponseDecoder;
import io.camunda.zeebe.protocol.record.ExecuteQueryResponseEncoder;
import io.camunda.zeebe.protocol.record.MessageHeaderDecoder;
import io.camunda.zeebe.protocol.record.MessageHeaderEncoder;
import io.camunda.zeebe.util.buffer.BufferReader;
import io.camunda.zeebe.util.buffer.BufferUtil;
import io.camunda.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class ExecuteQueryResponse implements BufferReader, BufferWriter {
  private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
  private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();

  private final ExecuteQueryResponseEncoder bodyEncoder = new ExecuteQueryResponseEncoder();
  private final ExecuteQueryResponseDecoder bodyDecoder = new ExecuteQueryResponseDecoder();

  private final DirectBuffer rawBpmnProcessId = new UnsafeBuffer();

  public ExecuteQueryResponse() {
    reset();
  }

  public ExecuteQueryResponse reset() {
    rawBpmnProcessId.wrap(0, 0);
    return this;
  }

  public String getBpmnProcessId() {
    return BufferUtil.bufferAsString(rawBpmnProcessId);
  }

  public ExecuteQueryResponse setBpmnProcessId(final DirectBuffer bpmnProcessId) {
    rawBpmnProcessId.wrap(bpmnProcessId);
    return this;
  }

  @Override
  public int getLength() {
    return headerEncoder.encodedLength() + bodyEncoder.sbeBlockLength();
  }

  @Override
  public int write(final MutableDirectBuffer buffer, final int offset) {
    bodyEncoder
        .wrapAndApplyHeader(buffer, offset, headerEncoder)
        .putBpmnProcessId(rawBpmnProcessId, 0, rawBpmnProcessId.capacity());
    return getLength();
  }

  @Override
  public void wrap(final DirectBuffer buffer, final int offset, final int length) {
    bodyDecoder.wrapAndApplyHeader(buffer, offset, headerDecoder);
    bodyDecoder.wrapBpmnProcessId(rawBpmnProcessId);
  }
}
