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

import static io.zeebe.test.util.TestUtil.doRepeatedly;
import static io.zeebe.util.buffer.DirectBufferWriter.writerFor;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.dispatcher.Dispatchers;
import io.zeebe.test.util.AutoCloseableRule;
import io.zeebe.transport.impl.TransportHeaderDescriptor;
import io.zeebe.transport.impl.util.SocketUtil;
import io.zeebe.transport.util.RecordingMessageHandler;
import io.zeebe.transport.util.TransportTestUtil;
import io.zeebe.util.ByteValue;
import io.zeebe.util.sched.testing.ActorSchedulerRule;
import java.util.concurrent.atomic.AtomicInteger;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class BufferingServerTransportTest {
  public static final ByteValue BUFFER_SIZE = ByteValue.ofKilobytes(16);
  public static final SocketAddress SERVER_ADDRESS = SocketUtil.getNextAddress();
  public static final int NODE_ID = 1;

  public ActorSchedulerRule actorSchedulerRule = new ActorSchedulerRule(3);
  public AutoCloseableRule closeables = new AutoCloseableRule();

  @Rule public RuleChain ruleChain = RuleChain.outerRule(actorSchedulerRule).around(closeables);

  protected ClientTransport clientTransport;
  protected BufferingServerTransport serverTransport;

  protected RecordingMessageHandler serverHandler = new RecordingMessageHandler();
  private Dispatcher serverReceiveBuffer;

  @Before
  public void setUp() {
    clientTransport =
        Transports.newClientTransport("test").scheduler(actorSchedulerRule.get()).build();
    closeables.manage(clientTransport);

    serverReceiveBuffer =
        Dispatchers.create("serverReceiveBuffer")
            .bufferSize(BUFFER_SIZE)
            .actorScheduler(actorSchedulerRule.get())
            .build();
    closeables.manage(serverReceiveBuffer);

    serverTransport =
        Transports.newServerTransport()
            .scheduler(actorSchedulerRule.get())
            .bindAddress(SERVER_ADDRESS.toInetSocketAddress())
            .buildBuffering(serverReceiveBuffer);
    closeables.manage(serverTransport);
  }

  @Test
  public void shouldPostponeMessagesOnReceiveBufferBackpressure() throws InterruptedException {
    // given
    final int maximumMessageLength =
        serverReceiveBuffer.getMaxFrameLength()
            - TransportHeaderDescriptor.HEADER_LENGTH
            - 1; // https://github.com/zeebe-io/zb-dispatcher/issues/21

    final DirectBuffer largeBuf = new UnsafeBuffer(new byte[maximumMessageLength]);

    final int messagesToExhaustReceiveBuffer =
        ((int) BUFFER_SIZE.toBytes() / largeBuf.capacity()) + 1;

    clientTransport.registerEndpoint(NODE_ID, SERVER_ADDRESS);

    final ServerInputSubscription serverSubscription =
        serverTransport.openSubscription("foo", serverHandler, null).join();

    // exhaust server's receive buffer
    for (int i = 0; i < messagesToExhaustReceiveBuffer; i++) {
      doRepeatedly(() -> clientTransport.getOutput().sendMessage(NODE_ID, writerFor(largeBuf)))
          .until(s -> s);
    }

    TransportTestUtil.waitUntilExhausted(serverReceiveBuffer);
    Thread.sleep(200L); // give transport some time to try to push things on top

    // when
    final AtomicInteger receivedMessages = new AtomicInteger(0);
    doRepeatedly(
            () -> {
              final int polledMessages = serverSubscription.poll();
              return receivedMessages.addAndGet(polledMessages);
            })
        .until(m -> m == messagesToExhaustReceiveBuffer);

    // then
    assertThat(receivedMessages.get()).isEqualTo(messagesToExhaustReceiveBuffer);
  }
}
