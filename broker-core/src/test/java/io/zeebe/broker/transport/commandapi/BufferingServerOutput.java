/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.transport.commandapi;

import io.zeebe.protocol.record.ErrorResponseDecoder;
import io.zeebe.protocol.record.MessageHeaderDecoder;
import io.zeebe.transport.ServerOutput;
import io.zeebe.transport.ServerResponse;
import io.zeebe.transport.impl.RequestResponseHeaderDescriptor;
import io.zeebe.transport.impl.TransportHeaderDescriptor;
import io.zeebe.util.buffer.BufferReader;
import io.zeebe.util.buffer.BufferWriter;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.sbe.MessageDecoderFlyweight;

public class BufferingServerOutput implements ServerOutput {
  public static final int MESSAGE_START_OFFSET =
      TransportHeaderDescriptor.HEADER_LENGTH + RequestResponseHeaderDescriptor.HEADER_LENGTH;
  protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
  protected final ErrorResponseDecoder errorDecoder = new ErrorResponseDecoder();

  protected List<DirectBuffer> sentResponses = new CopyOnWriteArrayList<>();

  @Override
  public boolean sendMessage(int remoteStreamId, BufferWriter writer) {
    // ignore; not yet implemented
    return true;
  }

  @Override
  public boolean sendResponse(ServerResponse response) {
    final UnsafeBuffer buf = new UnsafeBuffer(new byte[response.getLength()]);
    response.write(buf, 0);
    sentResponses.add(buf);
    return true;
  }

  public List<DirectBuffer> getSentResponses() {
    return sentResponses;
  }

  public ErrorResponseDecoder getAsErrorResponse(int index) {
    return getAs(index, errorDecoder);
  }

  public void wrapResponse(final int index, final BufferReader reader) {
    final DirectBuffer buffer = sentResponses.get(index);
    reader.wrap(buffer, MESSAGE_START_OFFSET, buffer.capacity());
  }

  public int getTemplateId(final int index) {
    final DirectBuffer sentResponse = sentResponses.get(index);
    headerDecoder.wrap(sentResponse, MESSAGE_START_OFFSET);

    return headerDecoder.templateId();
  }

  protected <T extends MessageDecoderFlyweight> T getAs(int index, T decoder) {
    final DirectBuffer sentResponse = sentResponses.get(index);
    headerDecoder.wrap(sentResponse, MESSAGE_START_OFFSET);
    decoder.wrap(
        sentResponse,
        MESSAGE_START_OFFSET + headerDecoder.encodedLength(),
        headerDecoder.blockLength(),
        headerDecoder.version());

    return decoder;
  }
}
