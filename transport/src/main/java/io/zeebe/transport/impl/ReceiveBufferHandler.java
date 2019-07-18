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
import org.agrona.DirectBuffer;

public class ReceiveBufferHandler implements FragmentHandler {
  protected final Dispatcher receiveBuffer;
  private final TransportHeaderDescriptor transportHeaderDescriptor =
      new TransportHeaderDescriptor();

  public ReceiveBufferHandler(Dispatcher receiveBuffer) {
    this.receiveBuffer = receiveBuffer;
  }

  @Override
  public int onFragment(
      DirectBuffer buffer, int offset, int length, int streamId, boolean isMarkedFailed) {
    if (receiveBuffer == null) {
      return CONSUME_FRAGMENT_RESULT;
    }

    if (!isMarkedFailed) {
      transportHeaderDescriptor.wrap(buffer, offset);
      if (transportHeaderDescriptor.protocolId() == TransportHeaderDescriptor.CONTROL_MESSAGE) {
        // don't forward control messages
        return CONSUME_FRAGMENT_RESULT;
      }

      final long offerPosition = receiveBuffer.offer(buffer, offset, length, streamId);
      if (offerPosition < 0) {
        return POSTPONE_FRAGMENT_RESULT;
      } else {
        return CONSUME_FRAGMENT_RESULT;
      }
    } else {
      return CONSUME_FRAGMENT_RESULT;
    }
  }
}
