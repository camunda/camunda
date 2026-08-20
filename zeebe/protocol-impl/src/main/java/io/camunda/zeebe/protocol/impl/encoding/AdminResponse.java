/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.encoding;

import io.camunda.zeebe.protocol.management.AdminResponseDecoder;
import io.camunda.zeebe.protocol.management.AdminResponseEncoder;
import io.camunda.zeebe.protocol.management.MessageHeaderDecoder;
import io.camunda.zeebe.protocol.management.MessageHeaderEncoder;
import io.camunda.zeebe.util.buffer.BufferReader;
import io.camunda.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

public class AdminResponse implements BufferReader, BufferWriter {
  private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
  private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
  private final AdminResponseEncoder bodyEncoder = new AdminResponseEncoder();
  private final AdminResponseDecoder bodyDecoder = new AdminResponseDecoder();

  @Override
  public void wrap(final DirectBuffer buffer, final int offset, final int length) {
    bodyDecoder.wrapAndApplyHeader(buffer, offset, headerDecoder);
  }

  @Override
  public int getLength() {
    return headerEncoder.encodedLength() + bodyEncoder.sbeBlockLength();
  }

  @Override
  public int write(final MutableDirectBuffer buffer, final int offset) {
    bodyEncoder.wrapAndApplyHeader(buffer, offset, headerEncoder);
    return getLength();
  }

  public byte[] getPayload() {
    final byte[] payload = new byte[bodyDecoder.payloadLength()];
    bodyDecoder.getPayload(payload, 0, payload.length);
    return payload;
  }
}
