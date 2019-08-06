/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.transport;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.test.util.AutoCloseableRule;
import io.zeebe.transport.impl.TransportHeaderDescriptor;
import io.zeebe.transport.impl.util.SocketUtil;
import io.zeebe.util.buffer.DirectBufferWriter;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.testing.ActorSchedulerRule;
import java.util.concurrent.ExecutionException;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class MixedProtocolsTest {
  protected final UnsafeBuffer requestBuffer = new UnsafeBuffer(new byte[1024]);
  protected final DirectBufferWriter bufferWriter = new DirectBufferWriter();
  public ActorSchedulerRule actorSchedulerRule = new ActorSchedulerRule(3);
  public AutoCloseableRule closeables = new AutoCloseableRule();
  @Rule public RuleChain ruleChain = RuleChain.outerRule(actorSchedulerRule).around(closeables);

  @Test
  public void shouldEchoMessages() throws InterruptedException, ExecutionException {
    final int nodeId = 1;
    final SocketAddress addr = SocketUtil.getNextAddress();
    final int numRequests = 1000;

    final ClientTransport clientTransport =
        Transports.newClientTransport("test").scheduler(actorSchedulerRule.get()).build();
    closeables.manage(clientTransport);

    final ReverseOrderChannelHandler handler = new ReverseOrderChannelHandler();

    final ServerTransport serverTransport =
        Transports.newServerTransport()
            .bindAddress(addr.toInetSocketAddress())
            .scheduler(actorSchedulerRule.get())
            .build(handler, handler);
    closeables.manage(serverTransport);

    clientTransport.registerEndpointAndAwaitChannel(nodeId, addr);

    for (int i = 0; i < numRequests; i++) {
      requestBuffer.putInt(0, i);
      bufferWriter.wrap(requestBuffer, 0, requestBuffer.capacity());
      final ActorFuture<ClientResponse> responseFuture =
          clientTransport.getOutput().sendRequest(nodeId, bufferWriter);

      requestBuffer.putInt(0, numRequests - i);
      final boolean success = clientTransport.getOutput().sendMessage(nodeId, bufferWriter);
      if (!success) {
        throw new RuntimeException("Could not send message");
      }

      final ClientResponse response = responseFuture.join();
      assertThat(response.getResponseBuffer().getInt(0)).isEqualTo(i);
    }
  }

  /**
   * Echos messages by copying to the send buffer, but inverts the order of request-response
   * messages and single messages. I.e. on a {@link TransportHeaderDescriptor#REQUEST_RESPONSE}
   * messages, it waits for the next {@link TransportHeaderDescriptor#FULL_DUPLEX_SINGLE_MESSAGE}
   * messages, echos this message, and only then echos the first message.
   */
  public static class ReverseOrderChannelHandler
      implements ServerMessageHandler, ServerRequestHandler {
    protected final ServerResponse response = new ServerResponse();
    protected UnsafeBuffer requestResponseMessageBuffer;

    public ReverseOrderChannelHandler() {
      this.requestResponseMessageBuffer = new UnsafeBuffer(new byte[1024 * 1024]);
    }

    @Override
    public boolean onRequest(
        ServerOutput output,
        RemoteAddress remoteAddress,
        DirectBuffer buffer,
        int offset,
        int length,
        long requestId) {
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
        ServerOutput output,
        RemoteAddress remoteAddress,
        DirectBuffer buffer,
        int offset,
        int length) {
      return output.sendMessage(
          remoteAddress.getStreamId(), new DirectBufferWriter().wrap(buffer, offset, length));
    }
  }
}
