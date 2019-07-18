/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.transport.impl;

import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.dispatcher.FragmentHandler;
import io.zeebe.transport.ClientInputListener;
import io.zeebe.transport.impl.sender.Sender;
import java.util.List;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class ClientReceiveHandler implements FragmentHandler {
  protected final Sender requestPool;
  protected final Dispatcher receiveBuffer;
  protected final List<ClientInputListener> listeners;
  private final TransportHeaderDescriptor transportHeaderDescriptor =
      new TransportHeaderDescriptor();
  private final RequestResponseHeaderDescriptor requestResponseHeaderDescriptor =
      new RequestResponseHeaderDescriptor();

  public ClientReceiveHandler(
      Sender requestPool, Dispatcher receiveBuffer, List<ClientInputListener> listeners) {
    this.requestPool = requestPool;
    this.receiveBuffer = receiveBuffer;
    this.listeners = listeners;
  }

  @Override
  public int onFragment(
      DirectBuffer buffer, int readOffset, int length, int streamId, boolean isMarkedFailed) {
    transportHeaderDescriptor.wrap(buffer, readOffset);
    readOffset += TransportHeaderDescriptor.headerLength();
    length -= TransportHeaderDescriptor.headerLength();

    final int protocolId = transportHeaderDescriptor.protocolId();

    switch (protocolId) {
      case TransportHeaderDescriptor.REQUEST_RESPONSE:
        requestResponseHeaderDescriptor.wrap(buffer, readOffset);
        readOffset += RequestResponseHeaderDescriptor.headerLength();
        length -= RequestResponseHeaderDescriptor.headerLength();

        final long requestId = requestResponseHeaderDescriptor.requestId();

        final UnsafeBuffer responseBuffer = new UnsafeBuffer(new byte[length]);
        buffer.getBytes(readOffset, responseBuffer, 0, length);

        invokeResponseListeners(streamId, requestId, buffer, readOffset, length);
        requestPool.submitResponse(new IncomingResponse(requestId, responseBuffer));

        return CONSUME_FRAGMENT_RESULT;

      case TransportHeaderDescriptor.FULL_DUPLEX_SINGLE_MESSAGE:
        if (!isMarkedFailed) {
          final int result = onMessage(buffer, readOffset, length, streamId);
          if (result == CONSUME_FRAGMENT_RESULT) {
            invokeMessageListeners(streamId, buffer, readOffset, length);
          }
          return result;
        } else {
          return CONSUME_FRAGMENT_RESULT;
        }

      default:
        // ignore / fail

    }

    return CONSUME_FRAGMENT_RESULT;
  }

  protected int onMessage(DirectBuffer buffer, int offset, int length, int streamId) {
    if (receiveBuffer == null) {
      return CONSUME_FRAGMENT_RESULT;
    }

    final long offerPosition = receiveBuffer.offer(buffer, offset, length, streamId);
    if (offerPosition < 0) {
      return POSTPONE_FRAGMENT_RESULT;
    } else {
      return CONSUME_FRAGMENT_RESULT;
    }
  }

  protected void invokeMessageListeners(int streamId, DirectBuffer buf, int offset, int length) {
    if (listeners != null) {
      for (int i = 0; i < listeners.size(); i++) {
        listeners.get(i).onMessage(streamId, buf, offset, length);
      }
    }
  }

  protected void invokeResponseListeners(
      int streamId, long requestId, DirectBuffer buf, int offset, int length) {
    if (listeners != null) {
      for (int i = 0; i < listeners.size(); i++) {
        listeners.get(i).onResponse(streamId, requestId, buf, offset, length);
      }
    }
  }
}
