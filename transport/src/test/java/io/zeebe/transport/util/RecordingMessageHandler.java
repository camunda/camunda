/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.transport.util;

import io.zeebe.transport.ClientMessageHandler;
import io.zeebe.transport.ClientOutput;
import io.zeebe.transport.RemoteAddress;
import io.zeebe.transport.ServerMessageHandler;
import io.zeebe.transport.ServerOutput;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class RecordingMessageHandler implements ServerMessageHandler, ClientMessageHandler {
  protected final List<ReceivedMessage> receivedMessages = new CopyOnWriteArrayList<>();

  @Override
  public boolean onMessage(
      final ServerOutput output,
      final RemoteAddress remoteAddress,
      final DirectBuffer buffer,
      final int offset,
      final int length) {
    recordMessage(remoteAddress, buffer, offset, length);

    return true;
  }

  @Override
  public boolean onMessage(
      final ClientOutput output,
      final RemoteAddress remoteAddress,
      final DirectBuffer buffer,
      final int offset,
      final int length) {
    recordMessage(remoteAddress, buffer, offset, length);

    return true;
  }

  private void recordMessage(
      final RemoteAddress remoteAddress,
      final DirectBuffer buffer,
      final int offset,
      final int length) {
    final ReceivedMessage msg = new ReceivedMessage();
    msg.remote = remoteAddress;
    msg.buffer = new UnsafeBuffer(new byte[length]);
    msg.buffer.putBytes(0, buffer, offset, length);

    receivedMessages.add(msg);
  }

  public ReceivedMessage getMessage(final int index) {
    return receivedMessages.get(index);
  }

  public int numReceivedMessages() {
    return receivedMessages.size();
  }

  public static class ReceivedMessage {
    protected RemoteAddress remote;
    protected UnsafeBuffer buffer;

    public RemoteAddress getRemote() {
      return remote;
    }

    public UnsafeBuffer getBuffer() {
      return buffer;
    }
  }
}
