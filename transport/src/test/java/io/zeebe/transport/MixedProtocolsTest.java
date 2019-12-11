/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.transport;

import io.zeebe.test.util.AutoCloseableRule;
import io.zeebe.transport.impl.TransportHeaderDescriptor;
import io.zeebe.util.buffer.DirectBufferWriter;
import io.zeebe.util.sched.testing.ActorSchedulerRule;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Rule;
import org.junit.rules.RuleChain;

public class MixedProtocolsTest {
  public final ActorSchedulerRule actorSchedulerRule = new ActorSchedulerRule(3);
  public final AutoCloseableRule closeables = new AutoCloseableRule();
  @Rule public RuleChain ruleChain = RuleChain.outerRule(actorSchedulerRule).around(closeables);
  protected final UnsafeBuffer requestBuffer = new UnsafeBuffer(new byte[1024]);
  protected final DirectBufferWriter bufferWriter = new DirectBufferWriter();

  /**
   * Echos messages by copying to the send buffer, but inverts the order of request-response
   * messages and single messages. I.e. on a {@link TransportHeaderDescriptor#REQUEST_RESPONSE}
   * messages, it waits for the next {@link TransportHeaderDescriptor#FULL_DUPLEX_SINGLE_MESSAGE}
   * messages, echos this message, and only then echos the first message.
   */
  public static class ReverseOrderChannelHandler
      implements ServerMessageHandler, ServerRequestHandler {
    protected final ServerResponse response = new ServerResponse();
    protected final UnsafeBuffer requestResponseMessageBuffer;

    public ReverseOrderChannelHandler() {
      this.requestResponseMessageBuffer = new UnsafeBuffer(new byte[1024 * 1024]);
    }

    @Override
    public boolean onRequest(
        final ServerOutput output,
        final RemoteAddress remoteAddress,
        final DirectBuffer buffer,
        final int offset,
        final int length,
        final long requestId) {
      requestResponseMessageBuffer.putBytes(0, buffer, offset, length);
      response
          .reset()
          .requestId(requestId)
          .remoteAddress(remoteAddress)
          .buffer(requestResponseMessageBuffer, 0, length);
      return output.sendResponse(response);
    }

    @Override
    public boolean onMessage(
        final ServerOutput output,
        final RemoteAddress remoteAddress,
        final DirectBuffer buffer,
        final int offset,
        final int length) {
      return output.sendMessage(
          remoteAddress.getStreamId(), new DirectBufferWriter().wrap(buffer, offset, length));
    }
  }
}
