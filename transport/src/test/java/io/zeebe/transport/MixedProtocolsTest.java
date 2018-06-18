/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.transport;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.test.util.AutoCloseableRule;
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
  public ActorSchedulerRule actorSchedulerRule = new ActorSchedulerRule(3);
  public AutoCloseableRule closeables = new AutoCloseableRule();

  @Rule public RuleChain ruleChain = RuleChain.outerRule(actorSchedulerRule).around(closeables);

  protected final UnsafeBuffer requestBuffer = new UnsafeBuffer(new byte[1024]);
  protected final DirectBufferWriter bufferWriter = new DirectBufferWriter();

  protected final TransportMessage message = new TransportMessage();

  @Test
  public void shouldEchoMessages() throws InterruptedException, ExecutionException {

    final SocketAddress addr = new SocketAddress("localhost", 51115);
    final int numRequests = 1000;

    final ClientTransport clientTransport =
        Transports.newClientTransport().scheduler(actorSchedulerRule.get()).build();
    closeables.manage(clientTransport);

    final ReverseOrderChannelHandler handler = new ReverseOrderChannelHandler();

    final ServerTransport serverTransport =
        Transports.newServerTransport()
            .bindAddress(addr.toInetSocketAddress())
            .scheduler(actorSchedulerRule.get())
            .build(handler, handler);
    closeables.manage(serverTransport);

    final RemoteAddress remoteAddress = clientTransport.registerRemoteAndAwaitChannel(addr);

    for (int i = 0; i < numRequests; i++) {
      requestBuffer.putInt(0, i);
      bufferWriter.wrap(requestBuffer, 0, requestBuffer.capacity());
      final ActorFuture<ClientResponse> responseFuture =
          clientTransport.getOutput().sendRequest(remoteAddress, bufferWriter);

      requestBuffer.putInt(0, numRequests - i);
      message.reset().buffer(requestBuffer).remoteAddress(remoteAddress);

      final boolean success = clientTransport.getOutput().sendMessage(message);
      if (!success) {
        throw new RuntimeException("Could not send message");
      }

      final ClientResponse response = responseFuture.join();
      assertThat(response.getResponseBuffer().getInt(0)).isEqualTo(i);
    }
  }

  /**
   * Echos messages by copying to the send buffer, but inverts the order of request-response
   * messages and single messages. I.e. on a {@link Protocols#REQUEST_RESPONSE} messages, it waits
   * for the next {@link Protocols#FULL_DUPLEX_SINGLE_MESSAGE} messages, echos this message, and
   * only then echos the first message.
   */
  public static class ReverseOrderChannelHandler
      implements ServerMessageHandler, ServerRequestHandler {
    protected UnsafeBuffer requestResponseMessageBuffer;

    protected final ServerResponse response = new ServerResponse();
    protected final TransportMessage message = new TransportMessage();

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
      message.reset().buffer(buffer, offset, length).remoteAddress(remoteAddress);

      return output.sendMessage(message);
    }
  }
}
