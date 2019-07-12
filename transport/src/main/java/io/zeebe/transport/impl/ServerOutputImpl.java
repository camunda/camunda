/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.transport.impl;

import io.zeebe.transport.ServerOutput;
import io.zeebe.transport.ServerResponse;
import io.zeebe.transport.impl.sender.OutgoingMessage;
import io.zeebe.transport.impl.sender.Sender;
import io.zeebe.transport.impl.sender.TransportHeaderWriter;
import io.zeebe.util.buffer.BufferWriter;
import java.nio.ByteBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class ServerOutputImpl implements ServerOutput {
  private static final long NO_RETRIES = 0;

  private final Sender sender;

  public ServerOutputImpl(Sender sender) {
    this.sender = sender;
  }

  @Override
  public boolean sendMessage(int remoteStreamId, BufferWriter writer) {
    final int framedMessageLength =
        TransportHeaderWriter.getFramedMessageLength(writer.getLength());

    final ByteBuffer allocatedBuffer = sender.allocateMessageBuffer(framedMessageLength);

    if (allocatedBuffer != null) {
      try {
        final UnsafeBuffer bufferView = new UnsafeBuffer(allocatedBuffer);
        final TransportHeaderWriter headerWriter = new TransportHeaderWriter();
        headerWriter.wrapMessage(bufferView, writer, remoteStreamId);

        final OutgoingMessage outgoingMessage =
            new OutgoingMessage(remoteStreamId, bufferView, NO_RETRIES);

        sender.submitMessage(outgoingMessage);

        return true;
      } catch (RuntimeException e) {
        sender.reclaimMessageBuffer(allocatedBuffer);
        throw e;
      }
    } else {
      return false;
    }
  }

  @Override
  public boolean sendResponse(ServerResponse response) {
    final BufferWriter writer = response.getWriter();
    final int framedLength = TransportHeaderWriter.getFramedRequestLength(writer.getLength());

    final ByteBuffer allocatedBuffer = sender.allocateMessageBuffer(framedLength);

    if (allocatedBuffer != null) {
      try {
        final int remoteStreamId = response.getRemoteStreamId();
        final long requestId = response.getRequestId();

        final UnsafeBuffer bufferView = new UnsafeBuffer(allocatedBuffer);
        final TransportHeaderWriter headerWriter = new TransportHeaderWriter();

        headerWriter.wrapRequest(bufferView, writer);

        headerWriter.setStreamId(remoteStreamId).setRequestId(requestId);

        final OutgoingMessage outgoingMessage =
            new OutgoingMessage(remoteStreamId, bufferView, NO_RETRIES);

        sender.submitMessage(outgoingMessage);

        return true;
      } catch (RuntimeException e) {
        sender.reclaimMessageBuffer(allocatedBuffer);
        throw e;
      }
    } else {
      return false;
    }
  }
}
