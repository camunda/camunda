/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.transport.impl;

import io.zeebe.dispatcher.FragmentHandler;
import io.zeebe.transport.RemoteAddress;
import io.zeebe.transport.RemoteAddressList;
import io.zeebe.transport.ServerControlMessageListener;
import io.zeebe.transport.ServerMessageHandler;
import io.zeebe.transport.ServerOutput;
import io.zeebe.transport.ServerRequestHandler;
import org.agrona.DirectBuffer;

public class ServerReceiveHandler implements FragmentHandler {
  protected final ServerOutput output;
  private final TransportHeaderDescriptor transportHeaderDescriptor =
      new TransportHeaderDescriptor();
  private final RequestResponseHeaderDescriptor requestResponseHeaderDescriptor =
      new RequestResponseHeaderDescriptor();
  private final RemoteAddressList remoteAddressList;
  private final ServerMessageHandler messageHandler;
  private final ServerRequestHandler requestHandler;
  private final ServerControlMessageListener controlMessageListener;

  public ServerReceiveHandler(
      ServerOutput output,
      RemoteAddressList remoteAddressList,
      ServerMessageHandler messageHandler,
      ServerRequestHandler requestHandler,
      ServerControlMessageListener controlMessageListener) {
    this.output = output;
    this.remoteAddressList = remoteAddressList;
    this.messageHandler = messageHandler;
    this.requestHandler = requestHandler;
    this.controlMessageListener = controlMessageListener;
  }

  @Override
  public int onFragment(
      DirectBuffer buffer, int readOffset, int length, int streamId, boolean isMarkedFailed) {
    int result = CONSUME_FRAGMENT_RESULT;

    final RemoteAddress remoteAddress = remoteAddressList.getByStreamId(streamId);

    transportHeaderDescriptor.wrap(buffer, readOffset);
    readOffset += TransportHeaderDescriptor.headerLength();
    length -= TransportHeaderDescriptor.headerLength();

    final int protocolId = transportHeaderDescriptor.protocolId();

    switch (protocolId) {
      case TransportHeaderDescriptor.REQUEST_RESPONSE:
        if (requestHandler != null) {
          requestResponseHeaderDescriptor.wrap(buffer, readOffset);
          readOffset += RequestResponseHeaderDescriptor.headerLength();
          length -= RequestResponseHeaderDescriptor.headerLength();

          final long requestId = requestResponseHeaderDescriptor.requestId();
          result =
              requestHandler.onRequest(output, remoteAddress, buffer, readOffset, length, requestId)
                  ? CONSUME_FRAGMENT_RESULT
                  : POSTPONE_FRAGMENT_RESULT;
        }

        break;

      case TransportHeaderDescriptor.FULL_DUPLEX_SINGLE_MESSAGE:
        if (messageHandler != null) {
          result =
              messageHandler.onMessage(output, remoteAddress, buffer, readOffset, length)
                  ? CONSUME_FRAGMENT_RESULT
                  : POSTPONE_FRAGMENT_RESULT;
        }

        break;

      case TransportHeaderDescriptor.CONTROL_MESSAGE:
        if (controlMessageListener != null) {
          final int messageType =
              buffer.getInt(readOffset, ControlMessages.CONTROL_MESSAGE_BYTEORDER);
          controlMessageListener.onMessage(output, remoteAddress, messageType);
        }

        break;

      default:
        // ignore / fail
        result = FAILED_FRAGMENT_RESULT;
    }

    return result;
  }
}
